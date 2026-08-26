package io.github.williamhuang1261.qrp.options;

/**
 * The five first- and second-order sensitivities, in <b>per-unit</b> terms.
 *
 * <p>Units are the thing that goes wrong here, so they are stated rather than
 * implied. Every figure is per one unit of the underlying, before any contract
 * multiplier, and with respect to a move of <b>1.0</b> in the input:
 *
 * <ul>
 *   <li>{@code delta} — per 1.0 of underlying price. Dimensionless.</li>
 *   <li>{@code gamma} — delta per 1.0 of underlying price.</li>
 *   <li>{@code vega}  — per <b>1.00</b> of volatility, i.e. 100 vol points.
 *       A desk quotes vega per point; {@link #vegaPerVolPoint()} does that
 *       division rather than leaving it to the caller to remember.</li>
 *   <li>{@code theta} — per <b>year</b>, and signed as a time derivative, so a
 *       long option that decays has a negative theta.
 *       {@link #thetaPerCalendarDay()} converts.</li>
 *   <li>{@code rho}   — per 1.00 of the risk-free rate, holding the dividend
 *       yield fixed. That convention is why {@link BlackScholesInputs} takes
 *       {@code q} rather than carry.</li>
 * </ul>
 *
 * <p>Reporting vega per 1.00 and theta per year keeps the record in the same
 * units the formulas produce, so a finite-difference check compares like with
 * like. The convenience accessors exist for reports, not for the maths.
 */
public record Greeks(double delta, double gamma, double vega, double theta, double rho) {

    private static final double DAYS_PER_YEAR = 365.0;
    private static final double VOL_POINTS = 100.0;

    /** Vega per one volatility point (a move of 0.01), the desk convention. */
    public double vegaPerVolPoint() {
        return vega / VOL_POINTS;
    }

    /** Theta per calendar day, negative for a decaying long position. */
    public double thetaPerCalendarDay() {
        return theta / DAYS_PER_YEAR;
    }

    /** Rho per one basis point of rate move. */
    public double rhoPerBasisPoint() {
        return rho / 10_000.0;
    }

    /** Every figure scaled by a contract multiplier and a position size. */
    public Greeks scaled(double factor) {
        if (!Double.isFinite(factor)) {
            throw new IllegalArgumentException("factor must be finite, got: " + factor);
        }
        return new Greeks(
                delta * factor, gamma * factor, vega * factor, theta * factor, rho * factor);
    }
}
