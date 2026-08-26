package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.options.OptionChainQuote;
import io.github.williamhuang1261.qrp.options.VolatilitySurface;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Writes a dense (strike, years, implied vol) grid for {@code tools/plot_surface.py}.
 *
 * <p>The chain itself is coarse -- a handful of strikes per expiry, real listed
 * chains are no denser -- so this samples {@link VolatilitySurface} at 40 strikes
 * per expiry rather than exporting the chain's own points, giving the plot a
 * smooth curve to draw instead of nine dots per expiry.
 */
final class SurfaceGridExporter {

    private static final int STRIKES_PER_EXPIRY = 40;

    private SurfaceGridExporter() {
    }

    static void writeCsv(OptionsRunner.Outcome outcome, Path csvFile) {
        List<OptionChainQuote> chain = outcome.chain();
        LocalDate valuationDate = outcome.valuationDate();
        VolatilitySurface surface = outcome.surface();

        double minStrike = chain.stream().mapToDouble(q -> q.contract().strike()).min().orElseThrow();
        double maxStrike = chain.stream().mapToDouble(q -> q.contract().strike()).max().orElseThrow();
        List<LocalDate> expiries = chain.stream().map(q -> q.contract().expiry()).distinct().sorted().toList();

        StringBuilder csv = new StringBuilder("expiry,years,strike,implied_vol\n");
        for (LocalDate expiry : expiries) {
            double years = java.time.temporal.ChronoUnit.DAYS.between(valuationDate, expiry) / 365.0;
            for (int i = 0; i < STRIKES_PER_EXPIRY; i++) {
                double t = i / (double) (STRIKES_PER_EXPIRY - 1);
                double strike = minStrike + t * (maxStrike - minStrike);
                try {
                    double iv = surface.impliedVolatility(strike, years);
                    csv.append(String.format(Locale.ROOT, "%s,%.6f,%.4f,%.6f%n", expiry, years, strike, iv));
                } catch (IllegalArgumentException e) {
                    // Past the surface's own no-extrapolation bound at this expiry; skip the point.
                }
            }
        }

        try {
            Files.writeString(csvFile, csv.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + csvFile.toAbsolutePath(), e);
        }
    }
}
