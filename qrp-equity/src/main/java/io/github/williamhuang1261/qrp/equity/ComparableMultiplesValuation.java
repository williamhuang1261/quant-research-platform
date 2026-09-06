package io.github.williamhuang1261.qrp.equity;

import java.util.List;

/**
 * Implies a fair value per share from a peer group's trailing
 * price-to-earnings multiples: the average peer P/E applied to this
 * company's own trailing EPS.
 *
 * <p>A comps check exists to catch a DCF that has drifted from how the
 * market actually prices similar businesses -- it does not replace the DCF,
 * it cross-checks it. Averaging peer P/E equally, rather than weighting by
 * market cap or similarity, is the same "simple, stated, honest" choice
 * {@code qrp-realassets}'s direct-capitalization approach makes: a real
 * comps desk would screen for size and growth similarity first, and that
 * screening is a stated, out-of-scope extension here, not something this
 * module pretends to do.
 */
public final class ComparableMultiplesValuation {

    private ComparableMultiplesValuation() {
    }

    /** The unweighted mean of the peer trailing P/E multiples. */
    public static double averagePeerPe(List<Double> peerTrailingPe) {
        if (peerTrailingPe == null || peerTrailingPe.isEmpty()) {
            throw new IllegalArgumentException("need at least one peer P/E");
        }
        double sum = 0.0;
        for (double pe : peerTrailingPe) {
            if (!(pe > 0.0) || !Double.isFinite(pe)) {
                throw new IllegalArgumentException("peer P/E must be positive and finite, got: " + pe);
            }
            sum += pe;
        }
        return sum / peerTrailingPe.size();
    }

    /** This company's trailing EPS times the peer group's average trailing P/E. */
    public static double impliedValuePerShare(double trailingEps, List<Double> peerTrailingPe) {
        if (!Double.isFinite(trailingEps)) {
            throw new IllegalArgumentException("trailingEps must be finite, got: " + trailingEps);
        }
        return trailingEps * averagePeerPe(peerTrailingPe);
    }
}
