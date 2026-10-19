package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;

import io.github.arcadiumclient.featuretest.mixin.ChatDispatchInputAccess;
import io.github.arcadiumclient.featuretest.mixin.ChatDispatchNetworkAccess;
import io.github.arcadiumclient.featuretest.mixin.MessagingChatProbeAccess;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

public final class ChatDispatch035Qa {
    private static final String TRACE = "org.polyfrost.oneconfig.internal.network.ChatSendTrace";
    private static final String BEDWARS = "io.github.arcadiumclient.runtime.client.bedwars.";
    private static final String[] MARKERS = {"ARC_CHAT035_FIRST", "ARC_CHAT035_CANCELLED", "ARC_CHAT035_HELD_A", "ARC_CHAT035_HELD_B"};
    private static final String INBOUND = "ARC_CHAT035_INBOUND", POLICY = "arcadium_qa_chat_hold";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final ConcurrentLinkedQueue<Integer> RECEIVED = new ConcurrentLinkedQueue<>();
    private static final CountDownLatch LOOKUPS = new CountDownLatch(3), RELEASE = new CountDownLatch(1);
    private static final long[] IDS = new long[4];
    private static Object http, data, cancelHandler;
    private static Channel channel;
    private static HoldingPolicy policy;
    private static Future<?> mutation;
    private static Screen screen;
    private static List<ChatMessage> raw, visible;
    private static List<String> recent;
    private static int phase, ticks;
    private static volatile boolean begun, done, waitingForSwing;
    private static volatile int unrelatedReceived;

