# qrp-options — derivatives pricing

Status: in progress. This spec grows one section per step, the same way the
other module specs did.

## Why the module exists

The platform values instruments and evaluates strategies on them. Options are
instruments, so they are priced through the same `Params`, the same specified
RNG and the same reproducibility rules as everything else, rather than in a
separate tool that happens to live in the same repository.

## Boundaries

- `qrp-options` depends on `qrp-core` (vocabulary) and `qrp-stats` (the normal
  distribution and `SplitMix64`). It depends on **no** engine and no data module,
  so a price is a pure function of its inputs.
- Nothing here reads a file or a clock. Valuation dates arrive as arguments.
- American exercise is rejected by the closed form rather than approximated by
  it. Pricing an American put as European understates it, silently, and only for
  the cases where the difference matters.

## Vocabulary

| Type | What it carries |
| --- | --- |
| `OptionType` | `CALL` / `PUT`, plus the sign and payoff those imply |
| `ExerciseStyle` | `EUROPEAN` / `AMERICAN`. Bermudan needs an exercise calendar and is out of scope |
| `OptionContract` | underlying, type, style, strike, expiry, contract multiplier |
| `BlackScholesInputs` | spot, strike, year fraction, volatility, `r`, `q` |
| `Greeks` | delta, gamma, vega, theta, rho, in stated units |

### D1 — contract terms are separate from `Instrument`

`Instrument` answers "what trades". Strike, expiry and exercise style change per
line of a chain while the underlying stays one thing, so folding them in would
turn a thousand-line chain into a thousand near-identical instruments.
`AssetClass` gains `OPTION` and `BOND` as *added* constants; the `Instrument`
record shape is unchanged.

### D2 — carry is a dividend yield, not a `b` parameter

`BlackScholesInputs` takes `q` and derives `b = r - q`, which makes one formula
cover four instruments:

| Instrument | `q` | carry | model |
| --- | --- | --- | --- |
| Non-dividend equity | 0 | `r` | Black-Scholes (1973) |
| Index with a yield | dividend yield | `r - q` | Merton (1973) |
| Future | `r` | 0 | Black (1976) |
| FX | foreign rate | `r_d - r_f` | Garman-Kohlhagen (1983) |

Taking `b` directly was rejected because **rho becomes ambiguous** under it:
differentiating with respect to `r` holding `b` fixed gives a different number
than holding `q` fixed, and the textbook equity value is the second. Taking `q`
removes the ambiguity from the signature instead of from a comment.

### D3 — ACT/365 fixed, stated rather than hidden

`OptionContract.yearsTo` uses ACT/365F. That is the wrong day count for a rates
desk, which wants ACT/360 or a business-day count off an exchange calendar. It
is used because this platform models **no holiday calendar at all**, and a day
count that silently assumed one would be a worse lie than a stated
approximation. Every pricer takes the year fraction as an argument, so a caller
with a real calendar supplies its own.

### D4 — the degenerate cases are priced, not rejected

At `T = 0` or `sigma = 0` there is no diffusion left, and the contract is worth
its discounted intrinsic value **on the forward**, not on spot — a call struck
below a forward that carries above it is worth something with no volatility at
all. This matters twice over: the implied-volatility solver brackets from zero
and will sit on this boundary, and the binomial tree needs a closed form to
compare against at its own limit.

The two branches are **not separate formulas**. In the degenerate case `N(d1)`
and `N(d2)` are replaced by the indicator that the forward finishes in the money
and the density by zero; every expression is then shared. Two independently
written formulas would eventually disagree at the seam, which is precisely where
a solver spends its time. `approachesTheZeroVolatilityLimitSmoothly` pins the
seam.

### D5 — `erfc` moved rather than being copied

`NormalQuantile` already carried a private `erfc` for its Halley refinement, and
option pricing needs the forward CDF the same routine provides. It moved to a
public `NormalDistribution` in `qrp-stats` and `NormalQuantile` now delegates.
The refactor is behaviour-preserving by construction: `NormalQuantileTest`
already pinned the published values to 1e-9 and still passes untouched.

`cdf` is built on `erfc` rather than a direct rational approximation because
`erfc` **underflows smoothly** — at `x = -20` it returns a positive number where
a polynomial returns a flat zero, and a deep out-of-the-money option asks for
exactly that.

## Greek units

