# Spec — matching engine (`qrp-matching`)

Status: implemented (Extension 19, steps 1-4)

## Why this exists

Everything `qrp-engine` had before this extension — `MarketOpenExecutionModel`,
`LimitOrderBookExecutionModel`, `SyntheticOrderBook` — prices a fill by
building a static book from a single bar's OHLCV and walking it once. That
answers "would this size have filled here," but it has no notion of an order
that persists: nothing rests across more than one call, nothing can be
cancelled, and two orders never trade directly against each other. A real
matching engine is a different shape of problem — resting state that survives
across many submissions, price-time priority as an invariant of the book
itself, and trades that happen because two independent orders crossed, not
because one order walked a snapshot. `qrp-matching` is that engine, built as
its own module rather than a third `ExecutionModel` implementation crammed
into `qrp-engine`, because its state (a live book of resting orders) has no
counterpart in the bar-by-bar, stateless world the existing models live in.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A price-time-priority order book: at the same price, the order that arrived first fills first |
| R2 | Limit and market orders, on both sides, with partial fills when a resting order's size does not fully cover an incoming order (or vice versa) |
| R3 | Cancellation of a resting order, with no side effect on any other resting order |
| R4 | A crossing trade executes at the resting (maker) order's price, never the incoming (taker) order's |
| R5 | Pluggable into the existing `ExecutionModel` SPI without modifying `MarketOpenExecutionModel`, `LimitOrderBookExecutionModel` or `SyntheticOrderBook` |
| R6 | Synthetic order flow derived from real OHLCV sample data (`SYNA`/`SYNB`/`SYNETF`), clearly labelled synthetic, not presented as a recorded order stream |

## `Order` and `Trade`

(`qrp-matching/.../Order.java`, `.../Trade.java`)

`Order` is a plain class, not a record, because a resting limit order's
`remainingQuantity()` genuinely changes as it fills — a record's whole point
is that its state does not. `sequence`, the tie-breaker for time priority, is
assigned by `OrderBook` the moment an order is accepted, not by the caller:
time priority has to be the book's own clock, not whatever order two calls
happened to arrive from client code in. A `MARKET` order carries no
`limitPrice` (`Double.NaN`) and is never eligible to rest.

`Trade` is a record: `makerOrderId`, `takerOrderId`, `price`, `quantity`,
`takerSide`. `price` is always the maker's own resting price (R4).

## `OrderBook`

(`qrp-matching/.../OrderBook.java`, package-private)

A `TreeMap<Double, Deque<Order>>` per side — bids ordered highest-price-first,
asks lowest-price-first — with an `ArrayDeque` per price level holding orders
in arrival order. `peekBest(side)` and `remove(order)` together give
price-time priority for free: the deque's head is always the order that has
rested longest at the best price, and `MatchingEngine` never has to compute
priority itself, only ask the book for the front of the queue.

A `Map<Long, Order>` indexed by id makes `cancel(orderId)` O(1) rather than a
scan of every price level.

## `MatchingEngine`

(`qrp-matching/.../MatchingEngine.java`)

`submit(order)` walks the opposite side of the book while the two are
crossable (a market order crosses anything with size available; a limit order
crosses only at a price at least as good as its own limit), generating a
`Trade` per match at the resting order's price, until either the incoming
order is fully filled or nothing left on the book can cross it. Whatever a
limit order does not fill rests via `OrderBook.addResting`; whatever a market
order does not fill is discarded, never queued — a resting order with no
price would have nothing to sit at.

`cancel(orderId)` removes a resting order and returns it, or `Optional.empty()`
if no such order is currently resting (already filled, already cancelled, or
never existed).

## `SyntheticOrderFlowGenerator` and `MatchingEngineExecutionModel`

(`qrp-matching/.../SyntheticOrderFlowGenerator.java`,
`.../MatchingEngineExecutionModel.java`)

`SyntheticOrderFlowGenerator.fromBar` mirrors `SyntheticOrderBook.fromBar`'s
own construction almost exactly (mid at the bar's open, spread as a fraction
of the bar's high-low range, depth as a fraction of the bar's volume decaying
geometrically across levels) but emits real `Order` objects instead of
`Level` records, because those orders need to become resting state inside a
`MatchingEngine`, not just entries in a walked snapshot.

`MatchingEngineExecutionModel` builds a fresh engine per `fill()` call, seeds
it with that synthetic flow, then submits the strategy's desired size as a
single `MARKET` order and reports whatever the engine actually matched as the
`Fill` — averaged across every `Trade` the submission produced, honestly
partial or empty if the synthetic book's depth did not cover it. It sits
beside `MarketOpenExecutionModel` and `LimitOrderBookExecutionModel` as a
third, additive `ExecutionModel`; neither existing class was changed.

## Design decisions

**D1 — a fresh `MatchingEngine` per bar, not one persisted across the whole
backtest.** `ExecutionModel.fill()` is called once per bar with no notion of
"the previous bar's leftover book." Persisting one engine across bars would
mean synthetic liquidity from bar 1 could still be sitting there, stale, when
bar 50 asks for a fill — a more surprising default than seeding a clean book
each time and stating plainly (see below) that resting orders do not survive
across bars, matching `LimitOrderBookExecutionModel`'s own documented
limitation on the same point.

**D2 — the strategy's own order is submitted as `MARKET`, not `LIMIT`.** A
limit order that does not fully cross would rest in the engine after
`fill()` returns, but that engine is discarded at the end of the call (D1),
silently losing a "resting" order that never actually rested anywhere
observable. A market order's unfilled remainder is honestly discarded
instead of pretending to persist state that does not exist.

**D3 — commission only, no separate slippage.** Same reasoning as
`LimitOrderBookExecutionModel`'s D2: the matching engine's own execution
price already reflects the cost of crossing the synthetic book, so a second,
flat slippage assumption on top would double-count it.

## What this deliberately does not model

- **Cross-bar order persistence.** Every call to `MatchingEngineExecutionModel.fill`
  builds a brand-new engine and a brand-new synthetic book; nothing rests from
  one bar into the next. A genuinely persistent book across an entire session
  is a different, larger feature (see Extension ideas).
- **Iceberg / hidden orders.** Every resting order shows its full remaining
  quantity; there is no notion of a displayed size smaller than the true size.
- **Self-trade prevention.** Nothing stops the same participant's own resting
  and incoming orders from matching against each other; this engine has no
  concept of "participant" at all, only order ids.
- **Multiple instruments in one book.** One `MatchingEngine` instance is one
  instrument's book. Routing across instruments is the caller's job.
- **A reconstructed real order book or a real order feed.** `SyntheticOrderFlowGenerator`
  derives its flow entirely from one bar's own OHLCV, the same honest
  limitation `SyntheticOrderBook` already states for its own construction.
- **Price-level aggregation / market depth reporting beyond best bid/ask.**
  `MatchingEngine` exposes `bestBid()`, `bestAsk()` and `tradeCount()`; it does
  not expose full depth-of-book snapshots.

## Extension ideas

A CLI flag (`--execution=matching`) selecting this model alongside
`market-open` and `lob`; a persistent, session-long book that survives across
bars instead of one per call; depth-of-book snapshots for a richer report
section; stop and iceberg order types; a simple self-trade prevention rule.
