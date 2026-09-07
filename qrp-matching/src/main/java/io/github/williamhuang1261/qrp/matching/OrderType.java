package io.github.williamhuang1261.qrp.matching;

/**
 * A limit order rests in the book at its price if it does not fully cross on
 * arrival; a market order never rests, it fills against whatever is on the
 * book (or as much of it as it can) and any unfilled remainder is discarded,
 * not queued.
 */
public enum OrderType {
    LIMIT,
    MARKET
}
