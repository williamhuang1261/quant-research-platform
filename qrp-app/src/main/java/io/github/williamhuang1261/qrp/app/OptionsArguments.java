package io.github.williamhuang1261.qrp.app;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Parsed {@code options} command arguments.
 *
 * <p>Kept separate from {@link CliArguments} rather than folded into it: that
 * record is the backtest's parameter surface, and gluing a second command's
 * unrelated flags onto it would make neither one easier to read.
 */
public record OptionsArguments(Path dataDirectory, String underlying, LocalDate valuationDate, Path exportCsv) {

    private static final String DEFAULT_DATA = "data/sample";
    private static final String DEFAULT_UNDERLYING = "SYNOPT";

    /**
     * The date {@code SyntheticChainGenerator} stamps the bundled sample chain
     * with; not a "today" default, since the sample data is a fixed snapshot.
     */
    private static final LocalDate DEFAULT_VALUATION_DATE = LocalDate.of(2026, 1, 2);

    public static String usageSuffix() {
        return """

                options options
                  --data <dir>          chain data directory        (default: data/sample)
                  --underlying <sym>    underlying symbol            (default: SYNOPT)
                  --date <yyyy-mm-dd>   valuation date               (default: 2026-01-02, the sample chain's snapshot)
                  --export <file>       write a dense (strike, expiry, vol) grid to CSV, for tools/plot_surface.py""";
    }

    /** @throws IllegalArgumentException with a usable message on any bad input */
    public static OptionsArguments parse(List<String> arguments) {
        Path data = Path.of(DEFAULT_DATA);
        String underlying = DEFAULT_UNDERLYING;
        LocalDate date = DEFAULT_VALUATION_DATE;
        Path exportCsv = null;

        for (int i = 0; i < arguments.size(); i++) {
            String flag = arguments.get(i);
            switch (flag) {
                case "--data" -> data = Path.of(value(arguments, ++i, flag));
                case "--underlying" -> underlying = value(arguments, ++i, flag);
                case "--date" -> date = parseDate(value(arguments, ++i, flag), flag);
                case "--export" -> exportCsv = Path.of(value(arguments, ++i, flag));
                default -> throw new IllegalArgumentException("unknown option: " + flag);
            }
        }
        return new OptionsArguments(data, underlying, date, exportCsv);
    }

    private static String value(List<String> arguments, int index, String flag) {
        if (index >= arguments.size()) {
            throw new IllegalArgumentException(flag + " needs a value");
        }
        return arguments.get(index);
    }

    private static LocalDate parseDate(String raw, String flag) {
        try {
            return LocalDate.parse(raw);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(flag + " expects yyyy-mm-dd, got: " + raw);
        }
    }
}
