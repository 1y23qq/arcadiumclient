package org.polyfrost.oneconfig.internal.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ChatSendTrace {
    private static final Logger LOGGER = LogManager.getLogger("Arcadium/ChatTiming");
    private static final String HANDLER = "arcadium_chat_timing";
    private static final int MAX_PENDING = 64, MAX_RECENT = 32;
    private static final long SLOW = TimeUnit.SECONDS.toNanos(2), EXPIRE = TimeUnit.SECONDS.toNanos(60);
    private static final Object LOCK = new Object();
    private static final AtomicLong IDS = new AtomicLong(), LAST_POLL = new AtomicLong();
    private static final ThreadLocal<ArrayDeque<Attempt>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<Long, Attempt> PENDING = new LinkedHashMap<>();
    private static final Map<Object, Attempt> PACKETS = new WeakHashMap<>();
    private static final ArrayDeque<Attempt> RECENT = new ArrayDeque<>();
    private static final Attempt NOOP = new Attempt(0, "inactive", 0, false, false, 0);
    private static long journalWindow, slowWindow;
    private static int journalCount, slowCount;
    private static volatile boolean enabled;
    static LongSupplier clock = System::nanoTime;

    private ChatSendTrace() {}

    public static final class Attempt implements AutoCloseable {
        final long id, started;
        final String origin;
        final int characters;
        final boolean command, clientThread;
        volatile long player, event, nativeCall, packet, write, flush, completed, returned;
        volatile String outcome = "pending";
        volatile boolean reported, journaled;
        int depth = 1;

        private Attempt(long id, String origin, int characters, boolean command, boolean clientThread, long started) {
            this.id = id; this.origin = origin; this.characters = characters;
            this.command = command; this.clientThread = clientThread; this.started = started;
        }

        public void eventReturned() { if (id != 0) event = clock.getAsLong(); }
        public void nativeCall() { if (id != 0) nativeCall = clock.getAsLong(); }
        public void cancelled() { if (id != 0) outcome = "event_cancelled"; }
        public void handledLocally() { if (id != 0) outcome = "handled_locally"; }

        @Override public void close() {
            if (id == 0 || --depth != 0) return;
            ArrayDeque<Attempt> scopes = SCOPES.get();
            if (scopes.peekLast() == this) scopes.removeLast(); else scopes.remove(this);
            if (scopes.isEmpty()) SCOPES.remove();
            returned = clock.getAsLong();
            if (packet == 0) finish(this, outcome.equals("pending") ? "no_packet_observed" : outcome);
        }
    }

    public static Attempt input(int characters, boolean command, boolean clientThread) {
        return begin("input", characters, command, clientThread);
    }

    public static Attempt send(int characters, boolean command, boolean clientThread) {
        if (!enabled) return NOOP;
        Attempt current = SCOPES.get().peekLast();
        if (current != null && current.origin.equals("input") && current.player == 0) {
            current.depth++;
            current.player = clock.getAsLong();
            return current;
        }
        Attempt trace = begin("send", characters, command, clientThread);
        trace.player = clock.getAsLong();
        return trace;
    }

    private static Attempt begin(String origin, int characters, boolean command, boolean clientThread) {
        if (!enabled) return NOOP;
        Attempt trace = create(origin, characters, command, clientThread);
        SCOPES.get().addLast(trace);
        return trace;
    }

    private static Attempt create(String origin, int characters, boolean command, boolean clientThread) {
        Attempt trace = new Attempt(IDS.incrementAndGet(), origin, characters, command, clientThread, clock.getAsLong());
        synchronized (LOCK) {
            if (PENDING.size() == MAX_PENDING) {
                Attempt oldest = PENDING.values().iterator().next();
                retire(oldest, "tracking_limit");
            }
            PENDING.put(trace.id, trace);
        }
        return trace;
    }

    public static void observe(Channel channel) {
        enabled = true;
        if (channel == null) return;
        try {
            if (channel.pipeline().get(HANDLER) == null) channel.pipeline().addLast(HANDLER, new Observer());
        } catch (RuntimeException ignored) {
            // Diagnostics must not interrupt a connection being closed.
        }
    }

    public static void packet(Object packet, int characters, boolean command, boolean clientThread) {
        if (!enabled) return;
        Attempt trace = SCOPES.get().peekLast();
        if (trace == null || trace.packet != 0 || trace.completed != 0) trace = create("packet", characters, command, clientThread);
        trace.packet = clock.getAsLong();
        synchronized (LOCK) { PACKETS.put(packet, trace); }
    }

    private static final class Observer extends ChannelOutboundHandlerAdapter {
        private final ArrayDeque<Attempt> unflushed = new ArrayDeque<>();

        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
            Attempt trace;
            synchronized (LOCK) { trace = PACKETS.remove(message); }
            if (trace != null) {
                trace.write = clock.getAsLong();
                if (unflushed.size() == MAX_PENDING) unflushed.removeFirst();
                unflushed.addLast(trace);
                long observedId = trace.id;
                try {
                    promise.addListener(future -> {
                        Attempt observed;
                        synchronized (LOCK) { observed = PENDING.get(observedId); }
                        if (observed != null) finish(observed, future.isSuccess() ? "local_write_complete" : "local_write_failed");
                    });
                } catch (RuntimeException ignored) {
                    finish(trace, "unobservable_promise");
                }
            }
            ctx.write(message, promise);
        }

        @Override public void flush(ChannelHandlerContext ctx) throws Exception {
            long now = clock.getAsLong();
            while (!unflushed.isEmpty()) unflushed.removeFirst().flush = now;
            ctx.flush();
        }
    }

    public static void poll() {
        long now = clock.getAsLong(), previous = LAST_POLL.get();
        if ((previous != 0 && now - previous < TimeUnit.SECONDS.toNanos(1)) || !LAST_POLL.compareAndSet(previous, now)) return;
        List<Map<String, Object>> slow = new ArrayList<>(), journal = new ArrayList<>();
        synchronized (LOCK) {
            for (Attempt trace : new ArrayList<>(PENDING.values())) {
                if (now - trace.started < SLOW) continue;
                if (!trace.reported) {
                    trace.reported = true;
                    if (allowLog(now, true)) slow.add(summary(trace, now));
                }
                if (now - trace.started >= EXPIRE) retire(trace, "tracking_expired");
            }
            for (Attempt trace : RECENT) {
                if (trace.journaled) continue;
                trace.journaled = true;
                if (allowLog(now, false)) journal.add(summary(trace, trace.completed));
            }
        }
        for (Map<String, Object> value : slow) LOGGER.warn("Outgoing chat client timing (no server receipt implied): {}", value);
        if (!journal.isEmpty()) LOGGER.info("Outgoing chat client timing (no server receipt implied): {}", journal);
    }

    private static void finish(Attempt trace, String outcome) {
        boolean slow;
        synchronized (LOCK) {
            if (trace.completed != 0) return;
            retire(trace, outcome);
            slow = trace.completed - trace.started >= SLOW && allowLog(trace.completed, true);
            if (slow) trace.journaled = true;
        }
        if (slow) LOGGER.warn("Outgoing chat client timing (no server receipt implied): {}", summary(trace, trace.completed));
    }

    private static void retire(Attempt trace, String outcome) {
        trace.outcome = outcome;
        trace.completed = clock.getAsLong();
        PENDING.remove(trace.id);
        PACKETS.values().removeIf(value -> value == trace);
        if (RECENT.size() == MAX_RECENT) RECENT.removeFirst();
        RECENT.addLast(trace);
    }

    public static Map<String, Object> snapshot() {
        synchronized (LOCK) {
            long now = clock.getAsLong();
            List<Map<String, Object>> pending = new ArrayList<>(), recent = new ArrayList<>();
            for (Attempt trace : PENDING.values()) pending.add(summary(trace, now));
            for (Attempt trace : RECENT) recent.add(summary(trace, trace.completed));
            return Map.of("pending", pending, "recent", recent);
        }
    }

    private static Map<String, Object> summary(Attempt trace, long now) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", trace.id); result.put("origin", trace.origin);
        result.put("characters", trace.characters); result.put("command", trace.command);
        result.put("clientThread", trace.clientThread); result.put("outcome", trace.outcome);
        result.put("ageMs", elapsed(trace.started, now));
        result.put("playerMs", elapsed(trace.started, trace.player));
        result.put("eventMs", elapsed(trace.player, trace.event));
        result.put("nativeMs", elapsed(trace.started, trace.nativeCall));
        result.put("packetMs", elapsed(trace.started, trace.packet));
        result.put("writeMs", elapsed(trace.packet, trace.write));
        result.put("flushMs", elapsed(trace.write, trace.flush));
        result.put("localCompleteMs", elapsed(trace.packet, trace.completed));
        result.put("callerMs", elapsed(trace.started, trace.returned));
        return result;
    }

    private static long elapsed(long start, long end) { return start == 0 || end == 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(end - start); }

    private static boolean allowLog(long now, boolean slow) {
        if (slow) {
            if (slowWindow == 0 || now - slowWindow >= EXPIRE) { slowWindow = now; slowCount = 0; }
            return slowCount++ < 16;
        }
        if (journalWindow == 0 || now - journalWindow >= EXPIRE) { journalWindow = now; journalCount = 0; }
        return journalCount++ < 16;
    }

    static void reset() {
        synchronized (LOCK) {
            PENDING.clear(); PACKETS.clear(); RECENT.clear();
            journalWindow = slowWindow = 0; journalCount = slowCount = 0;
        }
        SCOPES.remove(); LAST_POLL.set(0); enabled = true; clock = System::nanoTime;
    }
}
