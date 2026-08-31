# Changelog

Notable changes to the public core. Versions follow [semantic versioning](https://semver.org).

## [Unreleased]

Initial public core. Nothing has been released yet, so everything below is the
first cut rather than a change to something people depend on.

### Added
- `qrp-core` — market data types (`Bar`, `BarSeries`, `Instrument`, `Timeframe`,
  `DoubleSeries`, `Params`, `Signal`), the four service-provider interfaces, and
  `PluginRegistry`.
- `qrp-data` — `CsvMarketDataProvider` over a manifest plus per-series files,
  `SyntheticSeriesGenerator`, and three bundled synthetic daily series.
- `qrp-indicators` — `sma`, `ema`, `rsi`, `volatility` behind the `Indicator`
  SPI, plus `DuPontDecomposition` and `RealPriceAdjuster`, which deliberately sit
  outside it.
- `qrp-engine` — event-driven bar loop with next-open fills, `CostModel`,
  portfolio accounting, `PerformanceMetrics`, and the reference
  `sma-crossover` strategy.
- `qrp-stats` — moving-block bootstrap, Monte Carlo path resampling, jackknife
  correlation, historical VaR and expected shortfall, and the specified
  `SplitMix64` generator.
- `qrp-native` — optional C++17/OpenMP compute kernel bound through the foreign
  function API, with an ABI-version guard and a documented fallback.
- `qrp-app` — `run`, `list`, `workbench` and `options` commands; JavaFX
  equity, drawdown and metrics view.
- Specs for every module under `docs/`, plus `docs/runbook.md` for the native
  kernel's build, failure modes and rollback.

## [Unreleased] — derivatives extension

Extended for the DV Trading / DV Securities Futures & Options Trading Analyst
Intern posting (2026-08-26). Additive only; nothing above is removed or
changed in behaviour.

### Added
- `qrp-options` — Black-Scholes-Merton with analytic delta/gamma/vega/theta/rho;
  Cox-Ross-Rubinstein binomial trees (European and American); antithetic-variate
  Monte Carlo; a Newton-Raphson implied volatility solver with a bracketed
  bisection fallback; `VolatilitySurface` (total-variance interpolation over
  strike and expiry) fitted from an `OptionChainProvider` plugin;
  `NoArbitrageDiagnostics` (butterfly convexity, calendar monotonicity in total
  variance, put-call parity); `RatesCurve` and `TreasuryCurveLoader` over a
  real, dated US Treasury par-yield snapshot; `BondAnalytics` (price, Macaulay
  and modified duration, DV01, convexity).
- `data/sample/SYNOPT_chain.csv` — a synthetic option chain priced from a
  known, stated volatility function, so `VolatilitySurface` can be checked
  against ground truth rather than against itself.
- `data/rates/ust_cmt_2026-08-25.csv` — a real Treasury.gov daily par yield
  curve snapshot, fetched live and committed dated; `tools/fetch_ust_curve.py`
  refreshes it, `tools/plot_surface.py` renders a fitted surface to a PNG.
- `qrp-app` gained the `options` command: prices a chain, fits its surface,
  runs the diagnostics, optionally exports a dense grid for plotting.
- `docs/spec-options.md`, nine design decisions (D1-D9).

### Known limitations (in addition to the above)
- `RatesCurve` is not yet wired into `VolatilitySurface`'s own IV solving;
  the surface discounts at each chain quote's own flat rate.
- Treasury par yields are used directly as zero rates; no bootstrap.
- `BondAnalytics` uses continuous compounding, not the bond-market
  semi-annual bond-equivalent convention.

### Known limitations
- One instrument per run; no portfolio-level allocation.
- No financing, borrow, tax or corporate-action modelling.
- No correction for multiple testing across a strategy search.

## [Unreleased] — limit-order-book execution model

Extended for the AQR Capital Management 2027 Trading Analyst posting
(2026-08-26). Additive only; nothing above is removed or changed in
behaviour — `MarketOpenExecutionModel` reproduces the pre-extension fill
logic exactly, and `BacktestIntegrationTest`'s golden-run numbers
(final equity `92,229.0094522352`, CAGR `-0.0411589578`, Sharpe
`-0.1745144272`, max drawdown `0.2414947349`, 11 trades) are unchanged.

### Added
- `qrp-engine` — `ExecutionModel`, the interface behind the bar loop's fill
  decision (given the reference bar, the pending target and current
  cash/shares, decide the fill or decline it); `MarketOpenExecutionModel`,
  the platform's original fill-at-open-plus-`CostModel` logic extracted
  behind it unchanged; `SyntheticOrderBook`, a synthetic bid/ask book with
  spread and per-level depth derived from a single bar's own OHLCV;
  `LimitOrderBookExecutionModel`, which rests a limit order against that
  book and honestly reports a partial fill or no fill when the book's depth
  does not cover the requested size. `BacktestRequest`'s `CostModel costs`
  field is replaced by a required `ExecutionModel execution` field.
- `qrp-app` — `qrp run --execution market-open|lob` (default
  `market-open`; `--execution=lob` inline form also accepted), plus
  `--lob-spread`, `--lob-offset`, `--lob-levels`, `--lob-depth` to tune the
  synthetic book. `ReportFormatter` gains an `execution` section (fill
  attempts, fills completed, fill rate, total and average slippage vs.
  reference) computed the same way for either model, so a `market-open` run
  and a `lob` run on the same strategy and data are honestly comparable.
- `docs/spec-execution.md` — the `ExecutionModel` interface, both
  implementations, how `SyntheticOrderBook` is constructed from a bar, and
  its stated limitations.

### Known limitations (in addition to the above)
- `SyntheticOrderBook` is a heuristic over OHLCV, not a reconstructed real
  order book — no tick-by-tick quotes or real limit order feed exists in
  this data set, matching the `SYNA`/`SYNOPT` synthetic-labelling precedent.
- A partially filled or unfilled limit order does not rest into the next
  bar; each bar re-evaluates a fresh pending target from the strategy's
  current signal, with no cross-bar order persistence.
- The synthetic book is a single static snapshot per bar; there is no model
  of the book moving as the order itself executes against it, or of
  market-maker/counterparty behaviour behind the quoted spread and depth.

## [Unreleased] — fund comparison and competitive analysis report

Extended for the Mackenzie Investments (IGM Financial) Summer Intern 2027,
Inside Sales posting (2026-08-26). Additive only; nothing above is removed or
changed in behaviour — the new `qrp-report` module depends only on
`qrp-core`, `qrp-engine` and `qrp-stats`, and the new `compare` command sits
beside `run`/`list`/`workbench`/`options` without touching any of them.

### Added
- `qrp-report` — `ManagementFeeModel` (a flat annual MER compounded per bar
  onto an equity curve, exact regardless of bar frequency); `FundProfile`
  (a fund's display name, `Instrument` and fee, kept separate from
  `Instrument` since the same security can back funds charging different
  fees); `FundComparisonRow` and `FundComparisonTable` (gross CAGR, Sharpe
  and max drawdown read from the engine's own `PerformanceMetrics` rather
  than recomputed, plus fee-adjusted net CAGR and historical 95% VaR/ES
  computed here, ranked by net CAGR with the benchmark row always present);
  `NarrativeGenerator`, `TemplateNarrativeGenerator` (a deterministic,
  always-available rule-based summary naming the net-CAGR and Sharpe
  leaders) and `OllamaNarrativeGenerator` (an optional local-Ollama-backed
  summary over the JDK's own `HttpClient`, labelled `[AI-generated summary]`
  and falling back to the labelled template output on any failure — server
  not running, timeout, malformed response — rather than throwing).
- `qrp-app` — the `compare` command: `CompareArguments` (candidate and
  benchmark symbols, per-fund fee rates, `--narrative template|ollama`),
  `CompareRunner` (backtests every candidate and the benchmark identically —
  same strategy, params, `MarketOpenExecutionModel` and cost model — and
  builds the `FundComparisonTable`), and `FundComparisonReportFormatter`
  (a one-page fixed-width table plus the narrative paragraph, matching
  `ReportFormatter` and `OptionsReportFormatter`'s conventions).
- `docs/spec-report.md` — requirements R1-R10 and design decisions D1-D7,
  covering the fee model, the comparison table, and both narrative
  generators' honest-fallback and labelling behaviour.

### Known limitations (in addition to the above)
- No live fund or pricing data feed; `compare` runs the same synthetic
  `SYNA`/`SYNB`/`SYNETF` series every other command uses.
- No real MERs — `ManagementFeeModel`'s rates are a synthetic fee schedule,
  not sourced from any actual fund's fact sheet or prospectus, and the model
  is a single flat annual rate rather than the tiered, trailing-commission
  structure of an actual Canadian mutual fund.
- No regulatory or prospectus compliance checking; the report is a research/
  sales-support artifact, not a disclosure check.

## [Unreleased] — portfolio construction under constraints

Extended for the AQR Capital Management Portfolio Implementation Analyst
posting (2026-08-26). Additive only; nothing above is removed or changed in
behaviour — the new `qrp-portfolio` module composes the existing
single-instrument `BacktestEngine` per instrument rather than modifying it,
so `BacktestIntegrationTest`'s golden-run numbers (final equity
`92,229.0094522352`, CAGR `-0.0411589578`, Sharpe `-0.1745144272`, max
drawdown `0.2414947349`, 11 trades) are unchanged, and the new `portfolio`
command sits beside `run`/`list`/`workbench`/`options`/`compare` without
touching any of them.

### Added
- `qrp-portfolio` — `PortfolioOptimizer` SPI (expected returns + covariance +
  previous weights + constraints in, target weights out); `PortfolioConstraints`
  (max weight, max turnover, leverage, optional per-sector caps, validated
  together); `CovarianceEstimator` (an `n x n` sample covariance matrix built
  from `JackknifeCorrelation.correlation` on the off-diagonal and sample
  variance on the diagonal, so the correlation used to judge a signal and the
  covariance an optimizer allocates against can never disagree);
  `MeanVarianceOptimizer` (constrained mean-variance via projected gradient
  descent, long-only, matching the closed-form two-asset answer when nothing
  binds); `EqualRiskContributionOptimizer` (risk parity via cyclical
  coordinate descent, converging to the closed-form inverse-volatility
  weights on a diagonal covariance matrix; ignores expected returns
  entirely); `PortfolioProjections` (the shared capped-simplex and
  turnover-ball projections both optimizers use, so the SPI's
  box/leverage/turnover contract is honored identically by both);
  `PortfolioBacktestEngine` and `PortfolioBacktestResult` (scheduled
  multi-instrument rebalancing — `RebalanceFrequency.MONTHLY|WEEKLY` —
  composed on top of the unmodified single-instrument engine, reporting
  per-rebalance target weights, realized risk contribution and total
  turnover rather than just an aggregate equity curve).
- `qrp-app` — the `portfolio` command: `PortfolioArguments`
  (`--optimizer mean-variance|risk-parity`, `--rebalance monthly|weekly`,
  both with an inline `--flag=value` form; plus `--symbol` (repeatable, at
  least two), `--lookback`, `--max-weight`, `--turnover` (a number or
  `none`), `--risk-aversion`, `--cash`, `--costs`, `--data`),
  `PortfolioRunner` (resolves symbols, computes each instrument's view as a
  20-bar trailing momentum, and runs `PortfolioBacktestEngine`), and
  `PortfolioReportFormatter` (per-instrument average weight and average risk
  contribution, total turnover, in the same fixed-width, caveats-included
  style as `ReportFormatter` and `FundComparisonReportFormatter`).
- `docs/spec-portfolio.md` — the SPI, both optimizers' design decisions
  (D1-D9), the covariance estimator, and stated limitations.

### Known limitations (in addition to the above)
- No factor model — expected returns are supplied by the caller (the CLI's
  own 20-bar momentum, or a caller's own view); nothing here estimates them.
- No covariance shrinkage (Ledoit-Wolf or similar) — `CovarianceEstimator`
  is the plain sample estimator, a known weakness for a large instrument
  universe.
- No transaction-cost optimization beyond the turnover cap — a rebalance
  either fits inside `PortfolioConstraints.maxTurnover()` or it does not,
  with no cost-aware trade-off search.
- No sector-cap *enforcement* — `PortfolioConstraints` states per-sector
  bounds, but neither optimizer shipped in this extension allocates by
  sector.
- Synthetic series only — every number in the spec, the README transcript
  and the golden-run tests comes from the platform's bundled
  `SYNA`/`SYNB`/`SYNETF` series, not real prices.

## [Unreleased] — cross-sectional signal generation

Extended for the Connor, Clark & Lunn Investment Management Intern,
Quantitative Equity Analyst posting (2026-08-27). Additive only; nothing
above is removed or changed in behaviour — the new `qrp-signals` module adds
no field to `PortfolioOptimizer` or `PortfolioBacktestEngine`, and the
`portfolio` command's existing flat-momentum default is unchanged unless
`--signal` is passed.

### Added
- `qrp-signals` — `RankTransform` (average-rank ties); `InformationCoefficient`
  (Spearman rank correlation between a cross-sectional signal and its forward
  return, per period, built on `JackknifeCorrelation.correlation` over
  rank-transformed arrays rather than a second correlation formula);
  `SignalSignificance` (mean, standard error, z-statistic and two-sided
  p-value over an IC time series — a large-sample z-approximation, since the
  platform has no Student's-t implementation); `CrossSectionalSignalGenerator`
  (turns one `Indicator`'s cross-sectional rank each bar into a per-instrument
  `expectedReturns` forecast, in the exact shape `PortfolioBacktestEngine`
  already accepts); `ForwardReturns` (a close-to-close forward-return utility
  used only by tests, never by the generator, so the generator structurally
  cannot see the future it is later scored against).
- `qrp-app` — the `portfolio` command gained `--signal <indicator-id>`
  (plus `--signal-period`, `--signal-spread`), driving the view from a
  generated forecast instead of the flat placeholder; `PortfolioReportFormatter`
  prints the signal's IC, standard error, z, p and a significant-at-5% line
  when `--signal` is used.
- `docs/spec-signals.md` — requirements R1-R5, design decisions D1-D5.

### Known limitations (in addition to the above)
- Single indicator per signal — no multi-factor combination or factor
  orthogonalization.
- No transaction-cost-aware signal decay.
- The significance test is a large-sample z-approximation, not a
  small-sample Student's-t test; it understates uncertainty when the IC
  series has only a handful of periods.
- Synthetic series only — the golden-run IC (RSI on `SYNA`/`SYNB`/`SYNETF`,
  mean IC +0.0278, p 0.3695) is not a claim of a proven-predictive signal.

## [Unreleased] — Spring Boot REST API

Extended for the RBC Wealth Management Winter Technology/Developer Co-op
posting (2026-08-27). Additive only; nothing above is removed or changed in
behaviour — `qrp-api` is a new, ninth module that calls the existing
`qrp-app` code unmodified.

### Added
- `qrp-api` — `QrpApiApplication` (`@SpringBootApplication`), `RunController`
  (`POST /api/runs`), translating a JSON request into the exact `--flag
  value` list `CliArguments.parse` already accepts and handing it to the
  unmodified `BacktestRunner.run`; `RunRequest`/`RunResponse` DTOs;
  `ApiExceptionHandler` mapping `IllegalArgumentException`/
  `MarketDataException` to `400` with the CLI's own message.
- `docs/spec-api.md` — requirements R1-R5, design decisions D1-D5.
- Root `pom.xml` gained a Spring Boot 3.5.16 BOM import (`dependencyManagement`,
  not `spring-boot-starter-parent`, since the root pom is already the
  platform's parent) and the `qrp-api` module entry.

### Known limitations (in addition to the above)
- No authentication or authorization.
- No persistence — every run is stateless, with no way to list or re-fetch a
  past run by ID.
- No pagination or streaming for the equity curve; a long backtest returns
  its full curve in one JSON array.
- No data-directory override in the request body — accepting an arbitrary
  filesystem path from an HTTP request is a different trust boundary than a
  local CLI flag, deliberately out of scope.

## [Unreleased] — fund comparison report over REST

Extended for the BMO Capital Markets Winter 2027 Full Stack Engineer
(Data Cognition Team) posting (2026-08-27). Additive only; nothing above is
removed or changed — a second endpoint in the existing `qrp-api` module,
calling existing `qrp-report`/`qrp-app` code unmodified.

### Added
- `qrp-api` — `ReportController` (`GET /api/reports/compare`), translating
  query parameters into the exact `--flag value` list
  `CompareArguments.parse` already accepts and handing it to the unmodified
  `CompareRunner.run`; `ReportRowResponse`/`ReportResponse` DTOs. No CAGR,
  Sharpe, VaR/ES or narrative logic is reimplemented — every number and
  every sentence comes from `qrp-report`. Reuses the existing
  `ApiExceptionHandler` unchanged.
- `docs/spec-api.md` — requirements R6-R9, design decisions D6-D8 for the
  new endpoint.

### Known limitations (in addition to the above)
- Same no-auth, no-persistence, no-data-directory-override scope as
  `POST /api/runs`.
- `?narrative=ollama` inherits the CLI's own fail-closed behaviour (falls
  back to the labelled template on any Ollama failure); this extension adds
  no new fallback logic of its own.

## [Unreleased] — RDBMS/data-warehouse persistence layer

Extended for a PIMCO Technology Analyst, Software Engineering (EMEA)
posting (2026-08-31), flagged by a portfolio-fit check during CV tailoring:
the platform's only prior database use (`tools/energy_db.py`, SQLite) was
explicitly minimal, and this file's own "no-persistence" line, above, was
literally true until now. Additive only; both existing `qrp-api` endpoints
gain a read/write layer, nothing already shipped is removed.

### Added
- `qrp-warehouse` (14th module) — a real, embedded PostgreSQL server
  (`io.zonky.test:embedded-postgres`, no Docker daemon or external account
  needed), a Flyway-migrated star schema (`dim_instrument`, `dim_strategy`,
  `fact_price_bar`, `fact_backtest_run`, `fact_report_run`), five plain-JDBC
  repositories, and `WarehousePriceLoader`, a CSV-to-warehouse backfill
  loader.
- `qrp-api` — `RunController`/`ReportController` now read/write through the
  warehouse: an identical repeat request is served from Postgres instead of
  recomputed; a new `GET /api/runs/{id}` reads a prior run back with no
  engine invocation, structurally (its signature carries no symbol,
  strategy or params to recompute from); a new `PriceController`
  (`GET /api/warehouse/prices`) answers an indexed date-range query over
  the backfilled price fact table. `RunResponse`/`ReportResponse` gain
  `id`/`cached` fields, additive to their existing shape.
- `docs/spec-warehouse.md` — schema, cache-key design, why embedded
  Postgres over Testcontainers or docker-compose, and two real design gaps
  found and fixed before shipping (`fact_backtest_run` narrowing
  `RunResponse`'s shape; `fact_report_run`'s key omitting fee rates).

### Known limitations (in addition to the above)
- No eviction or TTL on a cached run or report — a row, once written, is
  served forever on a matching key.
- No multi-tenant isolation; one schema, no per-caller scoping.
- The report cache key is order-sensitive over candidate symbols:
  requesting the same candidates in a different order is a cache miss, not
  a hit, even though the computed result is identical.
- No connection-pool tuning beyond Spring Boot's own `spring-boot-starter-web`
  defaults.

## Release checklist

1. `mvn verify` passes on Ubuntu and macOS, with and without the native kernel.
2. `make -C native clean all` succeeds, and `mvn -pl qrp-native test` reports the
   equivalence tests as run rather than skipped.
3. The golden run in `BacktestIntegrationTest` is unchanged, or the change is
   explained in this file — those numbers are quoted in the README and in
   `docs/spec-engine.md`, and both must be updated with it.
4. Benchmark table in `README.md` and `docs/runbook.md` re-measured if anything
   in `qrp-stats` or `native/src/kernel.cpp` changed.
5. `qrp_abi_version()` bumped if any kernel signature or numeric convention
   changed.
6. Tag `v<major>.<minor>.<patch>`; the tag is the release.
