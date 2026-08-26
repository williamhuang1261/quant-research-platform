package io.github.williamhuang1261.qrp.options;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Static, model-free checks over a chain and the surface fitted from it.
 *
 * <p>"Static" and "model-free" both matter. These three checks need no
 * assumption about the process the underlying follows -- they follow from
 * absence of arbitrage alone, which is what the posting's "identify pricing
 * anomalies" bullet is actually asking for: a violation here is a data or
 * quoting problem, not a disagreement between models.
 *
 * <ol>
 *   <li><b>Butterfly convexity in strike.</b> A call's price must be a convex
 *       function of strike. For three strikes {@code K1 < K2 < K3}, the
 *       strike-weighted combination
 *       {@code w1*C(K1) + w3*C(K3) >= C(K2)}, with weights that make the
 *       combination replicate {@code K2} exactly, is the statement of convexity
 *       for unequally spaced strikes; a violation prices a butterfly spread
 *       negative, which is free money at zero cost to assemble.
 *   <li><b>Calendar monotonicity in total variance.</b> At a fixed strike, total
 *       variance {@code w = sigma^2 T} must not decrease as {@code T} grows. A
 *       decrease means a calendar spread -- long the far leg, short the near --
 *       can be assembled for a positive premium and is worth strictly more than
 *       zero at every future date, which is again free money.
 *   <li><b>Put-call parity</b> on the raw market quotes, not the fitted
 *       surface: {@code C - P = S e^{-qT} - K e^{-rT}} holds by static
 *       replication with no model at all, so a residual here is a data problem
 *       in the quotes themselves.
 * </ol>
 */
public final class NoArbitrageDiagnostics {

    /** @param kind one of {@code "butterfly"}, {@code "calendar"}, {@code "put-call-parity"} */
    public record Violation(String kind, String description, double magnitude) {
    }

    public record Report(List<Violation> violations) {
        public boolean isClean() {
            return violations.isEmpty();
        }
    }

    /** Reasonable tolerance above pure floating-point noise; a desk's own bid/ask is wider than this. */
    private static final double TOLERANCE = 1e-6;

    private NoArbitrageDiagnostics() {
    }

    /**
     * Runs all three checks. Butterfly and calendar are evaluated on the fitted
     * {@code surface} at the strikes and expiries the chain itself quoted;
     * parity is evaluated on the chain's raw market prices, independent of the
     * surface entirely.
     */
    public static Report check(List<OptionChainQuote> quotes, VolatilitySurface surface, LocalDate valuationDate) {
        if (quotes == null || quotes.isEmpty()) {
            throw new IllegalArgumentException("quotes must not be null or empty");
        }
        if (surface == null) {
            throw new IllegalArgumentException("surface must not be null");
        }

        List<Violation> violations = new ArrayList<>();
        violations.addAll(checkButterfly(quotes, surface, valuationDate));
        violations.addAll(checkCalendar(quotes, surface, valuationDate));
        violations.addAll(checkPutCallParity(quotes, valuationDate));
        return new Report(List.copyOf(violations));
    }

    private static List<Violation> checkButterfly(
            List<OptionChainQuote> quotes, VolatilitySurface surface, LocalDate valuationDate) {
        List<Violation> found = new ArrayList<>();
        for (Map.Entry<LocalDate, List<OptionChainQuote>> slice : groupByExpiry(quotes).entrySet()) {
            List<OptionChainQuote> atExpiry = slice.getValue();
            OptionChainQuote reference = atExpiry.get(0);
            double years = reference.contract().yearsTo(valuationDate);

            List<Double> strikes = distinctSortedStrikes(atExpiry);
            for (int i = 1; i < strikes.size() - 1; i++) {
                double k1 = strikes.get(i - 1);
                double k2 = strikes.get(i);
                double k3 = strikes.get(i + 1);

                double c1 = callPrice(reference, surface, k1, years);
                double c2 = callPrice(reference, surface, k2, years);
                double c3 = callPrice(reference, surface, k3, years);

                // Weights that reconstruct K2 as a convex combination of K1 and
                // K3: w1*K1 + w3*K3 = K2, w1 + w3 = 1. Convexity requires
                // w1*C1 + w3*C3 >= C2.
                double w1 = (k3 - k2) / (k3 - k1);
                double w3 = (k2 - k1) / (k3 - k1);
                double violation = c2 - (w1 * c1 + w3 * c3);

                if (violation > TOLERANCE) {
                    found.add(new Violation("butterfly",
                            String.format(java.util.Locale.ROOT,
                                    "expiry %s: strikes %.2f/%.2f/%.2f price convexity violated by %.6f",
                                    slice.getKey(), k1, k2, k3, violation),
                            violation));
                }
            }
        }
        return found;
    }

