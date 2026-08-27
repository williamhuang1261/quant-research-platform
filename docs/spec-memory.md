# Spec — native allocators (`native/`, `qrp-native`)

Status: implemented (Extension 8, steps 1-4)

## Why this exists

Before this extension, `native/src/kernel.cpp` allocated no heap memory at
all: `qrp_rolling_mean` writes into a caller-supplied buffer with O(1) extra
memory, and `qrp_bootstrap_means` sums each resampled block on the fly
without ever materializing it. There was no scratch-buffer need in this
kernel to make an allocator's job real. A median changes that: finding the
median of a block-bootstrap resample requires every resampled value in hand
before it can be sorted, so `qrp_bootstrap_medians` (Extension 8, step 2)
introduces a genuine per-draw, per-thread scratch buffer, and this spec
covers the two allocation strategies behind it.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | `ArenaAllocator`: a page-aligned, `mmap`-backed bump allocator that never touches the global heap after construction |
| R2 | `MallocAllocator`: the identical `alloc`/`reset`/`capacity`/`used` surface over `malloc`/`free`, so a caller can swap strategies without changing any other code |
| R3 | Neither implementation overruns its reserved capacity; exhaustion returns `nullptr`, never undefined behavior |
| R4 | `qrp_bootstrap_medians` selects between the two via a runtime flag, and both must produce bit-identical output for the same seed |
| R5 | An honest, reproducible benchmark comparing the two under the kernel's actual access pattern |

## `ArenaAllocator`

(`native/src/arena_allocator.hpp`, `.cpp`)

One `mmap(MAP_PRIVATE | MAP_ANONYMOUS)` region, sized up to a whole number
of pages via `sysconf(_SC_PAGESIZE)`. `alloc(size, align)` bumps an internal
offset with alignment padding and returns `nullptr` if the request would
exceed the mapped region — the bounds check happens before the pointer is
handed out, so a caller can never write past the mapping. `reset()` rewinds
the offset to zero without unmapping, so the same pages serve many rounds of
allocation. The destructor `munmap`s once.

## `MallocAllocator`

(`native/src/arena_allocator.hpp`, `.cpp`)

The same four-method surface over `posix_memalign`/`free`. `capacity_bytes`
is a soft budget tracked in software — `malloc` itself has no fixed
capacity — so this class reports exhaustion the same way `ArenaAllocator`
does, for a fair comparison. `reset()` frees every allocation issued since
the last reset (or construction) and clears the tracked list; the
destructor calls `reset()`.

## `qrp_bootstrap_medians`

(`native/src/kernel.cpp`)

```c
void qrp_bootstrap_medians(const double* sample, int32_t n, int32_t draws,
                            int32_t block_size, uint64_t seed, int32_t use_arena,
                            double* out);
```

Same seeding rule as `qrp_bootstrap_means` (`seed_for_draw`, one
`SplitMix64` stream per draw, parallel only across draws). What differs:
each OpenMP thread constructs exactly one allocator — `ArenaAllocator` when
`use_arena` is nonzero, `MallocAllocator` otherwise — on entering the
parallel region, then calls `reset()` at the top of every draw before
materializing that draw's `n`-length resample into the freshly reset
buffer, sorting it, and taking the median. `reset()` running first means
the malloc path's previous draw is freed before the new one is allocated,
and the arena path's previous draw's bytes are simply reused — the same
loop shape drives both strategies, via a single template
(`run_bootstrap_medians<Alloc>`) instantiated once per type.

**D1 — no exception safety across the parallel region.** `Alloc`'s
constructor can throw `std::bad_alloc` if the underlying `mmap` or
`malloc`-family call fails. Nothing in this file catches it, on either the
existing functions or this one — a `double[n]` scratch allocation failing
is treated as a fatal resource-exhaustion condition, not a recoverable one,
consistent with every other function in `kernel.cpp` already having no
exception-safety net.

