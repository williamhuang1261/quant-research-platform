package io.github.williamhuang1261.qrp.indicators;

/**
 * Return on equity split into the three factors of the DuPont identity:
 * {@code ROE = net margin x asset turnover x equity multiplier}.
 *
 * <p>Deliberately <strong>not</strong> an {@code Indicator}. The SPI maps a bar
 * series to one value per bar, and this is a function of a financial statement,
 * not of prices. Bending it into that contract would mean inventing a per-bar
 * value the data does not have, which is exactly the kind of quiet fabrication
 * a research platform should not make easy.
 *
 * <p>{@link #returnOnEquity()} reconstructs ROE from the factors, and
 * {@link #reconciles(double)} asserts it equals the direct
 * {@code netIncome / equity}. Publishing a decomposition without checking it
 * multiplies back is how a sign error survives a quarterly review.
 */
public record DuPontDecomposition(
        double netProfitMargin, double assetTurnover, double equityMultiplier) {

    /** Relative tolerance for the reconstruction check. */
    private static final double TOLERANCE = 1e-9;

    /**
     * @throws IllegalArgumentException if revenue, assets or equity is non-positive.
     *         Negative equity makes ROE meaningless rather than merely negative,
     *         so it is refused instead of returned as a number someone might plot.
     */
    public static DuPontDecomposition of(
            double netIncome, double revenue, double totalAssets, double totalEquity) {
        requirePositive(revenue, "revenue");
        requirePositive(totalAssets, "totalAssets");
        requirePositive(totalEquity, "totalEquity");
        if (!Double.isFinite(netIncome)) {
            throw new IllegalArgumentException("netIncome must be finite, got: " + netIncome);
        }
        return new DuPontDecomposition(
                netIncome / revenue,
                revenue / totalAssets,
                totalAssets / totalEquity);
    }

    public double returnOnEquity() {
        return netProfitMargin * assetTurnover * equityMultiplier;
    }

    /** True when the factors multiply back to the directly computed ROE. */
    public boolean reconciles(double directReturnOnEquity) {
        double reconstructed = returnOnEquity();
        double scale = Math.max(1.0, Math.abs(directReturnOnEquity));
        return Math.abs(reconstructed - directReturnOnEquity) <= TOLERANCE * scale;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive, got: " + value);
        }
    }
}
