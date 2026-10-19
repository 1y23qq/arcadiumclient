package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.featuretest.mixin.PresenceMinecraftAccess;
import io.github.arcadiumclient.featuretest.mixin.PresenceNetworkAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Session;
import net.minecraft.client.Screenshot;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.Window;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.World;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.GL11;

public final class PresenceQa {
    private static final UUID ALICE = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CHARLIE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String PREFIX = "io.github.arcadiumclient.runtime.client.presence.";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Long> CAPTURES = new LinkedHashMap<>();
    private static final Map<UUID, Integer> TAG_GLYPHS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> TAG_INLINE = new LinkedHashMap<>();
    private static final Map<UUID, String> TAG_TEXT = new LinkedHashMap<>();
    private static final List<Map<String, Object>> TAB_TEXT = new ArrayList<>();
    private static final List<Map<String, Integer>> TAB_ICONS = new ArrayList<>();
    private static final HttpClient CONTROL = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static Session originalSession;
    private static QaPlayer bob, charlie;
    private static UUID tagEntity;
    private static boolean inTab, started, ready, done;
    private static int ticks, worldTicks, phase, phaseTicks;
    private static String capture;
    private static String scene;

    private PresenceQa() {}
    public static boolean enabled() { return Boolean.getBoolean("arcadium.featureTest.presence"); }

