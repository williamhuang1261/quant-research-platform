# Spec — `qrp-signals`

Status: complete (Extension 5, all five steps)

## Why this module exists

`qrp-portfolio`'s `PortfolioOptimizer` SPI already takes `expectedReturns` as
an argument, but nothing in the platform produces that array from data —
every existing caller, including `PortfolioBacktestEngineTest`'s own golden
run, supplies a fixed placeholder view. `docs/spec-portfolio.md` states this
directly: *"no factor model — expected returns are supplied by the caller,
not estimated here."* This module is the first thing in the repo that turns
an indicator's output into a forecast, and — just as importantly — tests
whether that forecast has any real predictive relationship to what actually
happened next, rather than trusting a backtest's P&L as the only evidence.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A rank transform with average-rank tie handling, the input Spearman correlation needs (step 1, done) |
| R2 | A per-period information coefficient (Spearman rank correlation between a cross-sectional signal and the forward return it is trying to predict), reusing the platform's existing Pearson correlation rather than a second implementation (step 1, done) |
| R3 | A significance test over an IC time series — is the mean IC distinguishable from zero — reported honestly even when the answer is "no" (step 1, done) |
| R4 | A generator that turns one indicator's output across several instruments into a per-instrument expected-return forecast, in the exact shape `PortfolioBacktestEngine.run()` already accepts (step 2, done) |
| R5 | The CLI can drive the `portfolio` command from a generated signal instead of a hand-supplied view, and the report shows the IC and significance that justify trusting it (step 3, done) |

## Design decisions

**D1 — Spearman IC is Pearson correlation on ranks, not a second correlation
formula.** `InformationCoefficient.spearman` rank-transforms both arrays via
`RankTransform.ranks` and calls `JackknifeCorrelation.correlation` on the
result. `qrp-portfolio`'s `CovarianceEstimator` makes the identical choice for
covariance (`cov(i, j) = corr(i, j) * std(i) * std(j)`, reusing the same
Pearson routine) — this module continues that policy rather than adding a
competing "how related are these two series" implementation.

**D2 — ties rank by the average of the positions they span, not by sort
order.** Two cross-sectionally tied instruments (an indicator that returns
the same value for two names on the same bar is not unusual) each get the
mean of the ranks they jointly occupy. A naive "sort and take the index"
rank would silently prefer whichever tied instrument happened to sort first,
which is not a real signal.

