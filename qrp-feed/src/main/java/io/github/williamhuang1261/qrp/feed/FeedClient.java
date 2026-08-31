package io.github.williamhuang1261.qrp.feed;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.time.Duration;

/**
 * Connects to a {@link FeedServer} and reads its stream of bar updates.
 *
 * <p>On connect, sends the 8-byte resume handshake {@link FeedServer}
 * expects: {@link #lastReceivedSequence()} (0 for a fresh subscriber).
 * {@link #readNext()} tracks that sequence automatically as frames arrive,
 * so a later {@link #reconnectWithBackoff} resumes from exactly where this
 * client left off, with no gap and no duplicate, as long as the server's
 * backlog still covers the gap.
 */
public final class FeedClient implements Closeable {

    private final String host;
    private final int port;
    private Socket socket;
    private InputStream in;
    private volatile long lastReceivedSequence;

    public FeedClient(String host, int port) throws IOException {
        this(host, port, 0L);
    }

    public FeedClient(String host, int port, long resumeFromSequence) throws IOException {
        this.host = host;
        this.port = port;
        this.lastReceivedSequence = resumeFromSequence;
        connect(resumeFromSequence);
    }

    private void connect(long resumeFromSequence) throws IOException {
        Socket newSocket = new Socket(host, port);
        DataOutputStream handshake = new DataOutputStream(newSocket.getOutputStream());
        handshake.writeLong(resumeFromSequence);
        handshake.flush();
        this.socket = newSocket;
        this.in = newSocket.getInputStream();
    }

    /** Blocks for the next frame; returns {@code null} on a clean server close. */
    public FeedProtocol.Frame readNext() throws IOException {
        FeedProtocol.Frame frame = FeedProtocol.readFrame(in);
        if (frame != null) {
            lastReceivedSequence = frame.sequence();
        }
        return frame;
    }

    /** The highest sequence number received so far, or the initial resume point if none yet. */
    public long lastReceivedSequence() {
        return lastReceivedSequence;
    }

    /**
     * Closes the current connection (if any) and reconnects with exponential
     * backoff, resuming from {@link #lastReceivedSequence()}. Blocks the
     * calling thread for the duration of the retry loop.
     */
    public void reconnectWithBackoff(int maxAttempts, Duration initialDelay, Duration maxDelay)
            throws IOException, InterruptedException {
        closeSocketQuietly();
        Duration delay = initialDelay;
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                connect(lastReceivedSequence);
                return;
            } catch (IOException e) {
                lastFailure = e;
                if (attempt == maxAttempts) {
                    break;
                }
                Thread.sleep(delay.toMillis());
                delay = delay.multipliedBy(2);
                if (delay.compareTo(maxDelay) > 0) {
                    delay = maxDelay;
                }
            }
        }
        throw new IOException(
                "failed to reconnect to " + host + ":" + port + " after " + maxAttempts + " attempts", lastFailure);
    }

    @Override
    public void close() throws IOException {
        closeSocketQuietly();
    }

    private void closeSocketQuietly() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
    }
}
