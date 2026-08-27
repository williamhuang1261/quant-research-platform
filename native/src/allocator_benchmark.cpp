// Benchmarks the arena and malloc-backed allocators under the same
// reset-then-allocate loop the native kernel actually runs per draw:
// qrp_bootstrap_medians calls reset() then alloc() once per draw (see
// run_bootstrap_medians in kernel.cpp), so this benchmark reproduces that
// exact access pattern rather than a synthetic one that might favor either
// allocator.
//
// `make bench` builds and runs this with no arguments, printing the timing
// table for both allocators. The table in docs/spec-memory.md is pasted
// verbatim from a real run on the machine that wrote it, not estimated, and
// includes whichever configurations the arena does not win on rather than
// only the favorable ones.
//
// An optional argv[1] of "arena" or "malloc" runs only that allocator's
// configurations and skips the other -- this exists so `/usr/bin/time -l`
// (this machine has neither perf nor valgrind: perf is Linux-only, and
// valgrind has no Apple Silicon support) can attribute page reclaims and
// page faults to one allocation strategy at a time instead of reporting a
// single combined number for both.

#include "arena_allocator.hpp"

#include <chrono>
#include <cstdio>
#include <cstring>
#include <vector>

namespace {

struct Config {
    const char* label;
    std::size_t buffer_bytes;
    int iterations;
};

// Timed region starts after Alloc's constructor returns, matching how the
// kernel already pays the arena's one mmap call once per thread, outside
// the per-draw loop -- the loop below is reset()+alloc() only, exactly what
// run_bootstrap_medians executes once per draw.
template <typename Alloc>
double time_reset_then_alloc(std::size_t buffer_bytes, int iterations) {
    Alloc alloc(buffer_bytes);
    volatile void* sink = nullptr;  // keeps the loop from being optimized away

    const auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < iterations; ++i) {
        alloc.reset();
        void* p = alloc.alloc(buffer_bytes, alignof(double));
        sink = p;
    }
    const auto end = std::chrono::steady_clock::now();
    (void)sink;

    return std::chrono::duration<double, std::milli>(end - start).count();
}

}  // namespace

int main(int argc, char** argv) {
    const std::vector<Config> configs = {
        {"n=50 doubles (400 B), 10k draws", 400, 10'000},
        {"n=500 doubles (4000 B), 10k draws", 4'000, 10'000},
        {"n=500 doubles (4000 B), 1M draws", 4'000, 1'000'000},
        {"n=5000 doubles (40000 B), 100k draws", 40'000, 100'000},
    };

    const char* only = (argc > 1) ? argv[1] : nullptr;
    const bool run_arena = only == nullptr || std::strcmp(only, "arena") == 0;
    const bool run_malloc = only == nullptr || std::strcmp(only, "malloc") == 0;

    if (only == nullptr) {
        std::printf("%-40s %12s %12s %8s\n", "configuration", "arena (ms)", "malloc (ms)", "ratio");
        std::printf("%-40s %12s %12s %8s\n", "-------------", "----------", "-----------", "-----");
    } else {
        std::printf("%-40s %12s\n", "configuration", "time (ms)");
        std::printf("%-40s %12s\n", "-------------", "---------");
    }

    for (const Config& cfg : configs) {
        const double arena_ms = run_arena
            ? time_reset_then_alloc<qrp::ArenaAllocator>(cfg.buffer_bytes, cfg.iterations)
            : 0.0;
        const double malloc_ms = run_malloc
            ? time_reset_then_alloc<qrp::MallocAllocator>(cfg.buffer_bytes, cfg.iterations)
            : 0.0;

        if (only == nullptr) {
            std::printf("%-40s %12.3f %12.3f %7.2fx\n", cfg.label, arena_ms, malloc_ms,
                        malloc_ms / arena_ms);
        } else if (run_arena) {
            std::printf("%-40s %12.3f\n", cfg.label, arena_ms);
        } else {
            std::printf("%-40s %12.3f\n", cfg.label, malloc_ms);
        }
    }

    return 0;
}
