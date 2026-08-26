package io.github.williamhuang1261.qrp.options;

import java.util.List;
import java.util.TreeMap;

/**
 * A zero-coupon rate curve, linearly interpolated in the zero rate against
 * time, flat-extrapolated past either end.
 *
 * <p><b>The input is treated as zero rates even though a published Treasury
 * curve is par yields.</b> This is a stated simplification, not an oversight:
 * a real bootstrap strips coupon effects out of the par curve tenor by tenor,
 * and this platform does not build one. The gap between a par yield and the
 * corresponding zero rate is small at the front end -- close to exact under a
 * year -- and grows to tens of basis points by 20-30 years, where coupon
 * reinvestment has decades to compound. See {@code data/rates/README.md}.
 *
 * <p>Rates are continuously compounded throughout, matching
 * {@link BlackScholesInputs}: the same discount factor formula prices an
 * option and discounts a bond cash flow.
 */
public final class RatesCurve {

    private final double[] years;
    private final double[] zeroRates;

    private RatesCurve(double[] years, double[] zeroRates) {
        this.years = years;
        this.zeroRates = zeroRates;
    }

    /**
     * @param points tenor in years to zero rate as a decimal (5% is
     *               {@code 0.05}); at least two points, sorted internally
     */
    public static RatesCurve of(List<Point> points) {
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException(
                    "need at least 2 points to interpolate a curve, got: "
                            + (points == null ? 0 : points.size()));
        }
        TreeMap<Double, Double> byYear = new TreeMap<>();
        for (Point point : points) {
            if (!(point.years() > 0.0) || !Double.isFinite(point.years())) {
                throw new IllegalArgumentException(
                        "tenor must be positive and finite, got: " + point.years());
            }
            if (!Double.isFinite(point.zeroRate())) {
                throw new IllegalArgumentException(
                        "zero rate must be finite, got: " + point.zeroRate());
            }
            byYear.put(point.years(), point.zeroRate());
        }
        double[] years = byYear.keySet().stream().mapToDouble(Double::doubleValue).toArray();
        double[] rates = byYear.values().stream().mapToDouble(Double::doubleValue).toArray();
        return new RatesCurve(years, rates);
    }

    /** One tenor-rate pair, before interpolation. */
    public record Point(double years, double zeroRate) {
    }

    /**
     * The zero rate at an arbitrary tenor. Linear between the two bracketing
     * quoted tenors; flat beyond either end, since a rates desk's "no data past
     * here" answer is the last quoted point, not a guess.
     */
    public double zeroRate(double atYears) {
        if (!(atYears >= 0.0) || !Double.isFinite(atYears)) {
            throw new IllegalArgumentException("years must be non-negative and finite, got: " + atYears);
        }
        if (atYears <= years[0]) {
            return zeroRates[0];
        }
        if (atYears >= years[years.length - 1]) {
            return zeroRates[years.length - 1];
        }
        int upper = 1;
        while (years[upper] < atYears) {
            upper++;
        }
        int lower = upper - 1;
        double t = (atYears - years[lower]) / (years[upper] - years[lower]);
        return zeroRates[lower] + t * (zeroRates[upper] - zeroRates[lower]);
    }

    /** {@code e^{-r(T) * T}}, using the interpolated zero rate at {@code atYears}. */
    public double discountFactor(double atYears) {
        if (atYears == 0.0) {
            return 1.0;
        }
        return Math.exp(-zeroRate(atYears) * atYears);
    }

    /**
     * The continuously compounded forward rate over {@code [fromYears, toYears]},
     * implied by the two zero rates: {@code (r2*t2 - r1*t1) / (t2 - t1)}.
     */
    public double forwardRate(double fromYears, double toYears) {
        if (!(toYears > fromYears)) {
            throw new IllegalArgumentException(
                    "toYears must exceed fromYears, got from=" + fromYears + " to=" + toYears);
        }
        if (fromYears == 0.0) {
            return zeroRate(toYears);
        }
        double r1 = zeroRate(fromYears);
        double r2 = zeroRate(toYears);
        return (r2 * toYears - r1 * fromYears) / (toYears - fromYears);
    }

    /** The shortest and longest quoted tenors, for a report to state its range honestly. */
    public double shortestTenor() {
        return years[0];
    }

    public double longestTenor() {
        return years[years.length - 1];
    }

}
