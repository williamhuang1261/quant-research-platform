package io.github.williamhuang1261.qrp.options;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the {@code tenor_years,yield_pct,tenor_label} format
 * {@code tools/fetch_ust_curve.py} writes.
 *
 * <p>Yields on disk are in percent ({@code 4.64} means 4.64%), matching how
 * Treasury.gov itself publishes them; {@link RatesCurve} takes decimals, so the
 * conversion happens once, here, rather than at every call site.
 */
public final class TreasuryCurveLoader {

    private static final int EXPECTED_COLUMNS = 3;

    private TreasuryCurveLoader() {
    }

    /**
     * @param csvFile a file in the {@code data/rates/*.csv} format
     * @throws IllegalArgumentException if the file is malformed
     */
    public static RatesCurve load(Path csvFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + csvFile.toAbsolutePath(), e);
        }

        List<RatesCurve.Point> points = new ArrayList<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber).strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split(",");
            if (columns.length != EXPECTED_COLUMNS) {
                throw new IllegalArgumentException(
                        location(csvFile, lineNumber) + ": expected " + EXPECTED_COLUMNS
                                + " columns, got " + columns.length + ": " + line);
            }
            try {
                double years = Double.parseDouble(columns[0]);
                double yieldPercent = Double.parseDouble(columns[1]);
                points.add(new RatesCurve.Point(years, yieldPercent / 100.0));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(location(csvFile, lineNumber) + ": " + line, e);
            }
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("no data rows in " + csvFile.toAbsolutePath());
        }
        return RatesCurve.of(points);
    }

    /**
     * Reads the newest (lexicographically greatest, since filenames are
     * {@code ust_cmt_yyyy-mm-dd.csv}) snapshot in {@code directory}.
     */
    public static RatesCurve loadLatest(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("not a directory: " + directory.toAbsolutePath());
        }
        try (var files = Files.list(directory)) {
            Path newest = files
                    .filter(path -> path.getFileName().toString().startsWith("ust_cmt_")
                            && path.getFileName().toString().endsWith(".csv"))
                    .max(java.util.Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "no ust_cmt_*.csv files found in " + directory.toAbsolutePath()));
            return load(newest);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory.toAbsolutePath(), e);
        }
    }

    private static String location(Path file, int lineNumber) {
        return String.format(Locale.ROOT, "%s:%d", file.getFileName(), lineNumber + 1);
    }
}
