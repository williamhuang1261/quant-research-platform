package io.github.williamhuang1261.qrp.core;

/**
 * What a strategy wants, expressed as target exposure in {@code [-1, 1]}: the
 * fraction of the account to hold long (positive) or short (negative).
 *
 * <p>Strategies state intent, the engine decides fills. Target exposure rather
 * than BUY/SELL orders keeps position sizing, commission and slippage inside the
 * execution model, where they can be changed without touching a strategy, and
 * makes a strategy's output comparable across instruments of different prices.
 */
public record Signal(double targetExposure) {

    public Signal {
        if (!Double.isFinite(targetExposure)) {
            throw new IllegalArgumentException("targetExposure must be finite, got: " + targetExposure);
        }
        if (targetExposure < -1.0 || targetExposure > 1.0) {
            throw new IllegalArgumentException(
                    "targetExposure must lie in [-1, 1], got: " + targetExposure);
        }
    }

    public static Signal flat() {
        return new Signal(0.0);
    }

    public static Signal fullyLong() {
        return new Signal(1.0);
    }

    public static Signal fullyShort() {
        return new Signal(-1.0);
    }

    public boolean isFlat() {
        return targetExposure == 0.0;
    }

    public boolean isLong() {
        return targetExposure > 0.0;
    }

    public boolean isShort() {
        return targetExposure < 0.0;
    }
}
