#!/usr/bin/env python3
"""Downloads the EIA's daily Henry Hub Natural Gas Spot Price history and
writes it as a plain (date, price) CSV.

Not run by CI or by any Java code in this repository -- nothing under
qrp-*/native/ reads data/energy/ directly, so a clean clone stays fully
offline. Run this by hand when the committed snapshot should be refreshed:

    python3 tools/fetch_energy_prices.py > data/energy/henry_hub_$(date +%Y-%m-%d).csv

Source: eia.gov "Henry Hub Natural Gas Spot Price" (series RNGWHHD),
U.S. government work, public domain, no API key, no account -- same
"no accounts, no network by default" ethos as fetch_ust_curve.py's
Treasury-curve fetch. See data/energy/README.md for what this series does
and does not cover.

Needs xlrd (`pip install xlrd`) to read the legacy .xls EIA still serves for
this series; not part of the base matplotlib/numpy tooling dependency, so it
is documented here rather than assumed.
"""
import csv
import datetime
import subprocess
import sys
import tempfile

import xlrd

URL = "https://www.eia.gov/dnav/ng/hist_xls/RNGWHHDd.xls"
SHEET_NAME = "Data 1"
HEADER_ROWS = 3  # "Back to Contents", "Sourcekey", then the column-label row


def fetch_workbook(path: str) -> None:
    subprocess.run(
        ["curl", "-sf", "--max-time", "30", "-o", path, URL],
        check=True,
    )


def read_rows(path: str):
    workbook = xlrd.open_workbook(path)
    sheet = workbook.sheet_by_name(SHEET_NAME)
    for row_index in range(HEADER_ROWS, sheet.nrows):
        excel_date, price = sheet.row_values(row_index)
        date = datetime.datetime(
            *xlrd.xldate_as_tuple(excel_date, workbook.datemode)
        ).date()
        yield date.isoformat(), price


def main() -> None:
    with tempfile.NamedTemporaryFile(suffix=".xls") as handle:
        fetch_workbook(handle.name)
        rows = list(read_rows(handle.name))

    if not rows:
        raise SystemExit("no rows parsed from the EIA workbook")

    writer = csv.writer(sys.stdout)
    writer.writerow(["date", "price_usd_per_mmbtu"])
    writer.writerows(rows)

    print(
        f"# fetched {len(rows)} rows, {rows[0][0]}..{rows[-1][0]}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
