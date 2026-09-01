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

Optional: when a run is about to be flagged, draft_root_cause_summary()
asks an LLM (the Claude API, via the optional `anthropic` package -- see
tools/README.md) for a short root-cause guess to print alongside the raw
literal lists. This is opt-in and fails closed: with no ANTHROPIC_API_KEY
set, or the `anthropic` package not installed, or any API error, the tool's
output and exit code are byte-for-byte what they were before this feature
existed.
"""
import os
import re
import subprocess
import sys

try:
    import anthropic
except ImportError:  # pragma: no cover - exercised by test_returns_none_without_package
    anthropic = None

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


_ROOT_CAUSE_PROMPT = """\
A CI merge gate just flagged pull request {base_ref}..{head_ref} because a
pinned "golden-run" numeric literal changed in a Java test file with no
matching change under docs/ in the same diff. The literal lists below are
ordered but unlabelled -- they come from a regex over assertEquals(...)
calls, not a real parse, so a literal's position in the list is the only
information available about which assertion it belongs to.

{flagged_block}
Accompanying docs/ changes in this diff: {docs}

In 2-4 sentences, give the reviewer your best guess at what kind of change
in the underlying behaviour would explain this drift, and suggest one short
line for a docs/ note that would make the change legitimate. Be explicit
that this is a guess from the numbers alone, not a diff of the actual code
change.
"""


def _format_flagged_for_prompt(
    base_ref: str,
    head_ref: str,
    flagged: dict[str, tuple[list[float], list[float]]],
    docs: set[str],
) -> str:
    """Renders the same before/after literal lists main() already prints
    into the plain-text block the root-cause prompt is built from -- the
    prompt reads from data the tool already computed, not a second source
    of truth."""
    lines = []
    for path, (before, after) in flagged.items():
        lines.append(f"{path}:")
        lines.append(f"  before: {before}")
        lines.append(f"  after:  {after}")
    return _ROOT_CAUSE_PROMPT.format(
        base_ref=base_ref,
        head_ref=head_ref,
        flagged_block="\n".join(lines),
        docs=sorted(docs) if docs else "none",
    )


def draft_root_cause_summary(
    base_ref: str,
    head_ref: str,
    flagged: dict[str, tuple[list[float], list[float]]],
    docs: set[str],
) -> str | None:
    """Asks an LLM to draft a short root-cause guess for a flagged golden-run
    drift, for the reviewer to read alongside the raw before/after lists.

    Fails closed, always: returns None -- never raises -- when the
    `anthropic` package is not installed, when ANTHROPIC_API_KEY is unset,
    or when the API call itself fails for any reason. The gate's pass/fail
    decision never depends on this function succeeding; callers must treat
    None as "no summary available" and fall back to the plain output that
    already existed before this function did.
    """
    if anthropic is None:
        return None
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        return None
    try:
        client = anthropic.Anthropic(api_key=api_key)
        response = client.messages.create(
            model="claude-sonnet-5",
            max_tokens=300,
            messages=[
                {
                    "role": "user",
                    "content": _format_flagged_for_prompt(
                        base_ref, head_ref, flagged, docs
                    ),
                }
            ],
        )
        return response.content[0].text.strip()
    except Exception:
        return None


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

    if flagged:
        summary = draft_root_cause_summary(base_ref, head_ref, changed, docs)
        if summary is not None:
            print("## AI-drafted root-cause summary")
            print("[AI-generated, verify before relying on it]\n")
            print(summary)
            print()

    return 1 if flagged else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
