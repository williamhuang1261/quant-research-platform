#!/usr/bin/env python3
"""PySpark ETL over the platform's sample daily bars: deduplicate,
normalize the schema, and aggregate into weekly OHLCV via Spark SQL,
writing the result to a local Parquet mart guarded by an inline
data-quality check.

The weekly aggregation is a Spark SQL query, not a DataFrame one-liner: a
week's open/close need the first/last bar *in date order*, which a plain
groupBy(...).agg(...) cannot express (min/max on open or close would just
return the numerically smallest/largest price, not the first/last trading
day's). ROW_NUMBER() OVER (PARTITION BY symbol, week_start ORDER BY date)
picks out those specific rows.

Data-quality check (run after aggregation, before the mart is written):
- the aggregated result is non-empty
- no null values in any required column
- no duplicate (symbol, week_start) key

A failure raises DataQualityError rather than writing a bad mart.

Usage:
    python3 tools/spark_etl.py data/sample data/spark_mart

Needs `pyspark` (`pip install pyspark`) and a JDK on PATH; nothing else.
"""
import csv
import sys
from dataclasses import dataclass

from pyspark.sql import DataFrame, SparkSession
from pyspark.sql.types import (
    DoubleType,
    LongType,
    StringType,
    StructField,
    StructType,
)

REQUIRED_COLUMNS = ["symbol", "week_start", "open", "high", "low", "close", "volume"]
KEY_COLUMNS = ["symbol", "week_start"]

RAW_SCHEMA = StructType(
    [
        StructField("timestamp", StringType(), nullable=False),
        StructField("open", DoubleType(), nullable=False),
        StructField("high", DoubleType(), nullable=False),
        StructField("low", DoubleType(), nullable=False),
        StructField("close", DoubleType(), nullable=False),
        StructField("volume", LongType(), nullable=False),
    ]
)


class DataQualityError(Exception):
    """Raised when the aggregated mart fails a data-quality check."""


@dataclass
class DataQualityReport:
    row_count: int
    duplicate_keys: int
    null_counts: dict[str, int]

    def ok(self) -> bool:
        return (
            self.row_count > 0
            and self.duplicate_keys == 0
            and all(count == 0 for count in self.null_counts.values())
        )


def read_instruments(spark: SparkSession, sample_dir: str) -> DataFrame:
    """Reads `sample_dir/instruments.csv` and unions every referenced
    per-symbol CSV into one DataFrame with an added `symbol` column.

    The manifest and per-symbol files are read with Python's csv module
    first (not Spark's own CSV reader) so a malformed manifest row raises a
    clear, file:line-numbered error before any Spark job is scheduled,
    matching CsvMarketDataProvider's fail-fast convention on the Java side.
    """
    manifest_path = f"{sample_dir}/instruments.csv"
    with open(manifest_path, newline="", encoding="utf-8") as handle:
        instruments = list(csv.DictReader(handle))
    if not instruments:
        raise ValueError(f"{manifest_path}: no instruments listed")

    frames = []
    for row in instruments:
        symbol = row["symbol"]
        csv_path = f"{sample_dir}/{row['file']}"
        bars = spark.read.csv(csv_path, header=True, schema=RAW_SCHEMA)
        frames.append(bars.withColumn("symbol", spark_lit(symbol)))

    combined = frames[0]
    for frame in frames[1:]:
        combined = combined.unionByName(frame)
    return combined


def spark_lit(value: str):
    from pyspark.sql.functions import lit

    return lit(value)


def deduplicate(df: DataFrame) -> DataFrame:
    """Drops exact duplicate (symbol, timestamp) rows, keeping one copy."""
    return df.dropDuplicates(["symbol", "timestamp"])


def normalize_schema(df: DataFrame) -> DataFrame:
    """Parses the ISO-8601 timestamp into a `date` column and derives
    `week_start` (the Monday that date's week starts on), so every
    downstream aggregation groups on a consistent, typed key instead of a
    raw string.
    """
    from pyspark.sql.functions import date_trunc, to_date, to_timestamp

    return (
        df.withColumn("ts", to_timestamp("timestamp"))
        .withColumn("date", to_date("ts"))
        .withColumn("week_start", to_date(date_trunc("week", "ts")))
    )


