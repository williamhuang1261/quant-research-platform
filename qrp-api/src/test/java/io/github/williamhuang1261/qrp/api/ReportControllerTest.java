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
 * Exercises {@code GET /api/reports/compare} over real HTTP request/response
 * handling (MockMvc, no bound port). The no-params case is pinned to real
 * numbers read off an actual run against the bundled {@code data/sample}
 * series -- the same series and defaults {@code qrp compare} itself uses --
 * not assumed from the README's rounded transcript.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    private static final double DELTA = 1e-6;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void noParamsReproducesTheCliDefaultComparison() throws Exception {
        String json = mockMvc.perform(get("/api/reports/compare"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ReportResponse response = objectMapper.readValue(json, ReportResponse.class);

        assertEquals("sma-crossover", response.strategyId());
        assertEquals(java.util.List.of("SYNA", "SYNB"), response.candidateSymbols());
        assertEquals("SYNETF", response.benchmarkSymbol());
        assertEquals(3, response.rows().size());

        // Golden numbers read off a real `curl localhost:8080/api/reports/compare`
        // against the bundled data/sample series -- the same figures the
        // README's `qrp compare` transcript already shows rounded to 2dp.
        ReportRowResponse syna = response.rows().get(0);
        assertEquals("SYNA", syna.displayName());
        assertEquals(-0.04115895776844469, syna.grossCagr(), DELTA);
        assertEquals(-0.061038983750053344, syna.netCagr(), DELTA);
        assertEquals(-0.174514427227237, syna.sharpeRatio(), DELTA);
        assertEquals(0.24149473488945833, syna.maxDrawdown(), DELTA);
        assertEquals(-604.6589158938831, syna.benchmarkRelativeBps(), DELTA);

        ReportRowResponse synb = response.rows().get(1);
        assertEquals("SYNB", synb.displayName());
        assertEquals(-0.2749326919491941, synb.grossCagr(), DELTA);
        assertEquals(-0.2899657957563877, synb.netCagr(), DELTA);

        ReportRowResponse benchmarkRow = response.rows().get(2);
        assertTrue(benchmarkRow.isBenchmark(), "benchmark row must print last");
        assertEquals("SYNETF", benchmarkRow.displayName());
        assertEquals(0.0, benchmarkRow.benchmarkRelativeBps(), DELTA);

        assertEquals(
                "[template summary] SYNA posted the strongest net-of-fee return in this comparison, at -6.10% "
                        + "annualized. SYNA also delivered the best risk-adjusted return, with a Sharpe ratio of "
                        + "-0.17, so the same fund led on both measures. SYNA trailed the SYNETF benchmark by 605 "
                        + "bps net of fees.",
                response.narrative());
    }

    @Test
    void unknownSymbolIsA400WithTheCliMessage() throws Exception {
        String json = mockMvc.perform(get("/api/reports/compare").param("symbol", "NOPE"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiError error = objectMapper.readValue(json, ApiError.class);
        assertTrue(error.message().contains("unknown symbol"), error.message());
    }
}
