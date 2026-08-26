# Spec — `qrp-portfolio`

Status: in progress (step 1 of the Extension 4 plan)

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

## Deferred to later steps

- The two optimizer implementations (mean-variance, equal risk contribution) —
  steps 2 and 3.
- Turnover-cap and sector-cap *enforcement* — `PortfolioConstraints` states the
  bounds; the projection logic that keeps an optimizer's output inside them
  lands with the first optimizer in step 2.
- Multi-instrument backtesting and the CLI — steps 4 and 5.

## What this module deliberately does not do

Stated up front so a reviewer does not have to ask later:

- no factor model — expected returns are supplied by the caller, not estimated
  here;
- no covariance shrinkage (Ledoit-Wolf or similar) — the sample estimator is
  used as-is, which is a stated simplification for a small number of
  instruments and a known weakness for a large universe;
- no transaction-cost optimization beyond the turnover cap — a rebalance either
  fits inside the cap or it does not, there is no cost-aware trade-off search.
