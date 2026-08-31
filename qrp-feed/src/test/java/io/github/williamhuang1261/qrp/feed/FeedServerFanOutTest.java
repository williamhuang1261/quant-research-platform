package io.github.williamhuang1261.qrp.feed;

import io.github.williamhuang1261.qrp.core.Bar;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.github.williamhuang1261.qrp.feed.FeedServerClientTest.bar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedServerFanOutTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Test
    void everyConnectedSubscriberReceivesTheIdenticalSequenceOfBars() throws IOException, InterruptedException {
        try (FeedServer server = new FeedServer(0)) {
            int port = server.port();

            try (FeedClient first = new FeedClient("127.0.0.1", port);
                 FeedClient second = new FeedClient("127.0.0.1", port);
                 FeedClient third = new FeedClient("127.0.0.1", port)) {
                assertTrue(server.awaitSubscriberCount(3, TIMEOUT));

                List<Bar> published = List.of(
                        bar("2026-08-31T13:00:00Z", 100.0, 101.0, 99.5, 100.5, 1_000),
                        bar("2026-08-31T13:01:00Z", 100.5, 102.0, 100.0, 101.8, 1_500),
                        bar("2026-08-31T13:02:00Z", 101.8, 101.9, 100.9, 101.0, 900),
                        bar("2026-08-31T13:03:00Z", 101.0, 101.4, 100.2, 100.6, 700));

                for (Bar bar : published) {
                    server.publish("SYNA", bar);
                }

                for (FeedClient client : List.of(first, second, third)) {
                    for (int i = 0; i < published.size(); i++) {
                        FeedProtocol.Frame frame = client.readNext();
                        assertEquals(i + 1L, frame.sequence());
                        assertEquals(published.get(i), frame.bar());
                    }
                }
            }
        }
    }

    @Test
    void aStalledSubscriberIsDisconnectedWithoutSlowingOrDroppingBarsForHealthySubscribers() throws Exception {
        // Large enough that an actively-drained subscriber never comes close to
        // filling it given the pacing below, small relative to the total volume
        // below so a subscriber with zero reads overflows it (plus its OS-level
        // send/receive buffers, which pad the real threshold well past this
        // number) comfortably before the publish loop finishes.
        int queueCapacity = 1_000;
        try (FeedServer server = new FeedServer(0, queueCapacity)) {
            int port = server.port();

            // A raw socket that never reads: once its OS receive buffer and this
            // server's queueCapacity are both exhausted, the server must disconnect it
            // rather than let it stall delivery to the healthy subscriber below.
            Socket stalledSocket = new Socket();
            stalledSocket.setReceiveBufferSize(1024);
            stalledSocket.connect(new java.net.InetSocketAddress("127.0.0.1", port));
            // Send the resume handshake FeedServer requires (0 = fresh subscriber),
            // then never read again -- this raw socket deliberately bypasses FeedClient.
            new java.io.DataOutputStream(stalledSocket.getOutputStream()).writeLong(0L);

            List<FeedProtocol.Frame> received = new CopyOnWriteArrayList<>();
            try (FeedClient healthy = new FeedClient("127.0.0.1", port)) {
                assertTrue(server.awaitSubscriberCount(2, TIMEOUT));

                Thread reader = new Thread(() -> {
                    try {
                        FeedProtocol.Frame frame;
                        while ((frame = healthy.readNext()) != null) {
                            received.add(frame);
                        }
                    } catch (IOException ignored) {
                        // server closed or test tore the socket down; reader thread exits
                    }
                }, "test-healthy-reader");
                reader.setDaemon(true);
                reader.start();

                // Paced, not a raw burst: a real feed's publish rate is nowhere close to
                // "as fast as a tight in-process loop can go," and pacing gives the
                // healthy subscriber's writer/reader real wall-clock time to keep
                // draining concurrently, so only the never-reading stalled subscriber
                // falls behind enough to overflow its queue.
                int totalPublished = 20_000;
                List<Bar> publishedBars = new ArrayList<>(totalPublished);
                for (int i = 0; i < totalPublished; i++) {
                    Bar bar = bar("2026-08-31T13:00:00Z", 100.0 + i, 101.0 + i, 99.5 + i, 100.5 + i, 1_000 + i);
                    publishedBars.add(bar);
                    server.publish("SYNA", bar);
                    if (i % 20 == 19) {
                        Thread.sleep(1);
                    }
                }

                // The stalled subscriber's queue plus its OS-level send/receive buffers
                // cannot possibly absorb 20,000 unread frames, so it must have been
                // disconnected.
                // Some bytes the server wrote before disconnecting may still be sitting
                // unread in this socket's receive buffer, so drain toward EOF rather than
                // treating the first buffered byte as proof nothing was disconnected.
                java.io.InputStream stalledIn = stalledSocket.getInputStream();
                Instant readDeadline0 = Instant.now().plus(TIMEOUT);
                boolean stalledDisconnected = false;
                while (Instant.now().isBefore(readDeadline0)) {
                    long remainingMillis = Math.max(1, Duration.between(Instant.now(), readDeadline0).toMillis());
                    stalledSocket.setSoTimeout((int) remainingMillis);
                    try {
                        if (stalledIn.read() == -1) {
                            stalledDisconnected = true;
                            break;
                        }
                    } catch (java.net.SocketTimeoutException timedOut) {
                        break;
                    }
                }
                assertTrue(stalledDisconnected, "server never disconnected the stalled subscriber");

                Instant removalDeadline = Instant.now().plus(TIMEOUT);
                while (server.subscriberCount() != 1 && Instant.now().isBefore(removalDeadline)) {
                    Thread.sleep(5);
                }
                assertEquals(1, server.subscriberCount(), "only the healthy subscriber should remain connected");

                Instant readDeadline = Instant.now().plus(TIMEOUT);
                while (received.size() < totalPublished && Instant.now().isBefore(readDeadline)) {
                    Thread.sleep(10);
                }
                assertEquals(totalPublished, received.size(), "healthy subscriber must receive every published bar");
                for (int i = 0; i < totalPublished; i++) {
                    assertEquals(i + 1L, received.get(i).sequence());
                    assertEquals(publishedBars.get(i), received.get(i).bar());
                }
            } finally {
                stalledSocket.close();
            }
        }
    }
}
