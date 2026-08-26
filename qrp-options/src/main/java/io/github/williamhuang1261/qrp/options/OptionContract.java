package io.github.williamhuang1261.qrp.options;

import io.github.williamhuang1261.qrp.core.Instrument;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The terms of a listed option: what it is a right on, in which direction, at
 * what level, until when.
 *
 * <p>Separate from {@link Instrument} rather than folded into it. An
 * {@code Instrument} answers "what trades"; strike, expiry and exercise style
 * are contract terms that change per line of a chain while the underlying stays
 * one thing. Keeping them apart is also what lets a chain be a list of contracts
 * over a single shared underlying instead of a thousand near-identical
 * instruments.
 *
 * <p>Time to expiry is <b>ACT/365 fixed</b>. That is a choice, and it is the
 * wrong one for a rates desk, which would want ACT/360 or a business-day count
 * off an exchange calendar. It is used here because the platform models no
 * holiday calendar at all, and a day count that silently assumes one would be a
 * worse lie than a stated approximation. Every consumer takes the year fraction
 * as an argument, so a caller with a real calendar can supply its own.
 */
public record OptionContract(
        Instrument underlying,
        OptionType type,
        ExerciseStyle style,
        double strike,
        LocalDate expiry,
        double contractMultiplier) {

    /** Standard for US listed equity options: one contract covers 100 shares. */
    public static final double EQUITY_MULTIPLIER = 100.0;

    private static final double DAYS_PER_YEAR = 365.0;

    public OptionContract {
        Objects.requireNonNull(underlying, "underlying");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(expiry, "expiry");
        if (!(strike > 0.0) || !Double.isFinite(strike)) {
            throw new IllegalArgumentException("strike must be positive and finite, got: " + strike);
        }
        if (!(contractMultiplier > 0.0) || !Double.isFinite(contractMultiplier)) {
            throw new IllegalArgumentException(
                    "contract multiplier must be positive and finite, got: " + contractMultiplier);
        }
    }

    /** A European option on an equity underlying, 100 shares to the contract. */
    public static OptionContract european(
            Instrument underlying, OptionType type, double strike, LocalDate expiry) {
        return new OptionContract(
                underlying, type, ExerciseStyle.EUROPEAN, strike, expiry, EQUITY_MULTIPLIER);
    }

    /** An American option on an equity underlying, 100 shares to the contract. */
    public static OptionContract american(
            Instrument underlying, OptionType type, double strike, LocalDate expiry) {
        return new OptionContract(
                underlying, type, ExerciseStyle.AMERICAN, strike, expiry, EQUITY_MULTIPLIER);
    }

    /**
     * Year fraction from {@code valuationDate} to expiry on ACT/365F.
     *
     * @throws IllegalArgumentException if the contract expired before that date;
     *         an expired option is not worth zero, it is not a live position, and
     *         returning a negative year fraction would let it be priced anyway
     */
    public double yearsTo(LocalDate valuationDate) {
        Objects.requireNonNull(valuationDate, "valuationDate");
        long days = ChronoUnit.DAYS.between(valuationDate, expiry);
        if (days < 0) {
            throw new IllegalArgumentException(
                    "contract expired on " + expiry + ", before valuation date " + valuationDate);
        }
        return days / DAYS_PER_YEAR;
    }

    /** True once the year fraction is zero: expiry day, priced at intrinsic. */
    public boolean isExpiredOn(LocalDate valuationDate) {
        return !valuationDate.isBefore(expiry);
    }

    /** Intrinsic value per unit of underlying, ignoring the multiplier. */
    public double intrinsicValue(double underlyingPrice) {
        return type.payoff(underlyingPrice, strike);
    }

    /** The same contract with the opposite right, used by parity checks. */
    public OptionContract flipType() {
        return new OptionContract(underlying, type.opposite(), style, strike, expiry, contractMultiplier);
    }

    @Override
    public String toString() {
        return underlying.symbol() + " " + expiry + " " + strike + " " + type;
    }
}
