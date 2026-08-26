package io.github.williamhuang1261.qrp.options;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a chain priced off a known, hand-specified volatility function.
 *
 * <p>The generated chain is <strong>synthetic</strong>, in the same sense
 * {@link io.github.williamhuang1261.qrp.data.SyntheticSeriesGenerator} already
 * uses for bars: nobody's real market data, deterministic, and labelled as
 * such. The underlying symbol is {@code SYNOPT}, not a real ticker.
 *
 * <p>Not an SVI surface, and not claimed to be one. The generating function is
 * a quadratic in log-moneyness with a sloped at-the-money level -- enough to
 * produce a real skew and a real term structure, plain enough that its exact
 * closed form is stated here rather than fitted. That is what makes
 * {@code VolatilitySurfaceTest}'s headline check possible at all: a chain is
 * priced from this known function, {@link VolatilitySurface} refits it from
 * only the resulting prices, and the test asserts the surface recovers the
 * function it was generated from -- not merely internally consistent numbers.
 *
 * <p>Regenerate with:
 * <pre>mvn -q -pl qrp-options exec:java -Dexec.mainClass=io.github.williamhuang1261.qrp.options.SyntheticChainGenerator -Dexec.args=data/sample</pre>
 * Same inputs, same bytes.
 */
public final class SyntheticChainGenerator {

    public static final String UNDERLYING_SYMBOL = "SYNOPT";
    public static final String CHAIN_FILE = "SYNOPT_chain.csv";

    private static final double SPOT = 100.0;
    private static final double RISK_FREE_RATE = 0.045;
    private static final double DIVIDEND_YIELD = 0.015;
    private static final double[] YEARS_TO_EXPIRY = {0.25, 0.5, 1.0, 2.0};
    private static final double[] STRIKES = {70, 80, 90, 95, 100, 105, 110, 120, 130};

    // Generating function: sigma(k, T)^2 = atmLevel(T)^2 + skew*k + curvature*k^2,
    // k = ln(strike / forward). Floored so a wide wing never goes non-positive.
    private static final double ATM_LEVEL_BASE = 0.22;
    private static final double ATM_TERM_SLOPE = 0.03;
    private static final double SKEW = -0.18;
    private static final double CURVATURE = 0.35;
    private static final double MIN_VOLATILITY = 0.03;

    private SyntheticChainGenerator() {
    }

    /** The exact function the chain is priced from; {@code VolatilitySurfaceTest} checks against it directly. */
    public static double generatingVolatility(double logMoneyness, double years) {
        double atmLevel = ATM_LEVEL_BASE + ATM_TERM_SLOPE * years;
        double variance = atmLevel * atmLevel + SKEW * logMoneyness + CURVATURE * logMoneyness * logMoneyness;
        return Math.max(MIN_VOLATILITY, Math.sqrt(Math.max(variance, 0.0)));
    }

    public static List<OptionChainQuote> generate(LocalDate valuationDate) {
        List<OptionChainQuote> quotes = new ArrayList<>();
        io.github.williamhuang1261.qrp.core.Instrument underlying =
                io.github.williamhuang1261.qrp.core.Instrument.equity(UNDERLYING_SYMBOL);

        for (double nominalYears : YEARS_TO_EXPIRY) {
            LocalDate expiry = valuationDate.plusDays(Math.round(nominalYears * 365.0));
            // Rounding the nominal year fraction to a whole number of days moves
            // it slightly (0.25 becomes 91/365 = 0.249315...). Pricing must use
            // THIS recomputed value, not the nominal one: every consumer -- the
            // tests, VolatilitySurface.build -- reconstructs years from the
            // stored expiry via OptionContract.yearsTo(), and if the price on
            // disk were generated from a different year fraction than that
            // recomputation produces, the "known" generating volatility and the
            // volatility recovered from the price would silently disagree by the
            // rounding error. Pricing from the same recomputation the consumer
            // will use is what makes this generator's ground truth actually
            // ground truth.
            double years = (double) java.time.temporal.ChronoUnit.DAYS.between(valuationDate, expiry) / 365.0;
            double forward = SPOT * Math.exp((RISK_FREE_RATE - DIVIDEND_YIELD) * years);

            for (double strike : STRIKES) {
                double logMoneyness = Math.log(strike / forward);
                double volatility = generatingVolatility(logMoneyness, years);
                BlackScholesInputs inputs = BlackScholesInputs.equityWithYield(
                        SPOT, strike, years, volatility, RISK_FREE_RATE, DIVIDEND_YIELD);

                // OTM side of the money uses the cheaper-to-price, more liquid leg
                // of each strike, matching how a real chain is quoted.
                OptionType type = strike >= forward ? OptionType.CALL : OptionType.PUT;
                OptionContract contract = OptionContract.european(underlying, type, strike, expiry);
                double price = BlackScholesMerton.price(type, inputs);

                quotes.add(new OptionChainQuote(contract, SPOT, price, RISK_FREE_RATE, DIVIDEND_YIELD));
            }
        }
        return quotes;
    }

    public static void write(Path outputDirectory, LocalDate valuationDate) {
        try {
            Files.createDirectories(outputDirectory);
            StringBuilder rows = new StringBuilder(
                    "underlying,valuation_date,expiry,strike,type,style,spot,market_price,risk_free_rate,dividend_yield\n");
            for (OptionChainQuote quote : generate(valuationDate)) {
                OptionContract contract = quote.contract();
                rows.append(String.format(Locale.ROOT, "%s,%s,%s,%.4f,%s,%s,%.4f,%.6f,%.6f,%.6f%n",
                        contract.underlying().symbol(), valuationDate, contract.expiry(), contract.strike(),
                        contract.type(), contract.style(), quote.underlyingPrice(), quote.marketPrice(),
                        quote.riskFreeRate(), quote.dividendYield()));
            }
            Files.writeString(outputDirectory.resolve(CHAIN_FILE), rows.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write synthetic chain to " + outputDirectory, e);
        }
    }

    public static void main(String[] args) {
        Path output = Path.of(args.length > 0 ? args[0] : "data/sample");
        LocalDate valuationDate = LocalDate.of(2026, 1, 2);
        write(output, valuationDate);
        System.out.println("wrote " + generate(valuationDate).size() + " synthetic chain quotes to "
                + output.toAbsolutePath());
    }
}
