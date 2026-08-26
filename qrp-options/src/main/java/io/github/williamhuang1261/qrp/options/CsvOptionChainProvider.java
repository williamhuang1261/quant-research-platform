package io.github.williamhuang1261.qrp.options;

import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.options.spi.OptionChainProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Reads a chain from the CSV format {@link SyntheticChainGenerator} writes.
 *
 * <p>A directory of one file per underlying, the same shape
 * {@code CsvMarketDataProvider} already uses for bars: {@code fromDirectory}
 * reads every {@code *_chain.csv} file present, so a private jar can drop a
 * second underlying's chain into the same directory without a line of change
 * here.
 *
 * <p>The {@code valuation_date} column is not read back into the quotes: a
 * provider serving a cached snapshot answers for whatever date it is asked,
 * the same way a live provider would. The column exists in the file purely as
 * a record of when the snapshot was taken.
 */
public final class CsvOptionChainProvider implements OptionChainProvider {

    private static final String FILE_SUFFIX = "_chain.csv";
    private static final String DEFAULT_DIRECTORY = "data/sample";
    private static final int EXPECTED_COLUMNS = 10;

    private final List<OptionChainQuote> quotes;

    private CsvOptionChainProvider(List<OptionChainQuote> quotes) {
        this.quotes = quotes;
    }

    /** Reads every {@code *_chain.csv} file in {@code directory}. */
    public static CsvOptionChainProvider fromDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("not a directory: " + directory.toAbsolutePath());
        }
        List<OptionChainQuote> quotes = new ArrayList<>();
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                    .sorted()
                    .forEach(path -> quotes.addAll(parse(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory.toAbsolutePath(), e);
        }
        if (quotes.isEmpty()) {
            throw new IllegalArgumentException(
                    "no *" + FILE_SUFFIX + " files found in " + directory.toAbsolutePath());
        }
        return new CsvOptionChainProvider(quotes);
    }

    /** The default sample directory, {@code data/sample}; used by {@link java.util.ServiceLoader} discovery. */
    public CsvOptionChainProvider() {
        this(fromDirectory(Path.of(DEFAULT_DIRECTORY)).quotes);
    }

    private static List<OptionChainQuote> parse(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file.toAbsolutePath(), e);
        }

        List<OptionChainQuote> parsed = new ArrayList<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",");
            if (columns.length != EXPECTED_COLUMNS) {
                throw new IllegalArgumentException(location(file, lineNumber) + ": expected "
                        + EXPECTED_COLUMNS + " columns, got " + columns.length + ": " + line);
            }
            try {
                Instrument underlying = Instrument.equity(columns[0]);
                LocalDate expiry = LocalDate.parse(columns[2]);
                double strike = Double.parseDouble(columns[3]);
                OptionType type = OptionType.valueOf(columns[4]);
                ExerciseStyle style = ExerciseStyle.valueOf(columns[5]);
                double spot = Double.parseDouble(columns[6]);
                double marketPrice = Double.parseDouble(columns[7]);
                double riskFreeRate = Double.parseDouble(columns[8]);
                double dividendYield = Double.parseDouble(columns[9]);

                OptionContract contract = new OptionContract(underlying, type, style, strike, expiry, 100.0);
                parsed.add(new OptionChainQuote(contract, spot, marketPrice, riskFreeRate, dividendYield));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(location(file, lineNumber) + ": " + line, e);
            }
        }
        return parsed;
    }

    private static String location(Path file, int lineNumber) {
        return file.getFileName() + ":" + (lineNumber + 1);
    }

    @Override
    public String id() {
        return "csv-synthetic";
    }

    @Override
    public List<String> availableUnderlyings() {
        return quotes.stream().map(q -> q.contract().underlying().symbol()).distinct().toList();
    }

    @Override
    public List<OptionChainQuote> chain(String underlyingSymbol, LocalDate valuationDate) {
        Objects.requireNonNull(underlyingSymbol, "underlyingSymbol");
        List<OptionChainQuote> matches = quotes.stream()
                .filter(q -> q.contract().underlying().symbol().equalsIgnoreCase(underlyingSymbol))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "unknown underlying '%s'; available: %s",
                            underlyingSymbol, availableUnderlyings()));
        }
        return matches;
    }
}
