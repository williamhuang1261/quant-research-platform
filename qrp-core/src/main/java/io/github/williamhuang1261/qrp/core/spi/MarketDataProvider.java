package io.github.williamhuang1261.qrp.core.spi;

import io.github.williamhuang1261.qrp.core.BarSeries;
import io.github.williamhuang1261.qrp.core.Instrument;
import io.github.williamhuang1261.qrp.core.MarketDataException;
import io.github.williamhuang1261.qrp.core.Timeframe;
import java.time.Instant;
import java.util.List;

/**
 * A source of historical bars.
 *
 * <p>The public build ships a CSV provider over offline sample data. A live
 * provider, such as one backed by a broker API, is an implementation of this
 * interface that lives outside this repository: it needs credentials, a running
 * gateway and a market data subscription, none of which a reviewer cloning the
 * project has.
 */
public interface MarketDataProvider {

    String id();

    /** Instruments this provider can serve, for discovery in the CLI and the UI. */
    List<Instrument> available();

    /**
     * @param from inclusive lower bound on bar timestamps
     * @param to   exclusive upper bound
     * @return the bars in range, possibly empty, never null
     * @throws MarketDataException if the instrument is unknown or the data is unusable
     */
    BarSeries load(Instrument instrument, Timeframe timeframe, Instant from, Instant to);
}
