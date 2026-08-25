package io.github.williamhuang1261.qrp.engine;

import io.github.williamhuang1261.qrp.core.Timeframe;

/**
 * Bars per year for each timeframe, used to annualise volatility and Sharpe.
 *
 * <p>Based on 252 trading days and a 6.5 hour session, not on calendar time: a
 * daily series has 252 observations a year, not 365, and annualising by the
 * wrong constant misstates volatility by about 20 %.
 */
public final class Annualization {

    private static final double TRADING_DAYS = 252.0;
    private static final double SESSION_HOURS = 6.5;

    private Annualization() {
    }

    public static double periodsPerYear(Timeframe timeframe) {
        return switch (timeframe) {
            case MINUTE_1 -> TRADING_DAYS * SESSION_HOURS * 60.0;
            case MINUTE_5 -> TRADING_DAYS * SESSION_HOURS * 12.0;
            case MINUTE_15 -> TRADING_DAYS * SESSION_HOURS * 4.0;
            case HOUR_1 -> TRADING_DAYS * SESSION_HOURS;
            case DAY_1 -> TRADING_DAYS;
            case WEEK_1 -> 52.0;
        };
    }
}
