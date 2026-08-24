# Sample data

**These are not real prices.** Each series is a seeded geometric Brownian motion
produced by
[`SyntheticSeriesGenerator`](../../qrp-data/src/main/java/io/github/williamhuang1261/qrp/data/SyntheticSeriesGenerator.java),
committed so that a clone of this repository runs with no network, no vendor
account and no licensing question. The symbols are deliberately unmistakable.

| Symbol | Class | Bars | Period | Drift | Volatility |
| --- | --- | --- | --- | --- | --- |
| `SYNA` | equity | 504 | 2022-01-03 → 2023-12-07 | +8 %/yr | 24 %/yr |
| `SYNB` | equity | 504 | 2022-01-03 → 2023-12-07 | −3 %/yr | 35 %/yr |
| `SYNETF` | ETF | 504 | 2022-01-03 → 2023-12-07 | +6 %/yr | 14 %/yr |

Weekends are skipped; holidays are not modelled, so the only gaps are three-day
weekends. Timestamps are the bar close, 21:00 UTC.

Regenerate (same seeds produce the same bytes):

```bash
mvn -pl qrp-data exec:java
```

To run the platform on your own data instead, point
`CsvMarketDataProvider.ofDirectory(...)` at any directory laid out like this one:
an `instruments.csv` manifest plus one `SYMBOL_<timeframe>.csv` per series.
