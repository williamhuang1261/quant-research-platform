#!/usr/bin/env python3
"""Tests for spark_etl.py. Needs `pyspark`, same as the module under test.

Usage:
    python3 tools/test_spark_etl.py
    pytest tools/test_spark_etl.py
"""
import csv
import os
import tempfile
import unittest

from pyspark.sql import Row, SparkSession

import spark_etl


class SparkEtlTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spark = (
            SparkSession.builder.appName("qrp-spark-etl-test")
            .master("local[2]")
            .getOrCreate()
        )
        cls.spark.sparkContext.setLogLevel("WARN")

    @classmethod
    def tearDownClass(cls):
        cls.spark.stop()

    def test_deduplicate_drops_exact_duplicate_rows(self):
        df = self.spark.createDataFrame(
            [
                Row(symbol="SYNA", timestamp="2022-01-03T21:00:00Z", open=1.0),
                Row(symbol="SYNA", timestamp="2022-01-03T21:00:00Z", open=1.0),
                Row(symbol="SYNA", timestamp="2022-01-04T21:00:00Z", open=2.0),
            ]
        )
        deduped = spark_etl.deduplicate(df)
        self.assertEqual(deduped.count(), 2)

    def test_normalize_schema_derives_week_start(self):
        # 2022-01-03 is a Monday; 2022-01-05 falls in the same week.
        df = self.spark.createDataFrame(
            [
                Row(symbol="SYNA", timestamp="2022-01-03T21:00:00Z"),
                Row(symbol="SYNA", timestamp="2022-01-05T21:00:00Z"),
                Row(symbol="SYNA", timestamp="2022-01-10T21:00:00Z"),
            ]
        )
        normalized = spark_etl.normalize_schema(df)
        rows = {r["timestamp"]: str(r["week_start"]) for r in normalized.collect()}
        self.assertEqual(rows["2022-01-03T21:00:00Z"], rows["2022-01-05T21:00:00Z"])
        self.assertNotEqual(rows["2022-01-03T21:00:00Z"], rows["2022-01-10T21:00:00Z"])

    def test_aggregate_weekly_uses_first_and_last_day_not_min_max_price(self):
        # A week where the highest open is NOT the first day and the lowest
        # close is NOT the last day -- a plain groupBy(min/max) on open/close
        # would get this wrong; only date-ordered first/last is correct.
        raw = self.spark.createDataFrame(
            [
                Row(
                    symbol="SYNA",
                    timestamp="2022-01-03T21:00:00Z",
                    open=10.0,
                    high=12.0,
                    low=9.0,
                    close=11.0,
                    volume=100,
                ),
                Row(
                    symbol="SYNA",
                    timestamp="2022-01-04T21:00:00Z",
                    open=20.0,  # highest open of the week, but not day 1
                    high=21.0,
                    low=19.0,
                    close=5.0,  # lowest close of the week, but not the last day
                    volume=200,
                ),
                Row(
                    symbol="SYNA",
                    timestamp="2022-01-05T21:00:00Z",
                    open=15.0,
                    high=16.0,
                    low=14.0,
                    close=15.5,
                    volume=300,
                ),
            ]
        )
        normalized = spark_etl.normalize_schema(raw)
        weekly = spark_etl.aggregate_weekly(self.spark, normalized).collect()
        self.assertEqual(len(weekly), 1)
        row = weekly[0]
        self.assertEqual(row["open"], 10.0)  # day 1's open, not the max open
        self.assertEqual(row["close"], 15.5)  # day 3's close, not the min close
        self.assertEqual(row["high"], 21.0)
        self.assertEqual(row["low"], 9.0)
        self.assertEqual(row["volume"], 600)

    def test_data_quality_check_passes_on_clean_data(self):
        df = self.spark.createDataFrame(
            [
                Row(
                    symbol="SYNA",
                    week_start="2022-01-03",
                    open=10.0,
                    high=12.0,
                    low=9.0,
                    close=11.0,
                    volume=100,
                )
            ]
        )
        report = spark_etl.run_data_quality_checks(df)
        self.assertTrue(report.ok())
        self.assertEqual(report.row_count, 1)
        self.assertEqual(report.duplicate_keys, 0)

    def test_data_quality_check_catches_duplicate_keys(self):
        df = self.spark.createDataFrame(
            [
                Row(
                    symbol="SYNA",
                    week_start="2022-01-03",
                    open=10.0,
                    high=12.0,
                    low=9.0,
                    close=11.0,
                    volume=100,
                ),
                Row(
                    symbol="SYNA",
                    week_start="2022-01-03",  # duplicate (symbol, week_start) key
                    open=11.0,
                    high=13.0,
                    low=10.0,
                    close=12.0,
                    volume=150,
                ),
            ]
        )
        with self.assertRaises(spark_etl.DataQualityError):
            spark_etl.run_data_quality_checks(df)

    def test_data_quality_check_catches_null_required_column(self):
        # An explicit schema is required here: with `close` null on the
        # DataFrame's only row, Spark's type inference has nothing to infer
        # the column's type from and raises before the check under test
        # ever runs.
        from pyspark.sql.types import DoubleType, LongType, StringType, StructField, StructType

        schema = StructType(
            [
                StructField("symbol", StringType()),
                StructField("week_start", StringType()),
                StructField("open", DoubleType()),
                StructField("high", DoubleType()),
                StructField("low", DoubleType()),
                StructField("close", DoubleType()),
                StructField("volume", LongType()),
            ]
        )
        df = self.spark.createDataFrame(
            [("SYNA", "2022-01-03", 10.0, 12.0, 9.0, None, 100)], schema=schema
        )
        with self.assertRaises(spark_etl.DataQualityError):
            spark_etl.run_data_quality_checks(df)

    def test_end_to_end_run_against_a_small_fixture_directory(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            sample_dir = os.path.join(tmpdir, "sample")
            output_dir = os.path.join(tmpdir, "mart")
            os.makedirs(sample_dir)

            with open(
                os.path.join(sample_dir, "instruments.csv"), "w", newline="", encoding="utf-8"
            ) as handle:
                writer = csv.writer(handle)
                writer.writerow(["symbol", "currency", "asset_class", "timeframe", "file"])
                writer.writerow(["FIX", "USD", "EQUITY", "1d", "FIX_1d.csv"])

            with open(
                os.path.join(sample_dir, "FIX_1d.csv"), "w", newline="", encoding="utf-8"
            ) as handle:
                writer = csv.writer(handle)
                writer.writerow(["timestamp", "open", "high", "low", "close", "volume"])
                writer.writerow(["2022-01-03T21:00:00Z", "10.0", "12.0", "9.0", "11.0", "100"])
                writer.writerow(["2022-01-03T21:00:00Z", "10.0", "12.0", "9.0", "11.0", "100"])
                writer.writerow(["2022-01-04T21:00:00Z", "11.0", "13.0", "10.0", "12.5", "150"])

            report = spark_etl.run_pipeline(self.spark, sample_dir, output_dir)

            self.assertEqual(report.row_count, 1)  # one week, deduped first
            self.assertTrue(os.path.isdir(output_dir))

            written = self.spark.read.parquet(output_dir).collect()
            self.assertEqual(len(written), 1)
            self.assertEqual(written[0]["symbol"], "FIX")
            self.assertEqual(written[0]["volume"], 250)  # 100 + 150, dup dropped


if __name__ == "__main__":
    unittest.main()
