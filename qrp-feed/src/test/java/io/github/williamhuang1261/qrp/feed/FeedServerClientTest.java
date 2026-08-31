package io.github.williamhuang1261.qrp.feed;

import io.github.williamhuang1261.qrp.core.Bar;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedServerClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void clientReceivesBarsPushedThroughServerInOrderOverARealSocket() throws IOException, InterruptedException {
        try (FeedServer server = new FeedServer(0)) {
            int port = server.port();

            try (FeedClient client = new FeedClient("127.0.0.1", port)) {
                assertTrue(server.awaitSubscriberCount(1, TIMEOUT));

                List<Bar> published = List.of(
                        bar("2026-08-31T13:00:00Z", 100.0, 101.0, 99.5, 100.5, 1_000),
                        bar("2026-08-31T13:01:00Z", 100.5, 102.0, 100.0, 101.8, 1_500),
                        bar("2026-08-31T13:02:00Z", 101.8, 101.9, 100.9, 101.0, 900));

                for (Bar bar : published) {
                    server.publish("SYNA", bar);
                }

                for (int i = 0; i < published.size(); i++) {
                    FeedProtocol.Frame frame = client.readNext();
                    assertEquals(i + 1L, frame.sequence());
                    assertEquals("SYNA", frame.symbol());
                    assertEquals(published.get(i), frame.bar());
                }
            }
        }
    }

    @Test
    void readNextReturnsNullAfterServerClosesCleanly() throws IOException, InterruptedException {
        try (FeedServer server = new FeedServer(0)) {
            int port = server.port();

            try (FeedClient client = new FeedClient("127.0.0.1", port)) {
                assertTrue(server.awaitSubscriberCount(1, TIMEOUT));

                server.publish("SYNA", bar("2026-08-31T13:00:00Z", 100.0, 101.0, 99.5, 100.5, 1_000));
                assertEquals(1L, client.readNext().sequence());

                server.close();

                assertNull(client.readNext());
            }
        }
    }

    static Bar bar(String timestamp, double open, double high, double low, double close, long volume) {
        return new Bar(Instant.parse(timestamp), open, high, low, close, volume);
    }
}
