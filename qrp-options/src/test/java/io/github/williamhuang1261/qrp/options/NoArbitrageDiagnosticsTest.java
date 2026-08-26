package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoArbitrageDiagnosticsTest {

    private static final LocalDate VALUATION_DATE = LocalDate.of(2026, 1, 2);

    @Test
    @DisplayName("the generator's own chain, priced from a single smooth surface, is clean")
    void generatedChainIsClean() {
        List<OptionChainQuote> quotes = SyntheticChainGenerator.generate(VALUATION_DATE);
        VolatilitySurface surface = VolatilitySurface.build(quotes, VALUATION_DATE);

        NoArbitrageDiagnostics.Report report = NoArbitrageDiagnostics.check(quotes, surface, VALUATION_DATE);

        assertTrue(report.isClean(), "expected no violations, got: " + report.violations());
    }

    @Test
    @DisplayName("catches a butterfly violation planted at one strike")
    void catchesAButterflyViolation() {
        List<OptionChainQuote> quotes = new ArrayList<>(SyntheticChainGenerator.generate(VALUATION_DATE));

        // Find the first expiry's middle strike and drastically overprice it,
        // in a way large enough to break convexity but still within the
        // no-arbitrage price bounds ImpliedVolatility itself enforces (so the
        // surface still builds -- the violation is about SHAPE, not about a
        // single quote being individually impossible).
        LocalDate firstExpiry = quotes.stream().map(q -> q.contract().expiry()).min(LocalDate::compareTo).orElseThrow();
        int index = indexOfStrike(quotes, firstExpiry, 100.0);
        OptionChainQuote original = quotes.get(index);
        quotes.set(index, new OptionChainQuote(
                original.contract(), original.underlyingPrice(), original.marketPrice() * 3.0,
                original.riskFreeRate(), original.dividendYield()));

        VolatilitySurface surface = VolatilitySurface.build(quotes, VALUATION_DATE);
        NoArbitrageDiagnostics.Report report = NoArbitrageDiagnostics.check(quotes, surface, VALUATION_DATE);

        assertTrue(
                report.violations().stream().anyMatch(v -> v.kind().equals("butterfly")),
                "expected a butterfly violation, got: " + report.violations());
    }

    @Test
    @DisplayName("catches a calendar violation: total variance decreasing in time")
    void catchesACalendarViolation() {
        List<OptionChainQuote> quotes = new ArrayList<>(SyntheticChainGenerator.generate(VALUATION_DATE));

        // Inflate every strike at the FURTHEST expiry so its implied vol sits far
        // below the nearest expiry's -- total variance should be roughly
        // constant or growing across time, so a collapse at the long end is the
        // calendar violation.
        LocalDate lastExpiry = quotes.stream().map(q -> q.contract().expiry()).max(LocalDate::compareTo).orElseThrow();
        for (int i = 0; i < quotes.size(); i++) {
            OptionChainQuote quote = quotes.get(i);
            if (quote.contract().expiry().equals(lastExpiry)) {
                double years = quote.contract().yearsTo(VALUATION_DATE);
                BlackScholesInputs tinyVol = new BlackScholesInputs(
                        quote.underlyingPrice(), quote.contract().strike(), years, 0.01,
                        quote.riskFreeRate(), quote.dividendYield());
                double tinyPrice = BlackScholesMerton.price(quote.contract().type(), tinyVol);
                quotes.set(i, new OptionChainQuote(
                        quote.contract(), quote.underlyingPrice(), tinyPrice,
                        quote.riskFreeRate(), quote.dividendYield()));
            }
        }

        VolatilitySurface surface = VolatilitySurface.build(quotes, VALUATION_DATE);
        NoArbitrageDiagnostics.Report report = NoArbitrageDiagnostics.check(quotes, surface, VALUATION_DATE);

        assertTrue(
                report.violations().stream().anyMatch(v -> v.kind().equals("calendar")),
                "expected a calendar violation, got: " + report.violations());
    }

    @Test
    @DisplayName("catches a put-call parity violation on the raw quotes")
    void catchesAParityViolation() {
        List<OptionChainQuote> quotes = new ArrayList<>(SyntheticChainGenerator.generate(VALUATION_DATE));

        // The generator quotes only one side (call or put) per strike, so
        // synthesize the missing side at the same strike/expiry from a
        // DIFFERENT, inconsistent volatility, breaking parity deliberately.
        OptionChainQuote anyQuote = quotes.get(0);
        double years = anyQuote.contract().yearsTo(VALUATION_DATE);
        OptionType missingType = anyQuote.contract().type().opposite();
        BlackScholesInputs wrongVol = new BlackScholesInputs(
                anyQuote.underlyingPrice(), anyQuote.contract().strike(), years, 0.9,
                anyQuote.riskFreeRate(), anyQuote.dividendYield());
        double inconsistentPrice = BlackScholesMerton.price(missingType, wrongVol);
        OptionContract oppositeContract = anyQuote.contract().flipType();
        quotes.add(new OptionChainQuote(
                oppositeContract, anyQuote.underlyingPrice(), inconsistentPrice,
                anyQuote.riskFreeRate(), anyQuote.dividendYield()));

        VolatilitySurface surface = VolatilitySurface.build(quotes, VALUATION_DATE);
        NoArbitrageDiagnostics.Report report = NoArbitrageDiagnostics.check(quotes, surface, VALUATION_DATE);

        assertTrue(
                report.violations().stream().anyMatch(v -> v.kind().equals("put-call-parity")),
                "expected a put-call-parity violation, got: " + report.violations());
    }

    @Test
    @DisplayName("consistent call and put quotes at the same strike pass parity")
    void consistentQuotesPassParity() {
        List<OptionChainQuote> quotes = new ArrayList<>(SyntheticChainGenerator.generate(VALUATION_DATE));
        OptionChainQuote anyQuote = quotes.get(0);
        double years = anyQuote.contract().yearsTo(VALUATION_DATE);

        // This time synthesize the opposite side using the SAME volatility the
        // original was priced at -- parity has to hold exactly by construction.
        double forward = anyQuote.underlyingPrice()
                * Math.exp((anyQuote.riskFreeRate() - anyQuote.dividendYield()) * years);
        double impliedVol = ImpliedVolatility.solve(
                anyQuote.contract().type(),
                new BlackScholesInputs(anyQuote.underlyingPrice(), anyQuote.contract().strike(), years, 0.3,
                        anyQuote.riskFreeRate(), anyQuote.dividendYield()),
                anyQuote.marketPrice());
        BlackScholesInputs consistentVol = new BlackScholesInputs(
                anyQuote.underlyingPrice(), anyQuote.contract().strike(), years, impliedVol,
                anyQuote.riskFreeRate(), anyQuote.dividendYield());
        double consistentPrice = BlackScholesMerton.price(anyQuote.contract().type().opposite(), consistentVol);
        quotes.add(new OptionChainQuote(
                anyQuote.contract().flipType(), anyQuote.underlyingPrice(), consistentPrice,
                anyQuote.riskFreeRate(), anyQuote.dividendYield()));

        List<NoArbitrageDiagnostics.Violation> parityViolations = NoArbitrageDiagnostics
                .check(quotes, VolatilitySurface.build(quotes, VALUATION_DATE), VALUATION_DATE)
                .violations().stream()
                .filter(v -> v.kind().equals("put-call-parity"))
                .collect(Collectors.toList());

        assertTrue(parityViolations.isEmpty(), "expected no parity violations, got: " + parityViolations);
    }

    @Test
    @DisplayName("rejects null or empty inputs")
    void rejectsBadInputs() {
        List<OptionChainQuote> quotes = SyntheticChainGenerator.generate(VALUATION_DATE);
        VolatilitySurface surface = VolatilitySurface.build(quotes, VALUATION_DATE);

        assertThrows(IllegalArgumentException.class, () -> NoArbitrageDiagnostics.check(List.of(), surface, VALUATION_DATE));
        assertThrows(IllegalArgumentException.class, () -> NoArbitrageDiagnostics.check(quotes, null, VALUATION_DATE));
    }

    private static int indexOfStrike(List<OptionChainQuote> quotes, LocalDate expiry, double strike) {
        for (int i = 0; i < quotes.size(); i++) {
            OptionChainQuote q = quotes.get(i);
            if (q.contract().expiry().equals(expiry) && q.contract().strike() == strike) {
                return i;
            }
        }
        throw new AssertionError("no quote at expiry=" + expiry + " strike=" + strike);
    }
}
