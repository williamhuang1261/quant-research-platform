package io.github.williamhuang1261.qrp.api;

import java.util.Map;

/**
 * A backtest run, described the same way the CLI's own flags describe one.
 *
 * <p>Every field is nullable and optional: an absent field means "use the CLI's
 * own default," so an empty body reproduces {@code qrp run} with no flags,
 * including its pinned golden-run numbers. No field here invents a rule
 * {@link io.github.williamhuang1261.qrp.app.CliArguments#parse} does not
 * already enforce; this record only carries values to it.
 */
public record RunRequest(
        String symbol,
        String timeframe,
        String strategy,
        Map<String, Double> params,
        Double cash,
        String costs,
        Integer paths,
        Long seed,
        String execution,
        Double lobSpreadFraction,
        Double lobOffsetLevels,
        Integer lobLevels,
        Double lobDepthFraction) {

    /** The all-defaults run: what an absent request body is treated as. */
    static RunRequest empty() {
        return new RunRequest(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
