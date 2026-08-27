# Spec — `qrp-portfolio`

Status: complete (Extension 4, all five steps)

## Why this module exists

Every other module in this platform sizes and scores *one* instrument at a
time: `qrp-engine`'s `BacktestRequest` takes exactly one `BarSeries` and one
`Strategy`. Nothing turns several instruments' views into a single set of
target weights under a guideline — the thing "portfolio construction" actually
means. This module is that layer: a `PortfolioOptimizer` SPI, a covariance
estimator built on the platform's existing correlation tooling, and (in later
steps) a multi-instrument backtest that rebalances through it.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A covariance matrix from several instruments' return series, reusing the platform's existing pairwise correlation tool rather than a second implementation |
| R2 | A `PortfolioOptimizer` SPI: expected returns + covariance + constraints + previous weights in, target weights out |
| R3 | Constraints (`PortfolioConstraints`) that an optimizer must respect exactly: max weight per instrument, max turnover per rebalance, a leverage target, and optional per-sector caps |
| R4 | At least two independent optimizer implementations behind the SPI, each validated against a case with a known correct answer (step 2, step 3) |
| R5 | Multi-instrument rebalancing composed on top of the existing single-instrument engine, not a rewrite of it (step 4) |

## Design decisions

**D1 — The covariance estimator goes through `JackknifeCorrelation.correlation`,
not a separate covariance sum.** `cov(i, j) = corr(i, j) * std(i) * std(j)` is
algebraically the same number a direct covariance formula would produce, but
writing it this way means the correlation the platform already uses to judge
whether a signal is real, and the covariance the optimizer allocates against,
can never quietly disagree — there is only one implementation of "how related
are these two series" in the codebase.

**D2 — `PortfolioConstraints` works on instrument *indices*, not identities.**
The SPI's arrays (`expectedReturns`, `covariance`, `previousWeights`) are
already positional; `sectors` is a parallel list in the same order rather than
a `Map<Instrument, String>`, so the optimizer has no dependency on `qrp-core`'s
`Instrument` type at all. A caller that wants named instruments keeps that
mapping itself.

**D3 — sectors and sector caps are validated together or not at all.** A
`PortfolioConstraints` with `sectors` populated but `sectorCaps` empty (or vice
versa) is almost certainly a caller bug — a guideline that was meant to bind
but was not wired through — so the constructor rejects the mismatch instead of
silently treating it as "no sector guideline."

**D4 — `MeanVarianceOptimizer` is long-only.** The constraint set it is given
(`PortfolioConstraints`) has only a `maxWeight` and a `leverage` target, no
explicit short-selling allowance, so the box lower bound is `0`. This is the
stated scope of this implementation, not a silent assumption: a short-enabled
variant would need a signed lower bound added to `PortfolioConstraints`
itself, which is out of scope for this step.

**D5 — the box and leverage constraints are enforced as one joint projection,
not "clip to the box, then rescale to leverage" as a literal two-step reading
of the plan would suggest.** Clipping each weight into `[0, maxWeight]` and
then independently rescaling the vector so it sums to `leverage` can push a
weight back above `maxWeight` on the rescale (for example: three assets,
`maxWeight = 0.4`, weights land at `[0.4, 0.1, 0.1]` after the clip, summing
to `0.6`; rescaling by `1.0 / 0.6` to reach leverage `1.0` pushes the first
weight to `0.667`, violating the cap the clip just enforced). `MeanVarianceOptimizer`
instead projects onto the "capped simplex" `{w : 0 <= w_i <= maxWeight,
sum(w) == leverage}` directly, via bisection on a single shared threshold
`tau` where `x_i = clamp(w_i - tau, 0, maxWeight)` — standard for this exact
constraint shape, and the only way to make "respects `maxWeight` exactly" and
"hits `leverage` exactly" both true at once. The turnover cap is then applied
as a separate projection onto the L1 ball of radius `maxTurnover` around
`previousWeights`; because the capped-simplex set is convex and
`previousWeights` is assumed to already lie in it, scaling the move back
toward `previousWeights` cannot reintroduce a box or leverage violation.

**D6 — risk aversion (`lambda`) is a per-instance constructor parameter, not
an `optimize()` argument.** The `PortfolioOptimizer` SPI signature (frozen in
step 1) takes expected returns, covariance, previous weights and constraints —
no risk/return trade-off knob. Since the SPI's own contract requires
implementations to be stateless and reusable across calls, a fixed
constructor-level `lambda` satisfies both "the objective needs a risk-aversion
coefficient" and "instances stay stateless — nothing here varies per call."

**D7 — `EqualRiskContributionOptimizer` ignores `expectedReturns` and enforces
the box/leverage/turnover constraints via the same shared projections as
`MeanVarianceOptimizer`, not its own bespoke logic.** Risk parity's own
objective (equalize `w_i * (Sigma w)_i` across instruments) has no view of
expected return at all, so the raw cyclical-coordinate-descent solve ignores
that argument entirely — it is validated against a closed form (weights
inversely proportional to volatility on a diagonal covariance matrix) that has
nothing to do with `maxWeight` or `leverage` either. Routing the converged raw
solution through `PortfolioProjections`' capped-simplex and turnover-ball
helpers (the same two `MeanVarianceOptimizer` uses) is what makes "both
optimizers honor the SPI's constraint contract identically" a fact about the
code rather than a claim in a docstring — see the turnover-cap test repeated
against both optimizers in step 3.

