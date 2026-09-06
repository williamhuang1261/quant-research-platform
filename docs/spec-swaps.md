# qrp-options — interest rate swaps and swaptions

Status: extension to `spec-options.md`. `SwapValuation` and `SwaptionPricer`
sit on top of the existing `RatesCurve`/`BondAnalytics` infrastructure rather
than introducing a second curve or day-count convention.

## Why this extension exists

`qrp-options` already prices bonds and options off a real Treasury zero
curve. Interest rate swaps and swaptions are the other two instrument
families a rates desk actually trades, and they price off exactly the same
curve, so they belong in the same module rather than a separate one.

## D1 — single-curve pricing, stated rather than hidden

A real desk splits the floating leg's projection (e.g. off a SOFR curve) from
discounting (off an OIS curve), and the two disagree by a basis spread. This
module has one curve, `RatesCurve`, so both legs of `SwapValuation` are priced
off it.

Under that single-curve assumption the floating leg of a swap that resets
today needs no forecast of future fixings at all. It reduces to a closed
form:

```
floatingLegPv = notional * (1 - DF(T))
```

This is the same identity a floating-rate note trading at par reduces to: a
bond that always resets to the fair floating rate is worth its notional at
each reset, so the whole leg telescopes down to the difference between the
notional received today and the notional discounted back from the final
payment.

The fixed leg is priced the way `BondAnalytics` prices a bond's coupon
stream: `BondAnalytics.semiAnnualCashflowTimes` generates the schedule, and
each `notional * fixedRate * accrual` cash flow is discounted off the curve.

## D2 — the annuity is deliberately per-unit-notional

`SwapValuation.annuity()` returns `sum(accrual * DF(t))`, **excluding**
notional. `parRate()` divides two per-unit quantities
(`(1 - DF(T)) / annuity()`), so the notional cancels and the division stays
correct regardless of trade size.

Anything that needs a *dollar* annuity, like `SwaptionPricer`, multiplies
`annuity()` by `SwapValuation.notional()` itself rather than `SwapValuation`
baking notional into `annuity()`. This split was caught as a real bug during
the worked example below: an earlier draft of `SwaptionPricer` used
`swap.annuity()` directly and priced a $10M-notional 2y-into-5y payer
swaption at $0.03 instead of roughly $288,000 -- five orders of magnitude off
-- because the per-unit annuity from `parRate()`'s denominator was reused
without the multiplication a dollar payoff needs. `SwaptionPricerTest`'s
`swaptionValueScalesWithNotional` test exists specifically to catch a
regression back to that bug.

## D3 — DV01 by a closed-form parallel shift, not a curve rebuild

`SwapValuation.dv01()` reprices under a uniform 1bp bump to every tenor's
zero rate. Because `DF(t) = e^{-r(t) t}`, bumping `r(t)` by a constant `0.0001`
at every tenor scales that tenor's discount factor by exactly
`e^{-0.0001 t}`, whatever the curve's shape is between its own quoted points:

```
e^{-(r(t) + 0.0001) t} = e^{-r(t) t} * e^{-0.0001 t} = DF(t) * e^{-0.0001 t}
```

This lets `dv01()` reprice without needing `RatesCurve`'s own quoted points at
all, which it does not expose. The same finite-difference definition
`BondAnalytics.dv01` uses for a bond (dollar price change per 1bp) is used
here.

## D4 — swaptions reuse Black-Scholes-Merton's `d1`/`d2`, not a second formula

`SwaptionPricer` prices European swaptions under Black-76 on the forward par
swap rate. Constructing a `BlackScholesInputs.future(forward, strike, T,
vol, r)` -- the same factory an option on a future uses -- already sets zero
carry (`q = r`), which produces exactly Black-76's `d1`/`d2`. So
`SwaptionPricer` calls `BlackScholesMerton.d1`/`d2` rather than re-deriving
the formula, and the one place it diverges from pricing an option on a
future is deliberate: a future discounts by a single discount factor to
expiry, but a swaption's payoff is an annuity spread across the underlying
swap's whole remaining life, so `SwaptionPricer` scales `N(d1)`/`N(d2)` by
`notional * annuity()` instead of calling `BlackScholesMerton.price`
directly.

## D5 — flat volatility, not a fitted swaption surface, and why

`VolatilitySurface` is built from a chain of real market option quotes. This
module has no swaption-quote market data to fit a surface (or cube, across
expiry x tenor x strike) from. Fabricating quotes to populate one would be a
worse lie than the alternative taken here: `SwaptionPricer.price` accepts a
flat volatility as a caller-supplied input. Fitting a real swaption vol
surface is a stated, scoped-out extension.

## Worked example

A $10,000,000 notional, 5-year swap off an upward-sloping curve
(1y = 4.0%, 5y = 4.5%, 10y = 5.0%, linearly interpolated, continuously
compounded):

| Quantity | Value |
| --- | --- |
| Par swap rate | 4.5275% |
| Annuity (per unit notional) | 4.450221 |
| Fixed leg PV at a 4% coupon | $1,780,088.45 |
| Floating leg PV | $2,014,837.81 |
| Payer PV at a 4% coupon | $234,749.36 |
| DV01 | $4,464.34 |

A 2-year-into-5-year payer and receiver swaption, struck at 4.5% with 25%
flat volatility, off the same swap:

| Quantity | Value |
| --- | --- |
| Payer swaption | $288,016.35 |
| Receiver swaption | $275,778.04 |
| Payer - Receiver | $12,238.31 |
| Annuity * (F - K), the parity identity | $12,238.31 (matches to 1e-11) |

The payer is worth more than the receiver here because the forward par rate
(4.5275%) sits above the 4.5% strike: the payer is already in the money on
the forward.

## Not covered

- No basis spread between projection and discounting -- see D1.
- No amortizing, forward-starting-with-a-stub, or non-semi-annual schedules.
- No swaption vol surface -- see D5. A caller supplies one flat volatility
  per pricing call.
- No cap/floor pricing, though the same `RatesCurve` and Black-76 machinery
  would extend to one directly.
