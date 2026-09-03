package io.github.williamhuang1261.qrp.onchain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses {@code tools/amm_sim.py}'s output CSV
 * (header: {@code block_number,token_in,amount_in,amount_out,
 * reserve0_before,reserve1_before,reserve0_after,reserve1_after,
 * realized_price_1e18}) into {@link AmmSwapRow}s.
 *
 * <p>{@code realized_price_1e18} is not carried into {@link AmmSwapRow}: it
 * is fully derived from {@code amountOut/amountIn} and easy to recompute
 * when needed, so keeping it out of the row type avoids two copies of the
 * same number quietly drifting apart.
 *
 * <p>Every parse failure names the file and the 1-based line number, the
 * same convention {@code CsvMarketDataProvider} in {@code qrp-data} follows
 * — the first thing anyone asks about a rejected row is which one it was.
 */
public final class AmmSwapCsvReader {

    private static final String EXPECTED_HEADER =
            "block_number,token_in,amount_in,amount_out,reserve0_before,reserve1_before,"
                    + "reserve0_after,reserve1_after,realized_price_1e18";

    private AmmSwapCsvReader() {
    }

    public static List<AmmSwapRow> read(Path csvFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + csvFile, e);
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(csvFile + ": empty file, expected a header row");
        }
        String header = lines.get(0).strip();
        if (!header.equals(EXPECTED_HEADER)) {
            throw new IllegalArgumentException(
                    csvFile + ":1: unexpected header, got [" + header + "], expected [" + EXPECTED_HEADER + "]");
        }

        List<AmmSwapRow> rows = new ArrayList<>(lines.size() - 1);
        for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1).strip();
            if (line.isEmpty()) {
                continue;
            }
            rows.add(parseRow(csvFile, lineNumber, line));
        }
        return rows;
    }

    private static AmmSwapRow parseRow(Path csvFile, int lineNumber, String line) {
        String[] fields = line.split(",", -1);
        if (fields.length != 9) {
            throw new IllegalArgumentException(
                    csvFile + ":" + lineNumber + ": expected 9 fields, got " + fields.length + ": [" + line + "]");
        }
        try {
            long blockNumber = Long.parseLong(fields[0]);
            boolean tokenInIsToken0 = parseTokenIn(csvFile, lineNumber, fields[1]);
            BigInteger amountIn = new BigInteger(fields[2]);
            BigInteger amountOut = new BigInteger(fields[3]);
            BigInteger reserve0Before = new BigInteger(fields[4]);
            BigInteger reserve1Before = new BigInteger(fields[5]);
            BigInteger reserve0After = new BigInteger(fields[6]);
            BigInteger reserve1After = new BigInteger(fields[7]);
            // fields[8] (realized_price_1e18) is intentionally not parsed; see class Javadoc.
            return new AmmSwapRow(
                    blockNumber, tokenInIsToken0, amountIn, amountOut,
                    reserve0Before, reserve1Before, reserve0After, reserve1After);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    csvFile + ":" + lineNumber + ": failed to parse a numeric field: [" + line + "]", e);
        }
    }

    private static boolean parseTokenIn(Path csvFile, int lineNumber, String field) {
        String value = Objects.requireNonNull(field).strip();
        if (value.equals("token0")) {
            return true;
        }
        if (value.equals("token1")) {
            return false;
        }
        throw new IllegalArgumentException(
                csvFile + ":" + lineNumber + ": token_in must be \"token0\" or \"token1\", got [" + value + "]");
    }
}
