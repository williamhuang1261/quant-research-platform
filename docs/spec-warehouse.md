# Spec — `qrp-warehouse`

Status: implemented (Extension 12, steps 1-3)

## Why this exists

A background portfolio-fit check run while tailoring a CV for a PIMCO
Technology Analyst posting flagged that the platform's only existing
database use, `tools/energy_db.py`, is a single-table SQLite script the
project's own README calls "deliberately minimal," and that `qrp-api`'s own
spec stated plainly: "No persistence. Every run is stateless." The posting
explicitly lists "SQL, RDBMS and data warehouse skills or experience" as a
requirement. `qrp-warehouse` closes that gap with a real RDBMS, a small
star schema, and a genuine read/write layer in front of `qrp-api`'s two
existing compute endpoints.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A real PostgreSQL schema: instrument/strategy dimensions, price-bar and backtest/report-run facts |
| R2 | The schema is applied through versioned migrations, not a hand-rolled `CREATE TABLE IF NOT EXISTS` |
| R3 | `POST /api/runs` and `GET /api/reports/compare` write every result through to the warehouse, and serve an identical repeat request from it instead of recomputing |
| R4 | A prior run is fetchable by id (`GET /api/runs/{id}`) with no engine invocation |
| R5 | The price fact table is queryable by an indexed date range, not just written |
| R6 | A clean clone builds and tests with no Docker daemon and no external account |

## Design decisions

**D1 — embedded Postgres (Zonky), not Testcontainers or a docker-compose
service.** `WarehouseDataSourceFactory` runs the actual PostgreSQL server
binary (published by EnterpriseDB) as a managed subprocess via
`io.zonky.test:embedded-postgres`. The SQL, the wire protocol and the JDBC
driver are all genuinely PostgreSQL — not a compatibility-mode substitute
such as H2, which would silently accept SQL a real Postgres deployment
might reject. This was not a default reached for out of habit: this
machine's Docker CLI is installed but its daemon was not running, confirmed
by `docker ps` failing during planning, before any code was written. A
design that depended on a live Docker daemon would have been unverifiable
here and undemoable on a reviewer's laptop with the same gap.
`WarehouseDataSourceFactory.create()` still honors `QRP_DB_URL`/
`QRP_DB_USER`/`QRP_DB_PASSWORD` first, so pointing the same code at a real
hosted Postgres instance is a config change, not a code change.

**D2 — Flyway, not a hand-rolled schema check.** `V1__init.sql`,
`V2__backtest_run_full_response.sql` and `V3__report_run_full_key.sql` are
real, ordered, checksummed migrations, run automatically by
`WarehouseDataSourceFactory.create()`. A migration tool is itself part of
the "RDBMS skills" a posting like this expects — not just writing `SELECT`
statements, but managing schema evolution over time. **Both V2 and V3
exist because a first design was found incomplete after it shipped, not
because the schema was planned in three passes:** V1's `fact_backtest_run`
only stored the five headline metrics, which could not reproduce
`RunResponse`'s full shape (an existing API contract this project's
restrictions forbid narrowing) — see the plan log for step 2. V1's
`fact_report_run` cache key omitted the strategy's own params and both
management fee rates, so two requests differing only in `--fee` would have
silently collided onto the same cached report — see the plan log for step
3. Both gaps were caught by tests failing (or, for the fee gap, by
reasoning through the key before shipping it) rather than discovered later
against real traffic.

**D3 — plain JDBC, no ORM.** Every repository (`InstrumentDimensionRepository`,
`StrategyDimensionRepository`, `PriceBarFactRepository`,
`BacktestRunFactRepository`, `ReportRunFactRepository`) is hand-written
`java.sql`, matching `tools/energy_db.py`'s own choice for SQLite and the
platform's general dependency-light habit. `qrp-warehouse` itself has no
Spring dependency at all — only `qrp-api` is framework-aware, the same
module-boundary discipline `qrp-realassets`/`qrp-signals`/`qrp-portfolio`
already apply (a standalone domain module, framework concerns kept out).

**D4 — upsert-based dimension lookups, find-then-insert for facts.**
`InstrumentDimensionRepository.findOrCreate`/`StrategyDimensionRepository.findOrCreate`
are single-round-trip `INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING id`
upserts: idempotent, and race-free between two concurrent callers.
`BacktestRunFactRepository`/`ReportRunFactRepository` are deliberately
*not* upsert-and-return-existing at the repository layer: `findByKey` is
the read side, `insert` is a plain insert (the database's own unique
constraint still enforces the key), and the caller — `RunController`/
`ReportController` — decides whether to skip recomputation. This keeps the
cache decision visible at the call site rather than hidden inside a
repository method's fallback branch.

**D5 — the equity curve is a native Postgres array, not a JSON blob.**
`fact_backtest_run.equity_curve` is `double precision[]`, round-tripped via
`Connection.createArrayOf("float8", ...)` and `ResultSet.getArray(...)`.
This is the one genuinely Postgres-specific feature in this schema (SQLite
has no array type), and it is the correct shape for what it holds — a
plain `double[]` on the Java side, with no JSON parsing on the read path.

