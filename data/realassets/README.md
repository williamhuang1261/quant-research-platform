# data/realassets/

`SYNPROP_rentroll.csv` is a synthetic 20-unit multifamily rent roll: one row
per unit, its monthly market rent, and whether it is currently occupied.

**This is not real data, and that is a deliberate difference from
`data/rates/` and `data/energy/`.** Those two directories hold real, public,
no-API-key snapshots (a Treasury.gov par-yield curve, an EIA.gov commodity
price series) because a simple, no-account public source exists for both.
No equivalent exists for a real, unit-level commercial rent roll: that kind
of operating detail is normally proprietary (a landlord's own management
system, or a paid data vendor such as CoStar or REIS), not something a
public agency publishes. Rather than force a "real" label onto something not
genuinely comparable, this sample is clearly synthetic, named `SYNPROP` to
match the unmistakable-symbol convention `data/sample/README.md` already
established for `SYNA`/`SYNB`/`SYNETF`.

## Columns

- `unit_id` -- arbitrary unit label
- `monthly_market_rent` -- the unit's market rent, in dollars, at full
  occupancy
- `occupied` -- whether the unit is occupied as of this rent roll snapshot

## What `occupied` is and is not used for

`occupied` is informational only -- `NoiCalculator.physicalOccupancyRate`
reports it as a point-in-time fact. The valuation itself applies a
caller-supplied vacancy and collection loss rate to the gross potential
rent instead of deriving vacancy directly from this snapshot's occupied
count. This mirrors real underwriting practice: a loan officer or asset
manager typically stabilizes to a market-standard vacancy factor rather than
whatever a single day's rent roll happens to show, since a snapshot can be
temporarily better or worse than the property's steady-state performance.
`docs/spec-realassets.md` states this explicitly.
