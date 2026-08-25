package io.github.williamhuang1261.qrp.stats;

/**
 * Operations on an equity path.
 *
 * <p>Lives here rather than in the engine because a path built by resampling has
 * no trades behind it, and the drawdown of a simulated path must be computed the
 * same way as the drawdown of a real one for the comparison to mean anything.
 */
public final class EquityCurve {

    private EquityCurve() {
    }

    /** The deepest peak-to-trough decline, as a positive fraction of the peak. */
    public static double maxDrawdown(double[] equity) {
        if (equity.length == 0) {
            return Double.NaN;
        }
        double peak = equity[0];
        double worst = 0.0;
        for (double value : equity) {
            peak = Math.max(peak, value);
            worst = Math.max(worst, (peak - value) / peak);
        }
        return worst;
    }

    /** Compounds simple period returns into a path starting at {@code initial}. */
    public static double[] fromReturns(double initial, double[] returns) {
        double[] equity = new double[returns.length + 1];
        equity[0] = initial;
        for (int i = 0; i < returns.length; i++) {
            equity[i + 1] = equity[i] * (1.0 + returns[i]);
        }
        return equity;
    }

    /** Simple period returns of a path; length is {@code equity.length - 1}. */
    public static double[] toReturns(double[] equity) {
        if (equity.length < 2) {
            return new double[0];
        }
        double[] returns = new double[equity.length - 1];
        for (int i = 1; i < equity.length; i++) {
            returns[i - 1] = equity[i] / equity[i - 1] - 1.0;
        }
        return returns;
    }
}
