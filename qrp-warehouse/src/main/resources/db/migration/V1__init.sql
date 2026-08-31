-- A small star schema: two dimensions (what was traded, what ran it) and
-- three facts (the market data itself, and the two things qrp-api computes
-- over it). Everything a cache-key or a range query needs is a real
-- constraint or index here, not application-level discipline.

CREATE TABLE dim_instrument (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(32) NOT NULL,
    currency    CHAR(3)     NOT NULL,
    asset_class VARCHAR(32) NOT NULL,
    UNIQUE (symbol)
);

CREATE TABLE dim_strategy (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    UNIQUE (name)
);

-- One row per instrument/timeframe/timestamp. The unique constraint doubles
-- as the upsert key for the CSV backfill loader, and the index it implies is
-- the same one a date-range query needs -- no separate index to maintain.
CREATE TABLE fact_price_bar (
    id            BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT           NOT NULL REFERENCES dim_instrument (id),
    timeframe     VARCHAR(8)       NOT NULL,
    ts            TIMESTAMPTZ      NOT NULL,
    open          DOUBLE PRECISION NOT NULL,
    high          DOUBLE PRECISION NOT NULL,
    low           DOUBLE PRECISION NOT NULL,
    close         DOUBLE PRECISION NOT NULL,
    volume        BIGINT           NOT NULL,
    UNIQUE (instrument_id, timeframe, ts)
);

-- One row per distinct backtest configuration. The unique constraint is the
-- cache key qrp-api's RunController checks before recomputing: an identical
-- request is a row that already exists, not a run that needs re-running.
CREATE TABLE fact_backtest_run (
    id               BIGSERIAL PRIMARY KEY,
    instrument_id    BIGINT           NOT NULL REFERENCES dim_instrument (id),
    strategy_id      BIGINT           NOT NULL REFERENCES dim_strategy (id),
    params_json      TEXT             NOT NULL,
    cash             DOUBLE PRECISION NOT NULL,
    cost_model       VARCHAR(32)      NOT NULL,
    execution_model  VARCHAR(32)      NOT NULL,
    final_equity     DOUBLE PRECISION NOT NULL,
    cagr             DOUBLE PRECISION NOT NULL,
    sharpe           DOUBLE PRECISION NOT NULL,
    max_drawdown     DOUBLE PRECISION NOT NULL,
    trades           INTEGER          NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (instrument_id, strategy_id, params_json, cash, cost_model, execution_model)
);

-- One row per distinct fund-comparison report. Mirrors fact_backtest_run's
-- cache-key shape so ReportController can skip both the recompute and a
-- repeat Ollama narrative call on an identical request.
CREATE TABLE fact_report_run (
    id                       BIGSERIAL PRIMARY KEY,
    benchmark_instrument_id  BIGINT      NOT NULL REFERENCES dim_instrument (id),
    strategy_id              BIGINT      NOT NULL REFERENCES dim_strategy (id),
    candidate_symbols_csv    VARCHAR(256) NOT NULL,
    cash                     DOUBLE PRECISION NOT NULL,
    cost_model               VARCHAR(32) NOT NULL,
    narrative_source         VARCHAR(16) NOT NULL,
    table_json               TEXT        NOT NULL,
    narrative                TEXT        NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (benchmark_instrument_id, strategy_id, candidate_symbols_csv, cash, cost_model, narrative_source)
);
