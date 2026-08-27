# Spec — `qrp-report`

Status: implemented (Extension 3, steps 1-4)

## Why this module exists

The platform already runs a backtest and produces performance and risk
statistics for one instrument at a time. A fund comparison is the same numbers,
read side by side against a benchmark, with one thing added that no backtest
has: a fee charged to the investor rather than the strategy. This module is
that reporting layer — it does not price anything or run a new simulation.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A flat annual management fee applied to an equity curve, exact regardless of bar frequency |
| R2 | A comparison table across several fund-shaped backtests and one benchmark |
| R3 | Gross metrics (CAGR, Sharpe, max drawdown) reused from the engine, not recomputed |
| R4 | Net-of-fee CAGR computed by the same year-length formula the engine uses for gross CAGR |
| R5 | Historical 95% VaR and expected shortfall per fund |
| R6 | Rows ranked by net CAGR, with the benchmark always present and marked |
| R7 | A `compare` CLI command that runs the engine over several instruments and one benchmark and prints a one-page comparison table |
| R8 | A short, plain-English narrative paragraph closing the report, naming the net-CAGR leader and the risk-adjusted (Sharpe) leader and how the leader compares to the benchmark |
| R9 | A deterministic template narrative that always works offline, as the default |
| R10 | An optional local-Ollama narrative that falls back to the template, clearly labelled either way, on any failure — server not running, timeout, malformed response — rather than throwing |

## Design decisions

**D1 — The fee lives on `FundProfile`, not on `Instrument`.** The same
underlying security can back two funds charging different fees (an A-series and
an F-series of the same mandate, for instance), so the fee belongs to the fund
wrapper, not to the thing the platform already models as tradable.

**D2 — Gross metrics are read from `PerformanceMetrics`, never recomputed.**
`qrp-engine` already computes CAGR, Sharpe and max drawdown from the same
equity curve this module reads. A second implementation of the same formula is
a second place for the two to drift apart; this module only adds the two
numbers that do not exist upstream — the fee-adjusted CAGR and the tail-risk
pair.

**D3 — Net CAGR reuses the engine's exact CAGR formula.** `PerformanceMetrics`
defines a year as the real calendar span between the first and last bar
(`Duration.between(start, end) / 365.25`), not a bar count divided by a nominal
periods-per-year. `FundComparisonTable` copies that formula rather than
approximating it, so a 0% fee reproduces the engine's own gross CAGR to the bit
— tested directly, not just asserted close.

**D4 — VaR and expected shortfall are computed on gross returns.** A fee is a
level effect (it lowers the compounding rate), not a volatility effect; folding
it into the return series before measuring tail risk would conflate the two. A
later step may add a fee-adjusted risk view if a reviewer asks for it — nothing
here forecloses that.

**D5 — A rule-based template is the default narrative, not the local model.**
`CompareArguments.NarrativeSource` defaults to `TEMPLATE`; `--narrative=ollama`
is opt-in. A report generator that defaults to a network call would fail the
"clean clone runs offline, no network, no API key" guarantee every other
command in this platform makes — `qrp compare` with no flags must produce the
same one-page report whether or not Ollama happens to be installed.

**D6 — `OllamaNarrativeGenerator` fails closed to the template, never to an
exception.** `tryGenerate` catches `IOException`, `InterruptedException` and
`RuntimeException` around the whole HTTP round trip and JSON extraction, and
returns `Optional.empty()` on any of them — server not running, connection
refused, timeout, a 200 with no `"response"` field, an empty string. The
caller then falls back to `TemplateNarrativeGenerator`'s output. A narrative
generator failing a whole report over a model that is not running would make
the optional feature strictly worse than not having it.

**D7 — the narrative is always labelled with what actually produced it.**
`TemplateNarrativeGenerator.LABEL_PREFIX` (`"[template summary] "`) and
`OllamaNarrativeGenerator.LABEL_PREFIX` (`"[AI-generated summary] "`) are
prepended to every narrative string, including the fallback path: when Ollama
fails, `OllamaNarrativeGenerator.narrate` returns the template's own labelled
output unchanged, rather than re-labelling it as AI-generated or stripping the
label. A report that silently claims AI when the template ran, or silently
drops to the template when the model call fails, is worse than not having the
feature at all — the label always matches what actually produced the text.

## The narrative generator

(`qrp-report/.../NarrativeGenerator.java`,
`.../TemplateNarrativeGenerator.java`, `.../OllamaNarrativeGenerator.java`)

```java
public interface NarrativeGenerator {
    String narrate(FundComparisonTable table);
}
```

The comparison table already carries every number a reader would need; a
narrative generator's job is to say, in words, which fund led and by how much
— the written-communication half of a fund comparison, not a second source of
numbers.

**`TemplateNarrativeGenerator`** finds the net-CAGR leader and the Sharpe
leader independently — nothing assumes the fund that led on one also led on
the other — and states both, explicitly calling out the case where they
differ ("the top net performer was not the steadiest one"), then states
whether the net-CAGR leader beat or trailed the benchmark, in basis points.

**`OllamaNarrativeGenerator`** builds a compact prompt from every row's net
CAGR, Sharpe, max drawdown and benchmark-relative bps, `POST`s it to a local
Ollama server's `/api/generate` endpoint (`http://localhost:11434` by
default, model `llama3.2`, a 3-second connect/request timeout) using only the
JDK's `java.net.http.HttpClient` — no new dependency for a feature that is
off by default — and extracts the `"response"` field from the raw JSON body
with a small hand-rolled scanner rather than pulling in a JSON library for one
field. Same honest-fallback pattern the plan borrows from `mortality-copilot`'s
RAG copilot.

## What this module does not do

- It does not run a backtest. `FundComparisonTable.of` takes already-computed
  `BacktestResult`s; wiring three instruments through the engine is
  `qrp-app`'s `CompareRunner`, not this module.
- It does not model a real management expense ratio: no fund-of-fund layering,
  no trailing commission, no fee waiver, no tiered pricing by asset level. One
  flat annual rate per fund. See the README's engineering notes.
- It does not format the printed report. `FundComparisonReportFormatter`
  (`qrp-app`) renders the table and the narrative as fixed-width text,
  matching `ReportFormatter` and `OptionsReportFormatter`'s conventions.
- It does not invent numbers. Both narrative generators are constrained to the
  figures already in the table; the Ollama prompt explicitly instructs the
  model not to invent numbers beyond those given, though nothing in this
  module can verify a local model actually complied — a human reader is still
  the last check on AI-generated text, same as anywhere else it appears.

## Selecting a narrative source and running the report

`qrp compare --narrative template|ollama` (default `template`); see
`CompareArguments.usageSuffix()` for the full `compare` flag list (`--symbol`,
`--benchmark`, `--fee`, `--benchmark-fee`, and the shared data/timeframe/
strategy/cost flags). `CompareRunner.run` backtests every candidate and the
benchmark identically — same strategy, params, `MarketOpenExecutionModel` and
cost model — builds the `FundComparisonTable`, and hands it to whichever
`NarrativeGenerator` the arguments selected; `QrpCli`'s `compare` command
prints `FundComparisonReportFormatter.format(outcome)`. See the README's fund
comparison section for a real transcript.
