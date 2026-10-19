package org.polyfrost.oneconfig.internal.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatSendTraceTest {
    private final AtomicLong now = new AtomicLong(TimeUnit.SECONDS.toNanos(1));

    @BeforeEach void prepare() { ChatSendTrace.reset(); ChatSendTrace.clock = now::get; }
    @AfterEach void reset() { ChatSendTrace.reset(); }

    @Test void preservesOriginalPacketPromiseAndOrder() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter());
        ChatSendTrace.observe(channel);
        Object first = new Object(), second = new Object();
        ChannelPromise promise = channel.newPromise();
        try (ChatSendTrace.Attempt input = ChatSendTrace.input(9, false, true);
             ChatSendTrace.Attempt send = ChatSendTrace.send(9, false, true)) {
            send.eventReturned(); send.nativeCall();
            ChatSendTrace.packet(first, 9, false, true);
            assertSame(promise, channel.writeAndFlush(first, promise));
        }
        packet(channel, second);
        assertSame(first, channel.readOutbound()); assertSame(second, channel.readOutbound());
        assertNull(channel.readOutbound()); assertTrue(promise.isSuccess());
        assertEquals("input", recent().get(0).get("origin"));
        assertEquals("local_write_complete", recent().get(0).get("outcome"));
        assertEquals(0L, recent().get(0).get("localCompleteMs"));
        assertTrue(pending().isEmpty());
        channel.finish();
    }

    @Test void cancellationAndLocallyHandledCommandsNeverBecomeTransportWrites() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter()); ChatSendTrace.observe(channel);
        try (ChatSendTrace.Attempt input = ChatSendTrace.input(12, true, true);
             ChatSendTrace.Attempt send = ChatSendTrace.send(12, true, true)) {
            send.eventReturned(); send.cancelled();
        }
        try (ChatSendTrace.Attempt send = ChatSendTrace.send(8, true, true)) { send.eventReturned(); send.handledLocally(); }
        now.addAndGet(TimeUnit.SECONDS.toNanos(30)); ChatSendTrace.poll();
        assertNull(channel.readOutbound()); assertTrue(pending().isEmpty());
        assertEquals(List.of("event_cancelled", "handled_locally"), recent().stream().map(row -> row.get("outcome")).toList());
        channel.finish();
    }

    @Test void aBlockedWriteSchedulerIsVisibleWithoutPretendingDeliveryOrBlockingInboundTraffic() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter()); ChatSendTrace.observe(channel);
        ArrayDeque<Runnable> scheduler = new ArrayDeque<>();
        Object outgoing = new Object(), inbound = new Object();
        try (ChatSendTrace.Attempt send = ChatSendTrace.send(10, false, true)) {
            send.eventReturned(); send.nativeCall();
            ChatSendTrace.packet(outgoing, 10, false, true);
            scheduler.add(() -> channel.writeAndFlush(outgoing));
        }
        assertTrue(channel.writeInbound(inbound)); assertSame(inbound, channel.readInbound());
        now.addAndGet(TimeUnit.SECONDS.toNanos(20)); ChatSendTrace.poll();
        assertEquals(-1L, pending().get(0).get("writeMs"));
        assertEquals(-1L, pending().get(0).get("localCompleteMs"));
        assertEquals("pending", pending().get(0).get("outcome"));
        assertNull(channel.readOutbound());
        scheduler.removeFirst().run();
        assertSame(outgoing, channel.readOutbound());
        assertEquals(20000L, recent().get(0).get("writeMs"));
        assertEquals("local_write_complete", recent().get(0).get("outcome"));
        channel.finish();
    }

    @Test void aChatOnlyPolicyMayHoldChatWhileOtherPacketsAndInboundTrafficContinue() {
        HoldingPolicy policy = new HoldingPolicy();
        EmbeddedChannel channel = new EmbeddedChannel(policy); ChatSendTrace.observe(channel);
        Object first = new Object(), second = new Object(), movement = new Object(), inbound = new Object();
        policy.first = first; policy.second = second;
        packet(channel, first); packet(channel, second);
        channel.writeAndFlush(movement); channel.writeInbound(inbound);
        assertSame(movement, channel.readOutbound()); assertSame(inbound, channel.readInbound());
        now.addAndGet(TimeUnit.SECONDS.toNanos(20)); ChatSendTrace.poll();
        assertEquals(2, pending().size());
        for (Map<String, Object> row : pending()) {
            assertEquals(0L, row.get("writeMs")); assertEquals(0L, row.get("flushMs"));
            assertEquals(-1L, row.get("localCompleteMs"));
        }
        assertEquals(2, policy.held.size());
        policy.release();
        assertSame(first, channel.readOutbound()); assertSame(second, channel.readOutbound());
        assertNull(channel.readOutbound()); assertTrue(pending().isEmpty());
        assertEquals(20000L, recent().get(0).get("localCompleteMs"));
        channel.finish();
    }

    @Test void trackingLimitsAndExpiryDoNotCancelDropOrReorderPolicyHeldPackets() {
        HoldingPolicy policy = new HoldingPolicy(); policy.holdAll = true;
        EmbeddedChannel channel = new EmbeddedChannel(policy); ChatSendTrace.observe(channel);
        Object[] packets = new Object[100];
        for (int i = 0; i < packets.length; i++) { packets[i] = new Object(); packet(channel, packets[i]); }
        assertEquals(64, pending().size()); assertEquals(32, recent().size()); assertEquals(100, policy.held.size());
        now.addAndGet(TimeUnit.SECONDS.toNanos(61)); ChatSendTrace.poll();
        assertTrue(pending().isEmpty()); assertEquals(100, policy.held.size());
        policy.release();
        for (Object expected : packets) assertSame(expected, channel.readOutbound());
        assertNull(channel.readOutbound()); assertEquals(32, recent().size());
        channel.finish();
    }

    @Test void reentrantSendScopesKeepTheirOwnPacketsAndOutcomes() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter()); ChatSendTrace.observe(channel);
        Object outer = new Object(), nested = new Object();
        try (ChatSendTrace.Attempt input = ChatSendTrace.input(5, false, true);
             ChatSendTrace.Attempt send = ChatSendTrace.send(5, false, true)) {
            send.eventReturned();
            try (ChatSendTrace.Attempt inner = ChatSendTrace.send(6, true, true)) {
                inner.eventReturned(); inner.nativeCall(); ChatSendTrace.packet(nested, 6, true, true);
                channel.writeAndFlush(nested);
            }
            send.nativeCall(); ChatSendTrace.packet(outer, 5, false, true); channel.writeAndFlush(outer);
        }
        assertSame(nested, channel.readOutbound()); assertSame(outer, channel.readOutbound());
        assertEquals(2, recent().size());
        assertNotEquals(recent().get(0).get("id"), recent().get(1).get("id"));
        assertEquals(List.of(true, false), recent().stream().map(row -> row.get("command")).toList());
        assertEquals(List.of("send", "input"), recent().stream().map(row -> row.get("origin")).toList());
        channel.finish();
    }

    @Test void workerPacketsCannotBorrowAnotherThreadsClientInputScope() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter()); ChatSendTrace.observe(channel);
        Object packet = new Object();
        try (ChatSendTrace.Attempt input = ChatSendTrace.input(15, false, true)) {
            Thread worker = new Thread(() -> {
                ChatSendTrace.packet(packet, 15, false, false);
                channel.eventLoop().execute(() -> channel.writeAndFlush(packet));
            });
            worker.start(); worker.join(2000); assertFalse(worker.isAlive());
        }
        channel.runPendingTasks();
        assertSame(packet, channel.readOutbound());
        assertEquals(2, recent().size());
        Map<String, Object> worker = recent().stream().filter(row -> row.get("origin").equals("packet")).findFirst().orElseThrow();
        assertEquals(false, worker.get("clientThread")); assertEquals("local_write_complete", worker.get("outcome"));
        channel.finish();
    }

    @Test void installingTwiceAndVoidPromisesPreserveNativeWriteBehavior() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter()); ChatSendTrace.observe(channel); ChatSendTrace.observe(channel);
        Object packet = new Object();
        try (ChatSendTrace.Attempt send = ChatSendTrace.send(4, false, true)) {
            ChatSendTrace.packet(packet, 4, false, true); channel.writeAndFlush(packet, channel.voidPromise());
        }
        assertSame(packet, channel.readOutbound()); assertNull(channel.readOutbound());
        assertEquals("unobservable_promise", recent().get(0).get("outcome"));
        channel.finish();
    }

    @Test void diagnosticsNeverReadPacketContentsOrDescribeThemAsServerReceipts() {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter()); ChatSendTrace.observe(channel);
        Object packet = new Object() { @Override public String toString() { throw new AssertionError("Packet contents were inspected"); } };
        packet(channel, packet);
        Map<String, Object> row = recent().get(0);
        assertEquals("local_write_complete", row.get("outcome"));
        assertFalse(row.containsKey("message")); assertFalse(row.containsKey("server")); assertFalse(row.containsKey("recipient"));
        assertFalse(row.containsKey("delivered")); assertFalse(row.containsKey("token"));
        assertSame(packet, channel.readOutbound()); channel.finish();
    }

    private static void packet(EmbeddedChannel channel, Object packet) {
        try (ChatSendTrace.Attempt send = ChatSendTrace.send(10, false, true)) {
            send.eventReturned(); send.nativeCall(); ChatSendTrace.packet(packet, 10, false, true); channel.writeAndFlush(packet);
        }
    }

    @SuppressWarnings("unchecked") private static List<Map<String, Object>> pending() { return (List<Map<String, Object>>) ChatSendTrace.snapshot().get("pending"); }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> recent() { return (List<Map<String, Object>>) ChatSendTrace.snapshot().get("recent"); }

    private static final class HoldingPolicy extends ChannelOutboundHandlerAdapter {
        Object first, second;
        boolean holdAll;
        ChannelHandlerContext context;
        final ArrayDeque<Object[]> held = new ArrayDeque<>();
        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
            if (holdAll || message == first || message == second) { context = ctx; held.add(new Object[]{message, promise}); }
            else ctx.write(message, promise);
        }
        void release() {
            while (!held.isEmpty()) { Object[] item = held.removeFirst(); context.write(item[0], (ChannelPromise) item[1]); }
            context.flush();
        }
    }
}
