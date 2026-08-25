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
- `qrp-app` — `run`, `list` and `workbench` commands; JavaFX equity, drawdown
  and metrics view.
- Specs for every module under `docs/`, plus `docs/runbook.md` for the native
  kernel's build, failure modes and rollback.

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
