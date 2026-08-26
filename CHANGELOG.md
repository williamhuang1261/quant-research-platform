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