    public static void tick() {
        if (!enabled() || done) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (++ticks == 1) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Presence QA requires the isolated offline fixture");
                URI endpoint = URI.create(System.getProperty("arcadium.presenceEndpoint", ""));
                require("http".equals(endpoint.getScheme()) && "127.0.0.1".equals(endpoint.getHost()), "Presence QA requires the local fixture API");
                originalSession = mc.getSession();
                session(mc, false);
                set(PREFIX + "ArcadiumPlayersConfig", "showNametag", true);
                set(PREFIX + "ArcadiumPlayersConfig", "showTab", true);
                set("io.github.arcadiumclient.runtime.client.features.NickHiderConfig", "enabled", false);
                set("io.github.arcadiumclient.runtime.client.features.GuiScaleConfig", "enabled", false);
                mc.options.pauseOnUnfocus = false;
                mc.options.guiScale = 2;
                mc.options.viewDistance = 4;
                RESULT.put("version", "0.2.8");
                RESULT.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
                RESULT.put("localAuthenticatedFixture", true);
            }
            phaseTicks++;
            require(phaseTicks < 1000, "Presence QA phase timed out: " + phase);
            if (!started && ticks >= 60) {
                started = true;
                startWorld(mc, "arcadium-presence-qa");
            }
            if (mc.world != null && mc.player != null) {
                if (++worldTicks == 80) setup(mc);
                if (ready) pin(mc);
            }
            if (capture != null || !CAPTURES.isEmpty()) return;
            switch (phase) {
                case 0 -> {
                    if (ready && worldTicks >= 200 && mc.world.getBlockState(new BlockPos(8, 3, 8)).getBlock() == Blocks.STONE && online(ALICE) && online(BOB) && !online(CHARLIE)) {
                        RESULT.put("authentication", control("/status", false));
                        take("connected");
                        advance();
                    }
                }
                case 1 -> { control("/bob/disconnect", true); advance(); }
                case 2 -> {
                    if (online(ALICE) && !online(BOB) && !online(CHARLIE)) { take("peer-disconnected"); advance(); }
                }
                case 3 -> { control("/bob/connect", true); advance(); }
                case 4 -> {
                    if (online(ALICE) && online(BOB)) { take("peer-reconnected"); advance(); }
                }
                case 5 -> {
                    session(mc, true);
                    presenceTick();
                    require(!online(ALICE) && !online(BOB) && !online(CHARLIE), "Account switch retained the old badges");
                    RESULT.put("accountSwitchImmediateClear", true);
                    advance();
                }
                case 6 -> {
                    if (!online(ALICE) && online(BOB) && online(CHARLIE)) { take("account-switched"); advance(); }
                }
                case 7 -> {
                    ready = false;
                    mc.setWorld(null);
                    presenceTick();
                    require(!online(ALICE) && !online(BOB) && !online(CHARLIE), "Leaving the world retained badges");
                    RESULT.put("worldExitImmediateClear", true);
                    take("world-cleared");
                    advance();
                }
                case 8 -> {
                    if (phaseTicks >= 30) {
                        session(mc, false);
                        worldTicks = 0;
                        startWorld(mc, "arcadium-presence-qa-next");
                        advance();
                    }
                }
                case 9 -> {
                    if (ready && worldTicks >= 200 && mc.world.getBlockState(new BlockPos(8, 3, 8)).getBlock() == Blocks.STONE && online(ALICE) && online(BOB) && !online(CHARLIE)) { take("world-reentered"); advance(); }
                }
                case 10 -> {
                    mc.getServer().executeTask(() -> mc.getServer().getWorld(0).setTimeOfDay(18000));
                    set("io.github.arcadiumclient.runtime.client.features.LightLevelConfig", "enabled", true);
                    set("io.github.arcadiumclient.runtime.client.features.LightLevelConfig", "radius", 8);
                    set("io.github.arcadiumclient.runtime.client.features.LightLevelConfig", "lightSource", 1);
                    fullbright(true);
                    advance();
                }
                case 11 -> {
                    mc.openScreen(null);
                    if (phaseTicks == 30) {
                        RESULT.put("nightReadiness", Map.of("clientTime", mc.world.getTimeOfDay(), "serverTime", mc.getServer().getWorld(0).getTimeOfDay(), "cachedAmbientDarkness", mc.world.getAmbientDarkness(), "calculatedAmbientDarkness", mc.world.calculateAmbientDarkness(1f)));
                        write(mc);
                    }
                    if (phaseTicks >= 30 && mc.world.calculateAmbientDarkness(1f) == 11) { take("night-fullbright"); advance(); }
                }
                case 12 -> { fullbright(false); advance(); }
                case 13 -> {
                    if (phaseTicks >= 30) { take("night-unbrightened"); advance(); }
                }
                case 14 -> {
                    mc.getServer().executeTask(() -> mc.getServer().getWorld(0).setTimeOfDay(6000));
                    advance();
                }
                case 15 -> {
                    if (phaseTicks >= 30 && mc.world.calculateAmbientDarkness(1f) == 0) { take("day-unbrightened"); advance(); }
                }
                case 16 -> complete(mc);
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void session(Minecraft mc, boolean other) {
        ((PresenceMinecraftAccess) mc).arcadium$session(new Session(other ? "PresenceCharlie" : "PresenceAlice",
            (other ? CHARLIE : ALICE).toString().replace("-", ""),
            other ? "arcadium-presence-qa-charlie-token-001" : "arcadium-presence-qa-alice-token-00001", "mojang"));
    }

    private static void startWorld(Minecraft mc, String name) {
        mc.startGame(name, name, new WorldSettings(71, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
    }

    private static void setup(Minecraft mc) throws Exception {
        mc.getServer().executeTask(() -> {
            var world = mc.getServer().getWorld(0);
            world.getGameRules().set("doMobSpawning", "false");
            world.getGameRules().set("doDaylightCycle", "false");
            world.setTimeOfDay(6000);
            for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++) {
                world.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.defaultState());
                for (int y = 4; y <= 10; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultState());
            }
            mc.getServer().getPlayerManager().getAll().get(0).teleport(8.5, 4, 8.5);
        });
        bob = new QaPlayer(mc.world, new GameProfile(BOB, "PresenceBob"));
        charlie = new QaPlayer(mc.world, new GameProfile(CHARLIE, "PresenceCharlie"));
        mc.world.forceEntity(9998, bob);
        mc.world.forceEntity(9999, charlie);
        var players = ((PresenceNetworkAccess) mc.getNetworkHandler()).arcadium$onlinePlayers();
        players.clear();
        players.put(ALICE, new FixturePlayerInfo(new GameProfile(ALICE, "PresenceAlice")));
        players.put(BOB, new FixturePlayerInfo(new GameProfile(BOB, "PresenceBob")));
        players.put(CHARLIE, new FixturePlayerInfo(new GameProfile(CHARLIE, "PresenceCharlie")));
        mc.gui.getPlayerTabOverlay().setHeader(new LiteralText("Presence fixtures"));
        mc.openScreen(null);
        ready = true;
    }

    private static void pin(Minecraft mc) {
        mc.player.abilities.flying = true;
        mc.player.setPositionAndAngles(8.5, 4, 8.5, 0, 0);
        mc.player.lastX = mc.player.x;
        mc.player.lastY = mc.player.y;
        mc.player.lastZ = mc.player.z;
        mc.player.pitch = mc.player.lastPitch = phase >= 10 ? 48 : 0;
        mc.player.yaw = mc.player.lastYaw = 0;
        pin(bob, 6.5, 14.5);
        pin(charlie, 10.5, 14.5);
    }

    private static void pin(QaPlayer player, double x, double z) {
        player.setPositionAndAngles(x, 4, z, 180, 0);
        player.lastX = player.x;
        player.lastY = player.y;
        player.lastZ = player.z;
    }

    public static void beginFrame() {
        if (!enabled() || done) return;
        TAG_GLYPHS.clear(); TAG_INLINE.clear(); TAG_TEXT.clear(); TAB_TEXT.clear(); TAB_ICONS.clear();
        tagEntity = null;
    }

    public static void beginName(Entity entity) {
        if (!enabled() || done || !ready) return;
        tagEntity = entity.getUuid();
    }

    public static void endName() { tagEntity = null; }

    public static void text(String text, float x, float y) {
        if (!enabled() || done || text == null) return;
        if (inTab && text.contains("Presence")) TAB_TEXT.add(Map.of("text", text, "x", x, "y", y));
        if (tagEntity != null) {
            TAG_TEXT.put(tagEntity, text);
            if (text.indexOf('\uE000') >= 0) TAG_GLYPHS.merge(tagEntity, 1, Integer::sum);
        }
    }

    public static void tabIcon(int x, int y) { if (enabled() && !done && inTab) TAB_ICONS.add(Map.of("x", x, "y", y)); }
    public static void inlineIcon() { if (enabled() && !done && tagEntity != null) TAG_INLINE.merge(tagEntity, 1, Integer::sum); }

    public static void drawTab() {
        if (!enabled() || done || !ready || Minecraft.getInstance().world == null) return;
        inTab = true;
        try {
            var mc = Minecraft.getInstance();
            mc.gui.getPlayerTabOverlay().render(new Window(mc).getWidth(), mc.world.getScoreboard(), null);
        } finally { inTab = false; }
    }

    public static void afterFrame() {
        if (!enabled() || done) return;
        try {
            var mc = Minecraft.getInstance();
            if (capture != null) {
                if (scene.startsWith("night-") || scene.startsWith("day-")) verifyPhysicalLight(mc, scene);
                else if (!scene.equals("world-cleared")) verifyFrame(scene);
                Screenshot.take(mc.gameDir, "presence-qa-" + capture, mc.width, mc.height, mc.getRenderTarget());
                CAPTURES.put(capture, System.currentTimeMillis());
                capture = null;
            }
            for (String name : new ArrayList<>(CAPTURES.keySet())) {
                Path input = mc.gameDir.toPath().resolve("screenshots/presence-qa-" + name);
                if (!Files.isRegularFile(input)) continue;
                byte[] bytes = Files.readAllBytes(input);
                int length = bytes.length;
                if (length >= 12 && bytes[length - 8] == 'I' && bytes[length - 7] == 'E' && bytes[length - 6] == 'N' && bytes[length - 5] == 'D') {
                    Files.createDirectories(output(mc));
                    Files.write(output(mc).resolve(name), bytes);
                    CAPTURES.remove(name);
                } else require(System.currentTimeMillis() - CAPTURES.get(name) < 15000, "Presence PNG write timed out");
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void verifyFrame(String name) throws Exception {
        boolean switched = name.equals("account-switched");
        boolean disconnected = name.equals("peer-disconnected");
        require(TAG_TEXT.containsKey(BOB) && TAG_TEXT.containsKey(CHARLIE), "Native nametags were not rendered");
        require((TAG_GLYPHS.getOrDefault(BOB, 0) > 0) == !disconnected, "Bob nametag badge did not match presence");
        require((TAG_GLYPHS.getOrDefault(CHARLIE, 0) > 0) == switched, "Charlie nametag badge did not match presence");
        require((TAG_INLINE.getOrDefault(BOB, 0) > 0) == !disconnected, "Bob logo did not reach the native glyph renderer");
        require((TAG_INLINE.getOrDefault(CHARLIE, 0) > 0) == switched, "Charlie logo did not reach the native glyph renderer");
        require(TAB_TEXT.stream().anyMatch(row -> row.get("text").toString().contains("PresenceBob")), "Native Tab player rows were not rendered");
        require(TAB_ICONS.size() == (disconnected ? 1 : 2), "Native Tab badge count did not match the authenticated peers");
        RESULT.put(name, Map.of("aliceOnline", online(ALICE), "bobOnline", online(BOB), "charlieOnline", online(CHARLIE),
            "tabIcons", List.copyOf(TAB_ICONS), "tabText", List.copyOf(TAB_TEXT),
            "bobNametagGlyphs", TAG_GLYPHS.getOrDefault(BOB, 0), "bobLogoDraws", TAG_INLINE.getOrDefault(BOB, 0),
            "charlieNametagGlyphs", TAG_GLYPHS.getOrDefault(CHARLIE, 0), "charlieLogoDraws", TAG_INLINE.getOrDefault(CHARLIE, 0)));
    }

    private static void take(String name) { scene = name; capture = name + ".png"; }
    private static void fullbright(boolean enabled) throws Exception {
        Object config = Class.forName("org.polyfrost.fullbright.FullBright").getField("config").get(null);
        Field field = config.getClass().getDeclaredField("enable"); field.setAccessible(true); field.setBoolean(config, enabled);
        Class.forName("org.polyfrost.fullbright.legacy.LegacyLight").getMethod("reloadRenderers").invoke(null);
    }
    private static void verifyPhysicalLight(Minecraft mc, String name) throws Exception {
        var section = mc.world.getChunk(new BlockPos(8, 4, 8)).getSections()[0];
        int sky = section.getSkyLight(8, 4, 8), block = section.getBlockLight(8, 4, 8), darkness = mc.world.calculateAmbientDarkness(1f);
        boolean night = name.startsWith("night-");
        int expected = night ? 4 : 15;
        require(sky == 15 && block == 0 && darkness == (night ? 11 : 0), "Physical light fixture did not retain the expected sky/block/darkness");
        Class<?> type = Class.forName("io.github.arcadiumclient.runtime.client.features.LightLevelOverlay");
        Method scan = type.getDeclaredMethod("scan", World.class, BlockPos.class, int.class, boolean.class); scan.setAccessible(true);
        List<?> cells = (List<?>) scan.invoke(null, mc.world, new BlockPos(8, 4, 8), 8, true);
        Object selected = null;
        for (Object cell : cells) {
            if ((int) cell.getClass().getMethod("x").invoke(cell) == 8 && (int) cell.getClass().getMethod("z").invoke(cell) == 8) selected = cell;
        }
        int measured = selected == null ? -1 : (int) selected.getClass().getMethod("light").invoke(selected);
        RESULT.put(name + "Measured", Map.of("storedSky", sky, "storedBlock", block, "cachedAmbientDarkness", mc.world.getAmbientDarkness(), "calculatedAmbientDarkness", darkness, "combined", measured));
        require(measured == expected, "Physical combined light did not match the vanilla darkness");
        List<?> blockCells = (List<?>) scan.invoke(null, mc.world, new BlockPos(8, 4, 8), 8, false);
        boolean foundBlock = false;
        for (Object cell : blockCells) {
            if ((int) cell.getClass().getMethod("x").invoke(cell) == 8 && (int) cell.getClass().getMethod("z").invoke(cell) == 8) {
                foundBlock = true;
                require((int) cell.getClass().getMethod("light").invoke(cell) == 0, "Default block-only light included sky");
            }
        }
        require(foundBlock, "Default block-only scan missed the fixture floor");
        RESULT.put(name, Map.of("storedSky", sky, "storedBlock", block, "ambientDarkness", darkness, "combined", expected, "defaultBlock", 0));
    }
    private static void advance() { phase++; phaseTicks = 0; }
    private static boolean online(UUID player) throws Exception { return (boolean) Class.forName(PREFIX + "ArcadiumPresence").getMethod("isOnline", UUID.class).invoke(null, player); }
    private static void presenceTick() throws Exception {
        Class<?> type = Class.forName(PREFIX + "ArcadiumPresence");
        Method tick = type.getDeclaredMethod("tick"); tick.setAccessible(true); tick.invoke(type.getField("INSTANCE").get(null));
    }
    private static String control(String route, boolean post) throws Exception {
        String endpoint = System.getProperty("arcadium.presenceFixtureControl");
        URI uri = URI.create(endpoint + route);
        require("127.0.0.1".equals(uri.getHost()), "Presence control must remain local");
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3));
        if (post) builder.POST(HttpRequest.BodyPublishers.noBody());
        var response = CONTROL.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() == 200, "Presence fixture control failed");
        return response.body();
    }
    private static void set(String type, String property, Object value) throws Exception { Field field = Class.forName(type).getDeclaredField(property); field.setAccessible(true); field.set(null, value); }
    private static Path output(Minecraft mc) { return mc.gameDir.toPath().resolve("presence-qa"); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void write(Minecraft mc) throws Exception { Files.createDirectories(output(mc)); Files.writeString(output(mc).resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); }
    private static void complete(Minecraft mc) throws Exception {
        done = true;
        RESULT.put("passed", true);
        RESULT.put("finalFixture", control("/status", false));
        write(mc);
        ((PresenceMinecraftAccess) mc).arcadium$session(originalSession);
        mc.setWorld(null);
        System.out.println("Arcadium presence game QA: completed");
        if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
    }
    private static void fail(Throwable error) {
        if (done) return;
        done = true;
        RESULT.put("passed", false); RESULT.put("phase", phase); RESULT.put("failure", error.toString());
        RESULT.put("frameTags", TAG_TEXT); RESULT.put("frameTabIcons", TAB_ICONS); RESULT.put("frameTabText", TAB_TEXT);
        error.printStackTrace();
        try { var mc = Minecraft.getInstance(); if (mc.world != null) RESULT.put("failureWorld", Map.of("clientTime", mc.world.getTimeOfDay(), "serverTime", mc.getServer().getWorld(0).getTimeOfDay(), "ambientDarkness", mc.world.getAmbientDarkness())); write(mc); Screenshot.take(mc.gameDir, "presence-qa-failure.png", mc.width, mc.height, mc.getRenderTarget()); if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop(); }
        catch (Throwable ignored) {}
    }

    private static final class FixturePlayerInfo extends PlayerInfo {
        FixturePlayerInfo(GameProfile profile) { super(profile); }
        @Override public Identifier getSkinTexture() { return new Identifier("textures/entity/steve.png"); }
        @Override public Identifier getCapeTexture() { return null; }
        @Override public String getModelType() { return "default"; }
        @Override public WorldSettings.GameMode getGameMode() { return WorldSettings.GameMode.SURVIVAL; }
    }
}