def aggregate_weekly(spark: SparkSession, df: DataFrame) -> DataFrame:
    """Aggregates daily bars into weekly OHLCV via a Spark SQL query using
    ROW_NUMBER() window functions to pick out each week's first and last
    trading day, not just its numeric min/max price. See the module
    docstring for why a plain groupBy/agg cannot do this correctly.
    """
    df.createOrReplaceTempView("bars")
    return spark.sql(
        """
        WITH ordered AS (
            SELECT
                symbol, week_start, date, open, high, low, close, volume,
                ROW_NUMBER() OVER (
                    PARTITION BY symbol, week_start ORDER BY date ASC
                ) AS rn_first,
                ROW_NUMBER() OVER (
                    PARTITION BY symbol, week_start ORDER BY date DESC
                ) AS rn_last
            FROM bars
        )
        SELECT
            symbol,
            week_start,
            MAX(CASE WHEN rn_first = 1 THEN open END) AS open,
            MAX(high) AS high,
            MIN(low) AS low,
            MAX(CASE WHEN rn_last = 1 THEN close END) AS close,
            SUM(volume) AS volume
        FROM ordered
        GROUP BY symbol, week_start
        """
    )


def run_data_quality_checks(df: DataFrame) -> DataQualityReport:
    """Checks the aggregated mart: non-empty, no nulls in a required
    column, no duplicate (symbol, week_start) key. Returns a report either
    way so a caller can log it; raises DataQualityError only if a check
    actually fails.
    """
    row_count = df.count()

    null_counts = {}
    for column in REQUIRED_COLUMNS:
        null_counts[column] = df.filter(df[column].isNull()).count()

    key_count = df.count()
    distinct_key_count = df.select(*KEY_COLUMNS).distinct().count()
    duplicate_keys = key_count - distinct_key_count

    report = DataQualityReport(
        row_count=row_count, duplicate_keys=duplicate_keys, null_counts=null_counts
    )
    if not report.ok():
        raise DataQualityError(
            f"data-quality check failed: row_count={report.row_count}, "
            f"duplicate_keys={report.duplicate_keys}, "
            f"null_counts={report.null_counts}"
        )
    return report


def write_mart(df: DataFrame, output_dir: str) -> None:
    """Writes the aggregated mart as Parquet. `coalesce(1)` keeps this
    small, sample-sized mart to one output file, which is fine at this
    scale and matches the rest of the platform's committed-snapshot data
    files being single files rather than a partitioned directory tree.
    """
    df.select(*REQUIRED_COLUMNS).orderBy("symbol", "week_start").coalesce(
        1
    ).write.mode("overwrite").parquet(output_dir)


def run_pipeline(spark: SparkSession, sample_dir: str, output_dir: str) -> DataQualityReport:
    """Runs the full read -> dedup -> normalize -> aggregate -> DQ-check ->
    write pipeline against an already-running SparkSession, so a caller
    (a test, or another script) controls that session's lifecycle rather
    than this function stopping it out from under them.
    """
    raw = read_instruments(spark, sample_dir)
    raw_count = raw.count()
    deduped = deduplicate(raw)
    deduped_count = deduped.count()
    normalized = normalize_schema(deduped)
    aggregated = aggregate_weekly(spark, normalized)
    aggregated_count = aggregated.count()

    report = run_data_quality_checks(aggregated)

    write_mart(aggregated, output_dir)

    print(f"read {raw_count} row(s) from {sample_dir}")
    print(f"deduplicated to {deduped_count} row(s)")
    print(f"aggregated to {aggregated_count} weekly row(s)")
    print(
        f"data quality check passed: row_count={report.row_count}, "
        f"duplicate_keys={report.duplicate_keys}"
    )
    print(f"wrote mart to {output_dir}")
    return report


def run(sample_dir: str, output_dir: str) -> DataQualityReport:
    """CLI entry point: owns a SparkSession for the duration of one run and
    stops it afterward. Not used by tests, which share a session across
    many calls into run_pipeline instead.
    """
    spark = SparkSession.builder.appName("qrp-spark-etl").master("local[*]").getOrCreate()
    spark.sparkContext.setLogLevel("WARN")
    try:
        return run_pipeline(spark, sample_dir, output_dir)
    finally:
        spark.stop()


if __name__ == "__main__":
    sample_dir = sys.argv[1] if len(sys.argv) > 1 else "data/sample"
    output_dir = sys.argv[2] if len(sys.argv) > 2 else "data/spark_mart"
    run(sample_dir, output_dir)
