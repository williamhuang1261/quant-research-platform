package io.github.williamhuang1261.qrp.signals;

/**
 * Close-to-close forward returns, the thing a signal is actually scored
 * against.
 *
 * <p>Used only by {@link CrossSectionalSignalGenerator}'s validation tests —
 * never by the generator itself. The generator has no forward-looking input
 * of any kind; this class exists so a test can ask, after the fact, "did the
 * generated forecast have any real relationship to what happened next,"
 * without the generator ever being handed the answer.
 */
public final class ForwardReturns {

    private ForwardReturns() {
    }

    /**
     * @return {@code closes[index + horizonBars] / closes[index] - 1}, or
     *         {@code NaN} if the horizon runs past the end of the series
     */
    public static double forwardReturn(double[] closes, int index, int horizonBars) {
        if (horizonBars < 1) {
            throw new IllegalArgumentException("horizonBars must be positive, got: " + horizonBars);
        }
        if (index < 0 || index >= closes.length) {
            throw new IllegalArgumentException(
                    "index must lie within the series (length " + closes.length + "), got: " + index);
        }
        int target = index + horizonBars;
        if (target >= closes.length) {
            return Double.NaN;
        }
        return closes[target] / closes[index] - 1.0;
    }
}
