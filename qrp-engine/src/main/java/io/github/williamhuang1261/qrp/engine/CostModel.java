package io.github.williamhuang1261.qrp.engine;

/**
 * What a fill costs: commission and the price concession paid for demanding
 * liquidity.
 *
 * <p>Both are expressed against the traded notional rather than per share, so a
 * cost model stays meaningful across instruments priced at $4 and $400.
 * Slippage always moves against the trade: buys fill above the reference price,
 * sells below it. A backtest without this line item makes every high-turnover
 * strategy look profitable, which is the single most common way a research
 * result fails to survive contact with a broker.
 *
 * @param commissionBps    commission in basis points of notional
 * @param fixedCommission  currency amount charged per fill, e.g. a ticket fee
 * @param slippageBps      price concession in basis points, applied against the trade
 */
public record CostModel(double commissionBps, double fixedCommission, double slippageBps) {

    public CostModel {
        requireNonNegative(commissionBps, "commissionBps");
        requireNonNegative(fixedCommission, "fixedCommission");
        requireNonNegative(slippageBps, "slippageBps");
    }

    /** No costs at all: useful to isolate whether a result comes from the signal. */
    public static CostModel none() {
        return new CostModel(0.0, 0.0, 0.0);
    }

    /** A deliberately unflattering default: 1 bp commission, $1 a ticket, 2 bps of slippage. */
    public static CostModel retail() {
        return new CostModel(1.0, 1.0, 2.0);
    }

    /** @param buying true for a purchase, where the concession is paid upwards */
    public double fillPrice(double referencePrice, boolean buying) {
        double concession = referencePrice * slippageBps / 10_000.0;
        return buying ? referencePrice + concession : referencePrice - concession;
    }

    public double commission(double notional) {
        return Math.abs(notional) * commissionBps / 10_000.0 + fixedCommission;
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative, got: " + value);
        }
    }
}
