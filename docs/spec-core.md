# Spec — `qrp-core`

Status: implemented (step 1)

## Why this module exists

The platform is published as a *structure*: the proprietary indicators and
strategies it was written for stay private, and the public repository shows the
architecture they plug into. That only works if the boundary is real, so the
vocabulary and the extension points live in one module that depends on nothing
and contains no algorithms. Everything downstream depends on `qrp-core`;
`qrp-core` depends on the JDK alone.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Represent OHLCV bars, instruments and sampling intervals as immutable values |
| R2 | Reject malformed market data at construction, not at use |
| R3 | Represent a time-ordered series with cheap sub-views |
| R4 | Represent per-bar computed values aligned to that series |
| R5 | Define the extension points: indicator, strategy, market data provider, compute engine |
| R6 | Make look-ahead bias structurally impossible for a strategy author |

## Design decisions

**D1 — Values are immutable and validated in the constructor.** `Bar` refuses a
high below its open, a non-positive price or a negative volume. A backtest that
silently consumed a bad tick is worse than one that failed, because the result
still looks like a number. Validation lives in the type so no loader can skip it.

**D2 — Ordering is enforced, not repaired.** `BarSeries.of` rejects duplicate or
out-of-order timestamps rather than sorting them. A series that needed sorting
came from a loader with a bug, and hiding it costs more later than failing now.

**D3 — Gap detection takes a tolerance instead of guessing one.** Without an
exchange calendar the platform cannot distinguish a holiday from missing data,
so `gapsLongerThan(Duration)` asks the caller what a suspicious spacing is for
their data. Deriving it from `Timeframe.duration()` would report every weekend
in a daily series as a hole. The trade-off is that the caller must think; the
alternative is a data-quality check that cries wolf and gets ignored.

**D4 — Strategies receive a view, not the whole series.** `Strategy.onBar` takes
`BarSeries visible`, produced by `visibleAt(index)`, which ends at the bar being
decided. Look-ahead bias is the defect that makes a backtest worthless, and a
strategy author cannot commit it by accident if the future is not in the object
they were handed. `slice` is backed by `List.subList`, so building a view on
every bar costs no copy.

**D5 — Warm-up is `NaN`, never zero and never a shorter array.** `DoubleSeries`
is the same length as its bar series. Zero-padding feeds a strategy a price it
never saw; a shorter array shifts every index by the warm-up length, which is the
kind of off-by-`n` that produces a profitable-looking backtest.

**D6 — Strategies state intent, the engine decides fills.** `Signal` carries a
target exposure in `[-1, 1]` rather than an order. Sizing, commission and
slippage then live in one place (the execution model, step 4) and can change
without touching a strategy, and two strategies on instruments at different
prices stay comparable.

**D7 — Parameters are a validated map, not a record per algorithm.** Parameters
arrive from the CLI, the UI and parameter sweeps, none of which know the concrete
type of the plugin they configure. `Params.requireInt` fails on `14.5` at the
boundary instead of truncating it inside a loop.

## Out of scope for this module

Exchange calendars, tick sizes, contract multipliers, order books and any
venue simulation. The platform models a research backtest, not a matching engine,
and pretending otherwise would be a claim the code cannot support.
