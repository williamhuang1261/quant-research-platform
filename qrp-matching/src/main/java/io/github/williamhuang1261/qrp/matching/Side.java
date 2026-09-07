package io.github.williamhuang1261.qrp.matching;

/** Which side of the book an order rests on or crosses into. */
public enum Side {
    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
