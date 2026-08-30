#!/usr/bin/env python3
"""CI merge-gate over this platform's own pinned golden-run test numbers.

Five test classes across this repo pin exact numeric outputs of a real
backtest/portfolio/signal/report run (see `mprojects.md`'s do-not-remove
lists in the CV repo for why each one matters). Those numbers are meant to
move only when the underlying behaviour changes on purpose, with an
accompanying doc update explaining why -- never as a silent side effect of
an unrelated change. This script diffs two git refs, extracts every
golden-run numeric literal from each ref's version of those five files, and
flags any file whose literals changed with no matching change under docs/
in the same diff.

Limitation, stated plainly rather than hidden: this is a regex over
`assertEquals(<literal>, ...)` calls, not a real Java parse. It reads the
first token after the opening parenthesis and keeps it only if it looks
like a number. A golden-run literal that is not the first argument to its
assertEquals call would be missed; an unrelated numeric literal that
happens to be the first argument would be flagged. A stdlib-only tool has
no Java AST available, so this is the honestly-scoped trade-off, not an
oversight.

Usage:
    python3 tools/merge_gate.py <base_ref> <head_ref>

Exits 0 if every changed golden-run file's diff is accompanied by a docs/
change, non-zero otherwise. Falls back to $GITHUB_BASE_REF/$GITHUB_SHA when
no refs are given on the command line, so it also runs unmodified inside
the GitHub Actions workflow that wires it into pull_request checks.
"""
import os
import re
import subprocess
import sys

GOLDEN_RUN_FILES = [
    "qrp-engine/src/test/java/io/github/williamhuang1261/qrp/engine/BacktestIntegrationTest.java",
    "qrp-portfolio/src/test/java/io/github/williamhuang1261/qrp/portfolio/PortfolioBacktestEngineTest.java",
    "qrp-api/src/test/java/io/github/williamhuang1261/qrp/api/RunControllerTest.java",
    "qrp-api/src/test/java/io/github/williamhuang1261/qrp/api/ReportControllerTest.java",
    "qrp-signals/src/test/java/io/github/williamhuang1261/qrp/signals/CrossSectionalSignalGeneratorGoldenRunTest.java",
]

# The first token inside assertEquals(...) when it is a numeric literal:
# an optional sign, digits with optional `_` separators, an optional
# fractional part, keeping Java's `100_000.0`-style literals intact.
_ASSERT_LITERAL_RE = re.compile(
    r"assertEquals\(\s*(-?[0-9][0-9_]*(?:\.[0-9_]+)?)\s*[,)]"
)


def extract_asserted_literals(text: str) -> list[float]:
    """Returns every leading numeric literal found in an assertEquals(...) call."""
    literals = []
    for match in _ASSERT_LITERAL_RE.finditer(text):
        literals.append(float(match.group(1).replace("_", "")))
    return literals


def _git_show(ref: str, path: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{ref}:{path}"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        # File did not exist at this ref (e.g. added since base) -- treat as empty.
        return ""
    return result.stdout


def diff_golden_run(
    base_ref: str, head_ref: str, files: list[str] = GOLDEN_RUN_FILES
) -> dict[str, tuple[list[float], list[float]]]:
    """Returns {path: (base_literals, head_literals)} for every file whose
    golden-run literals differ between base_ref and head_ref."""
    changed = {}
    for path in files:
        base_literals = extract_asserted_literals(_git_show(base_ref, path))
        head_literals = extract_asserted_literals(_git_show(head_ref, path))
        if base_literals != head_literals:
            changed[path] = (base_literals, head_literals)
    return changed


def changed_docs(base_ref: str, head_ref: str) -> set[str]:
    """Returns the set of docs/ paths that changed between base_ref and head_ref."""
    result = subprocess.run(
        ["git", "diff", "--name-only", base_ref, head_ref, "--", "docs/"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return set()
    return {line for line in result.stdout.splitlines() if line}


def main(argv: list[str]) -> int:
    base_ref = argv[1] if len(argv) > 1 else os.environ.get("GITHUB_BASE_REF")
    head_ref = argv[2] if len(argv) > 2 else os.environ.get("GITHUB_SHA")
    if not base_ref or not head_ref:
        print(
            "Usage: merge_gate.py <base_ref> <head_ref> "
            "(or set GITHUB_BASE_REF / GITHUB_SHA)",
            file=sys.stderr,
        )
        return 2

    changed = diff_golden_run(base_ref, head_ref)
    docs = changed_docs(base_ref, head_ref)

    if not changed:
        print("Golden-run merge gate: no pinned literals changed. OK.")
        return 0

    flagged = False
    print(f"Golden-run merge gate: {base_ref}..{head_ref}\n")
    for path, (before, after) in changed.items():
        status = "OK" if docs else "FLAGGED: pinned value changed with no docs/ update"
        if not docs:
            flagged = True
        print(f"- {path}: {status}")
        print(f"    before: {before}")
        print(f"    after:  {after}")
    if docs:
        print(f"\nAccompanying docs/ changes: {sorted(docs)}")
    print()

    return 1 if flagged else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
