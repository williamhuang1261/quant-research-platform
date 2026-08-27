package io.github.williamhuang1261.qrp.portfolio;

/**
 * Shared constraint projections used by every {@link PortfolioOptimizer}
 * implementation, so the box/leverage/turnover contract in {@link
 * PortfolioConstraints} is honored identically no matter which optimizer
 * produced the raw weights.
 *
 * <p>Extracted from {@link MeanVarianceOptimizer} once a second optimizer
 * ({@link EqualRiskContributionOptimizer}) needed the exact same two
 * projections: both take an unconstrained-update vector, project it onto the
 * capped simplex (box {@code [0, maxWeight]} jointly with the leverage
 * target), then clip the move to the turnover cap around the previous
 * weights.
 */
final class PortfolioProjections {

    private static final int PROJECTION_BISECTION_STEPS = 100;

    private PortfolioProjections() {}

    /**
     * Projects {@code w} onto {@code {x : 0 <= x_i <= maxWeight, sum(x) ==
     * leverage}} (the "capped simplex"). Implemented by bisecting on a shared
     * threshold {@code tau} such that {@code x_i = clamp(w_i - tau, 0, maxWeight)}
     * sums to {@code leverage}; {@code f(tau) = sum(x_i)} is non-increasing in
     * {@code tau}, so bisection converges to machine precision.
     */
    static double[] projectOntoCappedSimplex(double[] w, double maxWeight, double leverage) {
        int n = w.length;
        double lo = minValue(w) - maxWeight;
        double hi = maxValue(w);

        double[] x = new double[n];
        for (int step = 0; step < PROJECTION_BISECTION_STEPS; step++) {
            double tau = 0.5 * (lo + hi);
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                x[i] = clamp(w[i] - tau, 0.0, maxWeight);
                sum += x[i];
            }
            if (sum > leverage) {
                lo = tau;
            } else {
                hi = tau;
            }
        }
        double tau = 0.5 * (lo + hi);
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            x[i] = clamp(w[i] - tau, 0.0, maxWeight);
            sum += x[i];
        }
        // Bisection leaves a tiny residual; distribute it proportionally to
        // headroom so the sum matches leverage to machine precision without
        // breaking the box bounds.
        double residual = leverage - sum;
        if (Math.abs(residual) > 1e-12) {
            double headroom = 0.0;
            double[] room = new double[n];
            for (int i = 0; i < n; i++) {
                room[i] = residual > 0 ? maxWeight - x[i] : x[i];
                headroom += room[i];
            }
            if (headroom > 1e-12) {
                for (int i = 0; i < n; i++) {
                    x[i] += residual * (room[i] / headroom);
                }
            }
        }
        return x;
    }

    /**
     * Projects {@code w} onto the L1 ball of radius {@code maxTurnover}
     * centered at {@code previousWeights}: if the total absolute move already
     * fits, {@code w} is returned unchanged; otherwise the move is scaled
     * back toward {@code previousWeights} by the ratio that makes it fit
     * exactly.
     */
    static double[] clipToTurnoverBall(double[] w, double[] previousWeights, double maxTurnover) {
        int n = w.length;
        double totalAbsDelta = 0.0;
        double[] delta = new double[n];
        for (int i = 0; i < n; i++) {
            delta[i] = w[i] - previousWeights[i];
            totalAbsDelta += Math.abs(delta[i]);
        }
        if (totalAbsDelta <= maxTurnover || totalAbsDelta < 1e-15) {
            return w;
        }
        double scale = maxTurnover / totalAbsDelta;
        double[] clipped = new double[n];
        for (int i = 0; i < n; i++) {
            clipped[i] = previousWeights[i] + delta[i] * scale;
        }
        return clipped;
    }

    private static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    private static double minValue(double[] values) {
        double min = Double.POSITIVE_INFINITY;
        for (double v : values) {
            min = Math.min(min, v);
        }
        return min;
    }

    private static double maxValue(double[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            max = Math.max(max, v);
        }
        return max;
    }
}
