# Runbook — the optional native kernel

The platform computes on a `ComputeEngine`. Two exist: `java`, which is always
present, and `openmp`, a C++ kernel reached through the foreign function API.
**The native one is optional.** Everything works without it, and this runbook
exists so that "the fast path is off" is a question with an answer rather than a
mystery.

## Build

```bash
make -C native info     # what was detected, without building
make -C native          # build build/libqrpkernel.{dylib,so}
make -C native clean    # remove it; the platform falls back to Java
```

macOS needs Homebrew's OpenMP runtime, since Apple clang ships none:

```bash
brew install libomp
```

Without it the Makefile still builds a working library, single-threaded, and says
so in its output.

## Verify

```bash
mvn -pl qrp-native test        # equivalence suite
mvn -pl qrp-native exec:java   # benchmark on this machine
```

The equivalence suite asserts the two engines agree **exactly** — no tolerance —
across four block sizes, five seeds and four draw counts, including counts either
side of the parallel threshold. If the kernel is not built, those tests skip and
print why; they never pass silently as though they had run.

## What it is worth

Measured on an Apple M-series laptop, 10 OpenMP threads, 2,000 observations,
block size 40:

| draws | java, 1 thread | java, parallel | openmp | vs 1 thread | vs parallel |
| --- | --- | --- | --- | --- | --- |
| 1,000 | 1.0 ms | 0.3 ms | 0.2 ms | 3.85x | 1.05x |
| 10,000 | 9.3 ms | 1.5 ms | 1.6 ms | 5.73x | 0.91x |
| 100,000 | 92.4 ms | 14.6 ms | 15.9 ms | 5.80x | 0.91x |

**The honest reading: the C++ kernel beats single-threaded Java by about 5.8x,
and does not beat Java's parallel streams.** It is marginally slower than them.
Both implementations parallelise the same outer loop, the JIT compiles the inner
summation about as well as `-O3` does, and the foreign-call boundary costs a
little on top.

That number is published rather than the flattering one. Reporting "5.8x faster
than Java" while quietly comparing against one thread would be the kind of
benchmark that survives exactly until someone reruns it.

Why the kernel stays despite that: it is the proof that the compute seam works
across a language boundary with *exact* numeric agreement, which is a stronger
claim about the architecture than a speedup would have been. If the inner loop
ever becomes something the JIT handles badly, the seam is already there.

## Failure modes

| Symptom | Cause | Fix |
| --- | --- | --- |
| `no libqrpkernel.dylib found; searched [...]` | never built, or `make clean` was run | `make -C native` |
| `reports ABI version N, expected M` | stale library from an older checkout | `make -C native clean all` |
| `qrp_bootstrap_means is missing` | library built from an older `kernel.cpp` | `make -C native clean all` |
| `could not bind ...: ... mach-o file, but is an incompatible architecture` | library built for a different CPU (x86 vs arm64) | rebuild on this machine |
| `threads : 1 (OpenMP)` in the benchmark | built without an OpenMP runtime | `brew install libomp`, then rebuild |
| A warning about restricted methods | `--enable-native-access` not passed | already set for tests and `exec:java`; add it to any custom launch |
| The workbench header says `compute engine: java` while `qrp list` says `openmp` | started with `mvn javafx:run`, whose forked JVM puts the implementation jars on the module path where their `ServiceLoader` providers are invisible | launch with `mvn -pl qrp-app exec:java -Dexec.args="workbench"`; changing the dependency scope does not help |

In every one of these the platform keeps working on the Java engine. There is no
configuration in which a missing or broken kernel produces *wrong* numbers rather
than *no* kernel — the ABI check exists precisely to keep that true.

## Rollback

```bash
make -C native clean
```

Selection then finds only the Java engine. Nothing else needs changing: no
configuration file names an engine, and `ComputeEngines.best()` asks each
candidate whether it is available rather than assuming.

## ABI version

`qrp_abi_version()` returns an integer that `KernelLibrary` checks on load.
**Bump it in `native/src/kernel.cpp` whenever a signature or a numeric
convention changes.** A stale library that still exports the right symbol names
would otherwise load cleanly and disagree with Java in the results, which is the
one failure this component must never have.
