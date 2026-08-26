# US Treasury par yield curve snapshots

## Source

U.S. Department of the Treasury, "Daily Treasury Par Yield Curve Rates",
published at
`home.treasury.gov/resource-center/data-chart-center/interest-rates/`.
Public domain (U.S. government work), no API key, no account.

`ust_cmt_2026-08-25.csv` is one dated snapshot, columns:

- `tenor_years` -- maturity, in years (`0.0833` = 1 month, `30.0` = 30 years)
- `yield_pct` -- the published par yield, in percent (`4.64` means 4.64%)
- `tenor_label` -- the Treasury's own column header for that tenor, kept for
  traceability back to the source table

## What this is not

The Treasury publishes **par yields** -- the coupon rate at which a bond priced
at that maturity would trade at par -- not zero-coupon (spot) rates. `RatesCurve`
treats them as spot rates directly, with no bootstrapping step. This is the same
simplification most desk spreadsheets make for a short-dated curve, and it is
close to exact for the front end where coupon effects are tiny; it understates
the truth at the 20- and 30-year points, where the gap between a par yield and
the corresponding spot rate can run tens of basis points. `docs/spec-options.md`
states this as a limitation. A proper bootstrap (stripping coupons tenor by
tenor) is listed as a natural extension and is deliberately not built here.

## Regenerating

```
python3 tools/fetch_ust_curve.py > data/rates/ust_cmt_<date>.csv
```

The script is documented in `tools/README.md` and is **not** run by CI or by any
Java code: `RatesCurve` reads only the committed CSV, so a clean clone works
fully offline, matching every other data file in this repository.
