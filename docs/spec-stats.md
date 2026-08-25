# Spec — `qrp-stats`

Status: implemented (step 5)

## Why this module exists

A backtest produces one number per metric from one ordering of history. The
useful question is what else could plausibly have happened: how wide is the
uncertainty around that mean return, how deep do the drawdowns get across
resampled paths, and is a correlation large enough to act on. This module answers
those, reproducibly.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Confidence intervals for the mean of a serially correlated return series |
| R2 | Monte Carlo paths that preserve the serial structure of the input |
| R3 | Jackknife bias and standard error for a correlation |
| R4 | Historical tail risk: VaR and expected shortfall |
| R5 | Identical results from the same seed, on any thread count |
| R6 | A compute seam a native kernel can plug into without changing callers |

## Design decisions

**D1 — The RNG is specified here, not inherited.** `SplitMix64` is five lines of
arithmetic with published constants, and a test pins its output from seed 0
against the reference vectors. The reason is step 6: the C++ kernel has to
produce *the same numbers*, and no two standard libraries agree on the internals
of their generators. Specifying the algorithm turns a cross-language equivalence
test from impossible into a transcription check.

**D2 — Each draw is seeded from the draw index, not from a shared stream.**
`SplitMix64.forDraw(seed, d)` means draw 7 is the same whether it ran first,
last, or on another thread. A shared stream would make results depend on
scheduling, so the parallel path could not be asserted equal to the sequential
one — and that assertion is the only reason to trust either. It is also what
allows the OpenMP kernel to parallelise the outer loop at all.

**D3 — Blocks, not individual observations.** Financial returns are serially
dependent, and an i.i.d. bootstrap destroys the runs that produce drawdowns. A
drawdown distribution from an i.i.d. resample is optimistic for the same reason a
shuffled deck has no straights. The scheme is written out in `Bootstrap` in
enough detail to transcribe: draw `d` uses `forDraw(seed, d)`, takes
`ceil(n / blockSize)` block starts uniformly from `[0, n - blockSize]`,
concatenates them in order, and truncates to `n`.

**D4 — Modulo bias is accepted, and quantified.** `nextInt` uses an unsigned
remainder, whose bias is of order `bound / 2^64`: below 1e-13 for any sample this
resamples. Rejection sampling would be more correct and would make the C++ side a
reimplementation rather than a transcription, which is the more expensive kind of
error.

**D5 — The percentile convention is named.** Linear interpolation between order
statistics, what R calls type 7 and NumPy uses by default. Nine conventions are
defensible; two bootstrap intervals computed under different ones disagree at
exactly the tails people quote.

**D6 — Risk figures are positive loss magnitudes.** A 95 % VaR of 0.045 means
4.5 % was lost or worse on the bad 5 % of days. Negative numbers read naturally
in code and badly in a report, and mixing the conventions is how a risk limit
ends up with the wrong sign.

**D7 — Tail risk is historical, not parametric.** The empirical tail of a return
series is fatter than a normal fitted to it, and the tail is the entire point of
the measure.

**D8 — The drawdown definition moved here, and the engine now delegates to it.**
`PerformanceMetrics.maxDrawdown` calls `EquityCurve.maxDrawdown`. A backtested
path and a simulated one have to be measured identically or the Monte Carlo
comparison compares two different quantities. `qrp-engine` therefore depends on
`qrp-stats`; the reverse would be wrong, since everything here works on plain
arrays and knows nothing about fills.

**D9 — The jackknife interval is a normal approximation, and says so.** It is
asymptotic: reasonable at a few hundred observations, poor at a dozen, and it can
extend past ±1 near a perfect correlation because it ignores the bounded range.
The bias and the standard error are therefore reported as separate fields rather
than folded into a single number that hides the assumption.

## Compute seam

`ComputeEngine` (from `qrp-core`) has two operations: `rollingMean` and
`bootstrapMeans`. `JavaComputeEngine` implements both, parallelising across
draws above a 256-draw threshold. `ComputeEngines.best()` takes the first
available engine discovered on the classpath and falls back to the portable one,
so a missing native library is a non-event rather than a failure. A test asserts
the sequential and parallel paths agree bit for bit, which is the property step 6
extends across languages.

## Out of scope

Parametric distribution fitting, GARCH or any volatility model, multiple-testing
corrections across a strategy search, and significance tests for Sharpe ratios.
The last one matters: nothing here corrects for the number of strategies tried
before one looked good, which is the largest source of false results in backtest
research and cannot be fixed inside a single run.
