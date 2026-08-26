package io.github.williamhuang1261.qrp.options;

import java.util.Objects;

/**
 * One line of a market chain: a contract, the underlying's price at the moment
 * of the quote, and the mid price the market puts on it.
 *
 * <p>Rate and dividend yield travel with the quote rather than being assumed
 * global, because a real chain snapshot has one valuation moment but the curve
 * a desk discounts against is looked up per tenor. Until {@code RatesCurve}
 * ships, providers supply a single flat rate here; a curve-backed provider
 * later fills this per contract from {@code curve.zeroRate(contract.yearsTo(...))}
 * without changing this record's shape.
 */
public record OptionChainQuote(
        OptionContract contract,
        double underlyingPrice,
        double marketPrice,
        double riskFreeRate,
        double dividendYield) {

    public OptionChainQuote {
        Objects.requireNonNull(contract, "contract");
        if (!(underlyingPrice > 0.0) || !Double.isFinite(underlyingPrice)) {
            throw new IllegalArgumentException(
                    "underlyingPrice must be positive and finite, got: " + underlyingPrice);
        }
        if (!(marketPrice >= 0.0) || !Double.isFinite(marketPrice)) {
            throw new IllegalArgumentException(
                    "marketPrice must be non-negative and finite, got: " + marketPrice);
        }
        if (!Double.isFinite(riskFreeRate)) {
            throw new IllegalArgumentException("riskFreeRate must be finite, got: " + riskFreeRate);
        }
        if (!Double.isFinite(dividendYield)) {
            throw new IllegalArgumentException("dividendYield must be finite, got: " + dividendYield);
        }
    }
}
