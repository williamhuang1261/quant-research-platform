# data/spark_mart/

Weekly OHLCV Parquet mart produced by `tools/spark_etl.py` from
`data/sample/*.csv`.

- **How it's built:** `tools/spark_etl.py` reads the sample daily bars via
  PySpark, deduplicates exact `(symbol, timestamp)` rows, normalizes the
  schema (parses the timestamp, derives a `date` and a `week_start`), and
  aggregates to weekly OHLCV with a Spark SQL query -- see the module
  docstring for why that aggregation needs `ROW_NUMBER()` window functions
  rather than a plain `groupBy(...).agg(min/max(...))`.
- **Not committed.** Unlike the rest of this repository's `data/`
  subdirectories, this mart is regenerated output, not a frozen snapshot --
  it is excluded via `.gitignore` and rebuilt on demand.
- **Regenerate:**
  ```bash
  pip install pyspark
  python3 tools/spark_etl.py data/sample data/spark_mart
  ```
- **What this is not:** a production data warehouse table. It is a small,
  local Parquet file meant to be read back by `tools/snowflake_loader.py`
  (a later step in this extension) or inspected directly with pandas
  (`pd.read_parquet("data/spark_mart")`).
