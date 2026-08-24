package io.github.williamhuang1261.qrp.core;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * A sequence of values aligned index-for-index with a {@link BarSeries}.
 *
 * <p>Alignment is the whole point: element {@code i} is the indicator's value
 * <em>as of</em> bar {@code i}. Values that cannot exist yet, because the
 * indicator has not warmed up, are {@link Double#NaN} rather than 0 or a shorter
 * array. Padding with zeros silently feeds a strategy prices it never saw, and
 * shortening the array shifts every subsequent index by the warm-up length.
 */
public final class DoubleSeries {

    private final double[] values;

    private DoubleSeries(double[] values) {
        this.values = values;
    }

    public static DoubleSeries of(double... values) {
        Objects.requireNonNull(values, "values");
        return new DoubleSeries(values.clone());
    }

    /** {@code length} undefined (NaN) values, the starting point of most indicators. */
    public static DoubleSeries undefined(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative, got: " + length);
        }
        double[] values = new double[length];
        java.util.Arrays.fill(values, Double.NaN);
        return new DoubleSeries(values);
    }

    public int size() {
        return values.length;
    }

    public double get(int index) {
        return values[index];
    }

    /** False while the indicator is still warming up at {@code index}. */
    public boolean isDefined(int index) {
        return !Double.isNaN(values[index]);
    }

    /** Index of the first defined value, or -1 if every value is NaN. */
    public int firstDefinedIndex() {
        for (int i = 0; i < values.length; i++) {
            if (!Double.isNaN(values[i])) {
                return i;
            }
        }
        return -1;
    }

    /** The last defined value, if any. */
    public OptionalDouble lastDefined() {
        for (int i = values.length - 1; i >= 0; i--) {
            if (!Double.isNaN(values[i])) {
                return OptionalDouble.of(values[i]);
            }
        }
        return OptionalDouble.empty();
    }

    /** A defensive copy; the internal array is never handed out. */
    public double[] toArray() {
        return values.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DoubleSeries other && java.util.Arrays.equals(values, other.values);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "DoubleSeries[" + values.length + " values]";
    }
}
