package io.github.williamhuang1261.qrp.feed;

import io.github.williamhuang1261.qrp.core.Bar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Objects;

/**
 * Wire encoding for one sequenced {@link Bar} update.
 *
 * <p>Each frame is a 4-byte big-endian length prefix followed by exactly that
 * many payload bytes: an 8-byte sequence number, the symbol as a UTF string,
 * an 8-byte epoch-millisecond timestamp, five 8-byte doubles (open, high,
 * low, close) and volume as a long. The length prefix lets a reader know
 * exactly how many bytes make up the next frame without scanning for a
 * delimiter, so a symbol or a future payload field can never be mistaken for
 * a frame boundary.
 */
public final class FeedProtocol {

    private FeedProtocol() {
    }

    /** One frame: a sequence number, the instrument symbol, and the bar itself. */
    public record Frame(long sequence, String symbol, Bar bar) {
        public Frame {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(bar, "bar");
        }
    }

    public static void writeFrame(OutputStream rawOut, Frame frame) throws IOException {
        ByteArrayOutputStream payloadBuffer = new ByteArrayOutputStream();
        try (DataOutputStream payload = new DataOutputStream(payloadBuffer)) {
            payload.writeLong(frame.sequence());
            payload.writeUTF(frame.symbol());
            Bar bar = frame.bar();
            payload.writeLong(bar.timestamp().toEpochMilli());
            payload.writeDouble(bar.open());
            payload.writeDouble(bar.high());
            payload.writeDouble(bar.low());
            payload.writeDouble(bar.close());
            payload.writeLong(bar.volume());
        }

        byte[] payloadBytes = payloadBuffer.toByteArray();
        DataOutputStream out = new DataOutputStream(rawOut);
        out.writeInt(payloadBytes.length);
        out.write(payloadBytes);
        out.flush();
    }

    /**
     * Reads exactly one frame. Returns {@code null} if the stream is closed
     * cleanly at a frame boundary (no partial frame in flight), and throws
     * {@link EOFException} if the stream ends mid-frame.
     */
    public static Frame readFrame(InputStream rawIn) throws IOException {
        DataInputStream in = new DataInputStream(rawIn);
        int length;
        try {
            length = in.readInt();
        } catch (EOFException clean) {
            return null;
        }
        if (length < 0) {
            throw new IOException("negative frame length: " + length);
        }

        byte[] payloadBytes = new byte[length];
        in.readFully(payloadBytes);

        try (DataInputStream payload = new DataInputStream(new ByteArrayInputStream(payloadBytes))) {
            long sequence = payload.readLong();
            String symbol = payload.readUTF();
            Instant timestamp = Instant.ofEpochMilli(payload.readLong());
            double open = payload.readDouble();
            double high = payload.readDouble();
            double low = payload.readDouble();
            double close = payload.readDouble();
            long volume = payload.readLong();
            Bar bar = new Bar(timestamp, open, high, low, close, volume);
            return new Frame(sequence, symbol, bar);
        }
    }
}
