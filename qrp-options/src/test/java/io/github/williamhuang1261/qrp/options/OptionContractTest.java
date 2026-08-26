package io.github.williamhuang1261.qrp.options;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.williamhuang1261.qrp.core.AssetClass;
import io.github.williamhuang1261.qrp.core.Instrument;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OptionContractTest {

    private static final Instrument SYNA = new Instrument("SYNA", "USD", AssetClass.EQUITY);
    private static final LocalDate EXPIRY = LocalDate.of(2027, 1, 15);

    @Test
    @DisplayName("year fraction is ACT/365 fixed")
    void yearFractionIsAct365Fixed() {
        OptionContract contract = OptionContract.european(SYNA, OptionType.CALL, 100.0, EXPIRY);

        // 2026-01-15 to 2027-01-15 is 365 days; neither year is a leap year.
        assertEquals(1.0, contract.yearsTo(LocalDate.of(2026, 1, 15)), 1e-15);
        assertEquals(30.0 / 365.0, contract.yearsTo(LocalDate.of(2026, 12, 16)), 1e-15);
        assertEquals(0.0, contract.yearsTo(EXPIRY), 1e-15);
    }

    @Test
    @DisplayName("an expired contract is refused rather than given a negative life")
    void refusesAnExpiredContract() {
        OptionContract contract = OptionContract.european(SYNA, OptionType.CALL, 100.0, EXPIRY);

        assertThrows(
                IllegalArgumentException.class,
                () -> contract.yearsTo(LocalDate.of(2027, 1, 16)));
        assertTrue(contract.isExpiredOn(LocalDate.of(2027, 1, 15)));
        assertTrue(!contract.isExpiredOn(LocalDate.of(2027, 1, 14)));
    }

    @Test
    @DisplayName("intrinsic value floors at zero on both sides")
    void intrinsicValueFloorsAtZero() {
        OptionContract call = OptionContract.european(SYNA, OptionType.CALL, 100.0, EXPIRY);
        OptionContract put = OptionContract.european(SYNA, OptionType.PUT, 100.0, EXPIRY);

        assertEquals(20.0, call.intrinsicValue(120.0), 1e-12);
        assertEquals(0.0, call.intrinsicValue(80.0), 1e-12);
        assertEquals(0.0, put.intrinsicValue(120.0), 1e-12);
        assertEquals(20.0, put.intrinsicValue(80.0), 1e-12);
    }

    @Test
    @DisplayName("flipping the type keeps every other term")
    void flipTypeKeepsTheRest() {
        OptionContract call = OptionContract.american(SYNA, OptionType.CALL, 95.0, EXPIRY);
        OptionContract put = call.flipType();

        assertEquals(OptionType.PUT, put.type());
        assertEquals(call.strike(), put.strike(), 1e-15);
        assertEquals(call.expiry(), put.expiry());
        assertEquals(call.style(), put.style());
        assertEquals(call.underlying(), put.underlying());
    }

    @Test
    @DisplayName("listed equity options default to a 100 share multiplier")
    void defaultsToTheEquityMultiplier() {
        assertEquals(
                100.0,
                OptionContract.european(SYNA, OptionType.CALL, 100.0, EXPIRY).contractMultiplier(),
                1e-15);
    }

    @Test
    @DisplayName("rejects a non-positive strike or multiplier")
    void rejectsImpossibleTerms() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OptionContract.european(SYNA, OptionType.CALL, 0.0, EXPIRY));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OptionContract(
                        SYNA, OptionType.CALL, ExerciseStyle.EUROPEAN, 100.0, EXPIRY, 0.0));
        assertThrows(
                NullPointerException.class,
                () -> OptionContract.european(SYNA, OptionType.CALL, 100.0, null));
    }

    @Test
    @DisplayName("payoff and sign are consistent for both types")
    void typeHelpersAreConsistent() {
        assertEquals(1.0, OptionType.CALL.sign(), 1e-15);
        assertEquals(-1.0, OptionType.PUT.sign(), 1e-15);
        assertEquals(OptionType.PUT, OptionType.CALL.opposite());
        assertEquals(5.0, OptionType.CALL.payoff(105.0, 100.0), 1e-12);
        assertEquals(0.0, OptionType.CALL.payoff(95.0, 100.0), 1e-12);
    }
}
