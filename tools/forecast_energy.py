#!/usr/bin/env python3
"""Forecasting baselines and a Monte Carlo price-path simulation over the
real Henry Hub series loaded by energy_db.py.

Two one-step-ahead forecasting baselines are backtested against a real
holdout window and reported honestly, whichever wins:

- seasonal-naive: forecast(t) = price(t - SEASON_LENGTH), a 5-trading-day
  (one business week) lag.
- simple exponential smoothing: level fit on the training window only
  (alpha chosen by in-sample SSE grid search, never touching the holdout),
  then updated one step at a time as each holdout actual arrives.

The Monte Carlo simulation is the same block-bootstrap idea qrp-stats
already uses for its Java Monte Carlo (moving-block resampling of historical
returns, not a parametric distribution assumption), applied here to real
commodity log-returns instead of a synthetic equity series.

Usage:
    python3 tools/energy_db.py data/energy/henry_hub_2026-08-29.csv /tmp/energy.db
    python3 tools/forecast_energy.py /tmp/energy.db docs/energy-forecast.png
"""
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

import energy_db

HOLDOUT_DAYS = 30
SEASON_LENGTH = 5  # trading days in one business week
ALPHA_GRID = np.arange(0.05, 1.00, 0.05)
MC_PATHS = 1000
MC_BLOCK_SIZE = 5
MC_SEED = 20260829


def load_series(db_path: str):
    rows = energy_db.date_range(db_path, "0000-01-01", "9999-12-31")
    dates = [r[0] for r in rows]
    prices = np.array([r[1] for r in rows], dtype=float)
    return dates, prices


def seasonal_naive_forecast(train: np.ndarray, holdout_len: int) -> np.ndarray:
    """forecast(t) = price observed SEASON_LENGTH steps before t, walking
    forward one step at a time so later holdout points can reference earlier
    *actual* holdout points once SEASON_LENGTH exceeds the holdout length."""
    history = list(train)
    forecasts = np.empty(holdout_len)
    for i in range(holdout_len):
        forecasts[i] = history[len(history) - SEASON_LENGTH]
        history.append(history[len(history) - SEASON_LENGTH])
    return forecasts


def fit_ses_alpha(train: np.ndarray) -> float:
    """Grid-searches alpha by in-sample one-step-ahead SSE on the training
    window only -- the holdout is never touched by this fit."""
    best_alpha, best_sse = None, np.inf
    for alpha in ALPHA_GRID:
        level = train[0]
        sse = 0.0
        for actual in train[1:]:
            sse += (actual - level) ** 2
            level = alpha * actual + (1 - alpha) * level
        if sse < best_sse:
            best_alpha, best_sse = alpha, sse
    return best_alpha


def ses_forecast(train: np.ndarray, holdout: np.ndarray, alpha: float) -> np.ndarray:
    """One-step-ahead SES forecasts over the holdout: the level absorbs each
    holdout *actual* only after that step's forecast has been recorded, so
    no forecast uses information from its own or a later step."""
    level = train[-1]
    forecasts = np.empty(len(holdout))
    for i, actual in enumerate(holdout):
        forecasts[i] = level
        level = alpha * actual + (1 - alpha) * level
    return forecasts


def mae_rmse(actual: np.ndarray, forecast: np.ndarray):
    error = actual - forecast
    return np.mean(np.abs(error)), np.sqrt(np.mean(error ** 2))


def block_bootstrap_paths(returns: np.ndarray, last_price: float, horizon: int,
                           n_paths: int, block_size: int, seed: int) -> np.ndarray:
    """Simulates `n_paths` future price paths of length `horizon` by
    resampling contiguous blocks of historical log-returns -- preserves
    local autocorrelation the way drawing i.i.d. single-day returns would
    not, the same moving-block idea qrp-stats' Java Monte Carlo uses."""
    rng = np.random.default_rng(seed)
    n_blocks = int(np.ceil(horizon / block_size))
    max_start = len(returns) - block_size
    paths = np.empty((n_paths, horizon))
    for p in range(n_paths):
        sampled = []
        for _ in range(n_blocks):
            start = rng.integers(0, max_start + 1)
            sampled.extend(returns[start:start + block_size])
        path_returns = np.array(sampled[:horizon])
        paths[p] = last_price * np.exp(np.cumsum(path_returns))
    return paths


