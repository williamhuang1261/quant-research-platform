-- fact_backtest_run originally stored only the headline metrics. RunResponse
-- (qrp-api) carries more than that -- engine id, initial equity, total
-- return, annualised volatility, time in market and the full equity curve --
-- and this project's restrictions forbid trimming an existing API response
-- shape to fit a smaller persisted one. So the fact table grows to match the
-- response instead. A native array column for the equity curve, rather than
-- a JSON blob, is deliberate: it is the one genuinely Postgres-specific
-- feature in this schema, and it is exactly the right shape for what it holds.
ALTER TABLE fact_backtest_run
    ADD COLUMN engine_id             VARCHAR(32)        NOT NULL DEFAULT '',
    ADD COLUMN initial_equity        DOUBLE PRECISION   NOT NULL DEFAULT 0,
    ADD COLUMN total_return          DOUBLE PRECISION   NOT NULL DEFAULT 0,
    ADD COLUMN annualised_volatility DOUBLE PRECISION   NOT NULL DEFAULT 0,
    ADD COLUMN time_in_market        DOUBLE PRECISION   NOT NULL DEFAULT 0,
    ADD COLUMN equity_curve          DOUBLE PRECISION[] NOT NULL DEFAULT '{}';
