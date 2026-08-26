package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyntheticChainGeneratorTest {

    @Test
    @DisplayName("is deterministic: the same valuation date produces the same chain")
    void isDeterministic() {
        LocalDate date = LocalDate.of(2026, 1, 2);
        List<OptionChainQuote> first = SyntheticChainGenerator.generate(date);
        List<OptionChainQuote> second = SyntheticChainGenerator.generate(date);

        assertEquals(first, second);
    }

    @Test
    @DisplayName("every generated quote reprices to the generating volatility")
    void quotesMatchTheGeneratingFunction() {
        // The generator prices from a known closed-form volatility; this pins
        // that the CSV it writes is consistent with that function, independent
        // of whatever VolatilitySurface later reconstructs from it.
        LocalDate date = LocalDate.of(2026, 1, 2);
        for (OptionChainQuote quote : SyntheticChainGenerator.generate(date)) {
            double years = quote.contract().yearsTo(date);
            double forward = quote.underlyingPrice()
                    * Math.exp((quote.riskFreeRate() - quote.dividendYield()) * years);
            double k = Math.log(quote.contract().strike() / forward);
            double expectedVol = SyntheticChainGenerator.generatingVolatility(k, years);

            BlackScholesInputs inputs = new BlackScholesInputs(
                    quote.underlyingPrice(), quote.contract().strike(), years, expectedVol,
                    quote.riskFreeRate(), quote.dividendYield());
            double expectedPrice = BlackScholesMerton.price(quote.contract().type(), inputs);

            assertEquals(expectedPrice, quote.marketPrice(), 1e-9,
                    "strike=" + quote.contract().strike() + " expiry=" + quote.contract().expiry());
        }
    }

    @Test
    @DisplayName("covers multiple expiries and multiple strikes, as a real chain does")
    void coversAGrid() {
        List<OptionChainQuote> quotes = SyntheticChainGenerator.generate(LocalDate.of(2026, 1, 2));

        long distinctExpiries = quotes.stream().map(q -> q.contract().expiry()).distinct().count();
        long distinctStrikes = quotes.stream().map(q -> q.contract().strike()).distinct().count();

        assertTrue(distinctExpiries >= 3, "expected several expiries, got " + distinctExpiries);
        assertTrue(distinctStrikes >= 5, "expected several strikes, got " + distinctStrikes);
    }

    @Test
    @DisplayName("the underlying symbol is labelled synthetic")
    void underlyingIsLabelledSynthetic() {
        assertEquals("SYNOPT", SyntheticChainGenerator.UNDERLYING_SYMBOL);
        for (OptionChainQuote quote : SyntheticChainGenerator.generate(LocalDate.of(2026, 1, 2))) {
            assertEquals("SYNOPT", quote.contract().underlying().symbol());
        }
    }
}
