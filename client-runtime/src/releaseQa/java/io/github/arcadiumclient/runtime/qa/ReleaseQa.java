package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.client.gui.ArcadiumServersScreen;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import io.github.arcadiumclient.runtime.qa.mixin.ChatInputAccess;
import java.awt.EventQueue;
import java.awt.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.render.entity.FishingBobberRenderer;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.network.DifferentThreadException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.LiteralText;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.polyfrost.oneconfig.internal.OneConfigConfig;

public final class ReleaseQa {
    public static boolean passwordProbe, rodProbe;
    public static final List<String> drawn = new ArrayList<>();
    public static float lineWidth;
    private static final Map<String, Object> result = new LinkedHashMap<>();
    private static final String SECRET = "Release051Dummy";
    private static int ticks, phase, phaseTicks, frames, sample, switches;
    private static long sampleStart, lastFrame, maxGap;
    private static boolean done, sampling;
    private static volatile int stalePackets;
    private static ClientPlayNetworkHandler previous;
    private static Object previousWorld;
    private static Frame focusWindow;
    private static final List<Object> fpsSamples = new ArrayList<>();
    private static final int[] CAPS = {60, 5, 200, -1};

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static void connect(int index) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        ArcadiumServersScreen screen = new ArcadiumServersScreen(mc.world == null ? new TitleScreen() : new GameMenuScreen());
        mc.openScreen(screen);
        var method = ArcadiumServersScreen.class.getDeclaredMethod("connect", ServerListEntry.class);
        method.setAccessible(true);
        method.invoke(screen, new ServerListEntry("Local QA " + index, System.getProperty("arcadium.release051.server" + index), false));
    }

    private static boolean ready() {
        Minecraft mc = Minecraft.getInstance();
        return mc.world != null && mc.player != null && mc.screen == null && mc.getNetworkHandler().getConnection().isConnected();
    }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use an isolated offline account");
            require(++ticks < 1800, "QA timed out at phase " + phase);
            phaseTicks++;
            if ((phase == 1 || phase == 8 || phase == 10) && phaseTicks == 50) {
                result.put("pendingSwitch", Map.of("phase", phase, "world", mc.world != null, "player", mc.player != null,
                    "screen", mc.screen == null ? "none" : mc.screen.getClass().getName(), "previousConnected", previous != null && previous.getConnection().isConnected()));
                Files.writeString(Path.of(System.getProperty("arcadium.release051.output")).resolveSibling("progress.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            }
            if (phase == 0 && ticks > 60) {
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus = false;
                mc.options.vsync = false;
                Display.setVSyncEnabled(false);
                mc.options.fpsLimit = 260;
                connect(0); phase = 1; phaseTicks = 0;
            } else if (phase == 1 && ready()) {
                PasswordHider.setEnabled(true);
                mc.openScreen(new ChatScreen("/login " + SECRET));
                passwordProbe = true; drawn.clear(); phase = 2; phaseTicks = 0;
            } else if (phase == 2 && phaseTicks > 5) {
                require(!drawn.isEmpty() && drawn.stream().noneMatch(text -> text.contains(SECRET)) && drawn.stream().anyMatch(text -> text.contains("*****")), "Password reached the text renderer or was not masked");
                require(((ChatScreenAccessor) mc.screen).arcadium$chatField().getText().equals("/login " + SECRET), "Masking modified the command");
                passwordProbe = false;
                ((ChatInputAccess) mc.screen).release$key('\r', 28);
                result.put("passwordRendering", true);
                ToggleSprintConfig.setEnabled(true);
                ToggleSprintConfig.setToggleSprint(true);
                ToggleSprintConfig.setToggleSneak(true);
                ToggleSprintConfig.setKeybindToggleSneak(false);
                ToggleSprintConfig.toggleSprintState = true;
                ToggleSprintConfig.toggleSneakState = false;
                phase = 3; phaseTicks = 0;
            } else if (phase == 3 && phaseTicks > 5) {
                require(!mc.player.input.sneaking && !mc.player.input.jumping, "Toggle Sprint forced unrelated input");
                pushKey(225, true); phase = 4; phaseTicks = 0;
            } else if (phase == 4 && phaseTicks > 4) {
                pushKey(225, false); phase = 5; phaseTicks = 0;
            } else if (phase == 5 && phaseTicks > 4) {
                require(ToggleSprintConfig.toggleSneakState && mc.player.input.sneaking, "Sneak did not stay toggled after release");
                pushKey(225, true); phase = 6; phaseTicks = 0;
            } else if (phase == 6 && phaseTicks > 4) {
                pushKey(225, false); phase = 7; phaseTicks = 0;
            } else if (phase == 7 && phaseTicks > 4) {
                require(!ToggleSprintConfig.toggleSneakState && !mc.player.input.sneaking, "Second press did not release sneak");
                result.put("nativeToggleSneak", true);
                previous = mc.getNetworkHandler(); previousWorld = mc.world;
                connect(1); phase = 8; phaseTicks = 0;
            } else if (phase == 8 && ready() && mc.world != previousWorld) {
                require(mc.getNetworkHandler() != previous && !previous.getConnection().isConnected(), "Previous server remained connected");
                Object currentWorld = mc.world;
                previous.onDisconnect(new LiteralText("Owned stale close notification"));
                require(mc.world == currentWorld && mc.screen == null, "An old disconnect notification closed the new session");
                scheduleStalePacket(previous);
                phase = 9; phaseTicks = 0;
            } else if (phase == 9 && phaseTicks > 10) {
                require(stalePackets == 0, "A packet from the closed server ran against the new world");
                result.put("serverSwitchAndStalePackets", true);
                previous = mc.getNetworkHandler(); previousWorld = mc.world;
                connect(0); phase = 10; phaseTicks = 0;
            } else if (phase == 10 && ready() && mc.world != previousWorld) {
                require(!previous.getConnection().isConnected(), "Second server remained connected on return");
                result.put("serverReturnWithoutRestart", true);
                if (++switches < 5) {
                    previous = mc.getNetworkHandler(); previousWorld = mc.world;
                    connect(1); phase = 8;
                } else { result.put("roundTrips", switches); phase = 11; }
                phaseTicks = 0;
            } else if (phase == 12 && phaseTicks > 15) {
                EventQueue.invokeLater(() -> {
                    focusWindow = new Frame("Arcadium FPS test");
                    focusWindow.setSize(300, 100); focusWindow.setLocation(20, 20);
                    focusWindow.setVisible(true); focusWindow.toFront(); focusWindow.requestFocus();
                });
                OneConfigConfig.limitUnfocusedFps = true; OneConfigConfig.unfocusedFps = CAPS[0];
                phase = 13; phaseTicks = 0;
            } else if (phase == 14 && phaseTicks > 15) {
                if (!Display.isActive() && phaseTicks < 100) {
                    Class<?> display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
                    long window = (long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
                    Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_RaiseWindow", long.class).invoke(null, window);
                    return;
                }
                require(Display.isActive(), "The game did not regain focus after the background limit");
                result.put("backgroundFps", fpsSamples);
                result.put("focusRestored", Display.isActive());
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }

    public static void frame() {
        if (done) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (phase == 11) {
                float before = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
                RodThickness.setEnabled(true); RodThickness.setThickness(4f);
                rodProbe = true;
                FishingBobberRenderer renderer = new FishingBobberRenderer(mc.getEntityRenderDispatcher());
                FishingBobberEntity bobber = new FishingBobberEntity(mc.world, mc.player);
                result.put("rodRenderer", renderer.getClass().getName());
                result.put("rodOwner", bobber.thrower != null);
                renderer.render(bobber, 0, 1, 5, 0, 0);
                rodProbe = false;
                require(lineWidth == 4f && GL11.glGetFloat(GL11.GL_LINE_WIDTH) == before, "Fishing line thickness or GL restoration failed: " + lineWidth);
                RodThickness.setEnabled(false);
                result.put("rodWidthAndStateRestoration", true);
                phase = 12; phaseTicks = 0;
            } else if (phase == 13) {
                if (Display.isActive()) { sampling = false; return; }
                long now = System.nanoTime();
                if (!sampling) { sampling = true; sampleStart = now; lastFrame = now; frames = 0; maxGap = 0; }
                else { frames++; maxGap = Math.max(maxGap, now - lastFrame); lastFrame = now; }
                long elapsed = now - sampleStart;
                if (elapsed < 2_000_000_000L) return;
                double fps = frames * 1e9 / elapsed;
                int cap = CAPS[sample];
                require(maxGap < 750_000_000L, "The background frame loop froze");
                if (cap > 0) require(fps <= cap * 1.25 + 2 && fps >= 2, "Background FPS cap failed: " + cap + " -> " + fps);
                fpsSamples.add(Map.of("cap", cap, "fps", fps, "maxGapMs", maxGap / 1e6));
                if (++sample == CAPS.length) {
                    EventQueue.invokeLater(() -> focusWindow.dispose());
                    phase = 14; phaseTicks = 0;
                } else {
                    OneConfigConfig.limitUnfocusedFps = CAPS[sample] > 0;
                    OneConfigConfig.unfocusedFps = Math.max(5, CAPS[sample]);
                    sampling = false;
                }
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void scheduleStalePacket(ClientPlayNetworkHandler handler) {
        Thread thread = new Thread(() -> {
            Packet<ClientPlayNetworkHandler> packet = new Packet<>() {
                public void read(PacketByteBuf input) { }
                public void write(PacketByteBuf output) { }
                public void handle(ClientPlayNetworkHandler listener) { stalePackets++; }
            };
            try { PacketUtils.ensureOnSameThread(packet, handler, Minecraft.getInstance()); }
            catch (DifferentThreadException expected) { }
        }, "Closed connection QA");
        thread.setDaemon(true); thread.start();
    }

    private static void pushKey(int scancode, boolean down) throws Exception {
        Class<?> type = Class.forName("org.lwjgl.sdl.SDL_Event");
        Class<?> events = Class.forName("org.lwjgl.sdl.SDLEvents");
        Object event = type.getMethod("calloc").invoke(null);
        try {
            int kind = events.getField(down ? "SDL_EVENT_KEY_DOWN" : "SDL_EVENT_KEY_UP").getInt(null);
            type.getMethod("type", int.class).invoke(event, kind);
            Object key = type.getMethod("key").invoke(event);
            key.getClass().getMethod("type", int.class).invoke(key, kind);
            key.getClass().getMethod("scancode", int.class).invoke(key, scancode);
            key.getClass().getMethod("down", boolean.class).invoke(key, down);
            Class<?> display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
            long window = (long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
            int id = (int) Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowID", long.class).invoke(null, window);
            key.getClass().getMethod("windowID", int.class).invoke(key, id);
            require(Boolean.TRUE.equals(events.getMethod("SDL_PushEvent", type).invoke(null, event)), "Native input rejected the test key");
        } finally { type.getMethod("free").invoke(event); }
    }

    private static void finish(Throwable error) {
        if (done) return;
        done = true; passwordProbe = false; rodProbe = false;
        if (error != null) { result.put("error", error.toString()); error.printStackTrace(); }
        result.put("passed", error == null);
        result.put("synthetic", true);
        try {
            if (focusWindow != null) EventQueue.invokeLater(() -> focusWindow.dispose());
            Files.writeString(Path.of(System.getProperty("arcadium.release051.output")), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
