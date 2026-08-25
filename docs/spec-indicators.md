# Spec — `qrp-indicators`

Status: implemented (step 3)

## Why this module exists

The platform's value is the indicators it was written for, and those are not in
this repository. What is here is the contract they satisfy and enough public
implementations to prove the contract works: four technical indicators found by
`ServiceLoader`, and two transforms that deliberately sit outside the SPI.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Discover indicators on the classpath with no list of them in the code |
| R2 | Reject a duplicate or blank indicator id at load time |
| R3 | Ship public reference implementations of the technical, economic and fundamental families |
| R4 | Hold every implementation, public or private, to one testable contract |
| R5 | State exactly where the SPI stops, rather than widening it to fit |

## What ships

| Family | Implementation | Id | Parameters |
| --- | --- | --- | --- |
| Technical | `SimpleMovingAverage` | `sma` | `period` |
| Technical | `ExponentialMovingAverage` | `ema` | `period` |
| Technical | `RelativeStrengthIndex` | `rsi` | `period` (>= 2) |
| Technical | `RollingVolatility` | `volatility` | `period` (>= 2), `annualization` (default 252) |
| Fundamental | `DuPontDecomposition` | not an `Indicator` | statement inputs |
| Economic | `RealPriceAdjuster` | not an `Indicator` | CPI series + base level |

## Design decisions

**D1 — Discovery lives in `qrp-core`, implementations live here.**
`PluginRegistry` sits next to the SPI it serves, so an application running only
private indicators depends on `qrp-core` and its own jar, and never on this
module. Content and mechanism are separable; that separation is the argument for
publishing the platform at all.

**D2 — A duplicate id is an error, not an override.** A private indicator
shadowing `sma` would mean the same configuration producing different numbers on
different classpaths, with nothing in the output saying which ran. The registry
names both implementing classes and refuses to load.

**D3 — Declaration order is preserved, deliberately.** The registry indexes into
a `LinkedHashMap` rather than `Map.copyOf`, whose iteration order is salted per
JVM run. A CLI or UI listing indicators would otherwise reshuffle on every
restart. This was found by a test asserting the listed order, which failed only
on some runs.

**D4 — The contract is tested against whatever the classpath provides.**
`IndicatorContractTest` reads the `META-INF/services` file, asserts the registry
found exactly those providers, and then holds every discovered indicator to the
same rules: one value per bar, nothing defined inside the declared warm-up, a
value at the end of it, deterministic across calls, lowercase id. Adding a
private indicator to the classpath subjects it to the same test without editing
the test.

**D5 — `DuPontDecomposition` and `RealPriceAdjuster` are not `Indicator`s.** The
SPI maps a bar series to one value per bar. A DuPont decomposition is a function
of a financial statement and has no per-bar value; a real-price adjustment needs
a second exogenous series that `compute(BarSeries, Params)` cannot carry.
Widening the SPI so these two could fit would make every future indicator carry
a parameter it does not use, and forcing them into it would mean inventing
per-bar values the data does not have. Knowing where an abstraction stops is
cheaper than discovering it later through the code that worked around it.

**D6 — The moving average trades a little precision for one pass.** A running
sum makes the SMA O(n) rather than O(n x period); adding and subtracting the same
doubles for hundreds of bars accumulates roughly 1e-12 relative drift, which a
test pins against a direct recomputation over 500 bars. That is orders of
magnitude below the noise of any price series, and the cost of the alternative is
paid on every parameter sweep.

**D7 — EMA seeds on a simple average, RSI uses Wilder's smoothing.** Seeding an
EMA on the first close makes its early values a function of one observation, and
a strategy trading the start of a series would be trading that artefact. RSI uses
`avg = (avg * (period - 1) + current) / period` because every published RSI level
assumes it; a plain mean of the last `period` changes moves a 30/70 crossing by
several bars.

**D8 — No CPI data ships.** An inflation series is a published statistic with
vintages and revisions. Bundling a stale copy would invite backtests against
numbers nobody can reproduce, so the caller supplies the index, aligned to the
bars, and a length mismatch is an error rather than a silent truncation.

## Out of scope

Indicators over anything but closing prices (the SPI passes the whole `BarSeries`,
so volume-weighted or range-based indicators are possible, none ship), multi-
instrument indicators, and any indicator whose value depends on a strategy's
position.
