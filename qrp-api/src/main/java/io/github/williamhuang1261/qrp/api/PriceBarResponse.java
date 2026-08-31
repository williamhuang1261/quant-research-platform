package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.core.Bar;
import java.time.Instant;

/** One bar, over the wire. A hand-picked mirror of {@link Bar}, same reasoning as every other response record here. */
public record PriceBarResponse(Instant timestamp, double open, double high, double low, double close, long volume) {

    static PriceBarResponse from(Bar bar) {
        return new PriceBarResponse(bar.timestamp(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume());
    }
}
