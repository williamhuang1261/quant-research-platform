# Spec — real-time market-data distribution layer (Extension 13)

Status: implemented (Extension 13, steps 1-3)

## Why this exists

Every module in this platform up to this point runs in-process: the engine
screens and backtests bars, `qrp-api` answers a synchronous HTTP request and
returns, and nothing ever moves data over a network on its own initiative.
`qrp-feed` is the platform's first data-plane module: a `FeedServer` streams
`qrp-core`'s existing `Bar` records to any number of remote subscribers in
real time over a plain TCP socket, the same vocabulary the rest of the
platform already consumes, just pushed rather than pulled.

## Requirements

| # | Requirement |
| --- | --- |
| R1 | Stream `Bar` values to a connected subscriber over a real TCP socket, correctly framed |
| R2 | Serve many subscribers at once from a single publish call |
| R3 | A slow subscriber cannot stall the producer or any other subscriber |
| R4 | A subscriber that loses its connection can reconnect and resume with no gap and no duplicate |
| R5 | Every design choice (backpressure policy, resume window) stated plainly, including what it does not cover |

## Wire format (`FeedProtocol`)

Each frame is a 4-byte big-endian length prefix followed by exactly that
many payload bytes, written with `DataOutputStream`/`DataInputStream`:

| Field | Type | Notes |
| --- | --- | --- |
| length | `int` (4 bytes) | payload byte count, excludes itself |
| sequence | `long` (8 bytes) | monotonically increasing, global across all subscribers |
| symbol | UTF string (`writeUTF`/`readUTF`) | 2-byte length prefix plus bytes |
| timestamp | `long` (8 bytes) | epoch milliseconds, `Bar.timestamp()` |
| open, high, low, close | `double` x4 (8 bytes each) | |
| volume | `long` (8 bytes) | |

The length prefix means a reader always knows exactly how many bytes make
up the next frame without scanning for a delimiter -- no symbol or future
field can ever be mistaken for a frame boundary.

**Resume handshake.** Immediately after the TCP connection is established,
the subscriber sends one 8-byte big-endian long: the highest sequence
number it already has (0 for a brand-new subscriber). This is the only
message that flows subscriber-to-server; everything else is server-to-
subscriber `Frame`s.

## Multi-subscriber fan-out (`FeedServer`)

A background daemon thread accepts new connections continuously. Each
accepted connection gets its own short-lived handshake thread (reads the
resume sequence, replays any owed backlog, then registers the subscriber),
so a slow or silent client cannot block the accept loop from serving other
connections. Each registered subscriber gets its own bounded
`ArrayBlockingQueue<Frame>` (default capacity 64) and its own writer
thread. `publish` fans a frame out to every subscriber with a non-blocking
`queue.offer`, so the calling thread never blocks on any subscriber, slow
or otherwise.

## Backpressure policy: disconnect, not drop-oldest

If a subscriber's queue is full when a new bar arrives, that subscriber is
disconnected. This was chosen over evicting the oldest queued frame to make
room, for one reason: disconnect keeps every subscriber's received sequence
gap-free by construction. A disconnected subscriber knows exactly what it
has and can ask to resume from there. A drop-oldest policy would let a
subscriber silently lose bars in the middle of its stream with no way to
tell that apart from "the market was simply quiet" -- a resume protocol
cannot be built safely on top of a policy that already breaks the
invariant it depends on.

The trade-off this accepts: a subscriber that falls behind loses its
connection rather than falling further behind. For a market-data feed this
is the right default -- a consumer's whole reason for subscribing is
current, sequential data, not eventually-consistent data.

## Resume window (`FeedServer`'s backlog)

`FeedServer` retains the most recently published `backlogCapacity` frames
(default 10,000) in an in-memory `ArrayDeque`, guarded by the same lock
`publish` uses to append to it and fan frames out live. A reconnecting
subscriber's handshake sequence is compared against this backlog under
that same lock, so registration and publication can never race: a frame
either lands in the backlog before the new subscriber is registered (and
is replayed) or is fanned out to it live after registration, never both,
never neither.

This is deliberately not durable storage. A subscriber that reconnects
after missing more than `backlogCapacity` frames gets only what the
backlog still holds, not a full replay, and there is no persistence across
a full server-process restart -- the sequence counter and the backlog both
live only in the `FeedServer` object's memory. See "What is deliberately
not here" below.

## Reconnect-with-backoff (`FeedClient`)

`FeedClient.reconnectWithBackoff(maxAttempts, initialDelay, maxDelay)`
closes the current socket (if any), then retries connecting with
exponential backoff capped at `maxDelay`, resuming from
`lastReceivedSequence()` on success. `lastReceivedSequence()` updates
automatically on every `readNext()`, so a caller never has to track it by
hand. If every attempt fails, an `IOException` naming the attempt count is
thrown rather than retrying forever or hanging silently.

**Scope note on "resume."** This closes a *dropped connection* -- a network
blip, a restart of the client process, anything that leaves the
`FeedServer` instance itself running with its backlog intact. It does not,
and cannot, cover a full server-process restart: without durable storage
behind the backlog, restarting the `FeedServer` object loses both the
sequence counter and every backlogged frame, so there is nothing left to
resume from. A durable backlog (the same shape of gap `qrp-warehouse`
closes for backtest runs, applied to this module) is the natural next
extension if that scope is ever needed.

## What is deliberately not here

- **Authentication.** Any TCP client that completes the handshake is
  admitted; there is no notion of a caller identity or an access check.
- **Multi-topic routing.** Every subscriber receives every published bar;
  there is no topic, channel or symbol-filter concept, so a subscriber
  that only wants `SYNA` still receives everything published.
- **Message durability or replay beyond the in-memory resume window.**
  See "Resume window" above -- no database, no disk-backed log, no
  replay across a server-process restart.
- **Encryption.** Plain TCP, no TLS.
- **A drop-oldest backpressure option.** Only disconnect-on-overflow is
  implemented; see "Backpressure policy" above for why.
- **Wiring into `qrp-api` or the CLI.** `qrp-feed` is a standalone module
  today, exercised by its own tests over real loopback sockets; nothing in
  `qrp-engine` or `qrp-api` calls `FeedServer.publish` yet.