    private ChatDispatch035Qa() {}

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.chatDispatch035Qa") || done) return true;
        guard(mc);
        try {
            if (!begun) begin(mc, checks);
            require(++ticks < 100, "Chat dispatch QA timed out at phase " + phase);
            switch (phase) {
                case 0 -> {
                    if (ticks < 4 || LOOKUPS.getCount() != 0) return false;
                    enter(mc, 0);
                }
                case 1 -> {
                    if (ticks < 4 || RECEIVED.size() < 1) return false;
                    require(RELEASE.getCount() == 1, "The cold-cache fixture completed before chat reached its local server");
                    require("local_write_complete".equals(attempt(IDS[0]).get("outcome")), "The native Enter path did not reach its original write promise");
                    RESULT.put("nativeEnterReachedLocalServerDuringColdBedwarsLookups", true);
                    Class<?> event = Class.forName("org.polyfrost.oneconfig.api.event.v1.events.ChatEvent$Send");
                    Consumer<Object> cancel = value -> {
                        try { if (MARKERS[1].equals(value.getClass().getField("message").get(value))) value.getClass().getField("cancelled").setBoolean(value, true); }
                        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                    };
                    cancelHandler = stat("org.polyfrost.oneconfig.api.event.v1.EventManager", "register", event, cancel);
                    mc.openScreen(new ChatScreen(MARKERS[1]));
                }
                case 2 -> { if (ticks < 4) return false; enter(mc, 1); }
                case 3 -> {
                    if (ticks < 6) return false;
                    require(RECEIVED.size() == 1 && "event_cancelled".equals(attempt(IDS[1]).get("outcome")), "Chat event cancellation was bypassed or retried");
                    unregisterCancellation();
                    policy = new HoldingPolicy();
                    mutation = channel.eventLoop().submit(() -> channel.pipeline().addBefore("arcadium_chat_timing", POLICY, policy));
                }
                case 4 -> { if (!mutation.isDone()) return false; mutation.get(); mc.openScreen(new ChatScreen(MARKERS[2])); }
                case 5 -> { if (ticks < 4) return false; enter(mc, 2); mc.openScreen(new ChatScreen(MARKERS[3])); }
                case 6 -> {
                    if (ticks < 4) return false;
                    enter(mc, 3); waitingForSwing = true;
                    mc.getNetworkHandler().sendPacket(new ArmSwingC2SPacket());
                }
                case 7 -> {
                    if (ticks < 8 || policy.count != 2 || unrelatedReceived == 0 || !inboundVisible(mc)) return false;
                    require(RECEIVED.size() == 1, "Diagnostics released a packet held by another outbound policy");
                    for (int index : new int[]{2, 3}) {
                        Map<String, Object> pending = attempt(IDS[index]);
                        require("pending".equals(pending.get("outcome")) && number(pending, "writeMs") >= 0
                                && number(pending, "flushMs") >= 0 && number(pending, "localCompleteMs") == -1,
                                "A pending policy-held packet was reported as written");
                    }
                    RESULT.put("unrelatedOutboundAndInboundContinueDuringChatOnlyHold", true);
                    mutation = channel.eventLoop().submit(policy::release);
                }
                case 8 -> {
                    if (ticks < 4 || RECEIVED.size() != 3 || !mutation.isDone()) return false;
                    mutation.get();
                    require(new ArrayList<>(RECEIVED).equals(List.of(0, 2, 3)), "Native chat packets were dropped, duplicated, or reordered");
                    require(RELEASE.getCount() == 1, "Bedwars lookup completion controlled outgoing chat delivery");
                    List<Map<String, Object>> timings = new ArrayList<>();
                    for (long id : IDS) timings.add(attempt(id));
                    RESULT.put("timings", timings);
                    RESULT.put("cancelledInputNotResent", true);
                    RESULT.put("packetOrder", new ArrayList<>(RECEIVED));
                    RESULT.put("originalPromiseCompletionObserved", true);
                    restore(mc);
                }
                case 9 -> {
                    if (mutation != null && !mutation.isDone()) return false;
                    if (mutation != null) mutation.get();
                    RESULT.put("restored", true); RESULT.put("completed", true); done = true; return true;
                }
                default -> throw new IllegalStateException("Unknown chat dispatch phase");
            }
            phase++; ticks = 0; return false;
        } catch (Exception failure) {
            if (begun) try { restore(mc); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        require(mc.gameDir.toPath().toRealPath().equals(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath()), "Chat dispatch QA requires its exact isolated directory");
        screen = mc.screen;
        MessagingChatProbeAccess chat = (MessagingChatProbeAccess) mc.gui.getChat();
        raw = new ArrayList<>(chat.arcadium$rawMessages()); visible = new ArrayList<>(chat.arcadium$visibleMessages());
        recent = new ArrayList<>(mc.gui.getChat().getRecentMessages());
        channel = ((ChatDispatchNetworkAccess) mc.getNetworkHandler().getConnection()).arcadium$chatDispatchChannel();
        require(channel.pipeline().get("arcadium_chat_timing") != null, "The actual native connection has no chat timing observer");
        checks.put("chatDispatch035", RESULT); begun = true;
        Class<?> transport = Class.forName(BEDWARS + "OverlayHttp$Transport");
        Object fixture = Proxy.newProxyInstance(transport.getClassLoader(), new Class<?>[]{transport}, (proxy, method, args) -> {
            require(method.getName().equals("exchange"), "Unexpected cold-cache transport operation");
            LOOKUPS.countDown();
            try { RELEASE.await(20, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException("Fixture stopped"); }
            return Class.forName(BEDWARS + "OverlayHttp$Response").getConstructor(int.class, String.class).newInstance(200, "{\"success\":false}");
        });
        Class<?> httpType = Class.forName(BEDWARS + "OverlayHttp");
        http = httpType.getConstructor(transport).newInstance(fixture);
        data = Class.forName(BEDWARS + "BedwarsData").getConstructor(httpType).newInstance(http);
        Object config = Class.forName(BEDWARS + "BedwarsData$Policy").getConstructor(boolean.class, boolean.class, String.class).newInstance(true, false, "");
        for (int i = 0; i < 3; i++) {
            UUID id = UUID.fromString("12345678-1234-4234-9234-123456789ab" + i);
            Object pending = call(data, "get", id, "ArcadiumFixture" + i, config);
            require(pending instanceof Future<?> && !((Future<?>) pending).isDone(), "The cold Bedwars lookup did not stay asynchronous");
            require(call(data, "present", id) == null, "The cold Bedwars fixture unexpectedly used a warm cache");
        }
        mc.openScreen(new ChatScreen(MARKERS[0]));
    }

    private static void enter(Minecraft mc, int marker) throws Exception {
        require(mc.screen instanceof ChatScreen, "The chat input closed before its native Enter key");
        long before = latestId();
        ((ChatDispatchInputAccess) mc.screen).arcadium$chatDispatchEnter('\r', 28);
        require(mc.screen == null, "Native Enter did not close the chat input");
        IDS[marker] = latestId();
        require(IDS[marker] > before, "The real Enter/send path was not traced");
        require("input".equals(attempt(IDS[marker]).get("origin")), "The player send did not retain its native Enter context");
    }

    public static boolean received(MinecraftServer server, ServerPlayerEntity player, String message) {
        if (!begun || done || !server.isOnSameThread()) return false;
        int index = -1; for (int i = 0; i < MARKERS.length; i++) if (MARKERS[i].equals(message)) index = i;
        if (index < 0) return false;
        guard(Minecraft.getInstance());
        require(server == Minecraft.getInstance().getServer() && player.getName().equals("ArcadiumTest"), "Chat dispatch received a packet outside its local fixture");
        RECEIVED.add(index); return true;
    }

    public static boolean unrelated(MinecraftServer server, ServerPlayerEntity player) {
        if (!begun || done || !waitingForSwing || !server.isOnSameThread()) return false;
        guard(Minecraft.getInstance());
        require(server == Minecraft.getInstance().getServer() && player.getName().equals("ArcadiumTest"), "The unrelated packet left its fixture");
        waitingForSwing = false; unrelatedReceived++;
        player.sendMessage(new LiteralText(INBOUND)); return true;
    }

    private static boolean inboundVisible(Minecraft mc) {
        return ((MessagingChatProbeAccess) mc.gui.getChat()).arcadium$rawMessages().stream().anyMatch(message -> message.getText().getString().contains(INBOUND));
    }

    @SuppressWarnings("unchecked") private static List<Map<String, Object>> timings() throws Exception {
        Map<String, Object> snapshot = (Map<String, Object>) stat(TRACE, "snapshot");
        List<Map<String, Object>> rows = new ArrayList<>((List<Map<String, Object>>) snapshot.get("pending"));
        rows.addAll((List<Map<String, Object>>) snapshot.get("recent")); return rows;
    }
    private static long latestId() throws Exception { return timings().stream().mapToLong(row -> number(row, "id")).max().orElse(0); }
    private static Map<String, Object> attempt(long id) throws Exception { return timings().stream().filter(row -> number(row, "id") == id).findFirst().orElseThrow(() -> new IllegalStateException("Chat timing attempt disappeared: " + id)); }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }

    private static void unregisterCancellation() throws Exception {
        if (cancelHandler == null) return;
        Class<?> manager = Class.forName("org.polyfrost.oneconfig.api.event.v1.EventManager");
        manager.getMethod("unregister", Class.forName("org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler")).invoke(manager.getField("INSTANCE").get(null), cancelHandler);
        cancelHandler = null;
    }

    private static void restore(Minecraft mc) throws Exception {
        RELEASE.countDown(); unregisterCancellation();
        if (data != null) call(data, "clear");
        if (http instanceof AutoCloseable closeable) closeable.close();
        if (channel != null && policy != null) mutation = channel.eventLoop().submit(() -> {
            policy.abort(); if (channel.pipeline().get(POLICY) != null) channel.pipeline().remove(POLICY);
        });
        waitingForSwing = false;
        MessagingChatProbeAccess chat = (MessagingChatProbeAccess) mc.gui.getChat();
        chat.arcadium$rawMessages().clear(); chat.arcadium$rawMessages().addAll(raw);
        chat.arcadium$visibleMessages().clear(); chat.arcadium$visibleMessages().addAll(visible);
        mc.gui.getChat().getRecentMessages().clear(); mc.gui.getChat().getRecentMessages().addAll(recent);
        mc.openScreen(screen);
    }

    private static void guard(Minecraft mc) {
        require(Boolean.getBoolean("arcadium.forgeQa") && "ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Chat dispatch QA requires the isolated offline account");
        require(mc.world != null && mc.player != null && mc.isSingleplayer() && mc.getServer() != null && !mc.getServer().isPublished()
                && mc.getServer().getPlayerManager().getAll().size() == 1, "Chat dispatch QA requires its unpublished single-player world");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static final class HoldingPolicy extends ChannelOutboundHandlerAdapter {
        final ArrayDeque<Object[]> held = new ArrayDeque<>();
        ChannelHandlerContext context;
        volatile int count;
        @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
            if (message instanceof ChatMessageC2SPacket packet && (MARKERS[2].equals(packet.getMessage()) || MARKERS[3].equals(packet.getMessage()))) {
                context = ctx; held.addLast(new Object[]{message, promise}); count++;
            } else ctx.write(message, promise);
        }
        void release() {
            while (!held.isEmpty()) { Object[] item = held.removeFirst(); context.write(item[0], (ChannelPromise) item[1]); }
            context.flush();
        }
        void abort() { while (!held.isEmpty()) ((ChannelPromise) held.removeFirst()[1]).tryFailure(new java.util.concurrent.CancellationException()); }
    }
}
