package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.nodes;
import static io.github.arcadiumclient.featuretest.Visual033Qa.object;
import static io.github.arcadiumclient.featuretest.Visual033Qa.properties;
import static io.github.arcadiumclient.featuretest.Visual033Qa.rectangle;
import static io.github.arcadiumclient.featuretest.Visual033Qa.semanticsRoot;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldSettings.GameMode;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class CuteVisuals035Qa {
    private static final String CONFIG = "io.github.arcadiumclient.runtime.client.features.CuteVisualsConfig";
    private static final String EFFECT = "io.github.arcadiumclient.runtime.client.features.CuteVisuals";
    private static final BlockPos FOOT = new BlockPos(10, 4, 10), HEAD = FOOT.south(), BEDROCK = new BlockPos(12, 4, 10);
    private static final BlockPos[] BLOCKS = {FOOT, HEAD, BEDROCK};
    private static final String[] OPTIONS = {"enabled", "bedSound", "bedBurst", "bedBurstCount", "bedBurstSize", "bedBurstSpeed",
            "bedBurstLifetime", "rainbow", "rainbowLineWidth", "rainbowDuration", "onlyWhileMoving", "opacity", "hearts",
            "heartsSpawnRate", "heartsLifetime", "dots", "dotsSpawnRate", "dotsLifetime", "pulse"};
    private static final Object[] DEFAULTS = {false, true, true, 20, 0.2f, 2.5f, 1500, true, 5f, 3000, true, 85, true, 200, 1500, true, 100, 1500, false};
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Object> ORIGINAL = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final BlockState[] CLIENT_BLOCKS = new BlockState[3], SERVER_BLOCKS = new BlockState[3];
    private static Object config, tree, backend, shell;
    private static Path file, output;
    private static byte[] contents;
    private static String search, pendingProbe, worldProbe;
    private static Screen screen;
    private static Pose pose, serverPose;
    private static Abilities abilities, serverAbilities;
    private static GameMode gameMode, serverMode;
    private static Future<?> preparing, restoring;
    private static boolean[] keys;
    private static float forward, sideways, health, serverHealth;
    private static boolean jumping, sneaking, hideGui, begun, done, restored;
    private static int perspective, phase, ticks, frames, drawStart, manualDraws, breakStart, packets;
    private static long expiringSince, worldProbeSince;
    private static Throwable renderFailure;
    private static ByteBuffer worldBefore, worldAfter;
    private static int worldX, worldY, worldWidth, worldHeight, worldAttempts;
    private static boolean samplingWorld, manualProbe;

    private CuteVisuals035Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.release035Qa") || done) return true;
        require(Boolean.getBoolean("arcadium.forgeQa") && "ArcadiumTest".equals(mc.getSession().getUsername())
                && "0".equals(mc.getSession().getAccessToken()), "Cute Visuals QA requires the isolated offline fixture");
        require(mc.isSingleplayer() && mc.world != null && mc.player != null, "Cute Visuals QA requires its loaded local world");
        try {
            if (!begun) begin(mc, checks);
            if (renderFailure != null) throw new IllegalStateException("Cute Visuals GL probe failed", renderFailure);
            if (phase >= 3 && phase <= 6) aimBed(mc);
            require(++ticks < 160, "Cute Visuals QA timed out at phase " + phase);
            switch (phase) {
                case 0 -> {
                    if (ticks < 14 || !preparing.isDone()) return false;
                    preparing.get();
                    require(mc.world.getBlockState(FOOT).getBlock() == Blocks.BED && mc.world.getBlockState(HEAD).getBlock() == Blocks.BED,
                            "The server-installed bed did not reach the client world");
                    require(mc.screen != null && mc.screen.getClass().getSimpleName().contains("OneConfigUIScreen")
                            && Visual033Qa.hasText(mc.screen, "Cute Visuals"), "The Cute Visuals module settings did not open");
                    toggle(mc);
                    require(enabled(), "The visible Enabled switch did not enable Cute Visuals");
                }
                case 1 -> {
                    if (ticks < 7) return false;
                    persistAndReload(true);
                    capture(mc, "cute035-settings-enabled.png");
                    mc.openScreen(null);
                    mc.options.hideGui = false; mc.options.perspective = 1;
                    clientMode(mc, GameMode.SURVIVAL);
                    move(mc, true);
                    drawStart = count("renderedCount"); manualDraws = 0;
                    worldProbe = "trail"; worldAttempts = 0; worldProbeSince = System.nanoTime();
                }
                case 2 -> {
                    if (ticks < 14) return false;
                    require(count("heartCount") >= 2 && count("dotCount") >= 3, "Registered native ticks did not emit the moving trail");
                    require(count("renderedCount") - drawStart > manualDraws + 2, "The real world renderer did not draw the moving trail");
                    require(RESULT.containsKey("trailWorldPixels"), "The moving trail did not change visible pixels in the actual world frame");
                    RESULT.put("nativeMovingTrail", Map.of("hearts", count("heartCount"), "dots", count("dotCount"),
                            "naturalDraws", count("renderedCount") - drawStart - manualDraws));
                    capture(mc, "cute035-moving-trail.png");
                    move(mc, false); mc.player.input.movementForward = 0; mc.player.input.movementSideways = 0;
                    pendingProbe = "trail";
                }
                case 3 -> {
                    if (ticks < 5 || pendingProbe != null) return false;
                    require(RESULT.containsKey("trailGlStateRestored"), "The moving trail GL probe did not run");
                    packetCases(mc);
                    drawStart = count("renderedCount"); manualDraws = 0;
                    worldProbe = "bed"; worldAttempts = 0; worldProbeSince = System.nanoTime();
                }
                case 4 -> {
                    if (ticks < 10) return false;
                    require(count("bedParticleCount") > 0 && count("rainbowCount") == 1, "The confirmed bed effect expired before it could render");
                    require(count("renderedCount") - drawStart > manualDraws + 2, "The actual world hook did not render the native bed effect");
                    require(RESULT.containsKey("bedWorldPixels"), "The native bed effect did not change visible pixels in the actual world frame");
                    capture(mc, "cute035-bed-burst-rainbow.png");
                    for (int i = 0; i < 12; i++) { send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, FOOT); send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, FOOT); }
                    require(count("bedParticleCount") == 200 && count("rainbowCount") == 5, "Repeated native packets did not respect the burst and rainbow pool limits");
                    clientMode(mc, GameMode.CREATIVE);
                    int before = count("breakCount");
                    send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, FOOT);
                    require(count("breakCount") == before + 1, "Creative START did not create an instant bed effect");
                    send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, FOOT);
                    require(count("breakCount") == before + 1, "Creative STOP repeated the instant bed effect");
                    clientMode(mc, GameMode.SURVIVAL);
                    RESULT.put("boundedNativePacketBurst", Map.of("bedParticles", count("bedParticleCount"), "rainbows", count("rainbowCount"), "sentPackets", packets));
                    RESULT.put("creativeStartOnly", true); pendingProbe = "bed";
                }
                case 5 -> {
                    if (ticks < 5 || pendingProbe != null) return false;
                    require(RESULT.containsKey("bedGlStateRestored"), "The bed effect GL probe did not run");
                    require(((Number) ((Map<?, ?>) RESULT.get("bedPixels")).get("pixels")).intValue() > 0,
                            "The native bed renderer submitted no visible pixels");
                    set("hearts", false); set("dots", false); set("bedBurstLifetime", 500); set("rainbowDuration", 1000);
                    expiringSince = System.nanoTime();
                }
                case 6 -> {
                    if (System.nanoTime() - expiringSince < 1_250_000_000L) return false;
                    require(count("activeCount") == 0, "Stationary native effects did not expire without new emission");
                    RESULT.put("nativeLifetimeExpiry", true);
                    set("hearts", true); set("dots", true); set("bedBurstLifetime", 1500); set("rainbowDuration", 3000);
                    move(mc, true);
                }
                case 7 -> {
                    if (ticks < 10) return false;
                    require(count("activeCount") > 0, "The trail failed to resume after expiry");
                    move(mc, false); mc.player.input.movementForward = 0; mc.player.input.movementSideways = 0;
                    send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, FOOT);
                    ClientWorld actual = mc.world;
                    try { mc.world = null; stat(EFFECT, "tick"); }
                    finally { mc.world = actual; }
                    stat(EFFECT, "tick");
                    require(count("activeCount") == 0, "Detaching and restoring the actual world retained old particles");
                    int before = count("breakCount"); send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, FOOT);
                    require(count("breakCount") == before, "A detached world retained pending bed completion");
                    RESULT.put("worldDetachClearsPoolsAndPendingBed", true);
                    move(mc, true);
                }
                case 8 -> {
                    if (ticks < 8) return false;
                    require(count("activeCount") > 0, "The restored world did not resume its trail");
                    move(mc, false); openSettings();
                }
                case 9 -> {
                    if (ticks < 10) return false;
                    toggle(mc); require(!enabled(), "The visible Enabled switch did not disable Cute Visuals");
                    persistAndReload(false);
                }
                case 10 -> {
                    if (ticks < 6) return false;
                    require(count("activeCount") == 0, "Disabling Cute Visuals retained active effects");
                    int before = count("breakCount");
                    send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, FOOT); send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, FOOT);
                    require(count("breakCount") == before && count("activeCount") == 0, "Disabled Cute Visuals reacted to a native mining packet");
                    capture(mc, "cute035-settings-disabled.png");
                    RESULT.put("uiDisableClearsEffects", true); RESULT.put("nativePacketCases", true);
                    restore(mc);
                }
                case 11 -> {
                    if (ticks < 8 || !restoring.isDone()) return false;
                    restoring.get(); restoreClient(mc);
                    require(mc.player.isAlive() && mc.player.getHealth() == health, "Cute Visuals QA changed fixture player health");
                    for (int i = 0; i < BLOCKS.length; i++) require(mc.world.getBlockState(BLOCKS[i]).equals(CLIENT_BLOCKS[i]), "A temporary block remained in the client fixture");
                    for (String name : OPTIONS) require(equal(call(property(name), "get"), ORIGINAL.get(name)), "Cute Visuals QA did not restore option " + name);
                    require(Arrays.equals(contents, Files.exists(file) ? Files.readAllBytes(file) : null), "The original Cute Visuals config bytes were not restored");
                    RESULT.put("restored", true); RESULT.put("completed", true); RESULT.put("framesObserved", frames);
                    done = true; return true;
                }
                default -> throw new IllegalStateException("Unknown Cute Visuals QA phase");
            }
            phase++; ticks = 0; return false;
        } catch (Exception failure) {
            if (begun) try { restore(mc); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        Path game = mc.gameDir.toPath().toRealPath();
        String expected = System.getProperty("arcadium.forgeQa.gameDir");
        require(expected != null && Path.of(expected).toRealPath().equals(game), "Cute Visuals QA requires its exact isolated game directory");
        config = object(CONFIG); tree = call(config, "getTree");
        require("arcadium-cute-visuals.json".equals(field(config, "id")), "The Cute Visuals module ID is incorrect");
        require("oneconfig.visuals".equals(call(field(config, "category"), "getName")), "Cute Visuals is not in Visuals");
        Object manager = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
        Path folder = ((Path) call(manager, "getFolder")).toAbsolutePath().normalize();
        file = folder.resolve("arcadium-cute-visuals.json"); backend = field(manager, "backend");
        require(folder.startsWith(game) && folder.toRealPath().startsWith(game) && !Files.isSymbolicLink(file), "Cute Visuals config must stay inside its fixture");
        contents = Files.exists(file) ? Files.readAllBytes(file) : null;
        require(contents == null || contents.length < 1024 * 1024, "The Cute Visuals fixture config is unexpectedly large");
        for (int i = 0; i < OPTIONS.length; i++) {
            Object prop = property(OPTIONS[i]); ORIGINAL.put(OPTIONS[i], call(prop, "get"));
            require(equal(call(prop, "getMetadata", "default"), DEFAULTS[i]), "Cute Visuals code default changed for " + OPTIONS[i]);
            require(equal(ORIGINAL.get(OPTIONS[i]), DEFAULTS[i]), "Cute Visuals QA requires fresh defaults for " + OPTIONS[i]);
        }
        require(!enabled(), "Cute Visuals must default to disabled in the fresh fixture");
        ranges("bedBurstCount", 5, 40, 1); ranges("bedBurstSize", 0.05, 0.6, 0.01); ranges("bedBurstSpeed", 0.5, 7, 0.1);
        ranges("bedBurstLifetime", 500, 3000, 100); ranges("rainbowLineWidth", 1, 12, 0.5); ranges("rainbowDuration", 1000, 6000, 200);
        ranges("opacity", 20, 100, 5); ranges("heartsSpawnRate", 50, 500, 10); ranges("heartsLifetime", 500, 4000, 100);
        ranges("dotsSpawnRate", 20, 200, 10); ranges("dotsLifetime", 500, 5000, 100);
        screen = mc.screen; gameMode = mc.interactionManager.getGameMode(); pose = Pose.capture(mc.player);
        abilities = Abilities.capture(mc.player.abilities); health = mc.player.getHealth();
        require(mc.player.isAlive(), "Cute Visuals QA requires a live fixture player");
        forward = mc.player.input.movementForward; sideways = mc.player.input.movementSideways;
        jumping = mc.player.input.jumping; sneaking = mc.player.input.sneaking;
        KeyBinding[] movement = movementKeys(mc); keys = new boolean[movement.length];
        for (int i = 0; i < keys.length; i++) keys[i] = movement[i].isPressed();
        perspective = mc.options.perspective; hideGui = mc.options.hideGui;
        shell = object("org.polyfrost.oneconfig.internal.ui.shell.ShellState"); search = (String) call(shell, "getSearchQuery");
        output = game.resolve("forge-qa"); Files.createDirectories(output);
        for (int i = 0; i < BLOCKS.length; i++) CLIENT_BLOCKS[i] = mc.world.getBlockState(BLOCKS[i]);
        begun = true; checks.put("cuteVisuals035", RESULT); RESULT.put("screenshots", CAPTURES);
        RESULT.put("optionDefaultsAndRanges", OPTIONS.length); RESULT.put("defaultEnabled", false);
        preparing = mc.getServer().executeTask(() -> {
            var player = mc.getServer().getPlayerManager().getAll().get(0); var world = player.getServerWorld();
            serverPose = Pose.capture(player); serverAbilities = Abilities.capture(player.abilities);
            serverMode = player.interactionManager.getGameMode(); serverHealth = player.getHealth();
            require(player.isAlive(), "Cute Visuals QA requires a live server fixture player");
            for (int i = 0; i < BLOCKS.length; i++) {
                SERVER_BLOCKS[i] = world.getBlockState(BLOCKS[i]);
                require(SERVER_BLOCKS[i].getBlock() == Blocks.AIR, "The temporary Cute Visuals blocks must start as empty fixture cells");
            }
            require(world.getBlockState(FOOT.down()).getBlock() == Blocks.STONE && world.getBlockState(HEAD.down()).getBlock() == Blocks.STONE,
                    "The temporary bed requires the known fixture floor");
            player.setGameMode(GameMode.SPECTATOR);
            BlockState bed = Blocks.BED.defaultState().set(HorizontalFacingBlock.FACING, Direction.SOUTH);
            world.setBlockState(FOOT, bed.set(BedBlock.PART, BedBlock.Part.FOOT), 2);
            world.setBlockState(HEAD, bed.set(BedBlock.PART, BedBlock.Part.HEAD), 2);
            world.setBlockState(BEDROCK, Blocks.BEDROCK.defaultState(), 2);
        });
        RESULT.put("packetFixture", "Server-installed bed; spectator server player prevents item drops while native client packets are observed");
        openSettings();
    }

    private static void packetCases(Minecraft mc) throws Exception {
        clientMode(mc, GameMode.SURVIVAL); breakStart = count("breakCount");
        send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, FOOT);
        require(count("breakCount") == breakStart && count("bedParticleCount") == 0, "Survival START emitted a premature burst");
        send(mc, PlayerHandActionC2SPacket.Action.ABORT_DESTROY_BLOCK, FOOT); send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, FOOT);
        require(count("breakCount") == breakStart, "An aborted native bed dig emitted an effect");
        send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, BEDROCK); send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, BEDROCK);
        require(count("breakCount") == breakStart, "Bedrock was mistaken for a bed");
        send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, FOOT); send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, BEDROCK);
        require(count("breakCount") == breakStart, "A different block completed a pending bed dig");
        send(mc, PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK, FOOT); send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, FOOT);
        require(count("breakCount") == breakStart + 1 && count("bedParticleCount") == 20 && count("rainbowCount") == 1,
                "Matching native START and STOP did not create the configured bed effect");
        send(mc, PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK, FOOT);
        require(count("breakCount") == breakStart + 1, "Repeated native STOP duplicated the bed effect");
        RESULT.put("abortAndBedrockRejected", true); RESULT.put("matchingStartStopConfirmedOnce", true);
    }

    private static void send(Minecraft mc, PlayerHandActionC2SPacket.Action action, BlockPos pos) {
        mc.player.networkHandler.sendPacket(new PlayerHandActionC2SPacket(action, pos, Direction.UP)); packets++;
    }

    private static void clientMode(Minecraft mc, GameMode mode) {
        mc.interactionManager.setGameMode(mode);
        mc.player.abilities.flying = true; mc.player.abilities.canFly = true; mc.player.abilities.invulnerable = true; mc.player.fallDistance = 0;
    }

    private static void aimBed(Minecraft mc) {
        move(mc, false);
        mc.options.perspective = 0;
        mc.player.setPositionAndAngles(8.5, 4, 5.5, -21.8f, 8f);
        mc.player.prevX = mc.player.x; mc.player.prevY = mc.player.y; mc.player.prevZ = mc.player.z;
        mc.player.lastYaw = mc.player.yaw; mc.player.lastPitch = mc.player.pitch;
        mc.player.velocityX = 0; mc.player.velocityY = 0; mc.player.velocityZ = 0;
        mc.player.input.movementForward = 0; mc.player.input.movementSideways = 0;
    }

    private static void move(Minecraft mc, boolean enabled) {
        for (KeyBinding key : movementKeys(mc)) KeyBinding.set(key.getKeyCode(), false);
        KeyBinding.set(mc.options.forwardKey.getKeyCode(), enabled);
    }

    private static KeyBinding[] movementKeys(Minecraft mc) {
        return new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sneakKey, mc.options.sprintKey};
    }

    private static void openSettings() throws Exception {
        call(shell, "setSearchQuery", "");
        Object route = Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute")
                .getConstructor(String.class, String.class).newInstance("arcadium-cute-visuals.json", "Cute Visuals");
        stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", route);
    }

    private static void toggle(Minecraft mc) throws Exception {
        Object label = null;
        for (Object node : nodes(semanticsRoot(mc.screen))) if (properties(node).get("Text") instanceof Collection<?> text)
            for (Object value : text) if ("Enabled".equals(call(value, "getText"))) { label = node; break; }
        require(label != null, "The visible Cute Visuals Enabled label is missing");
        Object labelBounds = call(label, "getBoundsInRoot");
        float labelY = (((Number) call(labelBounds, "getTop")).floatValue() + ((Number) call(labelBounds, "getBottom")).floatValue()) / 2;
        Object selected = null; float distance = Float.MAX_VALUE;
        for (Object node : nodes(semanticsRoot(mc.screen))) {
            if (!properties(node).containsKey("OnClick")) continue;
            Object rect = call(node, "getBoundsInRoot");
            float width = ((Number) call(rect, "getRight")).floatValue() - ((Number) call(rect, "getLeft")).floatValue();
            float top = ((Number) call(rect, "getTop")).floatValue(), bottom = ((Number) call(rect, "getBottom")).floatValue();
            float height = bottom - top, delta = Math.abs((top + bottom) / 2 - labelY);
            if (width < 20 || width > 160 || height < 10 || height > 80 || Math.abs(width / height - 2) > 0.08 || delta >= distance) continue;
            selected = node; distance = delta;
        }
        require(selected != null && distance < 30, "The actual Enabled switch could not be paired with its visible label");
        RESULT.put("enabledSwitchBounds", rectangle(selected, mc, "Cute Visuals Enabled"));
        require(Boolean.TRUE.equals(call(call(properties(selected).get("OnClick"), "getAction"), "invoke")), "The Cute Visuals Enabled switch rejected its click");
    }

    private static void persistAndReload(boolean expected) throws Exception {
        call(config, "save");
        JsonObject saved = new JsonParser().parse(Files.readString(file)).getAsJsonObject();
        require(saved.get("enabled").getAsBoolean() == expected, "The UI Enabled value was not saved");
        for (String name : OPTIONS) require(saved.has(name), "The saved Cute Visuals profile lost option " + name);
        stat(CONFIG, "setEnabled", !expected);
        require(Boolean.TRUE.equals(call(backend, "load", tree)) && enabled() == expected, "Reloading the native config backend lost the UI Enabled value");
        for (String name : OPTIONS) if (!name.equals("enabled")) require(equal(call(property(name), "get"), ORIGINAL.get(name)), "Saving or reloading Enabled changed option " + name);
        RESULT.put("uiEnabledSaveAndBackendReload", true);
    }

    private static Object property(String name) throws Exception {
        Object prop = call(tree, "get", name); require(prop != null, "Cute Visuals option is missing: " + name); return prop;
    }
    private static void set(String name, Object value) throws Exception { call(property(name), "setAsReferential", value); }
    private static boolean enabled() throws Exception { return Boolean.TRUE.equals(stat(CONFIG, "getEnabled")); }
    private static int count(String method) throws Exception { return ((Number) stat(EFFECT, method)).intValue(); }
    private static boolean equal(Object a, Object b) { return a instanceof Number x && b instanceof Number y ? Math.abs(x.doubleValue() - y.doubleValue()) < 0.00001 : java.util.Objects.equals(a, b); }
    private static void ranges(String name, double min, double max, double step) throws Exception {
        Object p = property(name);
        require(equal(call(p, "getMetadata", "min"), min) && equal(call(p, "getMetadata", "max"), max) && equal(call(p, "getMetadata", "step"), step),
                "Cute Visuals slider range changed for " + name);
    }

    public static void afterFrame() {
        if (!begun || done || !Boolean.getBoolean("arcadium.release035Qa")) return;
        frames++;
        if (pendingProbe == null || renderFailure != null) return;
        String requested = pendingProbe; pendingProbe = null;
        try {
            manualProbe = true;
            RESULT.put(requested + "Pixels", pixels(Minecraft.getInstance(), requested));
            RESULT.put(requested + "GlStateRestored", true);
        } catch (Throwable failure) { renderFailure = failure; }
        finally { manualProbe = false; }
    }

    public static void beforeWorldDraw(float tickDelta) {
        if (!begun || done || manualProbe || worldProbe == null || renderFailure != null || !Boolean.getBoolean("arcadium.release035Qa")) return;
        if (System.nanoTime() - worldProbeSince < 300_000_000L) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || count("activeCount") == 0) return;
            IntBuffer viewport = BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            worldX = viewport.get(0); worldY = viewport.get(1); worldWidth = viewport.get(2); worldHeight = viewport.get(3);
            require(worldWidth > 0 && worldHeight > 0 && worldWidth <= 4096 && worldHeight <= 2160, "The world pixel probe viewport is outside its bounds");
            int bytes = worldWidth * worldHeight * 4;
            if (worldBefore == null || worldBefore.capacity() != bytes) {
                worldBefore = BufferUtils.createByteBuffer(bytes); worldAfter = BufferUtils.createByteBuffer(bytes);
            }
            readWorldPixels(worldBefore); samplingWorld = true;
        } catch (Throwable failure) { renderFailure = failure; }
    }

    public static void afterWorldDraw(float tickDelta) {
        if (!samplingWorld) return;
        samplingWorld = false;
        try {
            readWorldPixels(worldAfter);
            int changed = 0, pink = 0;
            for (int i = 0; i < worldAfter.capacity(); i += 4) {
                int red = worldAfter.get(i) & 255, green = worldAfter.get(i + 1) & 255, blue = worldAfter.get(i + 2) & 255;
                int delta = Math.abs(red - (worldBefore.get(i) & 255)) + Math.abs(green - (worldBefore.get(i + 1) & 255)) + Math.abs(blue - (worldBefore.get(i + 2) & 255));
                if (delta <= 6) continue;
                changed++;
                if (red > green * 1.1 && blue > green * 1.05) pink++;
            }
            worldAttempts++;
            if (changed < 20) {
                require(worldAttempts < 30, "The actual " + worldProbe + " render hook produced no visible world pixels");
                return;
            }
            String requested = worldProbe;
            RESULT.put(requested + "WorldPixels", Map.of("changedPixels", changed, "pinkPixels", pink, "sampledFrames", worldAttempts,
                    "viewport", List.of(worldWidth, worldHeight), "partialTick", tickDelta));
            BufferedImage image = new BufferedImage(worldWidth, worldHeight, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < worldHeight; y++) for (int x = 0; x < worldWidth; x++) {
                int i = ((worldHeight - y - 1) * worldWidth + x) * 4;
                image.setRGB(x, y, (worldAfter.get(i) & 255) << 16 | (worldAfter.get(i + 1) & 255) << 8 | worldAfter.get(i + 2) & 255);
            }
            String capture = "cute035-" + requested + "-actual-world.png";
            ImageIO.write(image, "png", output.resolve(capture).toFile()); CAPTURES.add("forge-qa/" + capture);
            worldProbe = null;
        } catch (Throwable failure) { renderFailure = failure; }
    }

    private static void readWorldPixels(ByteBuffer pixels) {
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT), rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS), skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawFbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            pixels.clear(); GL11.glReadPixels(worldX, worldY, worldWidth, worldHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment); GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows); GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
        }
    }

    private static Map<String, Object> pixels(Minecraft mc, String name) throws Exception {
        GlSnapshot original = new GlSnapshot();
        Object stored = Class.forName("org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState").getConstructor(int.class).newInstance(330);
        call(stored, "capture");
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE), fbo = GL30.glGenFramebuffers(), texture = GL11.glGenTextures();
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glOrtho(-8, 8, -4, 8, -40, 40);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL20.glUseProgram(0); GlStateManager.bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 256, 192, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo); GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Cute Visuals pixel framebuffer is incomplete");
            GL11.glViewport(0, 0, 256, 192); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GlStateManager.disableDepthTest(); GlStateManager.disableAlphaTest();
            GL11.glClearColor(3f / 255, 5f / 255, 7f / 255, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            for (int variant = 0; variant < 2; variant++) {
                if (variant == 0) { GlStateManager.enableTexture(); GlStateManager.enableCull(); GlStateManager.enableLighting(); GlStateManager.disableBlend(); GlStateManager.disableDepthTest(); GlStateManager.disableAlphaTest(); GL11.glDisable(GL11.GL_LINE_SMOOTH); }
                else { GlStateManager.disableTexture(); GlStateManager.disableCull(); GlStateManager.disableLighting(); GlStateManager.enableBlend(); GlStateManager.enableDepthTest(); GlStateManager.enableAlphaTest(); GL11.glEnable(GL11.GL_LINE_SMOOTH); }
                GlStateManager.depthMask(variant == 0); GlStateManager.color4f(0.35f, 0.55f, 0.75f, 0.65f);
                GlStateManager.blendFuncSeparate(770, 771, 1, 0); GL11.glLineWidth(1.75f);
                GlSnapshot before = new GlSnapshot(); int batches = count("batchCount"), draws = count("renderedCount");
                stat(EFFECT, "render", 0f); manualDraws++;
                require(count("renderedCount") == draws + 1, "Cute Visuals skipped the live native pixel probe");
                require(count("batchCount") - batches <= 8, "Cute Visuals exceeded its bounded per-frame draw batches");
                GlSnapshot after = new GlSnapshot();
                require(before.same(after), "Cute Visuals leaked GL state in " + name + " variant " + variant);
            }
            ByteBuffer pixels = BufferUtils.createByteBuffer(256 * 192 * 4);
            GL11.glReadPixels(0, 0, 256, 192, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            BufferedImage image = new BufferedImage(256, 192, BufferedImage.TYPE_INT_RGB); int ink = 0;
            for (int y = 0; y < 192; y++) for (int x = 0; x < 256; x++) {
                int at = ((191 - y) * 256 + x) * 4;
                int rgb = (pixels.get(at) & 255) << 16 | (pixels.get(at + 1) & 255) << 8 | pixels.get(at + 2) & 255;
                if (rgb != 0x030507) ink++;
                image.setRGB(x, y, rgb);
            }
            require(ink > 0, "Cute Visuals produced no visible " + name + " pixels");
            String capture = "cute035-" + name + "-pixels.png"; ImageIO.write(image, "png", output.resolve(capture).toFile()); CAPTURES.add("forge-qa/" + capture);
            return Map.of("pixels", ink, "glVariants", 2, "maxBatchesPerFrame", 8);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(mode);
            call(stored, "restore"); stat("org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLStateKt", "resyncTextureBindCache");
            original.restore(); GL30.glDeleteFramebuffers(fbo); GlStateManager.deleteTextures(texture);
        }
    }

    private static final class GlSnapshot {
        final int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), smooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        final float line = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        final float[] color = floats(GL11.GL_CURRENT_COLOR, 4), model = floats(GL11.GL_MODELVIEW_MATRIX, 16), projection = floats(GL11.GL_PROJECTION_MATRIX, 16);
        boolean same(GlSnapshot other) {
            return binding == other.binding && mode == other.mode && src == other.src && dst == other.dst && srcAlpha == other.srcAlpha && dstAlpha == other.dstAlpha
                    && texture == other.texture && lighting == other.lighting && cull == other.cull && blend == other.blend && depth == other.depth
                    && mask == other.mask && smooth == other.smooth && alpha == other.alpha && Math.abs(line - other.line) < 0.001 && near(color, other.color) && near(model, other.model) && near(projection, other.projection);
        }
        void restore() {
            if (texture) { GlStateManager.disableTexture(); GlStateManager.enableTexture(); } else { GlStateManager.enableTexture(); GlStateManager.disableTexture(); }
            if (lighting) { GlStateManager.disableLighting(); GlStateManager.enableLighting(); } else { GlStateManager.enableLighting(); GlStateManager.disableLighting(); }
            if (cull) { GlStateManager.disableCull(); GlStateManager.enableCull(); } else { GlStateManager.enableCull(); GlStateManager.disableCull(); }
            if (blend) { GlStateManager.disableBlend(); GlStateManager.enableBlend(); } else { GlStateManager.enableBlend(); GlStateManager.disableBlend(); }
            if (depth) { GlStateManager.disableDepthTest(); GlStateManager.enableDepthTest(); } else { GlStateManager.enableDepthTest(); GlStateManager.disableDepthTest(); }
            if (alpha) { GlStateManager.disableAlphaTest(); GlStateManager.enableAlphaTest(); } else { GlStateManager.enableAlphaTest(); GlStateManager.disableAlphaTest(); }
            GlStateManager.depthMask(!mask); GlStateManager.depthMask(mask);
            if (smooth) GL11.glEnable(GL11.GL_LINE_SMOOTH); else GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(line); GlStateManager.blendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            GlStateManager.clearColor(); GlStateManager.color4f(color[0], color[1], color[2], color[3]);
            GlStateManager.bindTexture(binding);
        }
        private static float[] floats(int name, int count) {
            FloatBuffer values = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(name, values); float[] result = new float[count];
            for (int i = 0; i < count; i++) result[i] = values.get(i); return result;
        }
        private static boolean near(float[] a, float[] b) {
            for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 0.0001f) return false; return true;
        }
    }

    private static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); CAPTURES.add("screenshots/" + name); }

    private static void restore(Minecraft mc) throws Exception {
        pendingProbe = null; worldProbe = null; samplingWorld = false; worldBefore = null; worldAfter = null;
        if (!restored) {
            restored = true; move(mc, false);
            mc.openScreen(screen);
            for (String name : OPTIONS) set(name, ORIGINAL.get(name));
            call(config, "save");
            if (contents == null) Files.deleteIfExists(file); else Files.write(file, contents);
            call(shell, "setSearchQuery", search);
            preparing.get(5, TimeUnit.SECONDS);
            restoring = mc.getServer().executeTask(() -> {
                var player = mc.getServer().getPlayerManager().getAll().get(0); var world = player.getServerWorld();
                for (int i = 0; i < BLOCKS.length; i++) if (SERVER_BLOCKS[i] != null) world.setBlockState(BLOCKS[i], SERVER_BLOCKS[i], 2);
                player.setGameMode(serverMode); serverAbilities.restore(player.abilities); serverPose.restore(player);
                require(player.isAlive() && player.getHealth() == serverHealth, "Cute Visuals QA changed server fixture health");
                for (int i = 0; i < BLOCKS.length; i++) require(world.getBlockState(BLOCKS[i]).equals(SERVER_BLOCKS[i]), "Cute Visuals QA failed to restore a server block");
            });
        }
        restoreClient(mc);
    }

    private static void restoreClient(Minecraft mc) {
        mc.interactionManager.setGameMode(gameMode); abilities.restore(mc.player.abilities); pose.restore(mc.player);
        mc.options.perspective = perspective; mc.options.hideGui = hideGui;
        KeyBinding[] movement = movementKeys(mc); for (int i = 0; i < keys.length; i++) KeyBinding.set(movement[i].getKeyCode(), keys[i]);
        mc.player.input.movementForward = forward; mc.player.input.movementSideways = sideways;
        mc.player.input.jumping = jumping; mc.player.input.sneaking = sneaking;
    }

    private record Abilities(boolean flying, boolean canFly, boolean invulnerable, boolean creative, boolean canModify) {
        static Abilities capture(PlayerAbilities a) { return new Abilities(a.flying, a.canFly, a.invulnerable, a.creativeMode, a.canModifyWorld); }
        void restore(PlayerAbilities a) { a.flying = flying; a.canFly = canFly; a.invulnerable = invulnerable; a.creativeMode = creative; a.canModifyWorld = canModify; }
    }
    private record Pose(double x, double y, double z, double prevX, double prevY, double prevZ, float yaw, float pitch, float lastYaw, float lastPitch,
                        double vx, double vy, double vz, float fall) {
        static Pose capture(Entity e) { return new Pose(e.x, e.y, e.z, e.prevX, e.prevY, e.prevZ, e.yaw, e.pitch, e.lastYaw, e.lastPitch, e.velocityX, e.velocityY, e.velocityZ, e.fallDistance); }
        void restore(Entity e) { e.setPositionAndAngles(x, y, z, yaw, pitch); e.prevX = prevX; e.prevY = prevY; e.prevZ = prevZ; e.lastYaw = lastYaw; e.lastPitch = lastPitch; e.velocityX = vx; e.velocityY = vy; e.velocityZ = vz; e.fallDistance = fall; }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
