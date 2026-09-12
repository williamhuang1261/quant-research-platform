# Spec — P&L attribution (`qrp-pnl`)

Status: implemented (Extension 20, steps 1-3)

## Why this exists

`qrp-engine` already tracks everything a P&L breakdown needs at the level of
a single fill: `Trade.slippageCost()` and `Trade.commission()` cost each
trade, and `BacktestResult.equityCurve()` marks the whole account to market
every bar. What did not exist was anything that summed those pieces into an
answer to "where did this run's P&L actually come from" — how much was the
market moving in the position's favor (or against it), versus how much was
paid away to slippage and commission just to get in and out. `qrp-pnl`
is that decomposition, built as its own module rather than another method on
`BacktestResult`, because the arithmetic touches both the engine's trade
list and its bar series and is substantial enough to earn its own tests.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | `PnlAttribution.of(BacktestResult, annualCarryRate)` decomposes a run's realized P&L into a price-move component and an execution-cost component |
| R2 | The two components sum exactly to the run's actual equity change (`equityCurve.last - equityCurve.first`), proven by a reconciliation test, not asserted by inspection |
| R3 | A third, clearly separate "implied carry" figure at a caller-supplied annual rate, useful as a memo but never mixed into the reconciled total |
| R4 | A `GET /api/pnl/attribution` endpoint in `qrp-api` surfaces the breakdown for a real run over HTTP |

## The reconciliation identity

(`qrp-pnl/.../PnlAttribution.java`)

`BacktestEngine`'s own per-bar update is, in full:

```
equity[i] - equity[i-1]
    = sharesBeforeFill * (close[i] - close[i-1])          // price move on the position already held
    + fillDelta * (close[i] - referencePrice)              // the new fill's own move from execution to close
    - commission
    - slippageCost                                         // fillDelta * (fillPrice - referencePrice), always adverse
```

`PnlAttribution.of` walks the same `series`, `equityCurve` and `trades`
`BacktestResult` already produces and accumulates the first two terms into
`priceMovePnl` and the last two into `executionCostPnl`. Because the
identity above is exact algebra, not an approximation,
`priceMovePnl + executionCostPnl` (`totalReconciledPnl()`) equals
`equityCurve.last - equityCurve.first` to floating-point precision on every
run, including the real 20/50 crossover over `SYNA_1d.csv`
(`PnlAttributionRealRunTest`, 11 real fills) and not just the hand-built
3-bar case (`PnlAttributionTest`) it was first written against.

## Implied carry: a memo, not a component

`impliedCarryPnl` multiplies the cash balance actually held each bar by a
per-bar rate derived from a caller-supplied `annualCarryRate` (via the
existing `Annualization.periodsPerYear`). This number is deliberately **not**
added into `totalReconciledPnl()` and the Javadoc says so explicitly: the
engine charges no real interest on cash anywhere, so a "carry" component
folded into the reconciled total would be money the run never actually
earned or paid. It exists to answer a real, separate question a desk asks
("what would financing this position have cost at a given rate") without
pretending the answer is part of the P&L that already happened. Both
`PnlAttributionTest` and `PnlAttributionRealRunTest` include a case proving a
non-zero carry rate never changes `totalReconciledPnl()`.

## `GET /api/pnl/attribution`

(`qrp-api/.../PnlController.java`, `PnlAttributionResponse.java`)

A third caller of `BacktestRunner.run` alongside the CLI and `RunController`,
parsing the same `--symbol`/`--timeframe`/`--strategy`/`--cash`/`--costs`
flags through the unmodified `CliArguments.parse`, plus its own
`annualCarryRate` query parameter. Unlike `RunController` and
`ReportController`, it never writes to the warehouse: attribution is cheap
arithmetic over a run that already happened, not something worth caching
behind Postgres. `PnlControllerTest`'s default-parameter case reconciles the
same golden run `RunControllerTest`'s empty-body case already pins.

## What this deliberately does not model

- **Multi-instrument or portfolio-level attribution.** `PnlAttribution`
  decomposes one `BacktestResult`, which is itself single-instrument, the
  same scope `qrp-engine` already has. Attributing P&L across a multi-asset
  portfolio (`qrp-portfolio`'s concern) would need to net positions across
  instruments first, a different and larger feature.
- **Real cash interest.** Nothing in `qrp-engine` charges or pays interest
  on the cash balance; `impliedCarryPnl` is a stated-rate memo figure
  computed after the fact, not a cost or income the backtest itself ever
  applied. Restated here because it is the fact most likely to be missed by
  a reader skimming only the API response.
- **Intraday or tick-level attribution.** The decomposition is bar-close
  granularity, matching `BacktestEngine`'s own next-open-fill, mark-at-close
  timing. It cannot say anything about P&L that accrued and reversed within
  a single bar.
- **Realized versus unrealized P&L as separate lines.** `priceMovePnl`
  mixes both: the move on a position ultimately closed and the mark-to-market
  move on a position still open at the run's end. A backtest's terminal
  "unrealized" P&L is exactly its terminal open position's contribution,
  recoverable from the trade list if needed, but this module does not split
  it out as its own field.
