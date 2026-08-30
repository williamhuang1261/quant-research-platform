# Spec — commercial real estate income-approach valuation (Extension 11)

Status: implemented (Extension 11, steps 1-2)

## Why this exists

Every discounting problem this platform has solved so far is a financial
instrument: `RatesCurve`/`BondAnalytics` (`qrp-options`) discount a bond's
coupon/principal stream off a real Treasury curve, and `qrp-portfolio`
allocates capital across financial instruments. No module has ever valued
a physical, income-producing real asset. `qrp-realassets` closes that gap
with the two standard commercial real estate income-approach methods:
direct capitalization and a multi-year discounted cash flow with a
terminal value. It is the same "discount a cash-flow stream off a rate"
shape as the fixed-income work, applied to a different, real-asset class.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Turn a unit-level rent roll and an operating expense budget into net operating income (NOI) |
| R2 | Value NOI by direct capitalization (`NOI / cap rate`) |
| R3 | Value a multi-year, growing NOI stream by discounted cash flow, including a terminal (resale) value |
| R4 | Report how sensitive the DCF value is to the discount rate and the vacancy/collection loss rate |
| R5 | A documented sample property a reviewer can run against without any external data source |

## NOI (`NoiCalculator`)

Gross potential rent (every unit's market rent at full occupancy, annualized)
minus a stated vacancy and collection loss rate gives effective gross income;
effective gross income minus total operating expenses gives NOI. NOI is
deliberately a pre-financing, pre-capex number -- no debt service and no
capital expenditure reserve are deducted, matching the standard commercial
real estate definition of NOI a loan committee would recognize.

The vacancy and collection loss rate is a caller-supplied proforma
assumption, not derived from the rent roll's own occupied/vacant flags.
`physicalOccupancyRate` reports the snapshot's actual occupancy (weighted
by rent dollars, so a vacant high-rent unit counts for more than a vacant
low-rent one) separately, as a fact about that one day, not an input to the
valuation. This mirrors how a loan officer underwrites: to a market-standard
vacancy factor, not to whatever one rent roll happens to show.

## Direct capitalization (`DirectCapValuation`)

`value = NOI / capRate`. No holding period, no NOI growth path, and no exit
assumption of its own -- it treats next year's NOI as a flat perpetuity.
The simplest and fastest of the two methods, and the one most often quoted
as a single "cap rate" headline number.

## Discounted cash flow (`DcfValuation`)

A holding period of `n` years, each year's NOI grown from year 1 at a
stated annual rate, each year's NOI discounted at a stated discount rate,
plus a terminal value: the NOI of the year immediately after the holding
period, capitalized at a stated exit cap rate, discounted back to the
present the same way the holding-period NOI is.

**Annual, discretely compounded discounting** (`(1 + r)^-t`), not the
continuous compounding (`e^{-rt}`) `qrp-options` uses for bonds and
options. This is a deliberate, module-local convention choice, not an
inconsistency with the rest of the platform: a real estate proforma is
conventionally built one discrete fiscal year at a time, the market
convention this module exists to match, the same way `BondAnalytics`'s own
javadoc explains why it does not follow the bond market's semi-annual
bond-equivalent-yield convention either.

## Sensitivity grid (`DiscountVacancySensitivityGrid`)

Grids over discount rate and vacancy/collection loss rate -- the two
assumptions a loan officer or asset manager typically stresses first,
since neither is directly observable and both move value in the same
direction (down) as they rise. Each cell recomputes NOI at that cell's
vacancy rate via `NoiCalculator` and feeds it to `DcfValuation` at that
cell's discount rate, reusing both rather than duplicating their
arithmetic. Correctness is checked by monotonicity (value strictly falls
as either rate rises) and one exact cross-check against a direct
`DcfValuation` call, not a second independently-derived golden number.

## Sample data (`data/realassets/`)

`SYNPROP_rentroll.csv`: a synthetic 20-unit multifamily property, 10 units
at $1,000/month and 10 at $1,200/month, two vacant. Clearly synthetic and
named to match the platform's `SYNA`/`SYNB`/`SYNETF`/`SYNOPT` convention,
for a reason stated in `data/realassets/README.md`: unlike Treasury.gov
(`data/rates/`) and EIA.gov (`data/energy/`), no public, no-account source
exists for real, unit-level commercial rent-roll data -- that level of
operating detail is normally proprietary. Golden-run numbers on this
sample, pinned from the actual computed output, not hand-derived: gross
potential rent $264,000/yr, effective gross income at a 5% vacancy/
collection loss factor $250,800/yr, NOI $161,260/yr, direct-cap value at a
6.5% cap rate $2,480,923.08, 5-year DCF present value at a 2% NOI growth
rate, 8% discount rate and 7% exit cap rate $2,399,157.61.

## What is deliberately not here

- **No market or demographic data feed.** Cap rates, discount rates, NOI
  growth rates and vacancy assumptions are all caller-supplied inputs, not
  derived from any market, comparable-sale, or demographic data source.
- **No debt-service coverage or loan-sizing layer.** NOI is reported
  pre-financing; nothing here sizes a loan against a debt-service coverage
  ratio or a loan-to-value constraint.
- **No partnership, syndication, or REIT-structure logic.** Waterfall
  distributions, promote structures, and tax-credit equity mechanics (as
  in a low-income housing partnership) are entirely out of scope.
- **No real property data.** `SYNPROP` is synthetic; see
  `data/realassets/README.md` for why no real, unit-level equivalent to
  the Treasury curve or the Henry Hub series exists for this domain.
- **No capital expenditure reserve or replacement-reserve deduction from
  NOI.** A real underwriting proforma often nets a reserve out before
  arriving at a distributable cash flow; this module stops at NOI itself.
