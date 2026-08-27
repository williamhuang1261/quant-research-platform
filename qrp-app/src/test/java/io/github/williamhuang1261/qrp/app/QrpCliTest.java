package io.github.williamhuang1261.qrp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives the CLI end to end against the bundled data: data loading, indicator
 * and strategy discovery, the engine, the resampling and the formatting, in one
 * pass through the real entry point.
 */
class QrpCliTest {

    private record Run(int status, String out, String err) {
    }

    private static Run invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status;
        try (PrintStream outStream = new PrintStream(out, true, StandardCharsets.UTF_8);
                PrintStream errStream = new PrintStream(err, true, StandardCharsets.UTF_8)) {
            status = QrpCli.run(List.of(args), outStream, errStream);
        }
        return new Run(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("run reproduces the golden backtest through the whole stack")
    void runReproducesTheGoldenResult() {
        Run result = invoke("run", "--data", "../data/sample", "--symbol", "SYNA", "--paths", "0");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("92,229.01"), result.out());
        assertTrue(result.out().contains("-7.77%"), result.out());
        assertTrue(result.out().contains("-4.12%"), result.out());
        assertTrue(result.out().contains("24.15%"), result.out());
        assertTrue(result.out().contains("sma-crossover on SYNA.USD 1d"), result.out());
    }

    @Test
    @DisplayName("--execution=market-open is the default and reproduces the golden run's execution section")
    void marketOpenExecutionIsTheDefault() {
        Run explicit = invoke("run", "--data", "../data/sample", "--execution=market-open", "--paths", "0");
        Run implicit = invoke("run", "--data", "../data/sample", "--paths", "0");

        assertEquals(0, explicit.status(), explicit.err());
        assertEquals(implicit.out(), explicit.out());
        assertTrue(explicit.out().contains("execution: market-open"), explicit.out());
        assertTrue(explicit.out().contains("fill rate                                            100.00%"),
                explicit.out());
    }

    @Test
    @DisplayName("--execution=lob runs the same strategy and data through the synthetic order book")
    void lobExecutionCompletesAndReportsDifferentCosts() {
        Run marketOpen = invoke("run", "--data", "../data/sample", "--execution=market-open", "--paths", "0");
        Run lob = invoke("run", "--data", "../data/sample", "--execution=lob", "--paths", "0");

        assertEquals(0, lob.status(), lob.err());
        assertTrue(lob.out().contains("execution: lob"), lob.out());
        assertTrue(lob.out().contains("avg. slippage vs. reference"), lob.out());
        // Same strategy, same data, a genuinely different realized cost -- not
        // just a different label on an identical number.
        assertTrue(!lob.out().equals(marketOpen.out()), lob.out());
    }

    @Test
    @DisplayName("the lob model can decline fills the synthetic book won't support, dropping fill rate below 100%")
    void lobExecutionCanDeclineFills() {
        // offsetLevels below 1.0 rests the limit inside the synthetic spread,
        // short of the top of book on every bar -- documented on
        // LimitOrderBookExecutionModel as "a limit that may not even reach the
        // top of book." This is the model honestly reporting zero fills rather
        // than filling at a price the book never offered.
        Run declined = invoke("run", "--data", "../data/sample",
                "--execution=lob", "--lob-offset", "0.5", "--paths", "0");

        assertEquals(0, declined.status(), declined.err());
        assertTrue(declined.out().contains("execution: lob"), declined.out());
        assertTrue(declined.out().contains("fill rate                                              0.00%"),
                declined.out());
        assertTrue(declined.out().contains("fill attempts                                             11"),
                declined.out());
        assertTrue(declined.out().contains("fills completed                                            0"),
                declined.out());
    }

    @Test
    @DisplayName("--execution rejects anything that isn't market-open or lob")
    void unknownExecutionIsRejected() {
        Run result = invoke("run", "--data", "../data/sample", "--execution", "vwap");

        assertEquals(2, result.status());
        assertTrue(result.err().contains("--execution"), result.err());
    }

    @Test
    @DisplayName("the Monte Carlo section appears only when paths are requested")
    void monteCarloIsOptional() {
        Run without = invoke("run", "--data", "../data/sample", "--paths", "0");
        Run with = invoke("run", "--data", "../data/sample", "--paths", "500", "--seed", "1");

        assertTrue(!without.out().contains("Monte Carlo"), without.out());
        assertTrue(with.out().contains("Monte Carlo: 500 resampled paths"), with.out());
        assertTrue(with.out().contains("probability of loss"), with.out());
    }

