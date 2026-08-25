package io.github.williamhuang1261.qrp.data;

import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.core.Timeframe;
import io.github.williamhuang1261.qrp.core.spi.MarketDataProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads bars from a directory of CSV files described by a manifest.
 *
 * <p>Layout:
 * <pre>
 * &lt;root&gt;/instruments.csv          symbol,currency,asset_class,timeframe,file
 * &lt;root&gt;/SYNA_1d.csv              timestamp,open,high,low,close,volume
 * </pre>
 *
 * <p>The manifest exists because a price file cannot say what it is: currency
 * and asset class are not in the rows, and guessing them from a file name is how
 * a USD series ends up priced in euros. It also makes {@link #available()} a
 * cheap directory read instead of a parse of every series.
 *
 * <p>Every parse failure names the file and the line, because the first thing
 * anyone asks about a rejected data set is <em>which row</em>.
 */
public final class CsvMarketDataProvider implements MarketDataProvider {

    static final String MANIFEST_FILE = "instruments.csv";
    private static final String MANIFEST_HEADER = "symbol,currency,asset_class,timeframe,file";
    private static final String SERIES_HEADER = "timestamp,open,high,low,close,volume";

    private final Path root;
    private final Map<SeriesKey, Entry> entries;
    private final List<Instrument> instruments;

    private CsvMarketDataProvider(Path root, Map<SeriesKey, Entry> entries, List<Instrument> instruments) {
        this.root = root;
        this.entries = entries;
        this.instruments = instruments;
    }

    /**
     * Reads the manifest eagerly, so a broken data directory fails at startup
     * rather than on the first backtest that touches the missing series.
     */
    public static CsvMarketDataProvider ofDirectory(Path root) {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            throw new MarketDataException("not a directory: " + root.toAbsolutePath());
        }
        Path manifest = root.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifest)) {
            throw new MarketDataException("missing " + MANIFEST_FILE + " in " + root.toAbsolutePath());
        }

        Map<SeriesKey, Entry> entries = new LinkedHashMap<>();
        List<Instrument> instruments = new ArrayList<>();
        List<String> lines = readAllLines(manifest);
        requireHeader(manifest, lines, MANIFEST_HEADER);

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            String[] cells = split(manifest, lineNumber, line, 5);
            try {
                Instrument instrument = new Instrument(
                        cells[0], cells[1], AssetClass.valueOf(cells[2].trim().toUpperCase()));
                Timeframe timeframe = Timeframe.fromId(cells[3].trim());
                Path file = root.resolve(cells[4].trim());
                if (!Files.isRegularFile(file)) {
                    throw new MarketDataException(
                            location(manifest, lineNumber) + " references a missing file: " + file.getFileName());
                }
                SeriesKey key = new SeriesKey(instrument.symbol(), timeframe);
                if (entries.put(key, new Entry(instrument, file)) != null) {
                    throw new MarketDataException(
                            location(manifest, lineNumber) + " duplicates " + key);
                }
                if (!instruments.contains(instrument)) {
                    instruments.add(instrument);
                }
            } catch (IllegalArgumentException e) {
                throw new MarketDataException(location(manifest, lineNumber) + " " + e.getMessage(), e);
            }
        }
        return new CsvMarketDataProvider(
                root, java.util.Collections.unmodifiableMap(entries), List.copyOf(instruments));
    }

    @Override
    public String id() {
        return "csv";
    }

    @Override
    public List<Instrument> available() {
        return instruments;
    }

    /** Timeframes the manifest offers for an instrument, in manifest order. */
    public List<Timeframe> timeframesFor(Instrument instrument) {
        Objects.requireNonNull(instrument, "instrument");
        return entries.keySet().stream()
                .filter(key -> key.symbol().equals(instrument.symbol()))
                .map(SeriesKey::timeframe)
                .toList();
    }

    /** Every bar on file, for callers that do not want to name a range. */
    public BarSeries loadAll(Instrument instrument, Timeframe timeframe) {
        return load(instrument, timeframe, Instant.MIN, Instant.MAX);
    }

    /**
     * @param from inclusive, {@code to} exclusive
     * @throws MarketDataException if the series is unknown or any row is unusable
     */
    @Override
    public BarSeries load(Instrument instrument, Timeframe timeframe, Instant from, Instant to) {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(timeframe, "timeframe");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new MarketDataException("from (" + from + ") is after to (" + to + ")");
        }

        Entry entry = entries.get(new SeriesKey(instrument.symbol(), timeframe));
        if (entry == null) {
            throw new MarketDataException(
                    "no series for " + instrument + " " + timeframe.id() + " in " + root.toAbsolutePath()
                            + "; available: " + entries.keySet());
        }
        // Series are keyed by symbol, so a caller who assumed the wrong currency or
        // asset class is told which one the manifest declares rather than silently
        // receiving bars labelled with a different instrument than they asked for.
        if (!entry.instrument().equals(instrument)) {
            throw new MarketDataException("manifest declares " + instrument.symbol() + " as "
                    + entry.instrument().assetClass() + "/" + entry.instrument().currency()
                    + ", requested " + instrument.assetClass() + "/" + instrument.currency());
        }
        Path file = entry.file();

        List<String> lines = readAllLines(file);
        requireHeader(file, lines, SERIES_HEADER);

        List<Bar> bars = new ArrayList<>(Math.max(16, lines.size() - 1));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            Bar bar = parseBar(file, i + 1, line);
            if (!bar.timestamp().isBefore(from) && bar.timestamp().isBefore(to)) {
                bars.add(bar);
            }
        }

        try {
            return BarSeries.of(instrument, timeframe, bars);
        } catch (IllegalArgumentException e) {
            // Ordering is checked by BarSeries; re-report it against the file.
            throw new MarketDataException(file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static Bar parseBar(Path file, int lineNumber, String line) {
        String[] cells = split(file, lineNumber, line, 6);
        try {
            return new Bar(
                    Instant.parse(cells[0].trim()),
                    Double.parseDouble(cells[1].trim()),
                    Double.parseDouble(cells[2].trim()),
                    Double.parseDouble(cells[3].trim()),
                    Double.parseDouble(cells[4].trim()),
                    Long.parseLong(cells[5].trim()));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            throw new MarketDataException(location(file, lineNumber) + " " + e.getMessage(), e);
        }
    }

    private static String[] split(Path file, int lineNumber, String line, int expectedColumns) {
        String[] cells = line.split(",", -1);
        if (cells.length != expectedColumns) {
            throw new MarketDataException(location(file, lineNumber) + " expected " + expectedColumns
                    + " columns, found " + cells.length);
        }
        return cells;
    }

    private static void requireHeader(Path file, List<String> lines, String expected) {
        if (lines.isEmpty()) {
            throw new MarketDataException(file.getFileName() + " is empty");
        }
        String header = lines.get(0).replace(" ", "").toLowerCase();
        if (!header.equals(expected)) {
            throw new MarketDataException(
                    location(file, 1) + " expected header '" + expected + "', found '" + lines.get(0) + "'");
        }
    }

    private static List<String> readAllLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file.toAbsolutePath(), e);
        }
    }

    private static String location(Path file, int lineNumber) {
        return file.getFileName() + ":" + lineNumber;
    }

    /** Series are identified by symbol and timeframe; the manifest owns the rest. */
    private record SeriesKey(String symbol, Timeframe timeframe) {
        @Override
        public String toString() {
            return symbol + " " + timeframe.id();
        }
    }

    private record Entry(Instrument instrument, Path file) {
    }
}