**D8 — `PortfolioBacktestEngine` composes the existing single-instrument
`BacktestEngine` per rebalance segment, and pays for that composition with an
approximate turnover figure.** Reusing the unmodified engine (rather than a
second fill loop) means the golden-run numbers in `BacktestIntegrationTest`
cannot move — this extension touches zero `qrp-engine` files. The cost: the
engine always starts a run flat, so every sleeve transacts at every rebalance
boundary even when its target weight did not change. `totalTurnover()` is
therefore computed from the optimizer's own weight deltas (the same quantity
`PortfolioConstraints.maxTurnover()` bounds), not from the literal fills this
composition happens to make — a deliberate choice to keep the reported number
meaningful rather than an artifact of how the backtest happens to be wired.

**D9 — the CLI's per-instrument "view" is a plain trailing-momentum
computation in `qrp-app`, not a call into `qrp-indicators`.** `qrp-app`
depends on `qrp-indicators` at runtime scope only (see the README's
architecture diagram) — nothing in the module compiles against a concrete
indicator, discovering them instead through `PluginRegistry`. Wiring
`qrp portfolio`'s view through that registry would need an `Indicator` chosen
by convention (which one, at what parameters) with no principled default;
computing the same 20-bar momentum `PortfolioBacktestEngineTest`'s golden run
already uses keeps the CLI's number reproducible against that pinned test
without inventing a registry convention this step was not scoped to design.
Only `MeanVarianceOptimizer` reads this view; `risk-parity`, the CLI's
default, ignores it entirely (D7).

> **Addendum (Extension 5, `qrp-signals`).** The "no principled default"
> objection above is exactly what `qrp-signals`' `CrossSectionalSignalGenerator`
> answers: `qrp portfolio --signal <indicator-id>` now discovers an indicator
> through that same `PluginRegistry` and generates a real, scored forecast.
> This D9 entry is left as originally written because the reasoning was
> correct for the step it described — the flat 20-bar momentum remains the
> default with no `--signal` flag, unchanged. See `docs/spec-signals.md`.

## What steps 3-5 added

- **Step 3** — `EqualRiskContributionOptimizer`, the second SPI
  implementation, validated against the closed-form diagonal case and the same
  turnover-cap test step 2 used.
- **Step 4** — `PortfolioBacktestEngine` and `PortfolioBacktestResult`:
  scheduled multi-instrument rebalancing composed on top of the unmodified
  single-instrument engine (D8), with per-rebalance weights and realized risk
  contribution, and a comparison test showing risk parity's realized
  volatility contribution is materially more balanced than an equal-weight
  benchmark on the same data (coefficient of variation ~0.082 vs. ~0.694).
- **Step 5** — the `qrp portfolio` CLI command (`--optimizer=mean-variance
  |risk-parity`, `--rebalance=monthly|weekly`, plus `--symbol`, `--lookback`,
  `--max-weight`, `--turnover`, `--risk-aversion`, `--cash`, `--costs`),
  `PortfolioReportFormatter` (weights, risk contribution and turnover, in the
  same fixed-width, caveats-included style as `ReportFormatter` and
  `FundComparisonReportFormatter`), and this spec's completion.

Sector-cap *enforcement* remains a stated gap: `PortfolioConstraints` states
the bounds (R3), but neither optimizer shipped in this extension allocates by
sector — `MeanVarianceOptimizer` and `EqualRiskContributionOptimizer` both
enforce `maxWeight`, `leverage` and `maxTurnover` only. A sector-aware
optimizer is a real extension point, not something either implementation
silently claims to do.

## What this module deliberately does not do

Stated up front so a reviewer does not have to ask later:

- no factor model **within this module** — `qrp-portfolio` itself still only
  accepts `expectedReturns` as a caller-supplied array; `qrp-signals`
  (Extension 5) is what can now estimate one from a single indicator's
  cross-sectional rank, and it is a separate module `qrp-portfolio` has no
  dependency on;
- no covariance shrinkage (Ledoit-Wolf or similar) — the sample estimator is
  used as-is, which is a stated simplification for a small number of
  instruments and a known weakness for a large universe;
- no transaction-cost optimization beyond the turnover cap — a rebalance either
  fits inside the cap or it does not, there is no cost-aware trade-off search;
- synthetic series only — every number in this spec, the README transcript
  and the golden-run tests comes from the platform's bundled
  `SYNA`/`SYNB`/`SYNETF` geometric Brownian series, not real prices; the
  covariance estimate, both optimizers and the CLI all work on whatever
  `BarSeries` a `MarketDataProvider` hands them, but none of it has been run
  against, or validated on, real market data.
