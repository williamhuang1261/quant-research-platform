# Spec — execution models (`qrp-engine`)

Status: implemented (Extension 2, steps 1-3)

## Why this exists

Before this extension, `BacktestEngine.run()` had exactly one fill
mechanism inlined into the bar loop: fill the full requested size at the
reference bar's open, plus a flat `CostModel` concession, no matter what the
bar's own liquidity looked like. That is a reasonable default and it is now
`MarketOpenExecutionModel`, but it was never actually a *choice* — there was
nothing else behind the same seam to compare it against. `ExecutionModel`
makes the fill decision a first-class interface so a second, more literal
model of market structure — a resting limit order against a synthetic
bid/ask book — can exist beside it, selectable per run rather than requiring
a fork of the engine.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | An `ExecutionModel` interface: given the reference bar, the pending target exposure, and current cash/shares, decide the fill (price, signed share delta, commission) or decline it entirely |
| R2 | `MarketOpenExecutionModel` reproduces the engine's pre-extension fill logic exactly, byte for byte on the golden run |
| R3 | A second implementation, `LimitOrderBookExecutionModel`, that can honestly fill less than the requested size, or nothing at all, when a synthetic book does not support it |
| R4 | `SyntheticOrderBook`, the heuristic the second model fills against, built only from a single bar's own OHLCV — no new data source |
| R5 | Both models selectable from the CLI on the same strategy and data, with a report section that makes their fill rate and slippage comparable |

## The `ExecutionModel` interface

```java
public interface ExecutionModel {
    Optional<Fill> fill(Bar referenceBar, double pendingTarget, double cash, double shares);

    record Fill(double price, double deltaShares, double commission) { ... }
}
```

(`qrp-engine/.../ExecutionModel.java`)

Everything about **when** a decision is evaluated — next-open timing,
`BarSeries.visibleAt(i)` — stays in `BacktestEngine`, which calls this
interface once per pending target with the bar the fill executes against.
What differs between implementations is purely the mechanics of the fill:
a flat concession against the open, versus walking a synthetic order book.

**`Optional.empty()` is a first-class outcome, not an error.** A model that
cannot support the requested size at any acceptable price says so rather
than filling at a price it never actually offered. `Fill`'s compact
constructor enforces the same honesty at the type level: `deltaShares == 0.0`
throws, because a no-op fill must be `Optional.empty()`, not a `Fill` that
changed nothing.

## `MarketOpenExecutionModel`

(`qrp-engine/.../MarketOpenExecutionModel.java`)

```java
public record MarketOpenExecutionModel(CostModel costs) implements ExecutionModel
```

The platform's original execution logic, extracted from `BacktestEngine`
with zero behavior change: fills the full requested size at the reference
bar's open, concession included via `CostModel.fillPrice`. Direction is
decided at the reference price and then sizing is redone against the price
actually paid — sizing at the reference price and filling above it would
overshoot the target by exactly the concession, which shows up as negative
cash (the account borrowing to pay its own slippage). This is the same D2
fix documented in `docs/spec-engine.md`; it now lives in this class instead
of the bar loop.

Its one honest limitation, stated in its own Javadoc: it always fills in
full once a fill is warranted at all. It has no notion of how much liquidity
the bar actually offered at that price — no notion of size. That is exactly
what the second model adds.

`BacktestIntegrationTest`'s golden run — 20/50 crossover on `SYNA`, retail
costs, $100,000 initial cash, final equity `92,229.0094522352`, CAGR
`-0.0411589578`, Sharpe `-0.1745144272`, max drawdown `0.2414947349`, 11
trades — is unchanged by the extraction. That equivalence is what "zero
behavior change" means in practice, not just in the Javadoc.

## `SyntheticOrderBook`

(`qrp-engine/.../SyntheticOrderBook.java`)

**This is a heuristic over OHLCV, not a reconstructed real book.** No
exchange feed in this data set records individual resting orders — the same
honest limitation as the `SYNA`/`SYNB`/`SYNETF` synthetic price series and
the `SYNOPT` synthetic option chain elsewhere in this repository, which are
labelled synthetic rather than presented as recorded quotes or a real feed.
`SyntheticOrderBook` follows the same precedent explicitly, in its own class
Javadoc: it is a plausible microstructure invented from a single candle,
useful for asking "would this size have filled here," never for claiming
what the real book actually looked like.

**Construction, `SyntheticOrderBook.fromBar(bar, spreadFraction, levels, depthFraction)`:**

- **Mid** is the bar's open — the same "earliest honest execution price"
  anchor `ExecutionModel` already uses elsewhere.
- **Spread** is `spreadFraction` of the bar's high-low range (`bar.range()`),
  centred on the mid. A bar with a zero range (a single print, or a
  synthetic bar where high equals low) falls back to a tiny fraction of
  price instead of collapsing every bid and ask onto one level.
- **Depth** is `bar.volume() * depthFraction`, split evenly between the two
  sides, then apportioned across `levels` price levels per side with
  geometric decay — each level further from the top of book rests half the
  size of the one before it. A thin, low-volume bar runs out of depth after
  a handful of levels, which is the point: it is what lets the limit-order
  model decline a fill honestly instead of always finding a way to fill.

`SyntheticOrderBook.walk(side, desiredSize, limitPrice, buying)` accumulates
size level by level, best price first, stopping at the first level whose
price is no longer at or better than the limit. It returns `Optional.empty()`
if no size at all is available at or better than the limit, otherwise the
size actually fillable (which may be less than `desiredSize`) and its
volume-weighted average price. This is the primitive both the "partial fill"
and "no fill" behaviors below are built on.

## `LimitOrderBookExecutionModel`

(`qrp-engine/.../LimitOrderBookExecutionModel.java`)

