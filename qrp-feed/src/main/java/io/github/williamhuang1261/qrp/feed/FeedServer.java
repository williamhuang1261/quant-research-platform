package io.github.williamhuang1261.qrp.feed;

import io.github.williamhuang1261.qrp.core.Bar;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Streams {@link Bar} updates to any number of connected subscribers over
 * real TCP sockets.
 *
 * <p>A background thread accepts new subscriber connections as they arrive.
 * {@link #publish(String, Bar)} fans every bar out to every subscriber
 * currently connected and never blocks on a slow one: each subscriber gets
 * its own bounded outbound queue and its own writer thread, so one slow
 * reader can only ever stall its own queue, never delivery to any other
 * subscriber or the caller of {@code publish}.
 *
 * <p><b>Backpressure policy: disconnect the slow subscriber.</b> If a
 * subscriber's queue is full when a new bar arrives, that subscriber is
 * disconnected rather than the new bar being dropped, or an older one
 * being evicted to make room. This keeps every subscriber's sequence stream
 * gap-free by construction: a client that reconnects always knows exactly
 * what it missed and can ask to resume from there.
 *
 * <p><b>Resume handshake.</b> Immediately after connecting, a subscriber
 * must send one 8-byte big-endian long: the sequence number it already has
 * (0 for a brand-new subscriber with nothing yet). The server replays
 * every backlog frame after that sequence before switching the subscriber
 * over to live delivery, both under the same lock so no frame published
 * during registration is skipped or duplicated. The backlog is a bounded,
 * in-memory ring of the most recent {@code backlogCapacity} frames -- this
 * is deliberately not durable storage: a subscriber that reconnects after
 * missing more than {@code backlogCapacity} frames gets only what the
 * backlog still holds, not a full replay. There is no persistence across a
 * full server restart.
 */
public final class FeedServer implements Closeable {

    private static final int DEFAULT_QUEUE_CAPACITY = 64;
    private static final int DEFAULT_BACKLOG_CAPACITY = 10_000;

    private final ServerSocket serverSocket;
    private final int subscriberQueueCapacity;
    private final int backlogCapacity;
    private final AtomicLong sequence = new AtomicLong(0);
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final Deque<FeedProtocol.Frame> backlog = new ArrayDeque<>();
    private final Object backlogLock = new Object();
    private final Thread acceptThread;
    private volatile boolean closed;

    public FeedServer(int port) throws IOException {
        this(port, DEFAULT_QUEUE_CAPACITY, DEFAULT_BACKLOG_CAPACITY);
    }

    /** @param subscriberQueueCapacity max bars queued per subscriber before it is disconnected */
    public FeedServer(int port, int subscriberQueueCapacity) throws IOException {
        this(port, subscriberQueueCapacity, DEFAULT_BACKLOG_CAPACITY);
    }

    /**
     * @param subscriberQueueCapacity max bars queued per subscriber before it is disconnected
     * @param backlogCapacity max recently-published frames retained for a resuming subscriber to replay
     */
    public FeedServer(int port, int subscriberQueueCapacity, int backlogCapacity) throws IOException {
        if (subscriberQueueCapacity < 1) {
            throw new IllegalArgumentException("subscriberQueueCapacity must be >= 1");
        }
        if (backlogCapacity < 0) {
            throw new IllegalArgumentException("backlogCapacity must be >= 0");
        }
        this.subscriberQueueCapacity = subscriberQueueCapacity;
        this.backlogCapacity = backlogCapacity;
        this.serverSocket = new ServerSocket(port);
        this.acceptThread = new Thread(this::acceptLoop, "qrp-feed-accept");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    /** The bound port, useful when constructed with port 0 for a test. */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /** Number of subscribers currently connected. */
    public int subscriberCount() {
        return subscribers.size();
    }

    /** Polls until at least {@code count} subscribers are connected, or {@code timeout} elapses. */
    public boolean awaitSubscriberCount(int count, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (subscribers.size() < count) {
            if (Instant.now().isAfter(deadline)) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket socket = serverSocket.accept();
                Thread handshake = new Thread(() -> registerSubscriber(socket), "qrp-feed-handshake-" + socket.getPort());
                handshake.setDaemon(true);
                handshake.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    /** Reads the resume handshake, replays any owed backlog, then admits the subscriber to live delivery. */
    private void registerSubscriber(Socket socket) {
        try {
            long resumeFromSequence = new DataInputStream(socket.getInputStream()).readLong();
            synchronized (backlogLock) {
                Subscriber subscriber = new Subscriber(socket, subscriberQueueCapacity, this::disconnect);
                for (FeedProtocol.Frame frame : backlog) {
                    if (frame.sequence() > resumeFromSequence) {
                        subscriber.offer(frame);
                    }
                }
                subscribers.add(subscriber);
            }
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void disconnect(Subscriber subscriber) {
        subscribers.remove(subscriber);
        subscriber.closeQuietly();
    }

    /** Fans {@code bar} out to every currently connected subscriber; never blocks. */
    public void publish(String symbol, Bar bar) {
        synchronized (backlogLock) {
            long seq = sequence.incrementAndGet();
            FeedProtocol.Frame frame = new FeedProtocol.Frame(seq, symbol, bar);
            backlog.addLast(frame);
            if (backlog.size() > backlogCapacity) {
                backlog.removeFirst();
            }
            for (Subscriber subscriber : subscribers) {
                subscriber.offer(frame);
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        serverSocket.close();
        for (Subscriber subscriber : subscribers) {
            subscriber.closeQuietly();
        }
        subscribers.clear();
    }

    /** One connected subscriber: a bounded outbound queue plus its own writer thread. */
    private static final class Subscriber implements Closeable {
        private final Socket socket;
        private final BlockingQueue<FeedProtocol.Frame> queue;
        private final Thread writerThread;
        private volatile boolean closed;

        Subscriber(Socket socket, int queueCapacity, Consumer<Subscriber> onDisconnect) {
            this.socket = socket;
            this.queue = new ArrayBlockingQueue<>(queueCapacity);
            this.writerThread = new Thread(() -> writeLoop(onDisconnect), "qrp-feed-writer-" + socket.getPort());
            this.writerThread.setDaemon(true);
            this.writerThread.start();
        }

        /** Non-blocking enqueue; disconnects this subscriber if its queue is already full. */
        void offer(FeedProtocol.Frame frame) {
            if (closed) {
                return;
            }
            if (!queue.offer(frame)) {
                closeQuietly();
            }
        }

        private void writeLoop(Consumer<Subscriber> onDisconnect) {
            try {
                OutputStream out = socket.getOutputStream();
                while (!closed) {
                    FeedProtocol.Frame frame = queue.take();
                    FeedProtocol.writeFrame(out, frame);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // subscriber's socket died; fall through to disconnect below
            } finally {
                onDisconnect.accept(this);
            }
        }

        @Override
        public void close() {
            closeQuietly();
        }

        void closeQuietly() {
            if (closed) {
                return;
            }
            closed = true;
            writerThread.interrupt();
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