    private static List<Violation> checkCalendar(
            List<OptionChainQuote> quotes, VolatilitySurface surface, LocalDate valuationDate) {
        List<Violation> found = new ArrayList<>();

        // Strikes that appear at every expiry are where a calendar spread can
        // actually be assembled with the same strike on both legs.
        Set<Double> commonStrikes = null;
        for (List<OptionChainQuote> atExpiry : groupByExpiry(quotes).values()) {
            Set<Double> strikesHere = new LinkedHashSet<>(distinctSortedStrikes(atExpiry));
            commonStrikes = commonStrikes == null ? strikesHere : intersect(commonStrikes, strikesHere);
        }
        if (commonStrikes == null || commonStrikes.isEmpty()) {
            return found;
        }

        List<Double> expiriesInYears = groupByExpiry(quotes).keySet().stream()
                .map(expiry -> quotes.stream()
                        .filter(q -> q.contract().expiry().equals(expiry))
                        .findFirst().orElseThrow()
                        .contract().yearsTo(valuationDate))
                .sorted()
                .toList();

        for (double strike : commonStrikes) {
            double previousVariance = -1.0;
            for (double years : expiriesInYears) {
                double iv = surface.impliedVolatility(strike, years);
                double totalVariance = iv * iv * years;
                if (previousVariance >= 0.0 && totalVariance < previousVariance - TOLERANCE) {
                    found.add(new Violation("calendar",
                            String.format(java.util.Locale.ROOT,
                                    "strike %.2f: total variance decreased to %.6f at years=%.4f "
                                            + "(was %.6f at an earlier expiry)",
                                    strike, totalVariance, years, previousVariance),
                            previousVariance - totalVariance));
                }
                previousVariance = totalVariance;
            }
        }
        return found;
    }

    private static List<Violation> checkPutCallParity(List<OptionChainQuote> quotes, LocalDate valuationDate) {
        List<Violation> found = new ArrayList<>();
        Map<String, OptionChainQuote> byKey = new LinkedHashMap<>();
        for (OptionChainQuote quote : quotes) {
            String key = quote.contract().underlying().symbol() + "|" + quote.contract().expiry()
                    + "|" + quote.contract().strike() + "|" + quote.contract().type();
            byKey.put(key, quote);
        }

        for (OptionChainQuote call : quotes) {
            if (call.contract().type() != OptionType.CALL) {
                continue;
            }
            String putKey = call.contract().underlying().symbol() + "|" + call.contract().expiry()
                    + "|" + call.contract().strike() + "|" + OptionType.PUT;
            OptionChainQuote put = byKey.get(putKey);
            if (put == null) {
                continue;
            }

            double years = call.contract().yearsTo(valuationDate);
            double forwardLeg = call.underlyingPrice() * Math.exp(-call.dividendYield() * years);
            double strikeLeg = call.contract().strike() * Math.exp(-call.riskFreeRate() * years);
            double residual = (call.marketPrice() - put.marketPrice()) - (forwardLeg - strikeLeg);

            if (Math.abs(residual) > TOLERANCE) {
                found.add(new Violation("put-call-parity",
                        String.format(java.util.Locale.ROOT,
                                "expiry %s strike %.2f: parity residual %.6f",
                                call.contract().expiry(), call.contract().strike(), residual),
                        Math.abs(residual)));
            }
        }
        return found;
    }

    private static double callPrice(OptionChainQuote reference, VolatilitySurface surface, double strike, double years) {
        double iv = surface.impliedVolatility(strike, years);
        BlackScholesInputs inputs = new BlackScholesInputs(
                reference.underlyingPrice(), strike, years, iv, reference.riskFreeRate(), reference.dividendYield());
        return BlackScholesMerton.price(OptionType.CALL, inputs);
    }

    private static Map<LocalDate, List<OptionChainQuote>> groupByExpiry(List<OptionChainQuote> quotes) {
        Map<LocalDate, List<OptionChainQuote>> byExpiry = new TreeMap<>();
        for (OptionChainQuote quote : quotes) {
            byExpiry.computeIfAbsent(quote.contract().expiry(), ignored -> new ArrayList<>()).add(quote);
        }
        return byExpiry;
    }

    private static List<Double> distinctSortedStrikes(List<OptionChainQuote> quotes) {
        return quotes.stream().map(q -> q.contract().strike()).distinct().sorted().toList();
    }

    private static Set<Double> intersect(Set<Double> a, Set<Double> b) {
        Set<Double> result = new LinkedHashSet<>(a);
        result.retainAll(b);
        return result;
    }
}
