# Spec — `qrp-report`

Status: in progress (Extension 3, step 1 of 5)

## Why this module exists

The platform already runs a backtest and produces performance and risk
statistics for one instrument at a time. A fund comparison is the same numbers,
read side by side against a benchmark, with one thing added that no backtest
has: a fee charged to the investor rather than the strategy. This module is
that reporting layer — it does not price anything or run a new simulation.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A flat annual management fee applied to an equity curve, exact regardless of bar frequency |
| R2 | A comparison table across several fund-shaped backtests and one benchmark |
| R3 | Gross metrics (CAGR, Sharpe, max drawdown) reused from the engine, not recomputed |
| R4 | Net-of-fee CAGR computed by the same year-length formula the engine uses for gross CAGR |
| R5 | Historical 95% VaR and expected shortfall per fund |
| R6 | Rows ranked by net CAGR, with the benchmark always present and marked |

## Design decisions

**D1 — The fee lives on `FundProfile`, not on `Instrument`.** The same
underlying security can back two funds charging different fees (an A-series and
an F-series of the same mandate, for instance), so the fee belongs to the fund
wrapper, not to the thing the platform already models as tradable.

**D2 — Gross metrics are read from `PerformanceMetrics`, never recomputed.**
`qrp-engine` already computes CAGR, Sharpe and max drawdown from the same
equity curve this module reads. A second implementation of the same formula is
a second place for the two to drift apart; this module only adds the two
numbers that do not exist upstream — the fee-adjusted CAGR and the tail-risk
pair.

**D3 — Net CAGR reuses the engine's exact CAGR formula.** `PerformanceMetrics`
defines a year as the real calendar span between the first and last bar
(`Duration.between(start, end) / 365.25`), not a bar count divided by a nominal
periods-per-year. `FundComparisonTable` copies that formula rather than
approximating it, so a 0% fee reproduces the engine's own gross CAGR to the bit
— tested directly, not just asserted close.

**D4 — VaR and expected shortfall are computed on gross returns.** A fee is a
level effect (it lowers the compounding rate), not a volatility effect; folding
it into the return series before measuring tail risk would conflate the two. A
later step may add a fee-adjusted risk view if a reviewer asks for it — nothing
here forecloses that.

## What this module does not do

- It does not run a backtest. `FundComparisonTable.of` takes already-computed
  `BacktestResult`s; wiring three instruments through the engine is the CLI
  step, not this one.
- It does not model a real management expense ratio: no fund-of-fund layering,
  no trailing commission, no fee waiver, no tiered pricing by asset level. One
  flat annual rate per fund. See the README's engineering notes.
- It does not write the report. Formatting and the narrative are later steps.
