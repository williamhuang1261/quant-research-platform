#!/usr/bin/env python3
"""SQLite persistence and query layer over the Henry Hub CSV that
fetch_energy_prices.py produces.

No module in this platform has used a database before now; every other
"data" concept (bars, curves, option chains) is either an in-memory Java
object or a CSV read straight into one. This is a small, tested load/query
layer instead of a throwaway script, so the SQL is something a reviewer can
actually open and run.

Usage:
    python3 tools/energy_db.py data/energy/henry_hub_2026-08-29.csv /tmp/energy.db

Loading is idempotent: re-running against the same CSV and DB file leaves
the row count unchanged (`INSERT OR REPLACE` keyed on `date`), so refreshing
the snapshot never silently duplicates history.
"""
import csv
import sqlite3
import sys

SCHEMA = """
CREATE TABLE IF NOT EXISTS prices (
    date TEXT PRIMARY KEY,
    price_usd_per_mmbtu REAL NOT NULL
)
"""


def load_csv(db_path: str, csv_path: str) -> int:
    """Loads a Henry Hub CSV into `db_path`'s `prices` table.

    A small number of dates in the EIA's own history have no reported price
    (an unexplained reporting gap, not a market holiday -- those dates are
    simply absent from the source rather than present with a blank value);
    those rows are skipped rather than coerced into a fabricated 0.0 or
    forward-filled value, and the skip count is printed so it stays visible
    rather than silently dropped.

    Returns the number of rows in the table after loading (not the number of
    rows in the CSV), so a caller can tell an idempotent re-load from a
    genuine append.
    """
    with sqlite3.connect(db_path) as conn:
        conn.execute(SCHEMA)
        with open(csv_path, newline="", encoding="utf-8") as handle:
            rows = []
            skipped = 0
            for row in csv.DictReader(handle):
                if not row["price_usd_per_mmbtu"]:
                    skipped += 1
                    continue
                rows.append((row["date"], float(row["price_usd_per_mmbtu"])))
        if skipped:
            print(f"skipped {skipped} row(s) with no reported price", file=sys.stderr)
        conn.executemany(
            "INSERT OR REPLACE INTO prices (date, price_usd_per_mmbtu) VALUES (?, ?)",
            rows,
        )
        conn.commit()
        return conn.execute("SELECT COUNT(*) FROM prices").fetchone()[0]


def date_range(db_path: str, start: str, end: str):
    """Returns [(date, price), ...] for `start <= date <= end`, ascending."""
    with sqlite3.connect(db_path) as conn:
        return conn.execute(
            "SELECT date, price_usd_per_mmbtu FROM prices"
            " WHERE date BETWEEN ? AND ? ORDER BY date",
            (start, end),
        ).fetchall()


def rolling_average(db_path: str, window: int):
    """Returns [(date, price, rolling_avg), ...] via a SQL window function.

    `rolling_avg` is NULL for the first `window - 1` rows, where fewer than
    `window` prior rows exist -- SQLite's window frame simply uses what's
    available rather than padding, so those rows are reported as NULL
    instead of a partial-window average silently masquerading as a full one.
    """
    with sqlite3.connect(db_path) as conn:
        return conn.execute(
            """
            SELECT date, price_usd_per_mmbtu,
                   CASE WHEN COUNT(*) OVER win = ? THEN AVG(price_usd_per_mmbtu) OVER win END
            FROM prices
            WINDOW win AS (ORDER BY date ROWS BETWEEN ? PRECEDING AND CURRENT ROW)
            ORDER BY date
            """,
            (window, window - 1),
        ).fetchall()


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit(f"usage: {sys.argv[0]} <csv_path> <db_path>")
    csv_path, db_path = sys.argv[1], sys.argv[2]
    row_count = load_csv(db_path, csv_path)
    print(f"loaded {db_path}: {row_count} rows in prices")


if __name__ == "__main__":
    main()
