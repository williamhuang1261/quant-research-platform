# Contributing

## Building

```bash
mvn verify              # compile, test, package
make -C native          # optional compute kernel
```

JDK 25 and Maven. Everything else is pulled by Maven; the native kernel needs a
C++17 compiler and, for the parallel path, an OpenMP runtime.

## Adding a plugin

Nothing in this repository names the indicators or strategies it can run, so a
new one needs no change to existing code:

1. Implement `Indicator`, `Strategy`, `MarketDataProvider` or `ComputeEngine`.
2. Declare it in `src/main/resources/META-INF/services/<the interface's FQN>`.
3. Run `mvn -pl qrp-app exec:java -Dexec.args="list"` — it should appear.

`IndicatorContractTest` will hold it to the SPI contract automatically. That is
the intended way to extend the platform, including from a separate private jar.

## Review checklist

Every change is reviewed against this list before merge. It is short on purpose:
each line is a defect this codebase has actually had, or one whose absence a
reader of the results depends on.

- [ ] **No look-ahead.** A strategy sees `visibleAt(i)` and nothing later. An
      indicator precomputed over the full series must be causal; say so where it
      is done.
- [ ] **Every published number is reproducible.** Seeded, order-independent, and
      unchanged by thread count or by which compute engine was selected.
- [ ] **Costs are modelled where money moves.** A fill without commission and
      slippage flatters every high-turnover strategy.
- [ ] **Undefined is `NaN`, not zero.** A Sharpe ratio with no dispersion to
      divide by does not exist, and 0 puts it on a leaderboard.
- [ ] **Validation lives in the type.** A malformed bar should be unconstructible
      rather than rejected later by whoever remembers to check.
- [ ] **Errors name the input.** File and line for data, the flag for arguments,
      the available ids for an unknown plugin.
- [ ] **Tests assert behaviour, not implementation.** Cross-engine and
      parallel/sequential equivalence is asserted exactly (`delta = 0.0`).
- [ ] **The spec is updated.** Each module has one in `docs/`; a design decision
      that is not written down will be re-litigated.
- [ ] **Unflattering results stay.** If a measurement contradicts a claim in the
      README, the claim changes.

## Commits

```
<type>: <imperative subject, lowercase, <= 72 chars>

<why this change exists — the problem or the trade-off, not the diff>
```

Types: `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `chore`, `ci`.
One logical change per commit, and every commit builds: a reviewer may check out
any point in the history.
