package io.github.williamhuang1261.qrp.realassets;

/**
 * The income approach's simplest form: a single year's net operating income
 * capitalized at a market cap rate, {@code value = NOI / capRate}.
 *
 * <p>Direct capitalization treats next year's NOI as a perpetuity at a flat
 * rate -- it has no explicit holding period, no NOI growth path, and no exit
 * assumption of its own. {@link DcfValuation} is the same platform's
 * multi-year alternative when those assumptions matter enough to model
 * explicitly.
 */
public final class DirectCapValuation {

    private DirectCapValuation() {
    }

    /**
     * @param noi     a single year's net operating income (typically the
     *                next twelve months', i.e. a forward NOI)
     * @param capRate the market capitalization rate, as a decimal (6.5% is
     *                {@code 0.065})
     */
    public static double value(double noi, double capRate) {
        if (!Double.isFinite(noi)) {
            throw new IllegalArgumentException("noi must be finite, got: " + noi);
        }
        if (!(capRate > 0.0) || !Double.isFinite(capRate)) {
            throw new IllegalArgumentException("capRate must be positive and finite, got: " + capRate);
        }
        return noi / capRate;
    }
}
