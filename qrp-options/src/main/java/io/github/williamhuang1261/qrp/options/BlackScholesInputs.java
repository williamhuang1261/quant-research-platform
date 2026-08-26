package io.github.williamhuang1261.qrp.options;

/**
 * The six numbers a lognormal pricer needs, with carry expressed as a dividend
 * yield rather than assumed away.
 *
 * <p>Carrying {@code q} as a parameter is what makes one formula cover the four
 * cases a futures and options desk actually meets:
 *
 * <table border="1">
 *   <caption>Cost of carry by instrument</caption>
 *   <tr><th>Instrument</th><th>{@code q}</th><th>Carry {@code b = r - q}</th><th>Model</th></tr>
 *   <tr><td>Non-dividend equity</td><td>0</td><td>{@code r}</td><td>Black-Scholes (1973)</td></tr>
 *   <tr><td>Index with a yield</td><td>dividend yield</td><td>{@code r - q}</td><td>Merton (1973)</td></tr>
 *   <tr><td>Future</td><td>{@code r}</td><td>0</td><td>Black (1976)</td></tr>
 *   <tr><td>FX</td><td>foreign rate</td><td>{@code r_d - r_f}</td><td>Garman-Kohlhagen (1983)</td></tr>
 * </table>
 *
 * <p>The alternative was to take {@code b} directly. It was rejected because
 * {@code rho} is ambiguous under it: differentiating with respect to {@code r}
 * while holding {@code b} fixed gives a different number than holding {@code q}
 * fixed, and the textbook value for an equity option is the second one. Taking
 * {@code q} removes the ambiguity from the signature rather than from a comment.
 *
 * <p>Rates are continuously compounded and expressed as decimals: 5 % is
 * {@code 0.05}. Negative rates are allowed, because they happened.
 *
 * @param spot             current price of the underlying, or the futures price
 *                         when {@code q} is set to {@code r}
 * @param strike           contract strike
 * @param timeToExpiryYears year fraction to expiry; zero prices at intrinsic
 * @param volatility        annualized, as a decimal; zero prices deterministically
 * @param riskFreeRate      continuously compounded discount rate
 * @param dividendYield     continuous carry deduction, per the table above
 */
public record BlackScholesInputs(
        double spot,
        double strike,
        double timeToExpiryYears,
        double volatility,
        double riskFreeRate,
        double dividendYield) {

    public BlackScholesInputs {
        requirePositive("spot", spot);
        requirePositive("strike", strike);
        requireNonNegative("timeToExpiryYears", timeToExpiryYears);
        requireNonNegative("volatility", volatility);
        requireFinite("riskFreeRate", riskFreeRate);
        requireFinite("dividendYield", dividendYield);
    }

    /** An option on a non-dividend-paying equity: {@code q = 0}. */
    public static BlackScholesInputs equity(
            double spot, double strike, double years, double volatility, double riskFreeRate) {
        return new BlackScholesInputs(spot, strike, years, volatility, riskFreeRate, 0.0);
    }

    /** An option on an index or a dividend-paying equity. */
    public static BlackScholesInputs equityWithYield(
            double spot, double strike, double years, double volatility,
            double riskFreeRate, double dividendYield) {
        return new BlackScholesInputs(
                spot, strike, years, volatility, riskFreeRate, dividendYield);
    }

    /**
     * An option on a future, in the Black (1976) sense: zero carry, so the
     * forward is the futures price itself.
     *
     * @param futuresPrice the future's price, which takes the place of spot
     */
    public static BlackScholesInputs future(
            double futuresPrice, double strike, double years, double volatility, double riskFreeRate) {
        return new BlackScholesInputs(
                futuresPrice, strike, years, volatility, riskFreeRate, riskFreeRate);
    }

    /** An FX option under Garman-Kohlhagen: the foreign rate is the carry deduction. */
    public static BlackScholesInputs fx(
            double spot, double strike, double years, double volatility,
            double domesticRate, double foreignRate) {
        return new BlackScholesInputs(spot, strike, years, volatility, domesticRate, foreignRate);
    }

    /** Cost of carry, {@code r - q}. */
    public double carryRate() {
        return riskFreeRate - dividendYield;
    }

    /** Forward price of the underlying at expiry, {@code S e^{(r-q)T}}. */
    public double forward() {
        return spot * Math.exp(carryRate() * timeToExpiryYears);
    }

    /** Discount factor to expiry, {@code e^{-rT}}. */
    public double discountFactor() {
        return Math.exp(-riskFreeRate * timeToExpiryYears);
    }

    /** Carry factor, {@code e^{-qT}}: the deflator on the spot leg. */
    public double carryFactor() {
        return Math.exp(-dividendYield * timeToExpiryYears);
    }

    /**
     * True when there is no diffusion left to price: at expiry, or at zero
     * volatility. Both collapse to a deterministic payoff on the forward.
     */
    public boolean isDeterministic() {
        return timeToExpiryYears == 0.0 || volatility == 0.0;
    }

    /** {@code sigma * sqrt(T)}: total volatility over the life of the contract. */
    public double totalVolatility() {
        return volatility * Math.sqrt(timeToExpiryYears);
    }

    /** This instance is unchanged; a new one is returned. */
    public BlackScholesInputs withVolatility(double newVolatility) {
        return new BlackScholesInputs(
                spot, strike, timeToExpiryYears, newVolatility, riskFreeRate, dividendYield);
    }

    /** This instance is unchanged; a new one is returned. */
    public BlackScholesInputs withSpot(double newSpot) {
        return new BlackScholesInputs(
                newSpot, strike, timeToExpiryYears, volatility, riskFreeRate, dividendYield);
    }

    /** This instance is unchanged; a new one is returned. */
    public BlackScholesInputs withTimeToExpiry(double newYears) {
        return new BlackScholesInputs(
                spot, strike, newYears, volatility, riskFreeRate, dividendYield);
    }

    /** This instance is unchanged; a new one is returned. */
    public BlackScholesInputs withRiskFreeRate(double newRate) {
        return new BlackScholesInputs(
                spot, strike, timeToExpiryYears, volatility, newRate, dividendYield);
    }

    private static void requirePositive(String name, double value) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be positive and finite, got: " + value);
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!(value >= 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be non-negative and finite, got: " + value);
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite, got: " + value);
        }
    }
}