def render(dates, prices, holdout_len, seasonal_fc, ses_fc, mc_paths, output_path):
    fig, axes = plt.subplots(1, 3, figsize=(15, 4.2))

    axes[0].plot(range(len(prices)), prices, linewidth=0.8)
    axes[0].set_title(f"Henry Hub spot price, {dates[0]}..{dates[-1]}")
    axes[0].set_ylabel("$/MMBtu")
    axes[0].set_xlabel("trading day index")

    holdout_actual = prices[-holdout_len:]
    x = range(holdout_len)
    axes[1].plot(x, holdout_actual, label="actual", linewidth=1.5, color="black")
    axes[1].plot(x, seasonal_fc, label="seasonal-naive", linestyle="--")
    axes[1].plot(x, ses_fc, label="exp. smoothing", linestyle="--")
    axes[1].set_title(f"holdout backtest ({holdout_len} trading days)")
    axes[1].set_xlabel("holdout day index")
    axes[1].legend(fontsize=8)

    horizon = mc_paths.shape[1]
    median_path = np.median(mc_paths, axis=0)
    lower = np.percentile(mc_paths, 5, axis=0)
    upper = np.percentile(mc_paths, 95, axis=0)
    hx = range(horizon)
    for i in range(min(30, mc_paths.shape[0])):
        axes[2].plot(hx, mc_paths[i], color="gray", alpha=0.15, linewidth=0.6)
    axes[2].fill_between(hx, lower, upper, color="tab:blue", alpha=0.25, label="5-95% band")
    axes[2].plot(hx, median_path, color="tab:blue", label="median path")
    axes[2].set_title(f"block-bootstrap Monte Carlo ({mc_paths.shape[0]} paths)")
    axes[2].set_xlabel("days ahead")
    axes[2].legend(fontsize=8)

    fig.tight_layout()
    fig.savefig(output_path, dpi=130)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <db_path> <output_png>")
    db_path, output_path = sys.argv[1], sys.argv[2]

    dates, prices = load_series(db_path)
    if len(prices) <= HOLDOUT_DAYS + SEASON_LENGTH:
        raise SystemExit("not enough history for the configured holdout/season length")

    train, holdout = prices[:-HOLDOUT_DAYS], prices[-HOLDOUT_DAYS:]

    seasonal_fc = seasonal_naive_forecast(train, HOLDOUT_DAYS)
    seasonal_mae, seasonal_rmse = mae_rmse(holdout, seasonal_fc)

    alpha = fit_ses_alpha(train)
    ses_fc = ses_forecast(train, holdout, alpha)
    ses_mae, ses_rmse = mae_rmse(holdout, ses_fc)

    winner = "exponential smoothing" if ses_rmse < seasonal_rmse else "seasonal-naive"
    print(f"seasonal-naive:        MAE={seasonal_mae:.4f}  RMSE={seasonal_rmse:.4f}")
    print(f"exp. smoothing (a={alpha:.2f}): MAE={ses_mae:.4f}  RMSE={ses_rmse:.4f}")
    print(f"winner on this holdout: {winner}")

    log_returns = np.diff(np.log(prices))
    mc_paths = block_bootstrap_paths(
        log_returns, last_price=prices[-1], horizon=HOLDOUT_DAYS,
        n_paths=MC_PATHS, block_size=MC_BLOCK_SIZE, seed=MC_SEED,
    )
    median_final = np.median(mc_paths[:, -1])
    lower_final, upper_final = np.percentile(mc_paths[:, -1], [5, 95])
    print(
        f"Monte Carlo {HOLDOUT_DAYS}-day-ahead median final price: "
        f"{median_final:.2f} (5-95%: {lower_final:.2f}..{upper_final:.2f})"
    )

    render(dates, prices, HOLDOUT_DAYS, seasonal_fc, ses_fc, mc_paths, output_path)
    print(f"wrote {output_path}")


if __name__ == "__main__":
    main()
