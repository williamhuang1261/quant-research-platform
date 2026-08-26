#!/usr/bin/env python3
"""Downloads the latest U.S. Treasury daily par yield curve and writes it in
the format RatesCurve/TreasuryCurveLoader expects.

Not run by CI or by any Java code in this repository -- RatesCurve reads only
the committed CSV under data/rates/, so a clean clone stays fully offline.
Run this by hand when the committed snapshot should be refreshed:

    python3 tools/fetch_ust_curve.py > data/rates/ust_cmt_$(date +%Y-%m-%d).csv

Source: home.treasury.gov "Daily Treasury Par Yield Curve Rates", public
domain, no API key. See data/rates/README.md for what "par yield" does and
does not mean for this platform.

Shells out to curl rather than using urllib: a stock macOS python.org install
has no CA bundle wired into its default SSL context, which fails this fetch
with a certificate error that has nothing to do with Treasury.gov. curl uses
the system trust store and works out of the box on every platform this was
tested on.
"""
import csv
import subprocess
import sys

URL = (
    "https://home.treasury.gov/resource-center/data-chart-center/"
    "interest-rates/daily-treasury-rates.csv/{year}/all"
    "?type=daily_treasury_yield_curve&field_tdr_date_value={year}&page&_format=csv"
)

# Treasury.gov column header -> (tenor in years, tenor label to keep in the
# output). Order matches the order the source table publishes.
TENORS = [
    ("1 Mo", 1 / 12, "1 Mo"),
    ("1.5 Month", 1.5 / 12, "1.5 Month"),
    ("2 Mo", 2 / 12, "2 Mo"),
    ("3 Mo", 3 / 12, "3 Mo"),
    ("4 Mo", 4 / 12, "4 Mo"),
    ("6 Mo", 6 / 12, "6 Mo"),
    ("1 Yr", 1.0, "1 Yr"),
    ("2 Yr", 2.0, "2 Yr"),
    ("3 Yr", 3.0, "3 Yr"),
    ("5 Yr", 5.0, "5 Yr"),
    ("7 Yr", 7.0, "7 Yr"),
    ("10 Yr", 10.0, "10 Yr"),
    ("20 Yr", 20.0, "20 Yr"),
    ("30 Yr", 30.0, "30 Yr"),
]


def fetch_latest_row(year: int) -> dict:
    result = subprocess.run(
        ["curl", "-sf", "--max-time", "30", URL.format(year=year)],
        capture_output=True, text=True, check=True,
    )
    reader = csv.DictReader(result.stdout.splitlines())
    rows = list(reader)
    if not rows:
        raise SystemExit(f"no rows returned for year {year}")
    return rows[0]  # Treasury publishes most-recent-first.


def main() -> None:
    import datetime

    year = datetime.date.today().year
    row = fetch_latest_row(year)

    writer = csv.writer(sys.stdout)
    writer.writerow(["tenor_years", "yield_pct", "tenor_label"])
    for column, years, label in TENORS:
        value = row.get(column, "").strip()
        if not value:
            continue
        writer.writerow([round(years, 4), value, label])

    print(f"# snapshot date: {row.get('Date', 'unknown')}", file=sys.stderr)


if __name__ == "__main__":
    main()
