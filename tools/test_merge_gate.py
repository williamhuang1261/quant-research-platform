#!/usr/bin/env python3
"""Tests for tools/merge_gate.py.

Runs against a throwaway git repo built in a tempfile.TemporaryDirectory so
diff_golden_run/changed_docs exercise real `git show`/`git diff` subprocess
calls rather than mocked ones -- the same standard this repo already holds
tools/test_energy_db.py to.
"""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import merge_gate

FIXTURE_BASE = """\
package example;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExampleTest {
    void test() {
        assertEquals("sma-crossover", response.strategyId());
        assertEquals(100_000.0, metrics.initialEquity(), DELTA);
        assertEquals(92_229.0094522352, metrics.finalEquity(), DELTA);
        assertEquals(-0.0411589578, metrics.cagr(), DELTA);
        assertEquals(11, metrics.tradeCount());
        assertEquals(List.of("SYNA", "SYNB"), response.symbols());
    }
}
"""

FIXTURE_HEAD_CHANGED = FIXTURE_BASE.replace(
    "assertEquals(11, metrics.tradeCount());",
    "assertEquals(12, metrics.tradeCount());",
)


class ExtractAssertedLiteralsTest(unittest.TestCase):
    def test_extracts_only_numeric_first_arguments(self):
        literals = merge_gate.extract_asserted_literals(FIXTURE_BASE)
        self.assertEqual(
            literals, [100000.0, 92229.0094522352, -0.0411589578, 11.0]
        )

    def test_ignores_string_and_list_first_arguments(self):
        literals = merge_gate.extract_asserted_literals(
            'assertEquals("x", y()); assertEquals(List.of("a"), b());'
        )
        self.assertEqual(literals, [])

    def test_empty_text_yields_no_literals(self):
        self.assertEqual(merge_gate.extract_asserted_literals(""), [])


class GitBackedTest(unittest.TestCase):
    """Builds a real two-commit git repo to test diff_golden_run/changed_docs."""

    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmpdir.name)
        self._run("git", "init", "-q")
        self._run("git", "config", "user.email", "test@example.com")
        self._run("git", "config", "user.name", "Test")

        self.target_path = "qrp-engine/src/test/java/io/github/williamhuang1261/qrp/engine/BacktestIntegrationTest.java"
        target = self.repo / self.target_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(FIXTURE_BASE)
        self._run("git", "add", self.target_path)
        self._run("git", "commit", "-q", "-m", "base")
        self.base_sha = self._run("git", "rev-parse", "HEAD").strip()

    def tearDown(self):
        self.tmpdir.cleanup()

    def _run(self, *args: str) -> str:
        result = subprocess.run(
            args, cwd=self.repo, capture_output=True, text=True, check=True
        )
        return result.stdout

    def _commit_change(self, docs_too: bool) -> str:
        target = self.repo / self.target_path
        target.write_text(FIXTURE_HEAD_CHANGED)
        self._run("git", "add", self.target_path)
        if docs_too:
            docs = self.repo / "docs" / "spec.md"
            docs.parent.mkdir(parents=True, exist_ok=True)
            docs.write_text("tradeCount is now 12 because ...\n")
            self._run("git", "add", "docs/spec.md")
        self._run("git", "commit", "-q", "-m", "change tradeCount")
        return self._run("git", "rev-parse", "HEAD").strip()

    def test_diff_golden_run_detects_changed_literal(self):
        head_sha = self._commit_change(docs_too=False)
        import os

        cwd = os.getcwd()
        os.chdir(self.repo)
        try:
            changed = merge_gate.diff_golden_run(
                self.base_sha, head_sha, files=[self.target_path]
            )
        finally:
            os.chdir(cwd)
        self.assertIn(self.target_path, changed)
        before, after = changed[self.target_path]
        self.assertEqual(before[-1], 11.0)
        self.assertEqual(after[-1], 12.0)

    def test_diff_golden_run_reports_no_change_when_literals_match(self):
        # A commit that touches the file but keeps every literal identical.
        target = self.repo / self.target_path
        target.write_text(FIXTURE_BASE + "\n// comment only\n")
        self._run("git", "add", self.target_path)
        self._run("git", "commit", "-q", "-m", "comment only")
        head_sha = self._run("git", "rev-parse", "HEAD").strip()

        import os

        cwd = os.getcwd()
        os.chdir(self.repo)
        try:
            changed = merge_gate.diff_golden_run(
                self.base_sha, head_sha, files=[self.target_path]
            )
        finally:
            os.chdir(cwd)
        self.assertEqual(changed, {})

    def test_changed_docs_detects_docs_change(self):
        head_sha = self._commit_change(docs_too=True)
        import os

        cwd = os.getcwd()
        os.chdir(self.repo)
        try:
            docs = merge_gate.changed_docs(self.base_sha, head_sha)
        finally:
            os.chdir(cwd)
        self.assertEqual(docs, {"docs/spec.md"})

    def test_changed_docs_empty_when_no_docs_touched(self):
        head_sha = self._commit_change(docs_too=False)
        import os

        cwd = os.getcwd()
        os.chdir(self.repo)
        try:
            docs = merge_gate.changed_docs(self.base_sha, head_sha)
        finally:
            os.chdir(cwd)
        self.assertEqual(docs, set())


