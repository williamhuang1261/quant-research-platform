# Spec — `qrp-engine`

Status: implemented (step 4)

## Why this module exists

Everything before this module produces inputs. This is where a strategy meets
prices and costs and becomes a number someone might act on, which makes it the
module where a quiet mistake is most expensive: a backtest that is wrong still
returns a plausible equity curve.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Run a `Strategy` bar by bar over a `BarSeries`, with no look-ahead |
| R2 | Model commission and slippage on every fill |
| R3 | Keep cash and position accounting exact and auditable |
| R4 | Produce an equity curve aligned to the bars, plus the standard metrics |
| R5 | Be reproducible: the same request returns the same numbers |
| R6 | Depend on no concrete indicator or strategy at compile time |

## The bar loop

For each bar, in this order:

1. **Execute** the target decided on the previous bar, at this bar's **open**.
2. **Mark to market** at this bar's **close**.
3. **Decide**, from history up to and including this bar.

## Design decisions

**D1 — A decision on a close fills at the next open.** The close was not
knowable until the bar ended, so filling at it is a look-ahead worth about as
much as the strategy. Filling at the next open is the earliest honest execution.
The consequence is that the target stated on the final bar is never executed;
that is correct, not an off-by-one.

**D2 — Sizing uses the price actually paid.** An earlier version sized the
position at the reference price and filled at the concession, which overshot the
target by exactly the slippage and pushed cash negative: the account borrowed to
pay its own costs. The engine now determines direction at the reference price,
computes the fill price, and sizes against that. A test asserts the exact
slippage cost on a fill, which is what caught it.

**D3 — Whole shares, truncated.** Fractional sizing assumes a broker that
supports it and flatters small accounts; truncation is the conservative
direction, and the remainder stays visible as cash rather than as a rounding
gain.

**D4 — The engine trades on a change of target, not on drift.** A position that
drifts from 100 % to 103 % as prices move is not rebalanced. Rebalancing on
drift would generate a trade almost every bar, and the resulting cost drag would
be an artefact of the accounting rather than of the strategy.

**D5 — Undefined metrics are `NaN`, not zero.** A flat equity curve has no
dispersion to divide by, so its Sharpe ratio does not exist. Reporting 0 would
put it on a leaderboard between a losing strategy and a winning one.

**D6 — Annualisation is 252 bars a year, not 365 days.** A daily series has 252
observations a year; annualising by calendar days overstates volatility by about
20 %.

**D7 — The engine compiles against the SPI only.** `qrp-engine` depends on
`qrp-core`; `qrp-indicators` is a *test* dependency. The reference strategy
resolves `sma` through `PluginRegistry` at runtime, which is the same seam a
private indicator jar arrives through. If the engine imported an indicator
class, the plugin boundary would exist only in the documentation.

**D8 — The strategy precomputes both averages in `onStart`.** Computing an
indicator over the visible history on every bar is O(n²) for an identical
result, because a moving average is *causal*: its value at bar `i` depends on
bars up to `i` and nothing after. An indicator that looked ahead — a centred
average, a series-wide normalisation — could not be precomputed this way, and
the strategy documents that condition rather than leaving it implicit.

## The golden run

`BacktestIntegrationTest` pins one run end to end: a 20/50 crossover on `SYNA`,
retail costs, $100,000 initial cash.

| Metric | Value |
| --- | --- |
| Final equity | $92,229.01 |
| Total return | −7.77 % |
| CAGR | −4.12 % |
| Annualised volatility | 15.94 % |
| Sharpe | −0.17 |
| Max drawdown | 24.15 % |
| Trades | 11 |
| Time in market | 43.7 % |

These are a **characterisation**, not a claim: they capture what the
implementation currently does so that changing fill timing, share rounding or
the cost model breaks a test instead of silently restating every published
result. That the strategy loses money is the expected outcome for a 20/50
crossover, and it is published as it came out. Tuning the parameters until the
README looked better would make the number meaningless.

## Out of scope

Multi-instrument portfolios, intraday order types beyond a market fill at the
open, margin and financing costs, short borrow availability, and taxes. Each is
a real cost that this engine does not model, and a result from it should not be
read as though it did.
