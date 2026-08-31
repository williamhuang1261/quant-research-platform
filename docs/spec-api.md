# Spec — `qrp-api`

Status: complete (Extension 6, step 1-2; Extension 7, step 1-2)

## Why this module exists

`BacktestRunner.run(CliArguments)` is already the platform's one shared entry
point for running a backtest: the CLI (`QrpCli`) and the JavaFX workbench both
call it, and neither duplicates the other's parsing, defaulting or execution
logic. `qrp-api` is a third caller of that same method, over HTTP instead of a
terminal or a window — built to demonstrate Spring Boot / Java web-service
skills a job posting asked for, using the platform's own established pattern
of front ends sharing one core rather than inventing a second.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | `POST /api/runs` runs a backtest and returns its summary as JSON |
| R2 | An empty request body reproduces the CLI's own zero-flag `run`, including its pinned golden-run numbers |
| R3 | The request grammar mirrors the CLI's own flags, so no new validation or defaulting logic is written |
| R4 | A bad request (unknown symbol, unknown strategy, invalid parameter) returns `400` with a readable message, not a stack trace |
| R5 | Nothing outside `qrp-api` changes — every existing golden-run test stays byte-for-byte unchanged |
| R6 | `GET /api/reports/compare` runs the existing fund comparison report and returns it, including the AI-narrative text, as JSON |
| R7 | No parameters reproduces the CLI's own zero-flag `compare` — same ranked rows, same default template narrative |
| R8 | The query grammar mirrors `compare`'s own flags, so no new validation or defaulting logic is written for this endpoint either |
| R9 | Nothing outside `qrp-api` changes for this endpoint either — `qrp-report` and `qrp-app`'s `CompareRunner`/`CompareArguments` stay untouched |

## Design decisions

**D1 — the controller translates JSON into the CLI's own flag list, rather
than building a `BacktestRequest` by hand.** `RunController.toCliArgs`
converts a `RunRequest` into the exact `List<String>` shape
`CliArguments.parse` already accepts (`--symbol`, `--strategy`, `--param
k=v`, `--execution`, the four `--lob-*` flags, and so on), then calls
`CliArguments.parse` followed by the unmodified `BacktestRunner.run`. This
means the endpoint's defaults (`sma-crossover` with `fast=20`/`slow=50` when
no strategy is named, `data/sample` as the data directory, `retail` costs,
`market-open` execution) and its validation (an unknown symbol, `fast >=
slow`, a negative `--paths`) are the CLI's own, exercised through its public
API — not a second, parallel copy of the same rules that could quietly drift
from the first.

**D2 — the response is a hand-picked summary DTO, not a serialization of
`BacktestResult`.** `RunResponse` carries `PerformanceMetrics`' fields, the
trade count, and the equity curve as a plain `double[]`
(`DoubleSeries.toArray()`, the same method `BacktestRunner.simulate` already
uses internally). The wire format is a stable contract chosen for the API,
not whatever shape the internal result record happens to have — a change to
`BacktestResult`'s internals does not need to be a breaking API change.

**D3 — `qrp-api` depends on `qrp-app`, not directly on `qrp-engine`.** The
alternative — building a `BacktestRequest` directly against `qrp-engine` and
`qrp-core` — would mean re-implementing `CliArguments`' defaults and
validation a second time. Depending on `qrp-app` instead means every default
this module has is the CLI's own by construction. The cost is real and
stated rather than hidden: `qrp-app` carries JavaFX (`javafx-controls`,
`-graphics`, `-base`, `-swing`) as compile dependencies for the workbench,
so `qrp-api` pulls that dependency graph into a module that never opens a
window. Reusing the CLI's validated defaults was judged worth that cost over
a REST module with its own, unvalidated copy of the same rules.

**D4 — two exception types map to `400`, everything else falls through to
Spring's default `500`.** `ApiExceptionHandler` catches exactly
`IllegalArgumentException` (from `CliArguments.parse`, from
`BacktestRequest`'s compact constructor, from a strategy's own parameter
validation such as `fast >= slow`) and `MarketDataException` (an unknown
symbol). Both carry a message already written for a CLI user reading a
terminal; the handler surfaces that same message as JSON rather than writing
a second set of error strings. No other exception type is caught here, so an
unexpected failure is not silently reported as a client error.