class DraftRootCauseSummaryTest(unittest.TestCase):
    """None of these tests make a real network call -- `anthropic` is either
    monkeypatched to None (package not installed) or to a fake object whose
    behaviour the test controls directly."""

    FLAGGED = {
        "qrp-engine/.../BacktestIntegrationTest.java": ([11.0], [12.0]),
    }
    DOCS: set[str] = set()

    def test_returns_none_without_package(self):
        with mock.patch.object(merge_gate, "anthropic", None):
            result = merge_gate.draft_root_cause_summary(
                "base", "head", self.FLAGGED, self.DOCS
            )
        self.assertIsNone(result)

    def test_returns_none_without_api_key(self):
        sentinel = mock.Mock()
        with mock.patch.object(merge_gate, "anthropic", sentinel), mock.patch.dict(
            os.environ, {}, clear=False
        ):
            os.environ.pop("ANTHROPIC_API_KEY", None)
            result = merge_gate.draft_root_cause_summary(
                "base", "head", self.FLAGGED, self.DOCS
            )
        self.assertIsNone(result)
        sentinel.Anthropic.assert_not_called()

    def test_returns_none_when_client_raises(self):
        fake_client = mock.Mock()
        fake_client.messages.create.side_effect = RuntimeError("network down")
        fake_anthropic = mock.Mock()
        fake_anthropic.Anthropic.return_value = fake_client
        with mock.patch.object(
            merge_gate, "anthropic", fake_anthropic
        ), mock.patch.dict(os.environ, {"ANTHROPIC_API_KEY": "test-key"}):
            result = merge_gate.draft_root_cause_summary(
                "base", "head", self.FLAGGED, self.DOCS
            )
        self.assertIsNone(result)

    def test_returns_summary_text_on_success(self):
        fake_response = mock.Mock()
        fake_response.content = [mock.Mock(text="  Looks like a trade got added.  ")]
        fake_client = mock.Mock()
        fake_client.messages.create.return_value = fake_response
        fake_anthropic = mock.Mock()
        fake_anthropic.Anthropic.return_value = fake_client
        with mock.patch.object(
            merge_gate, "anthropic", fake_anthropic
        ), mock.patch.dict(os.environ, {"ANTHROPIC_API_KEY": "test-key"}):
            result = merge_gate.draft_root_cause_summary(
                "base", "head", self.FLAGGED, self.DOCS
            )
        self.assertEqual(result, "Looks like a trade got added.")
        fake_anthropic.Anthropic.assert_called_once_with(api_key="test-key")
        fake_client.messages.create.assert_called_once()
        _, kwargs = fake_client.messages.create.call_args
        self.assertEqual(kwargs["model"], "claude-sonnet-5")
        self.assertIn(
            "qrp-engine/.../BacktestIntegrationTest.java",
            kwargs["messages"][0]["content"],
        )


if __name__ == "__main__":
    unittest.main()
