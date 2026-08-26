package io.github.williamhuang1261.qrp.app;

import io.github.williamhuang1261.qrp.options.CsvOptionChainProvider;
import io.github.williamhuang1261.qrp.options.NoArbitrageDiagnostics;
import io.github.williamhuang1261.qrp.options.OptionChainQuote;
import io.github.williamhuang1261.qrp.options.VolatilitySurface;
import java.time.LocalDate;
import java.util.List;

/**
 * Loads a chain, fits its surface and runs the diagnostics, in one place so the
 * CLI does not duplicate this ordering.
 */
public final class OptionsRunner {

    /** One completed analysis: the raw chain, its fitted surface, and the diagnostics run against both. */
    public record Outcome(
            String underlying,
            LocalDate valuationDate,
            List<OptionChainQuote> chain,
            VolatilitySurface surface,
            NoArbitrageDiagnostics.Report diagnostics) {
    }

    private OptionsRunner() {
    }

    public static Outcome run(OptionsArguments arguments) {
        CsvOptionChainProvider provider = CsvOptionChainProvider.fromDirectory(arguments.dataDirectory());
        List<OptionChainQuote> chain = provider.chain(arguments.underlying(), arguments.valuationDate());

        VolatilitySurface surface = VolatilitySurface.build(chain, arguments.valuationDate());
        NoArbitrageDiagnostics.Report diagnostics =
                NoArbitrageDiagnostics.check(chain, surface, arguments.valuationDate());

        return new Outcome(arguments.underlying(), arguments.valuationDate(), chain, surface, diagnostics);
    }
}
