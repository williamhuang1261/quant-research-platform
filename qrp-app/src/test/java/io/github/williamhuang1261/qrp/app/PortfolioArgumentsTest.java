package io.github.williamhuang1261.qrp.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.williamhuang1261.qrp.engine.CostModel;
import io.github.williamhuang1261.qrp.portfolio.PortfolioBacktestEngine.RebalanceFrequency;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioArgumentsTest {

    @Test
    @DisplayName("runs with no flags at all, on the bundled synthetic series")
    void defaultsAreUsable() {
        PortfolioArguments arguments = PortfolioArguments.parse(List.of());

        assertEquals(List.of("SYNA", "SYNB", "SYNETF"), arguments.symbols());
        assertEquals(PortfolioArguments.OptimizerKind.RISK_PARITY, arguments.optimizer());
        assertEquals(RebalanceFrequency.MONTHLY, arguments.rebalance());
        assertEquals(60, arguments.covarianceLookbackBars());
        assertEquals(100_000.0, arguments.initialCash(), 1e-12);
        assertEquals(CostModel.retail(), arguments.costs());
        assertEquals(0.5, arguments.maxWeight(), 1e-12);
        assertEquals(Double.MAX_VALUE, arguments.maxTurnover(), 0.0);
    }

    @Test
    @DisplayName("--optimizer accepts both the space-separated and inline = forms")
    void optimizerAcceptsBothFlagForms() {
        assertEquals(PortfolioArguments.OptimizerKind.MEAN_VARIANCE,
                PortfolioArguments.parse(List.of("--optimizer", "mean-variance")).optimizer());
        assertEquals(PortfolioArguments.OptimizerKind.MEAN_VARIANCE,
                PortfolioArguments.parse(List.of("--optimizer=mean-variance")).optimizer());
    }

    @Test
    @DisplayName("--rebalance accepts both the space-separated and inline = forms")
    void rebalanceAcceptsBothFlagForms() {
        assertEquals(RebalanceFrequency.WEEKLY,
                PortfolioArguments.parse(List.of("--rebalance", "weekly")).rebalance());
        assertEquals(RebalanceFrequency.WEEKLY,
                PortfolioArguments.parse(List.of("--rebalance=weekly")).rebalance());
    }

    @Test
    @DisplayName("--symbol is repeatable and overrides the three-instrument default")
    void symbolIsRepeatable() {
        PortfolioArguments arguments = PortfolioArguments.parse(
                List.of("--symbol", "SYNA", "--symbol", "SYNB"));

        assertEquals(List.of("SYNA", "SYNB"), arguments.symbols());
    }

    @Test
    @DisplayName("a single --symbol is rejected: a portfolio needs at least two instruments")
    void singleSymbolIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioArguments.parse(List.of("--symbol", "SYNA")));
    }

    @Test
    @DisplayName("--turnover none means unconstrained, and a number sets the cap")
    void turnoverAcceptsNoneOrANumber() {
        assertEquals(Double.MAX_VALUE,
                PortfolioArguments.parse(List.of("--turnover", "none")).maxTurnover(), 0.0);
        assertEquals(0.25,
                PortfolioArguments.parse(List.of("--turnover", "0.25")).maxTurnover(), 1e-12);
    }

    @Test
    @DisplayName("parses the remaining options")
    void parsesEveryOption() {
        PortfolioArguments arguments = PortfolioArguments.parse(List.of(
                "--data", "/tmp/data", "--lookback", "40", "--cash", "50000", "--costs", "none",
                "--max-weight", "0.4", "--risk-aversion", "2.5"));

        assertEquals("/tmp/data", arguments.dataDirectory().toString());
        assertEquals(40, arguments.covarianceLookbackBars());
        assertEquals(50_000.0, arguments.initialCash(), 1e-12);
        assertEquals(CostModel.none(), arguments.costs());
        assertEquals(0.4, arguments.maxWeight(), 1e-12);
        assertEquals(2.5, arguments.riskAversion(), 1e-12);
    }

    @Test
    @DisplayName("an unknown option is rejected with a usable message")
    void unknownOptionIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PortfolioArguments.parse(List.of("--nope")));
        assertEquals("unknown option: --nope", e.getMessage());
    }

    @Test
    @DisplayName("an unknown optimizer or rebalance value is rejected with a usable message")
    void unknownEnumValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioArguments.parse(List.of("--optimizer", "vwap")));
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioArguments.parse(List.of("--rebalance", "daily")));
    }

    @Test
    @DisplayName("with no --signal, signalIndicatorId is null and the momentum view is used")
    void noSignalByDefault() {
        PortfolioArguments arguments = PortfolioArguments.parse(List.of());
        assertNull(arguments.signalIndicatorId());
        assertEquals(14, arguments.signalPeriod());
        assertEquals(0.02, arguments.signalSpread(), 1e-12);
    }

    @Test
    @DisplayName("--signal accepts both the space-separated and inline = forms")
    void signalAcceptsBothFlagForms() {
        assertEquals("rsi", PortfolioArguments.parse(List.of("--signal", "rsi")).signalIndicatorId());
        assertEquals("rsi", PortfolioArguments.parse(List.of("--signal=rsi")).signalIndicatorId());
    }

    @Test
    @DisplayName("--signal-period and --signal-spread override their defaults")
    void signalPeriodAndSpreadAreParsed() {
        PortfolioArguments arguments = PortfolioArguments.parse(
                List.of("--signal", "rsi", "--signal-period", "20", "--signal-spread", "0.05"));
        assertEquals(20, arguments.signalPeriod());
        assertEquals(0.05, arguments.signalSpread(), 1e-12);
    }

    @Test
    @DisplayName("a non-positive --signal-spread or a sub-1 --signal-period is rejected")
    void signalSpreadAndPeriodAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioArguments.parse(List.of("--signal", "rsi", "--signal-spread", "0.0")));
        assertThrows(IllegalArgumentException.class,
                () -> PortfolioArguments.parse(List.of("--signal", "rsi", "--signal-period", "0")));
    }
}
