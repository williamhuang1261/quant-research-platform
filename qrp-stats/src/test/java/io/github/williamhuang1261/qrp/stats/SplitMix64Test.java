package io.github.williamhuang1261.qrp.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SplitMix64Test {

    @Test
    @DisplayName("reproduces the published splitmix64 vectors from seed 0")
    void matchesPublishedVectors() {
        SplitMix64 random = new SplitMix64(0L);

        // The reference sequence; the C++ kernel must produce these same words.
        assertEquals(0xE220A8397B1DCDAFL, random.nextLong());
        assertEquals(0x6E789E6AA1B965F4L, random.nextLong());
        assertEquals(0x06C45D188009454FL, random.nextLong());
        assertEquals(0xF88BB8A8724C81ECL, random.nextLong());
    }

    @Test
    @DisplayName("the same seed replays the same stream")
    void isReproducible() {
        SplitMix64 first = new SplitMix64(12345L);
        SplitMix64 second = new SplitMix64(12345L);

        for (int i = 0; i < 100; i++) {
            assertEquals(first.nextLong(), second.nextLong());
        }
    }

    @Test
    @DisplayName("draw streams are independent of each other and of execution order")
    void perDrawStreamsAreIndependent() {
        long a = SplitMix64.forDraw(7L, 0).nextLong();
        long b = SplitMix64.forDraw(7L, 1).nextLong();

        assertNotEquals(a, b);
        // Reading draw 1 first must not change what draw 0 yields.
        assertEquals(a, SplitMix64.forDraw(7L, 0).nextLong());
    }

    @Test
    @DisplayName("nextInt stays inside the bound and covers it")
    void nextIntIsBounded() {
        SplitMix64 random = new SplitMix64(99L);
        boolean[] seen = new boolean[5];

        for (int i = 0; i < 500; i++) {
            int value = random.nextInt(5);
            assertTrue(value >= 0 && value < 5, "out of range: " + value);
            seen[value] = true;
        }

        for (int value = 0; value < 5; value++) {
            assertTrue(seen[value], "never drew " + value);
        }
    }

    @Test
    @DisplayName("nextDouble lies in [0, 1) with a mean near a half")
    void nextDoubleIsUniform() {
        SplitMix64 random = new SplitMix64(2024L);
        double sum = 0.0;
        int draws = 100_000;

        for (int i = 0; i < draws; i++) {
            double value = random.nextDouble();
            assertTrue(value >= 0.0 && value < 1.0, "out of range: " + value);
            sum += value;
        }

        assertEquals(0.5, sum / draws, 0.01);
    }

    @Test
    @DisplayName("a non-positive bound is a programming error")
    void rejectsBadBound() {
        assertThrows(IllegalArgumentException.class, () -> new SplitMix64(1L).nextInt(0));
    }
}
