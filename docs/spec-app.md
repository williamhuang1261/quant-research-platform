# Spec — `qrp-app`

Status: implemented (step 7)

## Why this module exists

Every other module is a library. This one is the assembly point: the only place
that puts data, indicators, a strategy, the engine, the statistics and a compute
kernel in the same JVM, and the only place a person interacts with.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Run a backtest from the command line and print a readable report |
| R2 | Show what is installed: indicators, strategies, compute engines, instruments |
| R3 | Draw the equity curve, the drawdown and the metrics in a window |
| R4 | Render that window to a file, so the layout can be checked without a display |
| R5 | Both front ends must report identical numbers for identical inputs |

## Commands

```bash
mvn -pl qrp-app exec:java -Dexec.args="run"                    # backtest + report
mvn -pl qrp-app exec:java -Dexec.args="list"                   # what is installed
mvn -pl qrp-app exec:java -Dexec.args="workbench"              # the JavaFX window
mvn -pl qrp-app exec:java -Dexec.args="workbench --snapshot docs/screenshot.png"
```

## Design decisions

**D1 — One runner, two front ends.** `BacktestRunner` assembles a run; the CLI
and the workbench differ only in presentation. Duplicating the assembly would let
the two drift into reporting different numbers for the same configuration, which
is the kind of bug nobody finds until someone compares a screenshot against a
terminal.

**D2 — The view holds no arithmetic.** `WorkbenchModel` produces every point,
label and formatted value; `Workbench` turns them into nodes. A JavaFX test needs
a toolkit, a display and a harness, while the questions worth asking — are these
the right points, is a drawdown ever positive, is the table missing a row — need
none of those. The model is tested; the view is looked at.

**D3 — Charts are capped at 1,500 points.** Past that, a `LineChart` costs
render time and shows no additional detail at screen resolution. The stride is
computed from the series length so the two charts stay aligned to the same bars.

**D4 — Arguments are parsed by hand.** The grammar is a dozen flags. A CLI
library would be one more dependency to justify in a project whose argument is
that its dependencies are few and open.

**D5 — Defaults exist only for the reference strategy.** `qrp run` with no flags
works, because `sma-crossover` has known windows. Any other strategy gets an
empty parameter set: nothing here can guess what a plugin expects, and inventing
a `period` for it would produce a confident run of the wrong thing.

**D6 — Caveats travel with the report.** The cost, financing and resampling
limitations print under every run rather than living in the README. A metrics
table pasted into a ticket carries them; a README does not.

**D7 — The workbench launches through `exec:java`, not `javafx:run`.** The
JavaFX plugin forks a JVM that places the implementation jars on the module path,
where their `ServiceLoader` providers are not visible: the workbench started that
way silently loses the native compute engine and the indicator registry comes up
short. `qrp workbench` launches `Application` in the same JVM the CLI runs in, so
both front ends see the same plugins. The plugin is kept for anyone who prefers
it, and the workbench prints its compute-engine table at startup, so the degraded
case is visible rather than silent.

**D8 — Unsigned quantities print unsigned.** A drawdown is `24.15%`, not
`+24.15%`; a return is `-7.77%`. Reading a signed drawdown as a gain is a
five-second mistake that a formatter can prevent.

## What the CLI reports

```
  sma-crossover on SYNA.USD 1d
  504 bars, 2022-01-03 to 2023-12-07, compute engine: openmp
  initial equity                                    100,000.00
  final equity                                       92,229.01
  total return                                          -7.77%
  CAGR                                                  -4.12%
  Sharpe ratio                                           -0.17
  max drawdown                                          24.15%
  Monte Carlo: 2000 resampled paths
  median final equity                                89,738.96
  final equity (95%)                   61,285.46 .. 130,600.17
  max drawdown (95%)                          12.85% .. 44.59%
  probability of loss                                   67.90%
```

The Monte Carlo section is the reason the module exists in this shape: the
backtest says the strategy lost 7.77 %, and the resampling says that two thirds
of the plausible orderings of those same returns also lose. One number is an
anecdote and the other is a statement about the strategy.

## Out of scope

Parameter sweeps and optimisation, saving or loading run configurations,
comparing runs side by side, and live trading of any kind. The last is not a
missing feature: nothing in this platform models an order's life after it is
filled.
