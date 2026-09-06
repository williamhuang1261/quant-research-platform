# qrp-equity — applied equity valuation and written research notes

Status: new module. `EquityDcfValuation` and `ComparableMultiplesValuation`
sit on top of `qrp-options`'s existing `RatesCurve` rather than introducing a
second discounting convention, and `EquityResearchNoteGenerator` applies the
same "structured result in, ruled-based prose out" shape `qrp-report`'s
`TemplateNarrativeGenerator` already uses for fund comparisons.

## Why this module exists

Every other module in this platform prices something -- an option, a bond, a
swap, a piece of real estate -- but none of them apply that pricing machinery
to a real company and write up the result. This module closes that gap: it
takes real fundamentals for a small, fixed set of real companies, values
each one two ways, and renders the result as a short written research note
with a hand-authored catalyst thesis, the same shape a buy-side analyst
actually produces.

## D1 -- the discount rate mixes a real curve with a stated constant, not a fitted beta

`EquityDcfValuation.impliedDiscountRate` adds a flat, stated equity risk
premium (4.5% in the committed sample notes) to the platform's real Treasury
zero rate (`RatesCurve.zeroRate`) at a chosen tenor. A rigorous cost-of-equity
estimate would fit a beta against a market index and scale the premium by it
(CAPM); this module does not fit one. The premium is a single, visible
constant instead, so every valuation this module produces is transparent
about exactly one assumption rather than hiding it inside a coefficient this
platform has no market-index regression to justify. 4.5% sits within the
range of commonly cited historical and implied U.S. equity risk premium
estimates; it is not calibrated to either covered company specifically.

## D2 -- annual, discretely compounded discounting, matching qrp-realassets, not qrp-options

`EquityDcfValuation` discounts with `(1 + r)^-t`, the same convention
`qrp-realassets`'s `DcfValuation` uses for real estate, not the continuous
`e^{-rt}` convention `qrp-options` uses for bonds and swaps. This is the same
reasoning `DcfValuation`'s own javadoc gives: a company's free-cash-flow
projection is conventionally built one discrete fiscal year at a time, the
way an analyst's spreadsheet model actually works, not on a continuous
trading-desk clock.

## D3 -- Gordon-growth terminal value, not an exit multiple

Unlike `qrp-realassets`'s exit-capitalization terminal value (built around
selling the asset), `EquityDcfValuation.discountedTerminalValue` uses a
constant-perpetual-growth (Gordon growth) terminal value: next year's FCF
divided by `(discountRate - terminalGrowthRate)`. A public company is not
conventionally modelled as being sold at the end of a holding period, so a
perpetuity is the standard choice here instead. `terminalGrowthRate` is
validated to be strictly less than `discountRate`, since the perpetuity
formula diverges (or goes negative) otherwise.

## D4 -- the FCF growth assumption is the candidate's own stated estimate, not a consensus figure

`EquityDcfValuation.Inputs.fcfGrowthRate` is supplied by the caller, not
derived from any historical trend in `EquityFundamentals`. The two committed
sample notes use 7% (AAPL) and 15% (MSFT) explicit-period growth, stated in
each note's Catalyst/Thesis section along with the reasoning behind the
choice, not silently baked into a number with no visible assumption.

## D5 -- the comps check compares P/E to an unweighted peer average, no similarity screening

`ComparableMultiplesValuation.averagePeerPe` takes the plain, unweighted mean
of the peer group's trailing P/E. A real comps desk screens peers for size,
growth rate and margin similarity before averaging them; that screening is a
stated, out-of-scope extension here, not something this module claims to do.

## D6 -- the fundamentals snapshot is a fixed, committed, one-time fetch

`data/equity/*.csv` are fetched once by hand via `tools/fetch_equity_fundamentals.py`
(`yfinance`, no API key) and committed, exactly like every other data file in
this platform (`data/rates/`, `data/energy/`). `EquityFundamentals`/`PeerComps`
read only the committed CSVs; a clean clone of this repository never touches
the network to build or test `qrp-equity`. Refreshing the snapshot is a
manual, occasional action, not something CI or any Java code triggers.

## D7 -- Microsoft's trailing free cash flow figure is unusually low, and the note says so

The data provider's trailing-twelve-month free cash flow figure for MSFT at
this fetch date ($16.5B) is far below its trailing operating cash flow
($182.9B), reflecting a period of exceptionally heavy capital expenditure on
AI data center capacity. `EquityDcfValuation` has no way to distinguish
"genuinely low steady-state cash generation" from "temporarily depressed by
a large, real capex program" -- it discounts whatever `free_cash_flow` value
it is given. The MSFT sample note states this explicitly rather than quietly
using the number: the resulting DCF fair value is a conservative floor built
on a real but likely-temporary trough, not a claim about Microsoft's
normalized cash-generating power.

## Worked example -- both committed sample notes, 2026-09-06 fetch

Risk-free rate: 10-year Treasury zero rate at the `2026-08-25` curve snapshot,
`RatesCurve.zeroRate(10.0)` = 4.64%. Equity risk premium: 4.5% (D1). Combined
discount rate: 9.14% for both notes. Explicit period: 5 years. Terminal
growth: 3.0% for both.

| Ticker | FCF growth (explicit) | DCF value/share | Comps value/share | Current price |
| --- | --- | --- | --- | --- |
| AAPL | 7.0% | $141.40 | $191.19 | $319.97 |
| MSFT | 15.0% | $58.50 | $444.80 | $499.70 |

Both methods value both companies below their current trading price. This is
the expected, honest result of applying a conservative DCF/comps floor to two
mega-cap growth compounders the market prices on forward expectations rather
than trailing cash flow -- it is not evidence the module is broken, and each
sample note's Catalyst/Thesis section says so explicitly rather than treating
the gap as a buy signal.

## What this module does not do

- No discounted-cash-flow sensitivity grid (varying growth/discount rate
  across a range) -- `qrp-realassets`'s `DiscountVacancySensitivityGrid` has
  this shape for real estate; a natural, additive extension here.
- No historical fundamentals time series -- one point-in-time snapshot per
  company, stated in `data/equity/README.md`.
- No sector- or size-matched peer screening for the comps check (D5).
- No fitted cost-of-equity beta (D1).
- Only two companies covered; the ticker list in
  `tools/fetch_equity_fundamentals.py` is intentionally small and fixed.
