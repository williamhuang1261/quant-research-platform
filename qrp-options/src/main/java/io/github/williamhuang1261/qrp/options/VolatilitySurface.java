package io.github.williamhuang1261.qrp.options;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implied volatility as a function of strike and time to expiry, built from a
 * chain of market quotes.
 *
 * <p>Interpolated in <b>total variance</b>, {@code w = sigma^2 T}, rather than
 * in volatility itself. Two reasons: total variance is what a forward-starting
 * or calendar-spread argument is actually about, so a surface that keeps it
 * monotone in time is the one that supports a calendar no-arbitrage check
 * downstream; and it linearizes better across expiries than volatility does,
 * because variance -- not its square root -- is what accumulates over time
 * under the model's own assumptions.
 *
 * <p>Interpolation is two-stage: within an expiry slice, linear in
 * log-moneyness {@code k = ln(K/F)} against total variance; across expiries,
 * linear in calendar time between the two bracketing slices' interpolated
 * variance at that {@code k}. No extrapolation: a query outside the quoted
 * strike or time range fails loudly rather than guessing.
 */
public final class VolatilitySurface {

    /** One expiry's quotes, sorted by log-moneyness, with the forward that defines it. */
    private record Slice(double years, double forward, double[] logMoneyness, double[] totalVariance) {
    }

    private final List<Slice> slices;

    private VolatilitySurface(List<Slice> slices) {
        this.slices = slices;
    }

    /**
     * Builds the surface from a chain of quotes, solving each one's implied
     * volatility along the way.
     *
     * @throws IllegalArgumentException if fewer than two distinct expiries are
     *         present, or any expiry has fewer than two distinct strikes
     */
    public static VolatilitySurface build(List<OptionChainQuote> quotes, java.time.LocalDate valuationDate) {
        if (quotes == null || quotes.isEmpty()) {
            throw new IllegalArgumentException("quotes must not be null or empty");
        }

        Map<Double, List<OptionChainQuote>> byExpiry = new TreeMap<>();
        for (OptionChainQuote quote : quotes) {
            double years = quote.contract().yearsTo(valuationDate);
            byExpiry.computeIfAbsent(years, ignored -> new ArrayList<>()).add(quote);
        }
        if (byExpiry.size() < 2) {
            throw new IllegalArgumentException(
                    "need quotes at 2+ distinct expiries to interpolate across time, got: " + byExpiry.size());
        }

        List<Slice> slices = new ArrayList<>();
        for (Map.Entry<Double, List<OptionChainQuote>> entry : byExpiry.entrySet()) {
            slices.add(buildSlice(entry.getKey(), entry.getValue()));
        }
        return new VolatilitySurface(slices);
    }

    private static Slice buildSlice(double years, List<OptionChainQuote> quotes) {
        // A forward-consistent smile needs one forward per slice; every quote at
        // this expiry is required to agree on spot and rates, which a chain
        // snapshot always does since it is priced from one valuation moment.
        OptionChainQuote first = quotes.get(0);
        double forward = first.underlyingPrice()
                * Math.exp((first.riskFreeRate() - first.dividendYield()) * years);

        record Point(double logMoneyness, double totalVariance) {
        }
        List<Point> points = new ArrayList<>();
        for (OptionChainQuote quote : quotes) {
            BlackScholesInputs contractInputs = new BlackScholesInputs(
                    quote.underlyingPrice(), quote.contract().strike(), years, 0.2,
                    quote.riskFreeRate(), quote.dividendYield());
            double iv = ImpliedVolatility.solve(quote.contract().type(), contractInputs, quote.marketPrice());
            double k = Math.log(quote.contract().strike() / forward);
            points.add(new Point(k, iv * iv * years));
        }
        points.sort(Comparator.comparingDouble(Point::logMoneyness));

        // De-duplicate exact strikes (a call and a put at the same strike both
        // land here once put-call parity is respected); keep the first.
        List<Point> distinct = new ArrayList<>();
        for (Point point : points) {
            if (distinct.isEmpty() || distinct.get(distinct.size() - 1).logMoneyness() != point.logMoneyness()) {
                distinct.add(point);
            }
        }
        if (distinct.size() < 2) {
            throw new IllegalArgumentException(
                    "need 2+ distinct strikes at years=" + years + " to interpolate across moneyness, got: "
                            + distinct.size());
        }

        double[] k = new double[distinct.size()];
        double[] w = new double[distinct.size()];
        for (int i = 0; i < distinct.size(); i++) {
            k[i] = distinct.get(i).logMoneyness();
            w[i] = distinct.get(i).totalVariance();
        }
        return new Slice(years, forward, k, w);
    }

