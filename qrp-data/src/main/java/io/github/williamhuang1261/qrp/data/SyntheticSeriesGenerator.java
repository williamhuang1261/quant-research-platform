package io.github.williamhuang1261.qrp.data;

import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.Bar;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.Timeframe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Writes the bundled sample data set.
 *
 * <p>The series are <strong>synthetic</strong>: a seeded geometric Brownian
 * motion, not recorded prices. Redistributing vendor history is a licensing
 * question this repository does not need to answer, and a generated series is
 * reproducible, which a downloaded one is not. The symbols say so — {@code SYNA},
 * {@code SYNB}, {@code SYNETF} — so nobody mistakes a demo for a backtest on
 * real data.
 *
 * <p>Regenerate with:
 * <pre>mvn -q -pl qrp-data exec:java -Dexec.mainClass=io.github.williamhuang1261.qrp.data.SyntheticSeriesGenerator -Dexec.args=data/sample</pre>
 * Same seeds, same bytes.
 */
public final class SyntheticSeriesGenerator {

    /** Bars are stamped at 21:00 UTC, roughly a US equity close. */
    private static final LocalTime CLOSE_TIME = LocalTime.of(21, 0);
    private static final LocalDate START_DATE = LocalDate.of(2022, 1, 3);
    private static final int TRADING_DAYS = 504;
    private static final double DAYS_PER_YEAR = 252.0;

    private SyntheticSeriesGenerator() {
    }

    /** How one synthetic instrument behaves. */
    public record Spec(
            Instrument instrument,
            Timeframe timeframe,
            String file,
            long seed,
            double startPrice,
            double annualDrift,
            double annualVolatility,
            long baseVolume) {
    }

    public static List<Spec> defaultSpecs() {
        return List.of(
                new Spec(new Instrument("SYNA", "USD", AssetClass.EQUITY), Timeframe.DAY_1,
                        "SYNA_1d.csv", 20240101L, 100.00, 0.08, 0.24, 3_500_000L),
                new Spec(new Instrument("SYNB", "USD", AssetClass.EQUITY), Timeframe.DAY_1,
                        "SYNB_1d.csv", 20240202L, 45.00, -0.03, 0.35, 1_200_000L),
                new Spec(new Instrument("SYNETF", "USD", AssetClass.ETF), Timeframe.DAY_1,
                        "SYNETF_1d.csv", 20240303L, 250.00, 0.06, 0.14, 8_000_000L));
    }

    /**
     * Deterministic for a given spec: same seed, same bars.
     *
     * <p>Prices are rounded to cents before the invariants are re-imposed, since
     * rounding a high down below its close would otherwise produce a bar that
     * {@link Bar} rightly refuses to construct.
     */
    public static List<Bar> generate(Spec spec, LocalDate start, int barCount) {
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(spec.seed());
        double dt = 1.0 / DAYS_PER_YEAR;
        double drift = (spec.annualDrift() - 0.5 * spec.annualVolatility() * spec.annualVolatility()) * dt;
        double diffusion = spec.annualVolatility() * Math.sqrt(dt);

        List<Bar> bars = new ArrayList<>(barCount);
        double previousClose = spec.startPrice();
        LocalDate date = start;

        for (int i = 0; i < barCount; i++) {
            while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                date = date.plusDays(1);
            }

            double close = previousClose * Math.exp(drift + diffusion * random.nextGaussian());
            double open = previousClose * Math.exp(0.3 * diffusion * random.nextGaussian());
            double wickUp = Math.abs(random.nextGaussian()) * diffusion * previousClose;
            double wickDown = Math.abs(random.nextGaussian()) * diffusion * previousClose;
            double high = Math.max(open, close) + wickUp;
            double low = Math.min(open, close) - wickDown;
            long volume = Math.max(1L, Math.round(spec.baseVolume() * Math.exp(0.35 * random.nextGaussian())));

            double roundedOpen = round(open);
            double roundedClose = round(close);
            double roundedHigh = Math.max(round(high), Math.max(roundedOpen, roundedClose));
            double roundedLow = Math.min(round(Math.max(low, 0.01)), Math.min(roundedOpen, roundedClose));

            bars.add(new Bar(
                    date.atTime(CLOSE_TIME).toInstant(ZoneOffset.UTC),
                    roundedOpen, roundedHigh, roundedLow, roundedClose, volume));

            previousClose = roundedClose;
            date = date.plusDays(1);
        }
        return bars;
    }

    /** Writes the manifest and one file per spec into {@code outputDirectory}. */
    public static void write(Path outputDirectory, List<Spec> specs) {
        try {
            Files.createDirectories(outputDirectory);
            StringBuilder manifest = new StringBuilder("symbol,currency,asset_class,timeframe,file\n");
            for (Spec spec : specs) {
                Instrument instrument = spec.instrument();
                manifest.append(instrument.symbol()).append(',')
                        .append(instrument.currency()).append(',')
                        .append(instrument.assetClass()).append(',')
                        .append(spec.timeframe().id()).append(',')
                        .append(spec.file()).append('\n');

                StringBuilder rows = new StringBuilder("timestamp,open,high,low,close,volume\n");
                for (Bar bar : generate(spec, START_DATE, TRADING_DAYS)) {
                    rows.append(String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.2f,%d%n",
                            bar.timestamp(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()));
                }
                Files.writeString(outputDirectory.resolve(spec.file()), rows.toString(), StandardCharsets.UTF_8);
            }
            Files.writeString(outputDirectory.resolve(CsvMarketDataProvider.MANIFEST_FILE),
                    manifest.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write sample data to " + outputDirectory, e);
        }
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static void main(String[] args) {
        Path output = Path.of(args.length > 0 ? args[0] : "data/sample");
        write(output, defaultSpecs());
        System.out.println("wrote " + defaultSpecs().size() + " synthetic series to " + output.toAbsolutePath());
    }
}