**D6 — cache keys are canonical JSON built by `CacheKeys`, not the raw
request body.** `CacheKeys.canonicalParamsJson` sorts a backtest's params
into a `TreeMap` before serializing, so two requests carrying the same
values in a different JSON field order still land on the same key; it
folds in the four LOB tuning knobs only when `execution=lob`, so a
market-open request's key never grows an unused shape.
`CacheKeys.canonicalReportParamsJson` does the same for a report's params
plus both management fee rates. `CacheKeys.costModelName` reconstructs
`"none"`/`"retail"` from the two `CostModel` constants `--costs` can ever
produce, rather than serializing the model's numeric fields — `CliArguments`/
`CompareArguments` never hold anything else.

**D7 — candidate symbol order is part of the report cache key.**
`ReportController` joins `arguments.candidateSymbols()` in request order
into `candidate_symbols_csv`. Requesting the same candidates in a different
order is a cache miss, not a hit, even though the computed result would be
identical (the table is ranked after the fact, so input order does not
change the output). Normalizing this would mean sorting the candidate list
before hashing it — not done, since the key as built mirrors exactly what a
client sent, which is easier to reason about than a key that silently
reorders part of the request.

## Schema

```
dim_instrument(id, symbol UNIQUE, currency, asset_class)
dim_strategy(id, name UNIQUE)

fact_price_bar(id, instrument_id FK, timeframe, ts, open, high, low, close, volume,
               UNIQUE(instrument_id, timeframe, ts))
               -- the unique constraint doubles as the range-query index

fact_backtest_run(id, instrument_id FK, strategy_id FK, params_json, cash,
                   cost_model, execution_model, engine_id, initial_equity,
                   final_equity, total_return, cagr, annualised_volatility,
                   sharpe, max_drawdown, trades, time_in_market,
                   equity_curve DOUBLE PRECISION[], created_at,
                   UNIQUE(instrument_id, strategy_id, params_json, cash,
                          cost_model, execution_model))

fact_report_run(id, benchmark_instrument_id FK, strategy_id FK,
                 candidate_symbols_csv, cash, cost_model, narrative_source,
                 params_json, timeframe, table_json, narrative, created_at,
                 UNIQUE(benchmark_instrument_id, strategy_id,
                        candidate_symbols_csv, cash, cost_model,
                        narrative_source, params_json, timeframe))
```

Two dimensions, three facts — a small star schema, not a normalized OLTP
design: `fact_backtest_run` and `fact_report_run` denormalize their own
inputs (params, cost model, fee rates) directly into the row rather than
splitting them into further dimension tables, because the point of this
schema is answering "has this exact request already been computed," not
supporting arbitrary slice-and-dice analytics.

## What `qrp-api` does with it

- `WarehouseConfig` wires one `DataSource` bean over
  `WarehouseDataSourceFactory.create()`, shared by every repository.
- `WarehouseBackfillRunner` (a `CommandLineRunner`) backfills
  `fact_price_bar` from `data/sample/*.csv` on every startup — idempotent,
  so a restart is a non-event, not a growing table.
- `RunController`/`ReportController` resolve the request's `Instrument`
  and strategy dimension, build a cache key, check the matching fact
  table, and only call `BacktestRunner.run`/`CompareRunner.run` on a miss.
  A cache hit returns before either of those calls happen — structurally,
  not merely as an observed behavior in one test run.
- `RunController` also exposes `GET /api/runs/{id}`: its signature carries
  only an id, no symbol/strategy/params, so there is no `CliArguments` to
  build and therefore no way to reach `BacktestRunner.run` from that method
  at all.
- `PriceController` exposes `GET /api/warehouse/prices`, the one endpoint
  that reads the warehouse as a data source in its own right rather than as
  a cache in front of a compute call.

## What is deliberately not here

- **No eviction or TTL on cached runs or reports.** A row, once written,
  is served forever on a matching key; nothing expires or invalidates it.
  For a demo project over static sample data this is a non-issue — the
  underlying series never changes — but it would need addressing before
  this pattern were used over data that does.
- **No multi-tenant isolation.** There is one schema, one set of tables;
  nothing partitions or scopes rows by caller.
- **No connection-pool tuning beyond Spring Boot's own defaults.**
  `spring-boot-starter-web` configures a HikariCP pool automatically; this
  module does not override its size or timeouts.
- **No candidate-symbol-order normalization on the report cache key** — see
  D7.
- **No live migration-safety tooling** (no shadow-read, no blue/green
  schema rollout). `ALTER TABLE ... ADD COLUMN ... DEFAULT ...` in V2/V3 is
  safe for this project's scale (an empty table at migration time in every
  realistic case, since the embedded database is recreated per process),
  not proven safe against a large, live, already-populated table.
