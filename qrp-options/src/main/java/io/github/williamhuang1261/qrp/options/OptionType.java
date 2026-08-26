package io.github.williamhuang1261.qrp.options;

/** Right conferred by the contract. */
public enum OptionType {
    CALL,
    PUT;

    /** {@code +1} for a call, {@code -1} for a put: the sign in a parity or payoff expression. */
    public double sign() {
        return this == CALL ? 1.0 : -1.0;
    }

    /** Intrinsic value against a reference price, floored at zero. */
    public double payoff(double underlyingPrice, double strike) {
        return Math.max(sign() * (underlyingPrice - strike), 0.0);
    }

    public OptionType opposite() {
        return this == CALL ? PUT : CALL;
    }
}