**D3 — the significance test is a large-sample z-approximation, not a
Student's-t test.** `SignalSignificance.of` computes `z = mean(IC) /
(sampleStdDev(IC) / sqrt(n))` and reads a two-sided p-value off the standard
normal CDF (`NormalDistribution.cdf`, already used by `NormalQuantile`'s
Halley refinement and the option pricers' Greeks). The platform has no
t-distribution implementation and this module does not add one — the normal
approximation understates true uncertainty when the IC series has only a
handful of periods, and is the stated scope of this test rather than a
silent assumption. The same caveat already applies to `JackknifeCorrelation`'s
own confidence interval.

**D4 — a constant, nonzero IC across every period reports zero standard
error and maximal significance (`z = ±Infinity`, `p = 0.0`), rather than
`NaN` from a `0.0 / 0.0` division.** `mean / standardError` in Java returns
`NaN` when both are exactly zero; `SignalSignificance.of` special-cases
`standardError == 0.0` so a perfectly consistent signal reports a defined,
correct answer instead of propagating `NaN` into a report. In practice this
branch is only reachable bit-for-bit for a series of exact `0.0` repeats — a
repeated nonzero literal (e.g. forty copies of `0.30`) rarely recovers its own
mean exactly after summation and division, so its measured standard error
lands at the order of one ULP rather than an exact zero, and `zStatistic`
comes out very large rather than literally infinite. The test suite pins the
exact-zero case (`constantZeroIcIsNotSignificant`) deterministically and the
nonzero case (`constantNonzeroIcIsMaximallySignificant`) with a "very large,
not exactly infinite" tolerance, matching what floating-point arithmetic
actually produces rather than what the branch's name suggests it should.

**D5 — `qrp-signals` depended on `qrp-stats` alone through step 1.** The rank
transform, the IC and the significance test have no dependency on `qrp-core`'s
bar/instrument types or on any concrete indicator. Step 2 added `qrp-core` at
compile scope (for `BarSeries`, `DoubleSeries`, `Params`, the `Indicator` SPI)
and `qrp-indicators`/`qrp-data` at test scope only, for the golden-run test's
real RSI indicator and sample series.

**D6 — the forecast is built from an indicator's cross-sectional *rank*, not
its raw value.** `CrossSectionalSignalGenerator` rank-transforms every
instrument's indicator output at each bar before centering and scaling it.
Not every indicator's raw output is comparable across instruments: a simple
moving average is in absolute price units, so a $500 instrument's SMA being
numerically larger than a $50 instrument's says nothing about which is more
bullish. Rank order is comparable by construction regardless of the
indicator's units, and it is the same quantity `InformationCoefficient`
scores the forecast against — the generator and the scorer agree on what
"the signal" actually means. This is a caller responsibility the generator
does not detect: an indicator whose *rank* is not meaningfully comparable
across instruments (none of the platform's shipped ones fall in this
category) would still produce a forecast, just not a useful one — the same
trust boundary every other `Indicator` consumer in the platform already has.

**D7 — the golden-run test uses 14-period RSI, not a moving average,
specifically because RSI is bounded `[0, 100]` and therefore cross-sectionally
comparable (see D6).** The generator itself is indicator-agnostic; RSI was
chosen for the one test that has to mean something, not baked into the
production code as a default.

**D8 — the CLI scores a `--signal`'s IC over the whole loaded series, not
just the backtested date range.** `PortfolioRunner.scoreSignal` reuses every
bar the data provider returns, independent of `--lookback` or the rebalance
schedule, because the question "is this signal real" is about the forecast
itself, not about how the backtest happened to sample it. It reuses the same
construction `CrossSectionalSignalGeneratorGoldenRunTest` pins, so the CLI's
printed numbers are checkable against a test rather than only against
themselves.

**D9 — `PortfolioReportFormatter`'s caveat paragraph branches on whether a
signal was used, rather than keeping one static "no factor model" claim.**
The pre-Extension-5 report said "there is no factor model" unconditionally,
which became false the moment `--signal` estimates `expectedReturns` from
data. The no-signal path still states the flat-momentum placeholder
explicitly; the `--signal` path states that the view comes from a single
indicator's cross-sectional rank and that the IC/significance line printed
above it is the only evidence it is worth trusting — never silently claiming
more rigor than a single-indicator rank forecast actually has.

## What steps 2-3 added

- **Step 2** — `CrossSectionalSignalGenerator` (D6) and `ForwardReturns`
  (test-only, never read by the generator itself — see its own Javadoc for
  why that separation matters). A golden-run test pins 14-period RSI's actual
  measured IC against the bundled synthetic series (D7): 485 usable periods,
  mean IC +0.0278, standard error 0.0310, z +0.897, p 0.3695 — not
  significant at any conventional alpha, the expected and honestly-reported
  result for a technical indicator on geometric Brownian motion with no
  embedded signal.
- **Step 3** — `qrp portfolio --signal <indicator-id>` (plus
  `--signal-period`, `--signal-spread`): discovers the indicator through the
  same `PluginRegistry<Indicator>` the `list` command already uses, generates
  the view, scores it (D8), and prints the score in the report (D9). The
  mean-variance optimizer uses the generated view directly; risk parity
  ignores it, exactly as it ignores the flat-momentum default.

## What this module deliberately does not do

Stated up front so a reviewer does not have to ask later:

- no multi-factor combination or factor orthogonalization — every signal this
  module scores or generates is a single indicator's cross-sectional output,
  not a blend of several;
- no transaction-cost-aware signal decay;
- the significance test is a large-sample z-approximation (D3), not a
  Student's-t test, and understates uncertainty when the IC series has only a
  handful of periods;
- synthetic series only — the golden-run IC, the CLI transcript and every
  other number this module has produced comes from the platform's bundled
  `SYNA`/`SYNB`/`SYNETF` geometric Brownian series; nothing here has been
  run against, or validated on, real market data.
