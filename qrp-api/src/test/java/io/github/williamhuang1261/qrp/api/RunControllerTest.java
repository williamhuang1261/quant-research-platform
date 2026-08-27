package io.github.williamhuang1261.qrp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the endpoint over real HTTP request/response handling (MockMvc,
 * no bound port). The empty-body case is pinned to the same golden-run
 * numbers {@code BacktestIntegrationTest} (qrp-engine) and the CLI smoke test
 * in {@code QrpCliTest} (qrp-app) already pin -- three tests, three front
 * ends, one engine.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RunControllerTest {

    private static final double DELTA = 1e-6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void emptyBodyReproducesTheCliGoldenRun() throws Exception {
        String json = mockMvc.perform(post("/api/runs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        RunResponse response = objectMapper.readValue(json, RunResponse.class);

        assertEquals("sma-crossover", response.strategyId());
        assertEquals("market-open", response.executionId());
        assertEquals(92229.0094522352, response.finalEquity(), DELTA);
        assertEquals(-0.0411589578, response.cagr(), DELTA);
        assertEquals(-0.1745144272, response.sharpeRatio(), DELTA);
        assertEquals(0.2414947349, response.maxDrawdown(), DELTA);
        assertEquals(11, response.tradeCount());
        assertTrue(response.equityCurve().length > 0);
    }

    @Test
    void explicitLobExecutionIsHonoured() throws Exception {
        String json = mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"execution\":\"lob\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        RunResponse response = objectMapper.readValue(json, RunResponse.class);
        assertEquals("lob", response.executionId());
    }

    @Test
    void unknownSymbolIsA400WithTheCliMessage() throws Exception {
        String json = mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiError error = objectMapper.readValue(json, ApiError.class);
        assertTrue(error.message().contains("unknown symbol"), error.message());
    }

    @Test
    void badStrategyParamIsA400WithTheCliMessage() throws Exception {
        String json = mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"sma-crossover\",\"params\":{\"fast\":50,\"slow\":20}}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiError error = objectMapper.readValue(json, ApiError.class);
        assertTrue(error.message().contains("must be shorter than"), error.message());
    }
}
