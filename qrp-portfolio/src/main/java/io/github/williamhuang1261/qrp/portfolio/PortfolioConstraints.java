package io.github.williamhuang1261.qrp.portfolio;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The guidelines an optimizer must respect exactly, not merely approach.
 *
 * <p>{@code sectors} assigns each instrument (by index, same order as the
 * optimizer's other arrays) to a sector label; {@code sectorCaps} bounds the
 * total weight any one sector may hold. A portfolio with no sector guidelines
 * passes an empty {@code sectors} list and an empty {@code sectorCaps} map —
 * the two must agree on that: either both are empty, or every sector named in
 * {@code sectorCaps} appears at least once in {@code sectors}, and every
 * instrument has an entry in {@code sectors}.
 *
 * @param maxWeight   the largest fraction of capital any single instrument may
 *                    hold, in {@code (0, 1]}
 * @param maxTurnover the largest total absolute change in weights a single
 *                    rebalance may make (sum of {@code |newWeight - oldWeight|}
 *                    across instruments), non-negative; {@code Double.MAX_VALUE}
 *                    for "unconstrained"
 * @param leverage    the target sum of weights, e.g. {@code 1.0} for fully
 *                    invested and not levered; positive
 * @param sectors     per-instrument sector labels, same order and length as the
 *                    optimizer's instrument arrays, or empty for no sector guideline
 * @param sectorCaps  sector label to maximum total weight in that sector, or
 *                    empty for no sector guideline
 */
public record PortfolioConstraints(
        double maxWeight,
        double maxTurnover,
        double leverage,
        List<String> sectors,
        Map<String, Double> sectorCaps) {

    public PortfolioConstraints {
        Objects.requireNonNull(sectors, "sectors");
        Objects.requireNonNull(sectorCaps, "sectorCaps");
        if (!(maxWeight > 0.0 && maxWeight <= 1.0)) {
            throw new IllegalArgumentException("maxWeight must lie in (0, 1], got: " + maxWeight);
        }
        if (!(maxTurnover >= 0.0)) {
            throw new IllegalArgumentException("maxTurnover must be non-negative, got: " + maxTurnover);
        }
        if (!(leverage > 0.0)) {
            throw new IllegalArgumentException("leverage must be positive, got: " + leverage);
        }
        if (sectors.isEmpty() != sectorCaps.isEmpty()) {
            throw new IllegalArgumentException(
                    "sectors and sectorCaps must both be empty or both be populated");
        }
        for (String sector : sectorCaps.keySet()) {
            if (!sectors.contains(sector)) {
                throw new IllegalArgumentException(
                        "sectorCaps names a sector with no instrument assigned to it: " + sector);
            }
        }
        for (double cap : sectorCaps.values()) {
            if (!(cap > 0.0 && cap <= leverage)) {
                throw new IllegalArgumentException(
                        "sector cap must lie in (0, leverage], got: " + cap + " with leverage " + leverage);
            }
        }
        sectors = List.copyOf(sectors);
        sectorCaps = Map.copyOf(sectorCaps);
    }

    /** No sector guideline, fully invested, no leverage. */
    public static PortfolioConstraints longOnly(double maxWeight, double maxTurnover) {
        return new PortfolioConstraints(maxWeight, maxTurnover, 1.0, List.of(), Map.of());
    }

    public boolean hasSectorCaps() {
        return !sectorCaps.isEmpty();
    }
}
