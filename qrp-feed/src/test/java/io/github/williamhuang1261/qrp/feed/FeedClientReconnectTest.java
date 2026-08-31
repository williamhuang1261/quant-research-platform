package io.github.williamhuang1261.qrp.feed;

import io.github.williamhuang1261.qrp.core.Bar;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static io.github.williamhuang1261.qrp.feed.FeedServerClientTest.bar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedClientReconnectTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void reconnectResumesFromBacklogWithNoGapAndNoDuplicate() throws Exception {
        try (FeedServer server = new FeedServer(0)) {
            int port = server.port();

            List<Bar> beforeDrop = List.of(
                    bar("2026-08-31T13:00:00Z", 100.0, 101.0, 99.5, 100.5, 1_000),
                    bar("2026-08-31T13:01:00Z", 100.5, 102.0, 100.0, 101.8, 1_500),
                    bar("2026-08-31T13:02:00Z", 101.8, 101.9, 100.9, 101.0, 900),
                    bar("2026-08-31T13:03:00Z", 101.0, 101.4, 100.2, 100.6, 700),
                    bar("2026-08-31T13:04:00Z", 100.6, 100.9, 100.1, 100.3, 650));

            List<Bar> whileDisconnected = List.of(
                    bar("2026-08-31T13:05:00Z", 100.3, 100.8, 100.0, 100.5, 720),
                    bar("2026-08-31T13:06:00Z", 100.5, 101.1, 100.3, 100.9, 810),
                    bar("2026-08-31T13:07:00Z", 100.9, 101.3, 100.6, 101.0, 690));

            List<Bar> afterReconnect = List.of(
                    bar("2026-08-31T13:08:00Z", 101.0, 101.6, 100.8, 101.4, 730),
                    bar("2026-08-31T13:09:00Z", 101.4, 101.9, 101.1, 101.7, 800));

            FeedClient client = new FeedClient("127.0.0.1", port);
            try {
                assertTrue(server.awaitSubscriberCount(1, TIMEOUT));

                for (Bar bar : beforeDrop) {
                    server.publish("SYNA", bar);
                }
                for (int i = 0; i < beforeDrop.size(); i++) {
                    FeedProtocol.Frame frame = client.readNext();
                    assertEquals(i + 1L, frame.sequence());
                    assertEquals(beforeDrop.get(i), frame.bar());
                }
                assertEquals(5L, client.lastReceivedSequence());

                // Simulate a network drop: this client's connection is lost, but the
                // server itself (and its backlog) keeps running throughout, matching
                // this extension's stated scope -- resume works across a dropped
                // connection, not across a full server-process restart (no durable
                // storage backs the backlog).
                client.close();

                // Bars published while the client is disconnected must still be
                // recoverable from the server's backlog once it reconnects.
                for (Bar bar : whileDisconnected) {
                    server.publish("SYNA", bar);
                }

                client.reconnectWithBackoff(5, Duration.ofMillis(10), Duration.ofMillis(200));
                assertTrue(server.awaitSubscriberCount(1, TIMEOUT));

                for (Bar bar : afterReconnect) {
                    server.publish("SYNA", bar);
                }

                List<Bar> expectedAfterResume = new java.util.ArrayList<>(whileDisconnected);
                expectedAfterResume.addAll(afterReconnect);

                for (int i = 0; i < expectedAfterResume.size(); i++) {
                    FeedProtocol.Frame frame = client.readNext();
                    assertEquals(6L + i, frame.sequence(), "sequence must continue with no gap or duplicate");
                    assertEquals(expectedAfterResume.get(i), frame.bar());
                }
                assertEquals(10L, client.lastReceivedSequence());
            } finally {
                client.close();
            }
        }
    }

    @Test
    void reconnectWithBackoffFailsCleanlyWhenNoServerIsListening() throws IOException, InterruptedException {
        try (FeedServer server = new FeedServer(0)) {
            int port = server.port();

            FeedClient client = new FeedClient("127.0.0.1", port);
            client.close();
            server.close();

            IOException failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () ->
                    client.reconnectWithBackoff(3, Duration.ofMillis(5), Duration.ofMillis(20)));
            assertTrue(failure.getMessage().contains("after 3 attempts"));
        }
    }
}
