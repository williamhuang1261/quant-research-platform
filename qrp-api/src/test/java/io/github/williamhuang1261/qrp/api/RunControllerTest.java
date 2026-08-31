package io.github.williamhuang1261.qrp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * ends, one engine. Now also exercises the real embedded Postgres warehouse:
 * every POST is a write-through, a repeat POST is a cache hit, and a prior
 * run is readable by id with no recomputation.
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
        RunResponse response = postRun("{}");

        assertEquals("sma-crossover", response.strategyId());
        assertEquals("market-open", response.executionId());
        assertEquals(92229.0094522352, response.finalEquity(), DELTA);
        assertEquals(-0.0411589578, response.cagr(), DELTA);
        assertEquals(-0.1745144272, response.sharpeRatio(), DELTA);
        assertEquals(0.2414947349, response.maxDrawdown(), DELTA);
        assertEquals(11, response.tradeCount());
        assertTrue(response.equityCurve().length > 0);
        assertTrue(response.id() > 0);
    }

    @Test
    void explicitLobExecutionIsHonoured() throws Exception {
        RunResponse response = postRun("{\"execution\":\"lob\"}");

        assertEquals("lob", response.executionId());
    }

    @Test
    void anIdenticalRepeatRequestIsServedFromTheCacheRatherThanRecomputed() throws Exception {
        String body = "{\"symbol\":\"SYNB\",\"strategy\":\"sma-crossover\",\"params\":{\"fast\":10,\"slow\":30}}";

        RunResponse first = postRun(body);
        RunResponse second = postRun(body);

        assertFalse(first.cached(), "the first request must compute and persist, not hit an empty cache");
        assertTrue(second.cached(), "an identical repeat request must be served from the warehouse");
        assertEquals(first.id(), second.id());
        assertEquals(first.finalEquity(), second.finalEquity(), DELTA);
        assertEquals(first.strategyId(), second.strategyId());
    }

    @Test
    void aDifferentRequestIsANewRowNotACacheHit() throws Exception {
        RunResponse first = postRun("{\"symbol\":\"SYNETF\",\"params\":{\"fast\":20,\"slow\":50}}");
        RunResponse second = postRun("{\"symbol\":\"SYNETF\",\"params\":{\"fast\":15,\"slow\":50}}");

        assertNotEquals(first.id(), second.id());
        assertFalse(second.cached());
    }

    @Test
    void aPersistedRunIsReadableByIdWithNoRecomputation() throws Exception {
        RunResponse posted = postRun("{\"symbol\":\"SYNA\",\"params\":{\"fast\":12,\"slow\":40}}");

        String json = mockMvc.perform(get("/api/runs/" + posted.id()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        RunResponse fetched = objectMapper.readValue(json, RunResponse.class);

        assertTrue(fetched.cached(), "a GET by id never recomputes");
        assertEquals(posted.id(), fetched.id());
        assertEquals(posted.finalEquity(), fetched.finalEquity(), DELTA);
        assertEquals(posted.strategyId(), fetched.strategyId());
        assertEquals(posted.equityCurve().length, fetched.equityCurve().length);
    }

    @Test
    void fetchingAnUnknownIdIsA404() throws Exception {
        String json = mockMvc.perform(get("/api/runs/999999999"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiError error = objectMapper.readValue(json, ApiError.class);
        assertTrue(error.message().contains("999999999"), error.message());
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

    private RunResponse postRun(String body) throws Exception {
        String json = mockMvc.perform(post("/api/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(json, RunResponse.class);
    }
}
