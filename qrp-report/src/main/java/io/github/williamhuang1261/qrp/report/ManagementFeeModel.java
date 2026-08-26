package io.github.williamhuang1261.qrp.report;

/**
 * A flat annual management fee applied to an equity curve.
 *
 * <p>A real Canadian mutual fund's management expense ratio is neither flat
 * nor uniform across share classes, and often bundles a trailing commission
 * back to the seller of record. This model deliberately simplifies that to one
 * constant annual rate, so a comparison isolates the effect of a return-drag
 * fee from every other structural difference between funds. See the module
 * README for what this does not capture.
 *
 * @param annualRate the fee, e.g. {@code 0.02} for a 2% MER
 */
public record ManagementFeeModel(double annualRate) {

    public ManagementFeeModel {
        if (!(annualRate >= 0.0) || annualRate >= 1.0) {
            throw new IllegalArgumentException("annualRate must lie in [0, 1), got: " + annualRate);
        }
    }

    /** No fee: {@link #applyTo} reproduces its input exactly. */
    public static ManagementFeeModel none() {
        return new ManagementFeeModel(0.0);
    }

    /**
     * Applies this fee to a gross equity curve, compounding a per-period drag
     * derived from {@code periodsPerYear} so the annual rate is exact
     * regardless of bar frequency.
     *
     * @param grossEquity  equity before fees, aligned index-for-index with the bars
     * @param periodsPerYear bars per year for this series, e.g. from {@code Annualization}
     */
    public double[] applyTo(double[] grossEquity, double periodsPerYear) {
        // No-op short circuit: multiplying every step by a retention of exactly
        // 1.0 should be a no-op mathematically, but reconstructing each value
        // through division-then-multiplication accumulates floating-point
        // drift. A zero fee must reproduce its input bit for bit.
        if (grossEquity.length == 0 || annualRate == 0.0) {
            return grossEquity.clone();
        }
        double perPeriodRetention = Math.pow(1.0 - annualRate, 1.0 / periodsPerYear);
        double[] net = new double[grossEquity.length];
        net[0] = grossEquity[0];
        for (int i = 1; i < grossEquity.length; i++) {
            double grossPeriodReturn = grossEquity[i] / grossEquity[i - 1];
            net[i] = net[i - 1] * grossPeriodReturn * perPeriodRetention;
        }
        return net;
    }
}
