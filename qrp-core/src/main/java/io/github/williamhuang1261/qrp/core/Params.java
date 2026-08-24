package io.github.williamhuang1261.qrp.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable named numeric parameters for an indicator or a strategy.
 *
 * <p>A map of doubles rather than a typed record per algorithm: parameters
 * arrive from the CLI, from the UI and from a sweep, none of which know the
 * concrete type of the plugin they are configuring. {@link #requireInt} exists
 * so a window length that arrives as {@code 14.5} fails loudly at the boundary
 * instead of being truncated somewhere inside a loop.
 */
public final class Params {

    private static final Params EMPTY = new Params(Map.of());

    private final Map<String, Double> values;

    private Params(Map<String, Double> values) {
        this.values = values;
    }

    public static Params empty() {
        return EMPTY;
    }

    public static Params of(String key, double value) {
        return empty().with(key, value);
    }

    public static Params from(Map<String, Double> values) {
        Objects.requireNonNull(values, "values");
        values.forEach(Params::validate);
        return new Params(Map.copyOf(values));
    }

    /** This instance is unchanged; a new one is returned. */
    public Params with(String key, double value) {
        validate(key, value);
        Map<String, Double> merged = new LinkedHashMap<>(values);
        merged.put(key, value);
        return new Params(Map.copyOf(merged));
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public double require(String key) {
        Double value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "missing required parameter '" + key + "', have: " + values.keySet());
        }
        return value;
    }

    public double getOrDefault(String key, double fallback) {
        return values.getOrDefault(key, fallback);
    }

    /** @throws IllegalArgumentException if the value is absent or not integral */
    public int requireInt(String key) {
        double value = require(key);
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException(
                    "parameter '" + key + "' must be a whole number, got: " + value);
        }
        return (int) value;
    }

    public int getIntOrDefault(String key, int fallback) {
        return has(key) ? requireInt(key) : fallback;
    }

    public Map<String, Double> asMap() {
        return values;
    }

    private static void validate(String key, Double value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value for " + key);
        if (key.isBlank()) {
            throw new IllegalArgumentException("parameter name must not be blank");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "parameter '" + key + "' must be finite, got: " + value);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Params other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
