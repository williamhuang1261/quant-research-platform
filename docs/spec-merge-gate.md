# Spec — CI merge gate over pinned golden-run numbers (Extension 10)

Status: implemented (Extension 10, steps 1-2)

## Why this exists

This platform pins exact numeric outputs from five real backtest / portfolio
/ signal / report runs across `BacktestIntegrationTest`,
`PortfolioBacktestEngineTest`, `RunControllerTest`, `ReportControllerTest`
and `CrossSectionalSignalGeneratorGoldenRunTest`. Those numbers are meant to
move only when the underlying behaviour changes on purpose, with a doc
update explaining why -- not as a silent side effect of an unrelated change.
Until this extension, nothing enforced that; a reviewer had to notice a
diff to a pinned literal by eye. `tools/merge_gate.py` and the
`merge-gate` GitHub Actions workflow (`.github/workflows/merge-gate.yml`)
make that check automatic on every pull request -- a small, honest analog
of what a merge-automation product does, built entirely on infrastructure
this platform already had.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Detect every changed pinned numeric literal in the five golden-run test files between a PR's base and head |
| R2 | Flag a changed literal only when no file under `docs/` changed in the same diff |
| R3 | Run on every pull request via GitHub Actions, printing a readable summary and failing the check on any flag |
| R4 | No new dependency; stdlib only, matching `tools/energy_db.py`'s convention |
| R5 | State the tool's real detection limitation rather than imply it understands Java |

## How it works (`tools/merge_gate.py`)

`extract_asserted_literals` is a regex over `assertEquals(<literal>, ...)`
calls: it keeps the first token inside the parentheses only when it looks
like a number (an optional sign, digits, optional `_` separators, an
optional fractional part -- the same underscore-grouped literal style the
real test files use, e.g. `100_000.0`). `diff_golden_run` runs `git show
<ref>:<path>` for each of the five files at both the PR's base and head
SHA, extracts literals from each version via `subprocess`, and returns
every file whose literal list differs. `changed_docs` runs `git diff
--name-only <base> <head> -- docs/` to get the set of changed doc paths in
the same range. `main()` prints one row per changed golden-run file --
`OK` if any `docs/` file changed in the same diff, otherwise `FLAGGED:
pinned value changed with no docs/ update` -- and exits non-zero only when
at least one file is flagged.

## Design decisions

**D1 -- regex, not a real Java parse.** A stdlib-only tool has no Java AST
available. The regex reads only the first argument of an `assertEquals`
call and only keeps it when it looks numeric. This means: a golden-run
literal that is not an `assertEquals` call's first argument would be
missed, and an unrelated numeric literal that happens to be first inside
an `assertEquals` call (e.g. a row-count or list-size assertion, both of
which already occur in these files today) would be flagged as if it were a
"golden-run" number. The tool over-flags rather than under-flags, by
design -- a false positive costs a reviewer one extra glance; a false
negative would let real drift through silently. `tools/test_merge_gate.py`
tests this behaviour directly, including the intentional exclusion of
`assertEquals`'s string- and `List.of(...)`-typed calls.

**D2 -- no PR-comment posting.** GitHub's default `GITHUB_TOKEN` often
lacks `pull-requests: write` on a fork-originated PR, and this platform's
CI is public-facing. Rather than build a comment-posting path that would
silently no-op (or need repo-settings changes a reviewer cloning this
cannot make), the workflow writes its summary to the job's own log and to
`$GITHUB_STEP_SUMMARY`, which needs no extra permission -- the honestly-
scoped choice, not a missing feature quietly worked around.

**D3 -- fails on flag, not on any diff.** A PR that intentionally changes a
pinned number and updates `docs/` to explain it passes cleanly. The gate
exists to catch *undocumented* drift, not to forbid the numbers from ever
changing -- forbidding change entirely would make this platform's own
extension history (which has legitimately moved pinned numbers before, each
time with a doc explaining why) impossible to ship through its own gate.

## What is deliberately not here

- **Cross-repo or cross-language support.** The five golden-run files are
  hardcoded to this platform's own Java test suite; there is no generic
  "find golden runs in any language" mode.
- **Semantic understanding of *which* value changed or why.** The tool
  reports literal lists before and after; it does not diff them
  field-by-field against the assertion each literal belongs to, so a
  reviewer still reads the two lists to see what moved.
- **PR-comment posting.** See D2.
- **A real Java parser.** See D1.

## Manual verification

Run against this repo's own real history (`ReportControllerTest.java`
being added, with its accompanying `docs/spec-api.md` change, between
`220e179` and `debad21`):

```
$ python3 tools/merge_gate.py 220e179 debad21
Golden-run merge gate: 220e179..debad21

- qrp-api/src/test/java/io/github/williamhuang1261/qrp/api/ReportControllerTest.java: OK
    before: []
    after:  [3.0, -0.04115895776844469, -0.061038983750053344, -0.174514427227237, 0.24149473488945833, -604.6589158938831, -0.2749326919491941, -0.2899657957563877, 0.0]

Accompanying docs/ changes: ['docs/spec-api.md']

$ echo $?
0
```

And against a disposable clone with a deliberately introduced, undocumented
drift (changing `BacktestIntegrationTest`'s pinned `tradeCount` from `11` to
`12` with no `docs/` change in the same commit):

```
$ python3 tools/merge_gate.py <before-sha> <after-sha>
Golden-run merge gate: <before-sha>..<after-sha>

- qrp-engine/.../BacktestIntegrationTest.java: FLAGGED: pinned value changed with no docs/ update
    before: [504.0, 100000.0, 92229.0094522352, ..., 11.0, ...]
    after:  [504.0, 100000.0, 92229.0094522352, ..., 12.0, ...]

$ echo $?
1
```
