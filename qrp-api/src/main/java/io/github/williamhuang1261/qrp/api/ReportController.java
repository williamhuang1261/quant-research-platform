package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.CompareArguments;
import io.github.williamhuang1261.qrp.app.CompareRunner;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint: run the fund comparison report, return it as JSON. A second
 * caller of the platform's existing entry points, alongside {@code qrp
 * compare} -- this controller's only job is translating query parameters into
 * the exact {@code --flag value} list {@link CompareArguments#parse} already
 * accepts, then handing the parsed record to the unmodified
 * {@link CompareRunner#run}. Every default, every validation rule and every
 * number in the response -- including the AI-narrative text -- is
 * {@code qrp-report}'s own, exercised through its public API rather than
 * re-implemented against a second copy of the same rules.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @GetMapping("/compare")
    public ReportResponse compare(
            @RequestParam(name = "symbol", required = false) List<String> symbol,
            @RequestParam(name = "benchmark", required = false) String benchmark,
            @RequestParam(name = "timeframe", required = false) String timeframe,
            @RequestParam(name = "strategy", required = false) String strategy,
            @RequestParam(name = "cash", required = false) Double cash,
            @RequestParam(name = "costs", required = false) String costs,
            @RequestParam(name = "fee", required = false) Double fee,
            @RequestParam(name = "benchmarkFee", required = false) Double benchmarkFee,
            @RequestParam(name = "narrative", required = false) String narrative) {
        List<String> args = new ArrayList<>();
        if (symbol != null) {
            symbol.forEach(value -> addFlag(args, "--symbol", value));
        }
        addFlag(args, "--benchmark", benchmark);
        addFlag(args, "--timeframe", timeframe);
        addFlag(args, "--strategy", strategy);
        addFlag(args, "--cash", cash);
        addFlag(args, "--costs", costs);
        addFlag(args, "--fee", fee);
        addFlag(args, "--benchmark-fee", benchmarkFee);
        addFlag(args, "--narrative", narrative);

        CompareArguments arguments = CompareArguments.parse(args);
        CompareRunner.Outcome outcome = CompareRunner.run(arguments);
        return ReportResponse.from(outcome);
    }

    private static void addFlag(List<String> args, String flag, Object value) {
        if (value != null) {
            args.add(flag);
            args.add(String.valueOf(value));
        }
    }
}
