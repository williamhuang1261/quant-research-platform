package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VolatilitySurfaceTest {

    private static final LocalDate VALUATION_DATE = LocalDate.of(2026, 1, 2);

    /**
     * The headline test. {@link SyntheticChainGenerator} prices a chain from a
     * known, hand-specified volatility function; this surface is refit from
     * only the resulting market prices, with no access to that function. If the
     * fitted surface's volatility at the exact grid points does not match the
     * function that generated them, the surface-fitting code is wrong -- this
     * is not comparing the surface against itself.
     */
    @Test
    @DisplayName("recovers the exact surface it was generated from, at the quoted grid points")
    void recoversTheGeneratingSurface() {
        List<OptionChainQuote> quotes = SyntheticChainGenerator.generate(VALUATION_DATE);
        VolatilitySurface surface = VolatilitySurface.build(quotes, VALUATION_DATE);

        int checked = 0;
        for (OptionChainQuote quote : quotes) {
            double years = quote.contract().yearsTo(VALUATION_DATE);
            double forward = quote.underlyingPrice()
                    * Math.exp((quote.riskFreeRate() - quote.dividendYield()) * years);
            double k = Math.log(quote.contract().strike() / forward);
            double expectedVol = SyntheticChainGenerator.generatingVolatility(k, years);

            double fittedVol = surface.impliedVolatility(quote.contract().strike(), years);

            assertEquals(expectedVol, fittedVol, 1e-6,
                    "strike=" + quote.contract().strike() + " years=" + years);
            checked++;
        }
        assertTrue(checked >= 30, "expected to check a real grid, only checked " + checked);
    }

    @Test
    @DisplayName("interpolates between grid points to a value the generating function agrees with")
    void interpolatesReasonablyBetweenGridPoints() {
        // Off-grid queries can only be checked loosely, since the surface's
        // interpolation (linear in total variance) is not the same functional
        // form as the generator's (quadratic in log-moneyness); the two should
        // still roughly agree away from the grid, unlike at it.
        List<OptionChainQuote> quotes = SyntheticChainGenerator.generate(VALUATION_DATE);
        VolatilitySurface surface = VolatilitySurface.build(quotes, VALUATION_DATE);

        double midStrike = 97.5;
        double midYears = 0.375;
        double forward = 100.0 * Math.exp((0.045 - 0.015) * midYears);
        double expected = SyntheticChainGenerator.generatingVolatility(Math.log(midStrike / forward), midYears);

        double fitted = surface.impliedVolatility(midStrike, midYears);

        assertEquals(expected, fitted, 0.01, "off-grid interpolation diverged from the generator");
    }

    @Test
    @DisplayName("refuses to extrapolate past the quoted expiry range")
    void refusesToExtrapolateInTime() {
        VolatilitySurface surface = VolatilitySurface.build(SyntheticChainGenerator.generate(VALUATION_DATE), VALUATION_DATE);

        assertThrows(IllegalArgumentException.class, () -> surface.impliedVolatility(100.0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> surface.impliedVolatility(100.0, 5.0));
    }

    @Test
    @DisplayName("refuses to extrapolate far past the quoted strikes")
    void refusesToExtrapolateInStrike() {
        VolatilitySurface surface = VolatilitySurface.build(SyntheticChainGenerator.generate(VALUATION_DATE), VALUATION_DATE);

        assertThrows(IllegalArgumentException.class, () -> surface.impliedVolatility(1.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> surface.impliedVolatility(10_000.0, 0.5));
    }

    @Test
    @DisplayName("requires at least two distinct expiries")
    void requiresMultipleExpiries() {
        List<OptionChainQuote> allQuotes = SyntheticChainGenerator.generate(VALUATION_DATE);
        LocalDate onlyExpiry = allQuotes.get(0).contract().expiry();
        List<OptionChainQuote> oneExpiry = allQuotes.stream()
                .filter(q -> q.contract().expiry().equals(onlyExpiry))
                .toList();

        assertThrows(IllegalArgumentException.class, () -> VolatilitySurface.build(oneExpiry, VALUATION_DATE));
    }

    @Test
    @DisplayName("rejects an empty or null quote list")
    void rejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> VolatilitySurface.build(List.of(), VALUATION_DATE));
        assertThrows(IllegalArgumentException.class, () -> VolatilitySurface.build(null, VALUATION_DATE));
    }
}