    @Test
    @DisplayName("every report carries its caveats")
    void reportCarriesCaveats() {
        Run result = invoke("run", "--data", "../data/sample", "--paths", "0");

        assertTrue(result.out().contains("financing, borrow and taxes are not"), result.out());
    }

    @Test
    @DisplayName("list shows the plugins and instruments that are actually installed")
    void listShowsInstalledPlugins() {
        Run result = invoke("list", "--data", "../data/sample");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("sma"), result.out());
        assertTrue(result.out().contains("sma-crossover"), result.out());
        assertTrue(result.out().contains("java"), result.out());
        assertTrue(result.out().contains("<- selected"), result.out());
        assertTrue(result.out().contains("SYNETF"), result.out());
    }

    @Test
    @DisplayName("an unknown symbol is an error with the available ones listed")
    void unknownSymbolIsExplained() {
        Run result = invoke("run", "--data", "../data/sample", "--symbol", "NOPE");

        assertEquals(3, result.status());
        assertTrue(result.err().contains("SYNA"), result.err());
    }

    @Test
    @DisplayName("options prices the bundled synthetic chain, fits its surface and reports it clean")
    void optionsPricesTheBundledChain() {
        Run result = invoke("options", "--data", "../data/sample");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("volatility surface: SYNOPT"), result.out());
        assertTrue(result.out().contains("36 quotes, 4 expiries, 9 strikes"), result.out());
        assertTrue(result.out().contains("no-arbitrage diagnostics: clean"), result.out());
        // A spot check of one cell against a hand-verified value, so a refactor
        // that quietly changes the interpolation math fails a specific number,
        // not just the section header.
        assertTrue(result.out().contains("23.05%"), result.out());
    }

    @Test
    @DisplayName("options carries its own caveats about the flat rate and no extrapolation")
    void optionsReportCarriesCaveats() {
        Run result = invoke("options", "--data", "../data/sample");

        assertTrue(result.out().contains("RatesCurve"), result.out());
        assertTrue(result.out().contains("does not extrapolate"), result.out());
    }

    @Test
    @DisplayName("an unknown underlying is an error with the available ones listed")
    void unknownUnderlyingIsExplained() {
        Run result = invoke("options", "--data", "../data/sample", "--underlying", "NOPE");

        assertEquals(2, result.status());
        assertTrue(result.err().contains("SYNOPT"), result.err());
    }

    @Test
    @DisplayName("compare runs SYNA and SYNB against the SYNETF benchmark through the whole stack")
    void compareRunsAllInstrumentsThroughTheRealEntryPoint() {
        Run result = invoke("compare", "--data", "../data/sample");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("SYNA"), result.out());
        assertTrue(result.out().contains("SYNB"), result.out());
        assertTrue(result.out().contains("SYNETF (bench)"), result.out());
        assertTrue(result.out().contains("fund comparison"), result.out());
        assertTrue(result.out().contains("vs. bench"), result.out());
    }

    @Test
    @DisplayName("compare's benchmark row always prints last, and reports no bps gap to itself")
    void compareBenchmarkRowIsLastAndUnranked() {
        Run result = invoke("compare", "--data", "../data/sample");

        // Bound the search to the table itself: the closing narrative paragraph names the
        // net-return leader by symbol too, and that mention must not be mistaken for a table row.
        String out = result.out();
        String table = out.substring(0, out.indexOf("Ranked by net CAGR"));
        int lastCandidate = Math.max(table.lastIndexOf("SYNA "), table.lastIndexOf("SYNB "));
        int benchmark = table.indexOf("SYNETF (bench)");
        assertTrue(benchmark > lastCandidate, out);
    }

    @Test
    @DisplayName("compare rejects an unknown candidate symbol with the available ones listed")
    void compareUnknownSymbolIsExplained() {
        Run result = invoke("compare", "--data", "../data/sample", "--symbol", "NOPE");

        assertEquals(3, result.status());
        assertTrue(result.err().contains("SYNA"), result.err());
    }

    @Test
    @DisplayName("portfolio runs the equal risk contribution optimizer through the whole stack")
    void portfolioRunsRiskParityThroughTheRealEntryPoint() {
        Run result = invoke("portfolio", "--data", "../data/sample", "--optimizer=risk-parity");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("portfolio: SYNA, SYNB, SYNETF"), result.out());
        assertTrue(result.out().contains("optimizer: equal-risk-contribution"), result.out());
        // Pinned from PortfolioBacktestEngineTest's golden run: same series, same
        // schedule, same constraints, reached through the CLI this time.
        assertTrue(result.out().contains("74,144.67"), result.out());
        assertTrue(result.out().contains("1.6775"), result.out());
        assertTrue(result.out().contains("rebalances                                                22"), result.out());
    }

    @Test
    @DisplayName("portfolio runs the mean-variance optimizer through the whole stack")
    void portfolioRunsMeanVarianceThroughTheRealEntryPoint() {
        Run result = invoke("portfolio", "--data", "../data/sample", "--optimizer=mean-variance");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("optimizer: mean-variance"), result.out());
        assertTrue(result.out().contains("SYNA"), result.out());
        assertTrue(result.out().contains("SYNB"), result.out());
        assertTrue(result.out().contains("SYNETF"), result.out());
        assertTrue(result.out().contains("avg. risk contribution"), result.out());
    }

    @Test
    @DisplayName("portfolio defaults to risk-parity and monthly rebalance with no flags")
    void portfolioDefaultsToRiskParityMonthly() {
        Run explicit = invoke("portfolio", "--data", "../data/sample", "--optimizer=risk-parity",
                "--rebalance=monthly");
        Run implicit = invoke("portfolio", "--data", "../data/sample");

        assertEquals(0, implicit.status(), implicit.err());
        assertEquals(explicit.out(), implicit.out());
    }

    @Test
    @DisplayName("portfolio's --rebalance=weekly runs a different schedule than the monthly default")
    void portfolioWeeklyRebalanceDiffersFromMonthly() {
        Run monthly = invoke("portfolio", "--data", "../data/sample", "--rebalance=monthly");
        Run weekly = invoke("portfolio", "--data", "../data/sample", "--rebalance=weekly");

        assertEquals(0, weekly.status(), weekly.err());
        assertTrue(weekly.out().contains("rebalance: weekly"), weekly.out());
        assertTrue(!weekly.out().equals(monthly.out()), weekly.out());
    }

    @Test
    @DisplayName("portfolio rejects an unknown symbol with the available ones listed")
    void portfolioUnknownSymbolIsExplained() {
        Run result = invoke("portfolio", "--data", "../data/sample", "--symbol", "NOPE", "--symbol", "SYNB");

        assertEquals(3, result.status());
        assertTrue(result.err().contains("SYNA"), result.err());
    }

    @Test
    @DisplayName("portfolio rejects an unknown optimizer or rebalance value")
    void portfolioRejectsBadFlags() {
        assertEquals(2, invoke("portfolio", "--optimizer", "vwap").status());
        assertEquals(2, invoke("portfolio", "--rebalance", "daily").status());
    }

    @Test
    @DisplayName("portfolio --signal drives the view from a generated indicator forecast and reports its IC")
    void portfolioSignalDrivesTheViewAndReportsIc() {
        Run result = invoke("portfolio", "--data", "../data/sample", "--optimizer=mean-variance", "--signal=rsi");

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("signal: rsi ("), result.out());
        assertTrue(result.out().contains("mean IC:"), result.out());
        // Pinned from CrossSectionalSignalGeneratorGoldenRunTest's golden run: same
        // series, same indicator, same forward horizon, reached through the CLI.
        assertTrue(result.out().contains("(485 periods)"), result.out());
        assertTrue(result.out().contains("significant at 5%: no"), result.out());
    }

    @Test
    @DisplayName("without --signal, the report carries no signal section")
    void portfolioWithoutSignalOmitsTheSignalSection() {
        Run result = invoke("portfolio", "--data", "../data/sample");

        assertEquals(0, result.status(), result.err());
        assertTrue(!result.out().contains("mean IC:"), result.out());
    }

    @Test
    @DisplayName("portfolio rejects an unknown --signal indicator id with the available ones listed")
    void portfolioUnknownSignalIsExplained() {
        Run result = invoke("portfolio", "--data", "../data/sample", "--signal=nope");

        assertEquals(2, result.status());
        assertTrue(result.err().contains("nope"), result.err());
        assertTrue(result.err().contains("rsi"), result.err());
    }

    @Test
    @DisplayName("bad usage exits non-zero and prints why")
    void badUsageIsRejected() {
        assertEquals(2, invoke("run", "--nope").status());
        assertEquals(2, invoke("options", "--nope").status());
        assertEquals(2, invoke("compare", "--nope").status());
        assertEquals(2, invoke("portfolio", "--nope").status());
        assertEquals(2, invoke("frobnicate").status());
        assertEquals(1, invoke().status());
        assertEquals(0, invoke("--help").status());
    }
}
