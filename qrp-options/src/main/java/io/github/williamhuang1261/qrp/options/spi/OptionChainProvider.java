package io.github.williamhuang1261.qrp.options.spi;

import io.github.williamhuang1261.qrp.options.OptionChainQuote;
import java.time.LocalDate;
import java.util.List;

/**
 * A source of option chain quotes for one underlying.
 *
 * <p>Lives in {@code qrp-options} rather than {@code qrp-core/spi}, unlike
 * {@code Indicator} or {@code MarketDataProvider}. Those live in core because
 * several independent modules implement or consume them across a dependency
 * boundary; nothing outside options needs this contract, and
 * {@code PluginRegistry<T>} is generic over any interface, so discovery through
 * {@link java.util.ServiceLoader} works identically without forcing core to
 * know an option chain's shape.
 *
 * <p>The public build ships one CSV-backed implementation over a synthetic
 * chain, generated from a known volatility surface so the surface-fitting code
 * can be checked against a ground truth rather than against itself. A live
 * provider -- backed by a vendor's option chain feed -- is an implementation of
 * this interface that lives outside this repository, exactly as
 * {@code TwsMarketDataProvider} does for bars.
 */
public interface OptionChainProvider {

    String id();

    /** Underlying symbols this provider has a chain for. */
    List<String> availableUnderlyings();

    /**
     * The full chain as of one valuation date: every strike and expiry this
     * provider quotes for the underlying.
     *
     * @throws IllegalArgumentException if the underlying is unknown
     */
    List<OptionChainQuote> chain(String underlyingSymbol, LocalDate valuationDate);
}
