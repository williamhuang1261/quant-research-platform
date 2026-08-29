#!/usr/bin/env python3
"""Tests for energy_db.py. Stdlib only (unittest), same zero-extra-dependency
bar as the rest of tools/ apart from the plotting/xls scripts, which already
document their own extra packages.

Usage:
    python3 tools/test_energy_db.py
"""
import csv
import os
import sqlite3
import tempfile
import unittest

import numpy as np

import energy_db

SAMPLE_ROWS = [
    ("2026-01-05", "2.10"),
    ("2026-01-06", "2.20"),
    ("2026-01-07", "2.05"),
    ("2026-01-08", "2.30"),
    ("2026-01-09", "2.15"),
]


class EnergyDbTest(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.TemporaryDirectory()
        self.csv_path = os.path.join(self.tmpdir.name, "sample.csv")
        self.db_path = os.path.join(self.tmpdir.name, "energy.db")
        with open(self.csv_path, "w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(["date", "price_usd_per_mmbtu"])
            writer.writerows(SAMPLE_ROWS)

    def tearDown(self):
        self.tmpdir.cleanup()

    def test_load_reports_correct_row_count(self):
        count = energy_db.load_csv(self.db_path, self.csv_path)
        self.assertEqual(count, len(SAMPLE_ROWS))

    def test_load_is_idempotent(self):
        energy_db.load_csv(self.db_path, self.csv_path)
        second_count = energy_db.load_csv(self.db_path, self.csv_path)
        self.assertEqual(second_count, len(SAMPLE_ROWS))
        with sqlite3.connect(self.db_path) as conn:
            total = conn.execute("SELECT COUNT(*) FROM prices").fetchone()[0]
        self.assertEqual(total, len(SAMPLE_ROWS))

    def test_date_range_returns_correct_slice(self):
        energy_db.load_csv(self.db_path, self.csv_path)
        rows = energy_db.date_range(self.db_path, "2026-01-06", "2026-01-08")
        self.assertEqual(
            rows,
            [("2026-01-06", 2.20), ("2026-01-07", 2.05), ("2026-01-08", 2.30)],
        )

    def test_rolling_average_matches_numpy(self):
        energy_db.load_csv(self.db_path, self.csv_path)
        window = 3
        rows = energy_db.rolling_average(self.db_path, window)
        prices = np.array([float(p) for _, p in SAMPLE_ROWS])

        for i, (date, price, rolling_avg) in enumerate(rows):
            if i < window - 1:
                self.assertIsNone(rolling_avg)
            else:
                expected = prices[i - window + 1 : i + 1].mean()
                self.assertAlmostEqual(rolling_avg, expected, places=9)


if __name__ == "__main__":
    unittest.main()
