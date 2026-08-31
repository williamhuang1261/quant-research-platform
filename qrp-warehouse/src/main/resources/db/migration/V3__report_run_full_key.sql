-- V1's cache key for fact_report_run omitted strategy params, both fee
-- rates and the timeframe: a request differing only in --fee or --param
-- would have collided with an unrelated cached report and returned its
-- (wrong) answer. Widened the same way fact_backtest_run's key already
-- covers everything that determines its result -- one canonical params_json
-- column plus timeframe, folded into the unique constraint alongside the
-- columns V1 already had.
ALTER TABLE fact_report_run
    ADD COLUMN params_json TEXT       NOT NULL DEFAULT '{}',
    ADD COLUMN timeframe   VARCHAR(8) NOT NULL DEFAULT '';

ALTER TABLE fact_report_run
    DROP CONSTRAINT fact_report_run_benchmark_instrument_id_strategy_id_candida_key;

ALTER TABLE fact_report_run
    ADD CONSTRAINT fact_report_run_cache_key UNIQUE (
        benchmark_instrument_id, strategy_id, candidate_symbols_csv,
        cash, cost_model, narrative_source, params_json, timeframe
    );