    /**
     * Interpolated implied volatility at an arbitrary strike and time to expiry.
     *
     * @throws IllegalArgumentException if {@code years} or the implied
     *         log-moneyness fall outside what the chain quoted; this surface
     *         does not extrapolate
     */
    public double impliedVolatility(double strike, double years) {
        if (!(strike > 0.0) || !Double.isFinite(strike)) {
            throw new IllegalArgumentException("strike must be positive and finite, got: " + strike);
        }
        if (!(years > 0.0) || !Double.isFinite(years)) {
            throw new IllegalArgumentException("years must be positive and finite, got: " + years);
        }

        double minYears = slices.get(0).years();
        double maxYears = slices.get(slices.size() - 1).years();
        if (years < minYears || years > maxYears) {
            throw new IllegalArgumentException(
                    "years=" + years + " outside the quoted range [" + minYears + ", " + maxYears + "]");
        }

        int upperIndex = 0;
        while (upperIndex < slices.size() && slices.get(upperIndex).years() < years) {
            upperIndex++;
        }
        Slice upper = slices.get(Math.min(upperIndex, slices.size() - 1));
        if (upper.years() == years) {
            return totalVarianceToVol(varianceWithinSlice(upper, strike), years);
        }
        Slice lower = slices.get(upperIndex - 1);

        double varianceLower = varianceWithinSlice(lower, strike);
        double varianceUpper = varianceWithinSlice(upper, strike);
        double t = (years - lower.years()) / (upper.years() - lower.years());
        double variance = varianceLower + t * (varianceUpper - varianceLower);

        return totalVarianceToVol(variance, years);
    }

    private static double varianceWithinSlice(Slice slice, double strike) {
        double k = Math.log(strike / slice.forward());
        double[] moneyness = slice.logMoneyness();
        double[] variance = slice.totalVariance();

        if (k <= moneyness[0]) {
            requireWithinTolerance(k, moneyness[0], slice.years());
            return variance[0];
        }
        if (k >= moneyness[moneyness.length - 1]) {
            requireWithinTolerance(k, moneyness[moneyness.length - 1], slice.years());
            return variance[moneyness.length - 1];
        }
        for (int i = 1; i < moneyness.length; i++) {
            if (k <= moneyness[i]) {
                double t = (k - moneyness[i - 1]) / (moneyness[i] - moneyness[i - 1]);
                return variance[i - 1] + t * (variance[i] - variance[i - 1]);
            }
        }
        throw new AssertionError("unreachable: k=" + k + " bracketed by the loop's own guard above");
    }

    /**
     * A query slightly beyond the quoted strikes is clamped to the edge rather
     * than rejected -- a strike a cent past the widest quote is a rounding
     * question, not a request to extrapolate. Anything further is refused.
     */
    private static void requireWithinTolerance(double k, double edge, double years) {
        double tolerance = 0.02;
        if (Math.abs(k - edge) > tolerance) {
            throw new IllegalArgumentException(
                    "log-moneyness " + k + " is outside the quoted strikes (nearest edge " + edge
                            + ") at years=" + years + "; this surface does not extrapolate");
        }
    }

    private static double totalVarianceToVol(double totalVariance, double years) {
        if (totalVariance < 0.0) {
            throw new IllegalStateException(
                    "interpolated total variance went negative (" + totalVariance
                            + ") at years=" + years + "; this is a calendar arbitrage in the input quotes");
        }
        return Math.sqrt(totalVariance / years);
    }
}