**D5 — the data directory is not a request field.** `CliArguments`'
`--data` flag exists, but `RunRequest` does not expose an equivalent:
accepting an arbitrary filesystem path from an HTTP request body is a
different trust boundary than accepting one from a local CLI invocation, and
this module does not need to solve that to demonstrate the REST layer. The
data directory stays the CLI's own default (`data/sample`, resolved relative
to the working directory), which is why both `maven-surefire-plugin`
(`qrp-api`'s tests) and `spring-boot-maven-plugin`'s `run` goal are
configured with `workingDirectory=${maven.multiModuleProjectDirectory}` —
the same override `qrp-app/pom.xml` already applies to
`exec-maven-plugin`, so the default resolves against the repo root in every
context this module runs in.

**D6 — `ReportController` reuses `ApiExceptionHandler` unchanged.**
`CompareRunner.run` can only throw `IllegalArgumentException` (from
`CompareArguments.parse` or `CompareArguments`' own compact constructor) or
`MarketDataException` (an unknown symbol) — the same two types
`ApiExceptionHandler` already maps to `400`. No new exception-handling code
was needed for this endpoint.

**D7 — the response is a hand-picked DTO mirroring `RunResponse`'s own
approach, not a serialization of `FundComparisonRow`.** `ReportRowResponse`
carries the same fields `FundComparisonRow` does, in the same order
`FundComparisonTable` already ranks them; `ReportResponse` adds the strategy
id, both symbol lists, and the resolved narrative string. As with D2, this
keeps the wire format a stable contract rather than tied to whatever shape
the internal report types happen to have.

**D8 — `?narrative=ollama` adds no new fallback logic.** The query parameter
maps straight onto `CompareArguments`' own `--narrative` flag, so
`OllamaNarrativeGenerator`'s existing fail-closed behaviour (try the local
Ollama server on a short timeout; fall back to the labelled template output
on any failure) is exercised unchanged — this extension is a second front
end for that behaviour, not a second implementation of it.

## What step 1 added

`qrp-api` (9th module): `QrpApiApplication` (`@SpringBootApplication`),
`RunController` (`POST /api/runs`), `RunRequest`/`RunResponse` DTOs,
`ApiError`, `ApiExceptionHandler`. 4 tests in `RunControllerTest`
(`@SpringBootTest` + `MockMvc`): an empty-body run pinned to the same
golden-run numbers `BacktestIntegrationTest` (`qrp-engine`) and `QrpCliTest`
(`qrp-app`) already pin, an explicit `--execution=lob` request, an unknown
symbol, and an invalid strategy parameter (`fast >= slow`).

## What Extension 7 added

A second endpoint in the same module: `ReportController` (`GET
/api/reports/compare`), `ReportRowResponse`/`ReportResponse` DTOs. 2 tests
in `ReportControllerTest`: a no-params request pinned to real numbers read
off an actual run against `data/sample` (SYNA/SYNB ranked against SYNETF,
the default template narrative), and an unknown symbol returning `400` with
the CLI's own message. `ApiError`/`ApiExceptionHandler` are unchanged (D6).

## What Extension 12 added

A real persistence layer, in a new module (`qrp-warehouse`), wired into
both existing endpoints: every run/report now writes through to Postgres
and an identical repeat request is served from it instead of recomputed;
`GET /api/runs/{id}` reads a prior run back with no engine invocation; a
new `PriceController` (`GET /api/warehouse/prices`) answers an indexed
date-range query over a backfilled price fact table. `RunResponse`/
`ReportResponse` gained `id`/`cached` fields. See
[`docs/spec-warehouse.md`](docs/spec-warehouse.md) for the schema, the
cache-key design, and why embedded Postgres over Testcontainers or a
docker-compose service. This directly retires the "No persistence" line
that used to be in this file's limitations section below.

## Running it

```
mvn -pl qrp-api spring-boot:run
```

starts the service on port 8080. A clean POST with no body runs the same
default backtest `qrp run` would:

```
$ curl -s -X POST localhost:8080/api/runs -H 'Content-Type: application/json' -d '{}'
{"id":1,"cached":false,"strategyId":"sma-crossover","engineId":"openmp","executionId":"market-open",
 "initialEquity":100000.0,"finalEquity":92229.0094522352,
 "totalReturn":-0.077709905477648,"cagr":-0.04115895776844469,
 "annualisedVolatility":0.15940362521662213,"sharpeRatio":-0.174514427227237,
 "maxDrawdown":0.24149473488945833,"tradeCount":11,
 "timeInMarket":0.4365079365079365,
 "equityCurve":[100000.0,100000.0, ... 502 values ..., 92229.0094522352]}
```

`finalEquity`, `cagr`, `sharpeRatio`, `maxDrawdown` and `tradeCount` are
exactly the numbers `BacktestIntegrationTest` and `QrpCliTest` already pin —
one engine, three front ends, the same answer. `id`/`cached` are new as of
Extension 12 — see [`docs/spec-warehouse.md`](docs/spec-warehouse.md) for a
real cache-miss-then-hit transcript. A request naming an unavailable symbol
returns the CLI's own error message as JSON, at `400`:

```
$ curl -s -X POST localhost:8080/api/runs -H 'Content-Type: application/json' -d '{"symbol":"NOPE"}'
{"message":"unknown symbol 'NOPE'; available: [SYNA, SYNB, SYNETF]"}
```

A no-params `GET` against the report endpoint runs the same default
comparison `qrp compare` would, narrative included:

```
$ curl -s localhost:8080/api/reports/compare
{"id":1,"cached":false,"strategyId":"sma-crossover","candidateSymbols":["SYNA","SYNB"],"benchmarkSymbol":"SYNETF",
 "rows":[
   {"displayName":"SYNA","isBenchmark":false,"grossCagr":-0.04115895776844469,
    "netCagr":-0.061038983750053344,"sharpeRatio":-0.174514427227237,
    "maxDrawdown":0.24149473488945833,"valueAtRisk95":0.021105515419002935,
    "expectedShortfall95":0.02669071332973513,"benchmarkRelativeBps":-604.6589158938831},
   {"displayName":"SYNB","isBenchmark":false,"grossCagr":-0.2749326919491941,
    "netCagr":-0.2899657957563877,"sharpeRatio":-1.376384068284148,
    "maxDrawdown":0.5633238562486849,"valueAtRisk95":0.026131564759296788,
    "expectedShortfall95":0.037046968822728316,"benchmarkRelativeBps":-2893.9270359572265},
   {"displayName":"SYNETF","isBenchmark":true,"grossCagr":3.6057950333079347E-4,
    "netCagr":-5.730921606650341E-4,"sharpeRatio":0.04418458761844169,
    "maxDrawdown":0.06967146713311362,"valueAtRisk95":0.009027492754577053,
    "expectedShortfall95":0.013041979156967852,"benchmarkRelativeBps":0.0}],
 "narrative":"[template summary] SYNA posted the strongest net-of-fee return in this
 comparison, at -6.10% annualized. SYNA also delivered the best risk-adjusted return,
 with a Sharpe ratio of -0.17, so the same fund led on both measures. SYNA trailed the
 SYNETF benchmark by 605 bps net of fees."}
```

These are the same figures the README's `qrp compare` transcript shows
rounded to two decimal places — one report, two front ends, the same
answer. An unknown symbol behaves identically to the run endpoint:

```
$ curl -s "localhost:8080/api/reports/compare?symbol=NOPE"
{"message":"unknown symbol 'NOPE'; available: [SYNA, SYNB, SYNETF]"}
```

## What this module deliberately does not do

- **No authentication or authorization.** Every request runs a backtest with
  no notion of a caller identity; not a concern this extension set out to
  demonstrate.
- ~~No persistence.~~ **Retired by Extension 12** — every run/report now
  writes through to a real Postgres warehouse; a past run is re-fetchable
  by ID (`GET /api/runs/{id}`); see
  [`docs/spec-warehouse.md`](docs/spec-warehouse.md).
- **No pagination or streaming for the equity curve.** A long backtest
  returns its full curve in one JSON array; a data set large enough to make
  that impractical would need a different wire format, not addressed here.
- **No data-directory override in the request.** See D5 — accepting an
  arbitrary filesystem path from an HTTP body is a different trust boundary
  than a local CLI flag, deliberately out of scope.
- **No rate limiting or request size limits beyond Spring Boot's own
  defaults.**
- **No data-directory override for the report endpoint either.** Same
  reasoning as D5; `GET /api/reports/compare` inherits `compare`'s own
  default `data/sample`.
- **`?narrative=ollama` carries the same no-guarantee-of-AI-output caveat as
  the CLI.** If no local Ollama server is reachable, the response's
  narrative field is the labelled template output, not an error — see D8.
