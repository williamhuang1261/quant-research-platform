package io.github.williamhuang1261.qrp.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises {@code GET /api/warehouse/prices} -- an indexed range query
 * against real, backfilled Postgres rows, not a compute call at all.
 * {@code WarehouseBackfillRunner} runs on Spring context startup, so the
 * bundled sample data is already in {@code fact_price_bar} by the time these
 * tests run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsOnlyBarsInsideTheRequestedRange() throws Exception {
        String json = mockMvc.perform(get("/api/warehouse/prices")
                        .param("symbol", "SYNA")
                        .param("timeframe", "1d")
                        .param("from", "2022-01-01T00:00:00Z")
                        .param("to", "2022-02-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<PriceBarResponse> bars = objectMapper.readValue(json, new TypeReference<List<PriceBarResponse>>() {
        });

        assertFalse(bars.isEmpty(), "the backfilled sample data must produce at least one bar in this range");
        for (PriceBarResponse bar : bars) {
            assertTrue(!bar.timestamp().isBefore(Instant.parse("2022-01-01T00:00:00Z")));
            assertTrue(bar.timestamp().isBefore(Instant.parse("2022-02-01T00:00:00Z")));
        }
    }

    @Test
    void unknownSymbolIsA400WithTheCliMessage() throws Exception {
        String json = mockMvc.perform(get("/api/warehouse/prices")
                        .param("symbol", "NOPE")
                        .param("timeframe", "1d")
                        .param("from", "2022-01-01T00:00:00Z")
                        .param("to", "2022-02-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ApiError error = objectMapper.readValue(json, ApiError.class);
        assertTrue(error.message().contains("unknown symbol"), error.message());
    }
}