```java
public record LimitOrderBookExecutionModel(
        CostModel costs, double spreadFraction, double offsetLevels, int levels, double depthFraction)
        implements ExecutionModel
```

Places the desired fill as a resting limit order at `offsetLevels`
half-spreads beyond the synthetic book's mid, on the side it needs to cross,
then walks that side's levels via `SyntheticOrderBook.walk`. Only the size
the book's levels actually support at or better than the limit price fills.

- `offsetLevels = 1.0` rests exactly at the synthetic top of book.
- Values above `1.0` cross further into the book: more fills, at a worse
  average price.
- Values below `1.0` are a limit that may not even reach the top of book.

`LimitOrderBookExecutionModel.defaults(costs)` is `spreadFraction = 0.5`
(spread is half the bar's high-low range), `offsetLevels = 1.0`,
`levels = 5`, `depthFraction = 0.1` (10% of the bar's volume counted as
visible resting depth) — the same defaults the CLI's `--execution=lob` uses
with no other flags.

Unlike `MarketOpenExecutionModel`, which always fills the full requested
size once a fill is warranted at all, this model can report a **partial**
fill or `Optional.empty()` when the synthetic book's depth does not cover
the full desired size at the price the order is willing to pay. Filling the
remainder at a price the book never offered would be exactly the dishonest
shortcut this model exists to avoid — `LimitOrderBookExecutionModelTest`
pins this directly: a large order (converting $1,000,000 of equity) against
a thin, 50-share-volume bar never fills in full, and the model never fills
at a price outside the range the synthetic book's levels actually walked.
Commission is charged on the notional actually filled; slippage is not
applied on top of it, because the limit price and the book walk already
express the cost of demanding liquidity.

## Selecting a model from the CLI

`qrp run --execution market-open` (default) or `qrp run --execution lob`
(`--execution=lob` is also accepted). LOB-specific tuning:
`--lob-spread <frac>`, `--lob-offset <levels>`, `--lob-levels <n>`,
`--lob-depth <frac>` — see `qrp run --help` for the full flag list and
defaults. `CliArguments.ExecutionKind` maps the flag to the concrete
`ExecutionModel` (`qrp-app/.../CliArguments.java`); `BacktestRunner`
constructs it and passes it into `BacktestRequest.execution`, the field
`ExecutionModel` occupies (see "Design decisions" below).

The report gains an `execution` section (`ReportFormatter.executionSection`)
computed the same way regardless of which model produced the trades, so a
`market-open` run and a `lob` run on the same strategy and data are honestly
comparable:

- **fill attempts** — counted from the exposure series, not a separate
  counter: `BacktestEngine` advances the held target every time it hands a
  pending target to the execution model, whether or not that model actually
  filled it, so a change in `exposure` between consecutive bars is exactly
  one fill attempt.
- **fills completed** — `result.trades().size()`.
- **fill rate** — completed / attempted; `n/a` if there were no attempts.
- **total slippage vs. reference** and **avg. slippage vs. reference** —
  summed and averaged (in bps of reference notional) from each `Trade`'s
  own `slippageCost()` and `referencePrice()`, so both models report
  slippage against the same reference regardless of how each one priced
  its own fill.

## Design decisions

**D1 — `BacktestRequest.execution` is required, not defaulted.** The plan
that led to this extension assumed the field would default to
`MarketOpenExecutionModel`. It does not: every call site constructs one
explicitly. `BacktestRequest`'s own Javadoc states why the class exists at
all — "a run that silently defaulted its cost model would be the kind of
result that gets quoted without the caveat." A default execution model
would violate that same principle for the same reason, so the field stayed
required and every existing call site (`BacktestRunner`, and three test
files) was updated to pass `new MarketOpenExecutionModel(costs)` explicitly
rather than relying on a default.

**D2 — `LimitOrderBookExecutionModel` charges no slippage on top of the
book walk.** `MarketOpenExecutionModel` applies `CostModel`'s flat slippage
concession because it has no other notion of execution cost. The LOB model's
walk *is* the cost model for price impact — the average price it reports
already reflects how far into the book the order had to reach — so stacking
a second, flat slippage assumption on top of a already-computed one would
double-count the same effect under two different names. Commission (a cost
independent of price impact) is still charged.

**D3 — depth decays geometrically across levels, not uniformly.** A book
where every level held the same size would let a large order reach the same
average price at any total size, which is not how order books actually look
and would make "runs out of depth" almost impossible to trigger on a
realistic bar. Halving the size at each level further from the top of book
means a thin bar's synthetic depth is exhausted after a handful of levels —
the behavior `LimitOrderBookExecutionModelTest` exercises directly.

## What this deliberately does not model

- **A reconstructed real order book.** `SyntheticOrderBook` is derived
  entirely from one bar's OHLCV — open, high, low, close, volume — not from
  tick-by-tick quotes or a real limit order feed, because this data set has
  no such feed. Restated here because it is the single most important fact
  about this model: it answers "would a plausible book have supported this
  size," not "what did the book actually look like."
- **Cross-bar order persistence.** A partially filled or unfilled limit
  order does not rest into the next bar; `BacktestEngine` re-evaluates a
  fresh pending target every bar from the strategy's current signal. An
  order book with resting unfilled orders that carry forward is a different
  and larger feature.
- **Order book dynamics within a bar.** The synthetic book is a single
  static snapshot per bar (built once, walked once); there is no attempt to
  model how the book might have moved as the order itself executed against
  it (its own market impact on the book), only how much of the book's
  stated depth the order could have consumed.
- **Market-maker or other counterparty behaviour.** The book's spread and
  depth come from a fixed heuristic over the bar's own range and volume,
  not from any model of who is quoting or why.
