package io.github.williamhuang1261.qrp.equity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Peer trailing price-to-earnings multiples for a covered ticker, as written
 * by {@code tools/fetch_equity_fundamentals.py}: real peer companies, real
 * multiples at the same fetch date as {@link EquityFundamentals}.
 */
public final class PeerComps {

    private static final int EXPECTED_COLUMNS = 3;

    private PeerComps() {
    }

    /**
     * Reads the {@code ticker,peer_ticker,peer_trailing_pe} format, keyed by
     * the covered ticker, each value the list of its peers' trailing P/E.
     *
     * @throws IllegalArgumentException if the file is malformed
     */
    public static Map<String, List<Double>> load(Path csvFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + csvFile.toAbsolutePath(), e);
        }

        Map<String, List<Double>> byTicker = new HashMap<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] c = line.split(",");
            if (c.length != EXPECTED_COLUMNS) {
                throw new IllegalArgumentException(
                        location(csvFile, lineNumber) + ": expected " + EXPECTED_COLUMNS
                                + " columns, got " + c.length + ": " + line);
            }
            try {
                double peerPe = Double.parseDouble(c[2]);
                byTicker.computeIfAbsent(c[0], k -> new ArrayList<>()).add(peerPe);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(location(csvFile, lineNumber) + ": " + line, e);
            }
        }
        if (byTicker.isEmpty()) {
            throw new IllegalArgumentException("no data rows in " + csvFile.toAbsolutePath());
        }
        return byTicker;
    }

    /**
     * Reads the newest (lexicographically greatest, since filenames are
     * {@code comps_yyyy-mm-dd.csv}) snapshot in {@code directory}.
     */
    public static Map<String, List<Double>> loadLatest(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("not a directory: " + directory.toAbsolutePath());
        }
        try (var files = Files.list(directory)) {
            Path newest = files
                    .filter(path -> path.getFileName().toString().startsWith("comps_")
                            && path.getFileName().toString().endsWith(".csv"))
                    .max(java.util.Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "no comps_*.csv files found in " + directory.toAbsolutePath()));
            return load(newest);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory.toAbsolutePath(), e);
        }
    }

    private static String location(Path file, int lineNumber) {
        return String.format(Locale.ROOT, "%s:%d", file.getFileName(), lineNumber + 1);
    }
}