**D2 — Java-side exposure is deliberately outside `ComputeEngine`.**
`NativeComputeEngine.bootstrapMedians(...)` is a public method, not an
`@Override` of the `ComputeEngine` interface, because there is no portable
Java median engine in this platform to hold it to the same
bit-identical-agreement standard the interface's existing methods promise.
This mirrors why `openmpThreads()` is native-only rather than part of the
SPI.

## Benchmark

(`native/src/allocator_benchmark.cpp`, `make -C native bench`)

Reproduces the kernel's actual per-draw access pattern: construct the
allocator once (outside the timed region, matching how each OpenMP thread
pays the arena's one `mmap` call once per call, not once per draw), then
loop `reset()` + `alloc()` for the configured iteration count. A real run
on this machine (Apple M-series, macOS, `arm64`, `clang++ -O3`):

```
configuration                              arena (ms)  malloc (ms)    ratio
-------------                              ----------  -----------    -----
n=50 doubles (400 B), 10k draws                 0.042        0.720   17.08x
n=500 doubles (4000 B), 10k draws               0.042        0.685   16.24x
n=500 doubles (4000 B), 1M draws                4.245       42.291    9.96x
n=5000 doubles (40000 B), 100k draws            0.484       13.826   28.54x
```

Repeated runs vary by roughly +/-20% on the small configurations (sub-
millisecond totals are noisy at this scale) but the arena wins every
configuration tried, by 8x to 30x depending on buffer size and iteration
count — there was no configuration in this sweep where `malloc` won, so
none is reported as a counterexample; if a future configuration ever
reverses that, the honest number goes here rather than being dropped.

### Cache and page-fault behavior

**Neither `perf` nor `valgrind` is available on the machine that wrote this
extension** (macOS 26, Apple Silicon `arm64`): `perf` is Linux-only, and
`valgrind` has never shipped Apple Silicon support. Rather than omit this
section, `/usr/bin/time -l` (macOS's own `getrusage`-backed resource-usage
report) was used instead, run separately per allocator via
`allocator_benchmark`'s optional `arena`/`malloc` argument so each path's
numbers are not combined into one total:

```
                         page reclaims   page faults   instructions retired   cycles elapsed
arena-only run                    250             1               39,695,209        16,529,387
malloc-only run                   254             1              868,794,629       113,695,927
```

Page reclaims and page faults are nearly identical between the two paths at
this scale — both allocators' total footprint across the whole benchmark
(a few hundred KB to a few MB across the four configurations) comfortably
fits inside what the OS had already resident, so neither path is
demonstrably faulting more than the other here. What *does* differ sharply
is instructions retired (about 22x) and cycles elapsed (about 7x), tracking
the wall-clock ratio: the malloc path's overhead is dominated by
`posix_memalign`/`free`'s own per-call bookkeeping, not by the kernel
paging memory in, which is a different (and honestly weaker) claim than "the
arena reduces page faults" would have been. This is the real, measured
mechanism, not the one the original recommendation assumed before the
allocators existed to measure.

## What is deliberately not here

- **No free-list.** Neither allocator can reclaim part of its allocated
  space early; `reset()` (whole-arena or whole-malloc-tracking-list) is the
  only way back to empty. The kernel's access pattern — one scratch buffer
  per draw, dead by the time the next draw starts — never needs partial
  reclamation, so none was built.
- **No thread safety within one allocator instance.** Each OpenMP thread
  owns its own `Alloc`; sharing one instance across threads without
  external synchronization is undefined, and nothing here prevents a
  caller from doing that incorrectly.
- **No growth.** `ArenaAllocator`'s mapping is fixed at construction;
  exhaustion returns `nullptr` rather than remapping larger. A caller
  needing a bigger scratch buffer must construct a bigger allocator.
- **`perf`/`valgrind` cache-line and TLB-level detail.** `/usr/bin/time -l`
  reports process-level `getrusage` counters, not per-line cache misses or
  TLB behavior the way `perf stat -e cache-misses` or `valgrind --tool=
  cachegrind` would. That level of detail is simply unavailable on this
  machine for this extension.
