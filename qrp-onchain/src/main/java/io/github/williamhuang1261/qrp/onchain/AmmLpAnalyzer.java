package io.github.williamhuang1261.qrp.onchain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Values a liquidity provider's position in {@code ConstantProductPool}
 * across a swap history: impermanent loss against a hold-both-assets
 * benchmark, and the pool's cumulative fee income as a stand-in for a
 * market maker's captured spread.
 *
 * <p><b>Impermanent loss.</b> Uses the pool's own spot price as the
 * reference price, the same convention the textbook IL formula assumes
 * (there is no external oracle in this simulation — see
 * {@code docs/spec-onchain.md}). With {@code r = P(t) / P(0)} the ratio of
 * the current to initial spot price of token0 in token1:
 *
 * <pre>
 *     IL(t) = 2 * sqrt(r) / (r + 1) - 1
 * </pre>
 *
 * always non-positive, and derived directly from the constant-product
 * invariant, not fitted or approximated. Because this simulation never
 * removes liquidity, the pool's reserves also grow from the fee each swap
 * leaves behind, which is why the realized IL reported here can come out
 * smaller in magnitude than the price move alone would suggest: fee income
 * is compounding into the same reserves IL is measured against.
 *
 * <p><b>Fee income.</b> Every swap pays a fixed 30bps fee (see
 * {@code ConstantProductPool.FEE_BPS}) on its input amount. This sums that
 * fee, converted into token1 terms at each swap's pre-trade spot price, as
 * a proxy for the spread a market maker holding the equivalent standing
 * quote would have captured over the same flow.
 */
public final class AmmLpAnalyzer {

    private static final MathContext MC = new MathContext(40, RoundingMode.HALF_UP);
    private static final BigDecimal FEE_FRACTION = new BigDecimal("0.0030"); // 30bps, matches ConstantProductPool

    private AmmLpAnalyzer() {
    }

    /**
     * The realized impermanent-loss fraction between the pool's state
     * before the first swap and its state after the last one.
     *
     * @throws IllegalArgumentException if {@code swaps} is empty
     */
    public static BigDecimal impermanentLossFraction(List<AmmSwapRow> swaps) {
        if (swaps.isEmpty()) {
            throw new IllegalArgumentException("swaps must not be empty");
        }
        AmmSwapRow first = swaps.get(0);
        AmmSwapRow last = swaps.get(swaps.size() - 1);

        BigDecimal p0 = spotPrice(first.reserve1Before(), first.reserve0Before());
        BigDecimal pt = spotPrice(last.reserve1After(), last.reserve0After());
        BigDecimal r = pt.divide(p0, MC);

        BigDecimal sqrtR = r.sqrt(MC);
        BigDecimal two = BigDecimal.valueOf(2);
        return two.multiply(sqrtR, MC).divide(r.add(BigDecimal.ONE, MC), MC).subtract(BigDecimal.ONE, MC);
    }

    /**
     * Cumulative fee income across every swap, converted to token1 terms at
     * each swap's pre-trade spot price, as a raw 18-decimal-scaled amount
     * (matching the CSV's own scale — divide by 1e18 for a human-readable
     * token1 quantity).
     */
    public static BigDecimal cumulativeFeeIncomeInToken1(List<AmmSwapRow> swaps) {
        BigDecimal total = BigDecimal.ZERO;
        for (AmmSwapRow swap : swaps) {
            BigDecimal amountIn = new BigDecimal(swap.amountIn());
            BigDecimal fee = amountIn.multiply(FEE_FRACTION, MC);
            if (swap.tokenInIsToken0()) {
                BigDecimal priceBefore = spotPrice(swap.reserve1Before(), swap.reserve0Before());
                total = total.add(fee.multiply(priceBefore, MC), MC);
            } else {
                total = total.add(fee, MC);
            }
        }
        return total;
    }

    /** token1 per token0, i.e. {@code reserve1 / reserve0}. */
    private static BigDecimal spotPrice(java.math.BigInteger reserve1, java.math.BigInteger reserve0) {
        return new BigDecimal(reserve1).divide(new BigDecimal(reserve0), MC);
    }
}
