package io.github.williamhuang1261.qrp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the endpoint over real HTTP request/response handling (MockMvc,
 * no bound port). The default-parameter case is pinned to the same golden-run
 * numbers {@code BacktestIntegrationTest} (qrp-engine) and {@code
 * RunControllerTest}'s empty-body case already pin: the same run, seen a
 * fourth way.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PnlControllerTest {

    private static final double DELTA = 1e-6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void defaultParametersReconcileTheGoldenRun() throws Exception {
        PnlAttributionResponse response = attribution("/api/pnl/attribution");

        assertEquals("sma-crossover", response.strategyId());
        assertEquals("market-open", response.executionId());
        assertEquals(100_000.0, response.initialEquity(), DELTA);
        assertEquals(92_229.0094522352, response.finalEquity(), DELTA);
        assertEquals(11, response.tradeCount());

        double expectedChange = response.finalEquity() - response.initialEquity();
        assertEquals(expectedChange, response.totalReconciledPnl(), DELTA);
        assertEquals(0.0, response.impliedCarryPnl(), DELTA, "a zero carry rate must imply zero carry");
    }

    @Test
    void aPositiveCarryRateAddsAPositiveMemoFigureWithoutChangingTheReconciledTotal() throws Exception {
        PnlAttributionResponse withoutCarry = attribution("/api/pnl/attribution");
        PnlAttributionResponse withCarry = attribution("/api/pnl/attribution?annualCarryRate=0.05");

        assertEquals(withoutCarry.totalReconciledPnl(), withCarry.totalReconciledPnl(), DELTA);
        assertTrue(withCarry.impliedCarryPnl() > 0.0);
    }

    @Test
    void unknownSymbolIsA400WithTheCliMessage() throws Exception {
        String json = mockMvc.perform(get("/api/pnl/attribution?symbol=NOPE"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiError error = objectMapper.readValue(json, ApiError.class);
        assertTrue(error.message().contains("unknown symbol"), error.message());
    }

    private PnlAttributionResponse attribution(String uri) throws Exception {
        String json = mockMvc.perform(get(uri))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, PnlAttributionResponse.class);
    }
}
