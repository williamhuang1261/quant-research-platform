package io.github.williamhuang1261.qrp.equity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A snapshot of one company's headline fundamentals, as reported by the
 * data provider at fetch time (trailing-twelve-month figures, not a specific
 * fiscal-year filing). See {@code data/equity/README.md} for the source and
 * what these numbers are not.
 */
public record EquityFundamentals(
        String ticker,
        String companyName,
        String sector,
        String fetchDate,
        double totalRevenue,
        double trailingEps,
        double netMargin,
        double dilutedSharesOutstanding,
        double trailingPe,
        double currentPrice,
        double freeCashFlow) {

    private static final int EXPECTED_COLUMNS = 11;

    /**
     * Reads the {@code ticker,company_name,sector,fetch_date,total_revenue,
     * trailing_eps,net_margin,diluted_shares_outstanding,trailing_pe,
     * current_price,free_cash_flow} format {@code tools/fetch_equity_fundamentals.py}
     * writes, keyed by ticker.
     *
     * @throws IllegalArgumentException if the file is malformed
     */
    public static Map<String, EquityFundamentals> load(Path csvFile) {
        java.util.List<String> lines;
        try {
            lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + csvFile.toAbsolutePath(), e);
        }

        Map<String, EquityFundamentals> byTicker = new HashMap<>();
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
                EquityFundamentals row = new EquityFundamentals(
                        c[0],
                        c[1],
                        c[2],
                        c[3],
                        Double.parseDouble(c[4]),
                        Double.parseDouble(c[5]),
                        Double.parseDouble(c[6]),
                        Double.parseDouble(c[7]),
                        Double.parseDouble(c[8]),
                        Double.parseDouble(c[9]),
                        Double.parseDouble(c[10]));
                byTicker.put(row.ticker(), row);
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
     * {@code fundamentals_yyyy-mm-dd.csv}) snapshot in {@code directory}.
     */
    public static Map<String, EquityFundamentals> loadLatest(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("not a directory: " + directory.toAbsolutePath());
        }
        try (var files = Files.list(directory)) {
            Path newest = files
                    .filter(path -> path.getFileName().toString().startsWith("fundamentals_")
                            && path.getFileName().toString().endsWith(".csv"))
                    .max(java.util.Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "no fundamentals_*.csv files found in " + directory.toAbsolutePath()));
            return load(newest);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + directory.toAbsolutePath(), e);
        }
    }

    private static String location(Path file, int lineNumber) {
        return String.format(Locale.ROOT, "%s:%d", file.getFileName(), lineNumber + 1);
    }
}
