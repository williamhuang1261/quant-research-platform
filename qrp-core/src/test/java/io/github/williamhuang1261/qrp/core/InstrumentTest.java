package io.github.williamhuang1261.qrp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InstrumentTest {

    @Test
    @DisplayName("normalises symbol and currency so lookups are case insensitive")
    void normalisesIdentifiers() {
        Instrument instrument = new Instrument(" msft ", "usd", AssetClass.EQUITY);

        assertEquals("MSFT", instrument.symbol());
        assertEquals("USD", instrument.currency());
        assertEquals("MSFT.USD", instrument.toString());
    }

    @Test
    @DisplayName("rejects a blank symbol or a non ISO currency")
    void rejectsUnusableIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> new Instrument("  ", "USD", AssetClass.EQUITY));
        assertThrows(IllegalArgumentException.class,
                () -> new Instrument("AAPL", "DOLLARS", AssetClass.EQUITY));
    }

    @Test
    @DisplayName("equity() is a USD equity")
    void equityHelper() {
        assertEquals(new Instrument("AAPL", "USD", AssetClass.EQUITY), Instrument.equity("AAPL"));
    }
}
