package io.github.williamhuang1261.qrp.core;

/** Broad category of a tradable instrument. */
public enum AssetClass {
    EQUITY,
    ETF,
    INDEX,
    FUTURE,
    FX,
    CRYPTO,

    /**
     * A derivative whose terms live in an {@code OptionContract} rather than
     * here: an {@code Instrument} identifies <em>what trades</em>, and strike,
     * expiry and exercise style are contract terms, not identity.
     */
    OPTION,

    /**
     * A fixed income instrument. Present so a Treasury quote has somewhere to
     * live; the platform models a curve, not an issue-level bond calendar.
     */
    BOND
}
