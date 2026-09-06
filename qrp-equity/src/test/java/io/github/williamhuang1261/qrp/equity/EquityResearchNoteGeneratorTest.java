package io.github.williamhuang1261.qrp.equity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquityResearchNoteGeneratorTest {

    private static final EquityFundamentals SAMPLE_FUNDAMENTALS = new EquityFundamentals(
            "AAPL", "Apple Inc.", "Technology", "2026-09-06",
            466_822_987_776.0, 8.74, 0.27618998, 14_594_180_000.0, 36.60984, 319.97, 107_721_875_456.0);

    private static EquityResearchNoteGenerator.NoteInputs sampleInputs() {
        return new EquityResearchNoteGenerator.NoteInputs(
                SAMPLE_FUNDAMENTALS,
                350.00,
                300.00,
                "Apple's next quarterly earnings call is the near-term catalyst: iPhone unit "
                        + "growth and Services segment margin trends will decide whether the market's "
                        + "current multiple is justified.",
                List.of("A slowdown in iPhone replacement cycles.", "Regulatory pressure on App Store fees."));
    }

    @Test
    @DisplayName("the note contains all four required section headers")
    void containsAllSectionHeaders() {
        String note = EquityResearchNoteGenerator.generate(sampleInputs());

        assertTrue(note.contains("## Business Summary"));
        assertTrue(note.contains("## Catalyst / Thesis"));
        assertTrue(note.contains("## Valuation Summary"));
        assertTrue(note.contains("## Key Risks"));
    }

    @Test
    @DisplayName("the note carries the company name, ticker and the hand-written thesis verbatim")
    void containsCompanyIdentityAndThesis() {
        String note = EquityResearchNoteGenerator.generate(sampleInputs());

        assertTrue(note.contains("Apple Inc."));
        assertTrue(note.contains("AAPL"));
        assertTrue(note.contains("iPhone unit growth"));
    }

    @Test
    @DisplayName("the note round-trips the DCF and comps values passed in")
    void roundTripsValuationNumbers() {
        String note = EquityResearchNoteGenerator.generate(sampleInputs());

        assertTrue(note.contains("$350.00"));
        assertTrue(note.contains("$300.00"));
        assertTrue(note.contains("$319.97"));
    }

    @Test
    @DisplayName("every hand-written risk appears as its own bullet")
    void listsEveryKeyRisk() {
        String note = EquityResearchNoteGenerator.generate(sampleInputs());

        assertTrue(note.contains("- A slowdown in iPhone replacement cycles."));
        assertTrue(note.contains("- Regulatory pressure on App Store fees."));
    }

    @Test
    @DisplayName("a blank catalyst thesis is rejected")
    void blankCatalystThesisRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EquityResearchNoteGenerator.NoteInputs(
                SAMPLE_FUNDAMENTALS, 350.0, 300.0, "  ", List.of("some risk")));
    }

    @Test
    @DisplayName("an empty key-risks list is rejected")
    void emptyKeyRisksRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EquityResearchNoteGenerator.NoteInputs(
                SAMPLE_FUNDAMENTALS, 350.0, 300.0, "some thesis", List.of()));
    }

    @Test
    @DisplayName("a non-positive DCF fair value is rejected")
    void nonPositiveDcfValueRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EquityResearchNoteGenerator.NoteInputs(
                SAMPLE_FUNDAMENTALS, -1.0, 300.0, "some thesis", List.of("some risk")));
    }
}
