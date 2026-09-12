package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.app.CliArguments;
import io.github.williamhuang1261.qrp.pnl.PnlAttribution;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint: run a backtest and return its P&amp;L attribution. A third
 * caller of {@link BacktestRunner#run} alongside the CLI and {@link
 * RunController}, reusing {@link CliArguments#parse} for every flag it
 * shares with a normal run, so the same defaults, validation and error
 * messages apply here too.
 *
 * <p>Unlike {@link RunController} and {@link ReportController}, this
 * endpoint never persists: attribution is cheap, derived arithmetic over a
 * run's own equity curve and trade list ({@link PnlAttribution#of}),
 * recomputed on every call rather than cached behind the warehouse.
 */
@RestController
@RequestMapping("/api/pnl")
public class PnlController {

    @GetMapping("/attribution")
    public PnlAttributionResponse attribution(
            @RequestParam(name = "symbol", required = false) String symbol,
            @RequestParam(name = "timeframe", required = false) String timeframe,
            @RequestParam(name = "strategy", required = false) String strategy,
            @RequestParam(name = "cash", required = false) Double cash,
            @RequestParam(name = "costs", required = false) String costs,
            @RequestParam(name = "annualCarryRate", required = false, defaultValue = "0.0") double annualCarryRate) {
        List<String> args = new ArrayList<>();
        addFlag(args, "--symbol", symbol);
        addFlag(args, "--timeframe", timeframe);
        addFlag(args, "--strategy", strategy);
        addFlag(args, "--cash", cash);
        addFlag(args, "--costs", costs);
        // Attribution has no use for the Monte Carlo report; skip it entirely.
        addFlag(args, "--paths", 0);

        CliArguments arguments = CliArguments.parse(args);
        BacktestRunner.Outcome outcome = BacktestRunner.run(arguments);
        PnlAttribution attribution = PnlAttribution.of(outcome.result(), annualCarryRate);

        return PnlAttributionResponse.from(outcome, attribution);
    }

    private static void addFlag(List<String> args, String flag, Object value) {
        if (value != null) {
            args.add(flag);
            args.add(String.valueOf(value));
        }
    }
}