Stated because this is what goes wrong. Per unit of underlying, before any
contract multiplier, per a move of **1.0** in the input:

- `delta` — per 1.0 of underlying price
- `gamma` — delta per 1.0 of underlying price
- `vega` — per **1.00** of volatility (100 vol points); `vegaPerVolPoint()` converts
- `theta` — per **year**, signed as a time derivative, so a decaying long is negative;
  `thetaPerCalendarDay()` converts
- `rho` — per 1.00 of `r`, holding `q` fixed (see D2)

The record stays in the units the formulas produce so a finite-difference check
compares like with like; the conversions exist for reports, not for the maths.

## How it is verified

- **Textbook values.** The Hull worked example, `c = 10.4506` / `p = 5.5735`.
- **Put-call parity to 1e-12**, across five parameter sets including a future, an
  FX pair and a negative rate. Parity holds by algebra with no appeal to the
  model, so a failure is an implementation bug rather than a disagreement.
- **All five Greeks against central differences.** Central rather than forward
  because the error is O(h^2), so a 1e-6 agreement needs no per-Greek tuning of
  the step. Theta is checked against **minus** the difference in time to expiry —
  the sign flip that is the most common error in a Greeks implementation.
- **No-arbitrage bounds**, monotonicity in spot and volatility, and the deep
  in/out-of-the-money limits.
- **`d2` as the exercise probability**, which catches a transposed `d1`/`d2`.

## Rates: RatesCurve, TreasuryCurveLoader, BondAnalytics

### D6 -- par yields used directly as zero rates, no bootstrap

`data/rates/*.csv` holds Treasury.gov's published **par yields**, and
`RatesCurve` treats them as zero (spot) rates with no bootstrapping step. This
is close to exact under a year and understates the true zero rate by up to
tens of basis points at the 20-30 year tenors, where coupon reinvestment has
decades to compound. Stated in `data/rates/README.md` rather than hidden
behind a plausible-looking curve; a real bootstrap is a natural extension, not
built here.

### D7 -- BondAnalytics uses continuous compounding, not the bond-market convention

A Treasury desk quotes and prices on a semi-annual bond-equivalent yield --
discrete compounding. `BondAnalytics` uses continuous compounding instead,
matching `BlackScholesInputs`'s `r` and `RatesCurve.discountFactor` elsewhere
in this module. Mixing compounding conventions inside one platform was judged
a worse source of a wrong number than picking the convention consistently. The
gap between the two is on the order of `y^2/2` per year of duration -- a few
basis points for a 10-year bond at 4-5%.

One consequence worth stating because it surprises anyone coming from a bond
desk: under continuous compounding, **Macaulay duration and modified duration
are the same number** (`Price(y) = sum CF_i e^{-y t_i}` differentiates
directly to the weighted-average-time definition, with no `1/(1+y/f)` factor).
`modifiedDuration` is a one-line delegation to `macaulayDuration`, not a second
formula that happens to agree.

### D8 -- the refresh script shells out to curl, not urllib

`tools/fetch_ust_curve.py`'s first draft used `urllib.request` and failed on
this machine with an SSL certificate error unrelated to Treasury.gov: a stock
macOS python.org install has no CA bundle wired into its default SSL context,
a common and well-documented gotcha. `curl`, confirmed working moments earlier,
uses the system trust store and needed no such workaround. Rewritten to shell
out to it, then re-run end to end and diffed against the committed CSV to
confirm the output matches byte for byte before trusting it.

### D9 -- RatesCurve is not wired into the volatility surface's own pricing yet

`VolatilitySurface` and `NoArbitrageDiagnostics` discount at each
`OptionChainQuote`'s own flat `riskFreeRate`, set at the point the chain was
generated. `RatesCurve` exists as an independent capability -- a real curve, a
real fetched snapshot -- but nothing in the surface-fitting path consumes it
yet. Wiring a curve-derived rate into `ImpliedVolatility.solve` per contract
tenor, rather than a flat number, is the natural next increment and is listed
as one in `mprojects.md`. The CLI report states this explicitly rather than
implying more integration than exists.

## Not here, deliberately

- Stochastic volatility calibration (Heston, SABR)
- Discrete dividend American trees
- Exotic payoffs
- Any vendor option chain
- A par-to-zero bootstrap for the Treasury curve (D6)
- RatesCurve wired into the surface's own IV solving (D9)
