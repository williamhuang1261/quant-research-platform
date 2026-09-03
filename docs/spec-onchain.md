# Spec — on-chain DeFi AMM module (Extension 15)

Status: implemented (Extension 15, steps 1-3)

## Why this exists

Every asset class this platform touches so far -- equities, options, rates,
commodities, real estate -- trades off-chain, through a broker or an
exchange feed. Decentralized finance is a different execution model
entirely: prices are set mechanically by a smart contract's own formula,
not quoted by a market maker, and the "market maker" role is instead played
by anyone willing to deposit both assets into that contract. This extension
adds that as a new, additive asset class: a real Solidity constant-product
AMM pool, a Python simulation driving it on a local Ethereum fork, and a
Java analysis layer valuing a liquidity provider's position the same way
the platform already values every other position it studies.

**What this is not.** No real capital is ever at risk anywhere in this
module. The pool is never deployed to a public testnet or mainnet -- every
transaction in `data/onchain/amm_swaps_2026-09-03.csv` was mined on a
local, disposable Anvil node (chain id 31337) that no longer exists once
the script that started it exits. This is a mechanics and analysis
exercise, not a production trading strategy or an audited contract.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | A real constant-product AMM pool contract, unit and fuzz tested with Foundry |
| R2 | A reproducible, seeded on-chain simulation producing a real (if local) swap history |
| R3 | An impermanent-loss calculation derived from the constant-product invariant, not fitted or approximated |
| R4 | A fee-income calculation as a stand-in for a market maker's captured spread |
| R5 | Both R3 and R4 checked against an independently computed golden run, not just against their own output |

## The pool (`onchain/src/ConstantProductPool.sol`)

A minimal two-asset pool in the Uniswap V2 tradition: `addLiquidity`,
`removeLiquidity`, and `swap`, holding `reserve0 * reserve1` non-decreasing
across every swap once the 30bps fee is accounted for. `MockERC20.sol` is a
small internal token pair with no external dependency, since the point is
the pool's own mechanics, not integration with a real token standard's
edge cases.

The first implementation had a real bug, caught by the test suite rather
than by inspection: `totalLpShares` was reduced by the locked
`MINIMUM_LIQUIDITY` amount along with the first depositor's own minted
shares, instead of only the depositor's share being reduced while
`totalLpShares` keeps the full, unreduced count. Uniswap V2's actual
convention mints that locked liquidity to the zero address and still
counts it in `totalSupply` -- getting this wrong silently corrupts every
later pro-rata add/remove-liquidity calculation. Fixed before the commit
that introduced it landed; the two affected tests' expected values were
corrected to match the fixed, correct semantics, not loosened to match the
bug.

`test/ConstantProductPool.t.sol`'s `testFuzz_PriceImpactGrowsWithTradeSize`
is the property test: for any pair of trade sizes where one is a multiple
of the other (both bounded to a realistic range), the larger trade's
realized execution price is always further from the pre-trade spot price.
This is the textbook AMM slippage property -- a market maker pricing
against this pool would rely on exactly this monotonicity holding.

## Simulation (`onchain/script/Rebalance.s.sol`, `tools/amm_sim.py`)

Two independent ways of driving the pool, demonstrating two different
skills:

- **`Rebalance.s.sol`** is a Foundry `Script`: deploy, seed 1,000,000-unit
  liquidity at 1:1, displace the price with one large swap, then execute
  the opposite-direction trade an arbitrageur would make to pull the price
  back. Run with `forge script script/Rebalance.s.sol --broadcast` against
  a local Anvil node; verified end to end with a real run (spot price 1e18
  -> 907159072611280069 after displacement -> 916212357479027616 after
  rebalancing).
- **`amm_sim.py`** drives the fuller simulation `qrp-onchain`'s analysis
  reads: starts its own Anvil node, deploys the same pool via `web3.py`
  using `onchain/out/`'s compiled ABI/bytecode, and executes 40 swaps of
  alternating direction and randomized size, seeded (`RANDOM_SEED =
  20260903`) so re-running it produces a byte-for-byte identical CSV --
  verified by running it twice and diffing the output.

Same "run once, commit the frozen snapshot" convention
`fetch_ust_curve.py` and `fetch_energy_prices.py` already establish:
`data/onchain/amm_swaps_2026-09-03.csv` is committed, and no CI job or Java
test spins up a live Anvil node -- a clean clone stays fully offline.

## Analysis (`qrp-onchain`)

`AmmSwapCsvReader` parses the committed CSV into `AmmSwapRow` records,
using `BigInteger` throughout -- reserves and trade sizes at 18-decimal
scale routinely exceed `Long.MAX_VALUE` (~9.2e18), and a `long` would
silently truncate them. Parse failures name the file and 1-based line
number, the same convention `CsvMarketDataProvider` in `qrp-data` follows.

`AmmLpAnalyzer.impermanentLossFraction` uses the standard derivation from
the constant-product invariant: with `r = P(t)/P(0)` the ratio of the
current to initial spot price of token0 in token1,

```
IL(t) = 2 * sqrt(r) / (r + 1) - 1
```

always non-positive. There is no external price oracle anywhere in this
simulation, so the pool's own spot price is the reference price -- the
same assumption the textbook formula itself makes. Because this simulation
never removes liquidity, the fee each swap leaves behind keeps compounding
into the same reserves IL is measured against, which is why the realized
IL over the 40-swap run comes out very small (about -0.024%) even though
the price moved about 4.5%: the fee income is partially offsetting the
loss, exactly as it should.

`AmmLpAnalyzer.cumulativeFeeIncomeInToken1` sums each swap's 30bps fee,
converted to token1 terms at that swap's pre-trade spot price, as a proxy
for the spread a market maker holding the equivalent standing quote would
have captured over the same order flow. Over the 40-swap committed run
this comes to roughly 1,294.9 token1 -- a small but real return on the
1,000,000-unit initial deposit given how little the price actually moved.

Both numbers are checked in `AmmLpAnalyzerTest` against an *independently
computed* Python calculation (`Decimal`, 50-digit precision) run directly
over the committed CSV, not against `AmmLpAnalyzer`'s own output -- the
point of a golden-run test here is to catch the Java formula disagreeing
with an independent derivation, not to confirm it agrees with itself.

## What a reviewer can run

```bash
cd onchain && forge test -vv                              # 6 tests, incl. the fuzz test
anvil &
forge script script/Rebalance.s.sol --broadcast \
  --rpc-url http://127.0.0.1:8545                          # from onchain/
cd .. && python3 tools/amm_sim.py                          # refresh the CSV snapshot
mvn -pl qrp-onchain -am test                                # 9 tests, incl. golden-run checks
```
