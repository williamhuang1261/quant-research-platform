# Spec — real commodity price data, SQL persistence, forecasting and Monte Carlo (Extension 9)

Status: implemented (Extension 9, steps 1-3)

## Why this exists

Every series shipped elsewhere in this repository is synthetic
(`SYNA`/`SYNB`/`SYNETF` in the equity/portfolio modules, `SYNOPT` in the
options module) and no module has ever persisted anything in a database --
bars, curves and option chains are all in-memory Java objects or a CSV read
straight into one. This extension adds a real, public commodity price
series (Henry Hub natural gas spot price) and a small SQLite layer around
it, then applies the platform's existing statistical style -- block-
bootstrap resampling, honestly-reported backtests -- to that real data
through a Python tool, the same relationship `plot_surface.py` and
`fetch_ust_curve.py` already have to the Java side.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A real, public, no-API-key commodity price series, committed and refreshable by hand |
| R2 | A SQLite schema and query layer over that series, with tests |
| R3 | Two one-step-ahead forecasting baselines, backtested against a real holdout and reported honestly regardless of which wins |
| R4 | A Monte Carlo price-path simulation via block-bootstrap resampling of real log-returns, not a parametric distribution assumption |
| R5 | Rendered output (PNG) a reviewer can look at without running anything |

## Data (`data/energy/`, `tools/fetch_energy_prices.py`)

Source: EIA.gov's "Henry Hub Natural Gas Spot Price" series (`RNGWHHD`),
fetched as the legacy `.xls` the EIA still serves for this series
(`https://www.eia.gov/dnav/ng/hist_xls/RNGWHHDd.xls`), parsed with `xlrd`.
Public domain, no API key, no account -- verified reachable with a plain
`curl -sI` before any code was written. `data/energy/henry_hub_<date>.csv`
is the committed snapshot; refreshing it is a manual, offline-by-default
step, not something CI or any Java code runs.

One date in the source data (2018-01-05) has no reported price -- an
unexplained gap in the EIA's own history, not a bug in this fetch. Rather
than fabricate a value, `energy_db.load_csv` skips it and prints the skip
count, so the gap is visible rather than silently interpolated or
forward-filled.

## SQLite layer (`tools/energy_db.py`)

Schema: `prices(date TEXT PRIMARY KEY, price_usd_per_mmbtu REAL NOT NULL)`.
`load_csv` uses `INSERT OR REPLACE`, so re-running against the same CSV and
database file is idempotent -- the row count is unchanged, never doubled.
`date_range` is a plain `BETWEEN` query. `rolling_average` uses a SQL
`WINDOW` clause (`ROWS BETWEEN n-1 PRECEDING AND CURRENT ROW`) rather than
recomputing the average in Python, and returns `NULL` for rows with fewer
than a full window of history behind them rather than a partial-window
average silently masquerading as a full one. `tools/test_energy_db.py` (4
unittest cases, stdlib only) checks the row count, idempotent reload, a
date-range slice, and cross-checks the rolling average against NumPy on the
same window.

## Forecasting backtest (`tools/forecast_energy.py`)

The final 30 trading days of the real series are held out. Two baselines
are fit on the training window only and forecast one step at a time over the
holdout:

- **Seasonal-naive** -- forecast(t) = the price observed 5 trading days
  (one business week) earlier, walking forward one step at a time.
- **Simple exponential smoothing** -- a single smoothing level, no trend or
  seasonal term. `alpha` is chosen by an in-sample SSE grid search over the
  training window (0.05 step), then the level is updated one holdout step
  at a time as each actual arrives -- a holdout point's forecast never sees
  its own or a later step's actual value.

Both are scored by MAE and RMSE over the holdout, and whichever wins is
reported as such. On the run that shipped with this extension, exponential
smoothing (alpha 0.90) won (MAE 0.0684, RMSE 0.0925) against seasonal-naive
(MAE 0.1910, RMSE 0.2564) -- a losing seasonal-naive baseline is published
here the same way this repo already publishes a losing moving-average
crossover strategy and a losing OpenMP-vs-serial benchmark: the honest
result, not a cherry-picked one.

## Monte Carlo price-path simulation

`block_bootstrap_paths` resamples contiguous blocks (5 trading days each)
of the full series' real daily log-returns to build simulated future price
paths, compounding each path from the last observed price via
`exp(cumsum(sampled_returns))`. This is the same moving-block idea
`qrp-stats`'s Java Monte Carlo already uses for the equity backtest --
resampling in blocks preserves whatever local autocorrelation the real
returns have, which drawing i.i.d. single-day returns would destroy. 1000
paths, 5-day blocks, a fixed seed for reproducibility. The rendered fan
chart shows the median path and the 5-95% band alongside a sample of
individual paths, not just the band, so a reader can see the dispersion
directly rather than only its summary.

## What is deliberately not here

- **No futures or forward curve.** Only the daily spot print is modelled;
  no term structure, no basis, no storage/carry cost.
- **No weather data**, despite weather being the stated driver of power/gas
  demand in Engelhart's own posting -- the Henry Hub spot series alone
  cannot separate a weather-driven move from any other cause, and no
  weather dataset was joined in to make that claim.
- **No real-time feed.** One EIA-reported daily print per trading day, no
  volume, no bid/ask, no intraday granularity.
- **No walk-forward re-fitting of the exponential-smoothing alpha.** It is
  fit once on the training window and held fixed through the whole holdout,
  not re-optimized as new holdout actuals arrive.
- **No ARIMA or other multi-parameter time-series model.** Seasonal-naive
  and single-parameter exponential smoothing are the two baselines; neither
  needs a library beyond NumPy, keeping this tool as dependency-light as
  `plot_surface.py`.
