// Compute kernels for the Quantitative Research Platform.
//
// This file is a deliberate TRANSCRIPTION of the Java implementation in
// qrp-stats, not an independent one. Every result it produces must be
// bit-identical to the Java engine's, because the platform selects between them
// at runtime and a research result must not depend on which one was installed.
//
// Two consequences, both intentional:
//   * splitmix64 is reproduced constant for constant, and the draw seeding rule
//     (seed + draw_index * GOLDEN) matches SplitMix64.forDraw exactly;
//   * the summation ORDER inside a draw is the same, because floating point
//     addition is not associative and a "better" reduction here would silently
//     disagree with Java in the last bits.
//
// Only the loop OVER draws is parallel. Each draw is independently seeded, so
// the output does not depend on thread count or scheduling.

#include <algorithm>
#include <cstdint>
#include <cstddef>

#include "arena_allocator.hpp"

#ifdef _OPENMP
#include <omp.h>
#endif

namespace {

constexpr uint64_t GOLDEN_GAMMA = 0x9E3779B97F4A7C15ULL;

inline uint64_t next_long(uint64_t& state) {
    uint64_t z = (state += GOLDEN_GAMMA);
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
    z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
    return z ^ (z >> 31);
}

// Matches SplitMix64.nextInt: unsigned remainder, bias below 1e-13 for any
// bound this kernel sees. Rejection sampling would be a reimplementation
// rather than a transcription, which is the more expensive kind of difference.
inline int32_t next_int(uint64_t& state, int32_t bound) {
    return static_cast<int32_t>(next_long(state) % static_cast<uint64_t>(bound));
}

inline uint64_t seed_for_draw(uint64_t seed, int32_t draw_index) {
    return seed + static_cast<uint64_t>(static_cast<int64_t>(draw_index)) * GOLDEN_GAMMA;
}

// Unlike qrp_bootstrap_means, a median needs every resampled value in hand
// before it can answer, so each draw materializes a full-length resample
// into a scratch buffer. Alloc is ArenaAllocator or MallocAllocator,
// selected by the caller-visible use_arena flag at the bottom of this file;
// this template exists so the two paths are identical except for the one
// line that differs (which allocator each thread owns).
//
// Each thread constructs exactly one Alloc when it enters the parallel
// region and reset()s it between draws, so the arena path reserves its
// pages once per thread rather than once per draw. Alloc's constructor can
// throw std::bad_alloc if the underlying mmap/malloc call fails; nothing in
// this kernel (or the rest of this file) catches that, matching the
// no-exception-safety-net convention every other function here already has.
// A single-threaded double[n] arena or malloc request failing is a fatal
// resource-exhaustion condition, not a recoverable one.
template <typename Alloc>
void run_bootstrap_medians(const double* sample, int32_t n, int32_t draws,
                            int32_t block_size, uint64_t seed, double* out) {
    const int32_t starts = n - block_size + 1;
    const std::size_t scratch_bytes = static_cast<std::size_t>(n) * sizeof(double);

#ifdef _OPENMP
#pragma omp parallel
#endif
    {
        Alloc allocator(scratch_bytes);

#ifdef _OPENMP
#pragma omp for schedule(static)
#endif
        for (int32_t draw = 0; draw < draws; ++draw) {
            allocator.reset();
            double* resample = static_cast<double*>(
                allocator.alloc(scratch_bytes, alignof(double)));

            uint64_t state = seed_for_draw(seed, draw);
            int32_t filled = 0;
            while (filled < n) {
                const int32_t start = next_int(state, starts);
                const int32_t remaining = n - filled;
                const int32_t length = block_size < remaining ? block_size : remaining;
                for (int32_t i = 0; i < length; ++i) {
                    resample[filled + i] = sample[start + i];
                }
                filled += length;
            }

            std::sort(resample, resample + n);
            out[draw] = (n % 2 == 1)
                ? resample[n / 2]
                : 0.5 * (resample[n / 2 - 1] + resample[n / 2]);
        }
    }
}

}  // namespace

extern "C" {

// Bumped whenever a signature or a numeric convention changes, so a stale
// library is refused rather than loaded and quietly disagreed with.
int32_t qrp_abi_version(void) {
    return 1;
}

// 1 when this build has OpenMP, else 0. The Java side reports it in the
// benchmark so a "no speedup" result is explainable rather than mysterious.
int32_t qrp_openmp_threads(void) {
#ifdef _OPENMP
    return omp_get_max_threads();
#else
    return 1;
#endif
}

// Trailing arithmetic mean; first (window - 1) entries are NaN.
// Serial on purpose: it is memory bound, and a parallel prefix sum would change
// the summation order and therefore the last bits.
void qrp_rolling_mean(const double* values, int32_t n, int32_t window, double* out) {
    if (n <= 0 || window < 1) {
        return;
    }
    const double nan_value = 0.0 / 0.0;
    double sum = 0.0;
    for (int32_t i = 0; i < n; ++i) {
        out[i] = nan_value;
        sum += values[i];
        if (i >= window) {
            sum -= values[i - window];
        }
        if (i >= window - 1) {
            out[i] = sum / window;
        }
    }
}

// Means of `draws` moving-block resamples. Parallel across draws only.
void qrp_bootstrap_means(const double* sample, int32_t n, int32_t draws,
                         int32_t block_size, uint64_t seed, double* out) {
    if (n <= 0 || draws <= 0 || block_size < 1 || block_size > n) {
        return;
    }
    const int32_t starts = n - block_size + 1;

#ifdef _OPENMP
#pragma omp parallel for schedule(static)
#endif
    for (int32_t draw = 0; draw < draws; ++draw) {
        uint64_t state = seed_for_draw(seed, draw);
        double sum = 0.0;
        int32_t filled = 0;
        while (filled < n) {
            const int32_t start = next_int(state, starts);
            const int32_t remaining = n - filled;
            const int32_t length = block_size < remaining ? block_size : remaining;
            for (int32_t i = 0; i < length; ++i) {
                sum += sample[start + i];
            }
            filled += length;
        }
        out[draw] = sum / n;
    }
}

// Medians of `draws` moving-block resamples, same seeding rule as
// qrp_bootstrap_means but materializing each draw's full resample to sort
// it. use_arena selects the scratch-buffer strategy: nonzero for the
// mmap-backed ArenaAllocator, zero for the malloc-backed fallback -- both
// produce identical output for the same seed, and the caller-visible
// signature difference is that one flag.
void qrp_bootstrap_medians(const double* sample, int32_t n, int32_t draws,
                           int32_t block_size, uint64_t seed, int32_t use_arena,
                           double* out) {
    if (n <= 0 || draws <= 0 || block_size < 1 || block_size > n) {
        return;
    }
    if (use_arena != 0) {
        run_bootstrap_medians<qrp::ArenaAllocator>(sample, n, draws, block_size, seed, out);
    } else {
        run_bootstrap_medians<qrp::MallocAllocator>(sample, n, draws, block_size, seed, out);
    }
}

}  // extern "C"
