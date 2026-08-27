package io.github.williamhuang1261.qrp.api;

import io.github.williamhuang1261.qrp.app.BacktestRunner;
import io.github.williamhuang1261.qrp.app.CliArguments;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint: run a backtest, return its summary. The controller's only job
 * is translating {@link RunRequest} into the {@code --flag value} list
 * {@link CliArguments#parse} already accepts, then handing the parsed record
 * to the unmodified {@link BacktestRunner#run}. Every default, every
 * validation rule and every error message here is the CLI's own, exercised
 * through its public API rather than re-implemented against a second copy of
 * the same rules.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    @PostMapping
    public RunResponse run(@RequestBody(required = false) RunRequest request) {
        RunRequest body = request == null ? RunRequest.empty() : request;
        CliArguments arguments = CliArguments.parse(toCliArgs(body));
        BacktestRunner.Outcome outcome = BacktestRunner.run(arguments);
        return RunResponse.from(outcome);
    }

    static List<String> toCliArgs(RunRequest request) {
        List<String> args = new ArrayList<>();
        addFlag(args, "--symbol", request.symbol());
        addFlag(args, "--timeframe", request.timeframe());
        addFlag(args, "--strategy", request.strategy());
        addFlag(args, "--cash", request.cash());
        addFlag(args, "--costs", request.costs());
        addFlag(args, "--paths", request.paths());
        addFlag(args, "--seed", request.seed());
        addFlag(args, "--execution", request.execution());
        addFlag(args, "--lob-spread", request.lobSpreadFraction());
        addFlag(args, "--lob-offset", request.lobOffsetLevels());
        addFlag(args, "--lob-levels", request.lobLevels());
        addFlag(args, "--lob-depth", request.lobDepthFraction());
        if (request.params() != null) {
            request.params().forEach((key, value) -> {
                args.add("--param");
                args.add(key + "=" + value);
            });
        }
        return args;
    }

    private static void addFlag(List<String> args, String flag, Object value) {
        if (value != null) {
            args.add(flag);
            args.add(String.valueOf(value));
        }
    }
}
