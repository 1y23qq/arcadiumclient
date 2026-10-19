package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.featuretest.mixin.ListWidgetProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.MultiplayerProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.gui.screen.inventory.menu.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.options.ServerList;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.render.Window;
import net.minecraft.client.resource.pack.ResourcePacks;
import net.minecraft.client.resource.pack.DirectoryResourcePack;
import net.minecraft.client.resource.pack.ZippedResourcePack;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.LightType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class FeatureQa028 {
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String HUDS = "org.polyfrost.evergreenhud.client.hud.";
    private static final String[] STAGES = {"hud-default", "hud-custom", "hud-high-tps", "item-reordered",
        "light-block", "light-block-unbrightened", "light-sky", "light-covered", "light-yaw90", "light-yaw180", "light-yaw270", "tab-transparent", "tab-opaque",
        "inventory-transparent", "inventory-opaque", "resource-packs", "server-list-drag"};
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<SavedField, Object> ORIGINALS = new LinkedHashMap<>();
    private static final Map<String, Long> CAPTURES = new LinkedHashMap<>();
    private static final List<Object> huds = new ArrayList<>();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer GLYPH_MATRIX = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final Set<Integer> READABLE_YAWS = new LinkedHashSet<>();
    private static int ticks, worldTicks, stage = -1, stageTicks;
    private static boolean started, done, inLight;
    private static int lightDraws, depthChecks, stateChecks;
    private static float[] lightMatrix;
    private static Object ping, tps, biome, potion, counter, tab;
    private static String capture;
    private static List<ResourcePacks.Entry> originalPacks;
    private static List<?> brightenedCells;
    private record SavedField(Object target, Field field) {}

    private FeatureQa028() {}
    public static boolean enabled() { return Boolean.getBoolean("arcadium.featureTest.028"); }

    public static void tick() {
        if (!enabled() || done) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (++ticks == 1) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "028 QA requires the isolated offline ArcadiumTest session");
                RESULT.put("version", "0.2.8");
                RESULT.put("session", "ArcadiumTest");
                RESULT.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            }
            if (!started && ticks >= 60) {
                started = true;
                mc.options.pauseOnUnfocus = false;
                mc.options.viewDistance = 4;
                mc.options.guiScale = 2;
                mc.startGame("arcadium-feature-qa-028", "Arcadium Feature QA 028",
                    new WorldSettings(71, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
            }
            if (mc.world == null || mc.player == null) return;
            if (++worldTicks == 60) setupWorld(mc);
            if (worldTicks < 140) return;
            if (stage < 0 || stageTicks >= 55) {
                if (stage + 1 == STAGES.length) {
                    if (CAPTURES.isEmpty()) complete(mc);
                    return;
                }
                stage++;
                stageTicks = 0;
                prepare(mc);
            }
            stageTicks++;
            mc.player.abilities.flying = true;
            mc.player.pitch = mc.player.lastPitch = STAGES[stage].equals("light-covered") ? 35 : 48;
            mc.player.yaw = mc.player.lastYaw = targetYaw();
            if (STAGES[stage].startsWith("hud-") || STAGES[stage].equals("item-reordered")) {
                setField(ping, "ping", STAGES[stage].equals("hud-custom") ? 75L : 0L);
                call(ping, "update");
                call(tps, "record", new Class<?>[]{float.class}, STAGES[stage].equals("hud-high-tps") ? 21f : 20f);
                call(counter, "update");
                call(potion, "update");
            }
            if (stageTicks == 34) mc.gui.getChat().clear();
            if (stageTicks == 35) {
                verify(mc);
                capture = STAGES[stage] + ".png";
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void setupWorld(Minecraft mc) {
        mc.getServer().executeTask(() -> {
            var world = mc.getServer().getWorld(0);
            world.getGameRules().set("doMobSpawning", "false");
            world.getGameRules().set("doDaylightCycle", "false");
            world.setTimeOfDay(6000);
            for (int x = 0; x <= 32; x++) for (int z = 0; z <= 32; z++) {
                world.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.defaultState());
                for (int y = 4; y <= 9; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultState());
            }
            world.setBlockState(new BlockPos(5, 4, 12), Blocks.TORCH.defaultState());
            for (int x = 12; x <= 20; x++) for (int z = 12; z <= 20; z++) {
                world.setBlockState(new BlockPos(x, 8, z), Blocks.STONE.defaultState());
                if (x == 12 || x == 20 || z == 12 || z == 20) {
                    for (int y = 4; y < 8; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.STONE.defaultState());
                }
            }
            mc.getServer().getPlayerManager().getAll().get(0).teleport(8.5, 7, 8.5);
        });
    }

    private static void prepare(Minecraft mc) throws Exception {
        String name = STAGES[stage];
        System.out.println("Arcadium feature QA 028: " + name);
        mc.openScreen(null);
        setConfig("LightLevelConfig", "enabled", name.startsWith("light-"));
        setConfig("LightLevelConfig", "radius", 16);
        setConfig("LightLevelConfig", "lightSource", name.equals("light-sky") ? 1 : 0);
        if (stage == 0) setupHuds(mc);
        for (Object hud : huds) call(hud, "setHidden", new Class<?>[]{boolean.class}, !name.startsWith("hud-") && !name.equals("item-reordered"));
        if (name.equals("hud-default")) testHuds(mc);
        if (name.equals("hud-custom")) setField(ping, "ping75To144", 0xFF58AAFF);
        if (name.equals("item-reordered")) call(counter, "setItem", new Class<?>[]{String[].class}, (Object) new String[]{"minecraft:golden_apple", "minecraft:diamond"});
        if (name.startsWith("tab-")) {
            setConfig("TabBackgroundConfig", "enabled", true);
            setConfig("TabBackgroundConfig", "opacity", name.endsWith("transparent") ? 0f : 100f);
            mc.gui.getPlayerTabOverlay().setHeader(new LiteralText("Arcadium QA 028"));
            mc.gui.getPlayerTabOverlay().setFooter(new LiteralText("Background " + (name.endsWith("transparent") ? "0%" : "100%")));
        }
        if (name.startsWith("inventory-")) {
            setStatic("dev.microcontrollers.betterscreens.config.BetterScreensConfig", "containerBackgroundOpacity", name.endsWith("transparent") ? 0f : 100f);
            mc.openScreen(new SurvivalInventoryScreen(mc.player));
        }
        if (name.equals("light-covered")) mc.player.setPositionAndAngles(16.5, 5.8, 16.5, 0, 35);
        else if (name.equals("light-yaw90")) mc.player.setPositionAndAngles(9.5, 7, 12.5, 90, 48);
        else if (name.equals("light-yaw180")) mc.player.setPositionAndAngles(5.5, 7, 16.5, 180, 48);
        else if (name.equals("light-yaw270")) mc.player.setPositionAndAngles(1.5, 7, 12.5, 270, 48);
        else mc.player.setPositionAndAngles(8.5, 7, 8.5, 0, 48);
        if (name.startsWith("light-")) {
            Object fullbright = Class.forName("org.polyfrost.fullbright.FullBright").getField("config").get(null);
            setField(fullbright, "enable", !name.equals("light-block-unbrightened"));
            Class.forName("org.polyfrost.fullbright.legacy.LegacyLight").getMethod("reloadRenderers").invoke(null);
            if (name.equals("light-block")) {
                BlockPos torch = new BlockPos(5, 4, 12);
                RESULT.put("light-fixture-update", mc.world.updateLight(LightType.BLOCK, torch));
                mc.getServer().executeTask(() -> mc.getServer().getWorld(0).updateLight(LightType.BLOCK, torch));
            }
        }
        if (name.equals("resource-packs")) preparePacks(mc);
        if (name.equals("server-list-drag")) prepareServerDrag(mc);
    }

    @SuppressWarnings("unchecked")
    private static void setupHuds(Minecraft mc) throws Exception {
        Object manager = singleton("org.polyfrost.oneconfig.api.hud.v1.HudManager");
        List<Object> active = (List<Object>) call(manager, "getActiveInstances");
        for (Object hud : active) call(hud, "setHidden", new Class<?>[]{boolean.class}, true);
        String[] names = {HUDS + "PingHud", HUDS + "TpsHud", HUDS + "BiomeHud", HUDS + "potion.PotionEffectsHud", HUDS + "ItemCounterHud"};
        Collection<?> providers = (Collection<?>) call(manager, "providers");
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            Object hud = active.stream().filter(item -> item.getClass().getName().equals(name)).findFirst().orElse(null);
            if (hud == null) {
                Object provider = providers.stream().filter(item -> item.getClass().getName().equals(name)).findFirst().orElseThrow();
                hud = call(provider, "make", new Class<?>[]{Class.forName("org.polyfrost.oneconfig.api.config.v1.Tree")}, new Object[]{null});
                active.add(hud);
                call(hud, "setup");
            }
            huds.add(hud);
            call(hud, "setShowShadow", new Class<?>[]{boolean.class}, true);
            call(hud, "setShowBackground", new Class<?>[]{boolean.class}, true);
            call(hud, "setAbsolutePosition", new Class<?>[]{float.class, float.class}, 16f, 24f + i * 36f);
            call(hud, "setHidden", new Class<?>[]{boolean.class}, false);
        }
        ping = huds.get(0); tps = huds.get(1); biome = huds.get(2); potion = huds.get(3); counter = huds.get(4);
        setField(ping, "colorByValue", true);
        setField(tps, "colorByValue", true);
        setField(counter, "showName", true);
        mc.player.inventory.items[0] = new ItemStack(Items.DIAMOND, 5);
        mc.player.inventory.items[1] = new ItemStack(Items.DIAMOND, 7);
        mc.player.inventory.items[2] = new ItemStack(Items.GOLDEN_APPLE, 64);
        mc.player.inventory.items[3] = new ItemStack(Items.GOLDEN_APPLE, 3);
        mc.player.addStatusEffect(new StatusEffectInstance(StatusEffect.SPEED.getId(), 1200));
        mc.player.addStatusEffect(new StatusEffectInstance(StatusEffect.REGENERATION.getId(), 1200));
        var chunk = mc.world.getChunk(new BlockPos(mc.player));
        byte[] biomes = chunk.getBiomes().clone();
        Arrays.fill(biomes, (byte) Biome.FOREST.id);
        chunk.setBiomes(biomes);
        call(biome, "updateWithText", new Class<?>[]{Object.class}, Biome.FOREST.name);
        Object vanilla = singleton("org.polyfrost.vanillahud.hud.Huds");
        tab = call(vanilla, "getTabList");
    }

    private static void testHuds(Minecraft mc) throws Exception {
        int[] values = {0, 74, 75, 144, 145, 199, 200, 299, 300, 399, 400};
        List<Map<String, Object>> pingResults = new ArrayList<>();
        for (int value : values) {
            setField(ping, "ping", (long) value);
            String text = (String) call(ping, "getText");
            require(text.contains(value + "ms"), "Ping value lost or formatted incorrectly: " + value);
            int color = argb(call(ping, "valueColor"));
            int expected = (int) call(tab, "pingColor", new Class<?>[]{int.class}, value);
            require(color == expected, "Ping HUD differs from Tab palette at " + value);
            if (value == 0) require((boolean) call(ping, "shouldShow"), "Zero ping was hidden");
            pingResults.add(Map.of("milliseconds", value, "color", String.format("%08X", color)));
        }
        Object tree = call(ping, "getTree");
        List<String> pickerIds = List.of("ping0To74", "ping75To144", "ping145To199", "ping200To299", "ping300To399", "ping400AndAbove");
        int[] representativePing = {0, 75, 145, 200, 300, 400};
        for (int i = 0; i < pickerIds.size(); i++) {
            String id = pickerIds.get(i);
            Object picker = call(tree, "getProp", new Class<?>[]{String.class}, id);
            require(picker != null, "Missing Ping color picker " + id);
            Object visualizer = call(picker, "getMetadata", new Class<?>[]{String.class}, "visualizer");
            require(visualizer instanceof Class<?> && ((Class<?>) visualizer).getName().endsWith("$ColorVisualizer"), "Ping field was not exposed as a color picker: " + id);
            int original = (int) field(ping.getClass(), id).get(ping);
            int custom = 0xFF112233 + i * 0x000D0905;
            setField(ping, id, custom);
            setField(ping, "ping", (long) representativePing[i]);
            call(ping, "getText");
            require(argb(call(ping, "valueColor")) == custom, "Ping picker did not affect its own range: " + id);
            setField(ping, id, original);
        }
        RESULT.put("ping-ranges", pingResults);
        RESULT.put("ping-custom-pickers", pickerIds.size());
        List<Map<String, Object>> tpsResults = new ArrayList<>();
        float[] samples = {15f, 15.05f, 19.449f, 19.45f, 20.5f, 20.55f, 21f};
        String[] rendered = {"15.0", "15.1", "19.4", "19.5", "20.5", "20.6", "21.0"};
        int[] colors = {0xFFFF5555, 0xFFFFFF55, 0xFFFFFF55, 0xFF55FF55, 0xFF55FF55, 0xFFFF5555, 0xFFFF5555};
        for (int i = 0; i < samples.length; i++) {
            call(tps, "resetStats");
            for (int repeat = 0; repeat < 5; repeat++) call(tps, "record", new Class<?>[]{float.class}, samples[i]);
            String text = (String) call(tps, "getCurrentText");
            require(text.equals(rendered[i]), "TPS decimal mismatch: " + text);
            int color = argb(call(tps, "valueColor"));
            require(color == colors[i], "TPS range mismatch for " + samples[i]);
            require(Math.abs((float) call(tps, "getValue") - samples[i]) < 0.001f, "TPS sample was capped");
            tpsResults.add(Map.of("sample", samples[i], "text", text, "color", String.format("%08X", color)));
        }
        RESULT.put("tps-ranges", tpsResults);
        Object tpsTree = call(tps, "getTree");
        for (String id : List.of("tpsHealthy", "tpsSlow", "tpsOutsideRange")) {
            Object picker = call(tpsTree, "getProp", new Class<?>[]{String.class}, id);
            require(picker != null && call(picker, "getMetadata", new Class<?>[]{String.class}, "visualizer") instanceof Class<?>, "TPS range color picker is missing");
        }
        int nativeBiome = mc.world.getBiome(new BlockPos(mc.player)).biomeColor;
        int expectedBiome = (int) Class.forName(FEATURES + "HudValuePalette").getMethod("readableBiomeColor", int.class).invoke(null, nativeBiome);
        require(argb(call(biome, "valueColor")) == expectedBiome, "Biome HUD did not use native biome color");
        RESULT.put("biome-color", Map.of("native", String.format("%06X", nativeBiome), "display", String.format("%08X", expectedBiome)));
        for (StatusEffect effect : List.of(StatusEffect.SPEED, StatusEffect.REGENERATION)) {
            Object row = call(potion, "row", new Class<?>[]{StatusEffectInstance.class, boolean.class}, new StatusEffectInstance(effect.getId(), 1200), false);
            int expected = 0xFF000000 | effect.getPotionColor();
            require(argb(call(row, "getNameColor")) == expected && argb(call(row, "getDurationColor")) == expected, "Potion row failed vanilla type colors");
        }
        RESULT.put("potion-colors", true);
        call(counter, "setItem", new Class<?>[]{String[].class}, (Object) new String[]{"minecraft:diamond", "minecraft:golden_apple"});
        call(counter, "update");
        Object itemsProperty = call(call(counter, "getTree"), "getProp", new Class<?>[]{String.class}, "item");
        require(Integer.valueOf(0).equals(call(itemsProperty, "getMetadata", new Class<?>[]{String.class}, "maxEntries")), "Item selector still limits selection to one item");
        require(Boolean.TRUE.equals(call(itemsProperty, "getMetadata", new Class<?>[]{String.class}, "reorderable")), "Item selector does not expose row ordering");
        RESULT.put("item-selector-metadata", Map.of("maxEntries", 0, "reorderable", true));
        requireCounter(false);
        call(counter, "setItem", new Class<?>[]{String[].class}, (Object) new String[]{"minecraft:diamond"});
        call(counter, "update");
        require(!(boolean) state(counter, "arcadium$multiple"), "Counter did not return to single-item mode");
        call(counter, "setItem", new Class<?>[]{String[].class}, (Object) new String[]{"minecraft:diamond", "minecraft:golden_apple"});
        call(counter, "update");
        require(rowTexts(counter).size() == 2, "Counter did not return to multiple-item mode");
        Object title = field(Class.forName("org.polyfrost.damagetint.client.DamageTintConfig"), "title").get(singleton("org.polyfrost.damagetint.client.DamageTintConfig"));
        require("Hit Color".equals(title), "DamageTint display title was not renamed");
        RESULT.put("item-transitions", true);
        RESULT.put("hit-color-title", title);
        require((boolean) call(singleton(FEATURES + "OnboardingFeatures"), "getSkinLayersAvailable"), "Bundled 3D Skin Layers is unavailable at runtime");
        RESULT.put("skin-layers-existing", true);
    }

    private static void verify(Minecraft mc) throws Exception {
        String name = STAGES[stage];
        if (name.equals("hud-custom")) require(argb(call(ping, "valueColor")) == 0xFF58AAFF, "Custom Ping range color did not apply");
        if (name.equals("hud-high-tps")) require("21.0".equals(call(tps, "getCurrentText")) && argb(call(tps, "valueColor")) == 0xFFFF5555, "High TPS was capped or colored incorrectly");
        if (name.equals("item-reordered")) requireCounter(true);
        if (name.startsWith("light-")) {
            Class<?> overlay = Class.forName(FEATURES + "LightLevelOverlay");
            List<?> cells = (List<?>) field(overlay, "cells").get(null);
            require(!cells.isEmpty(), "Light Level did not cache any ground cells");
            List<Integer> levels = new ArrayList<>();
            for (Object cell : cells) levels.add((int) call(cell, "light"));
            Map<String, Object> lightChecks = new LinkedHashMap<>();
            lightChecks.put("cells", cells.size());
            lightChecks.put("minimum", levels.stream().min(Integer::compareTo).orElseThrow());
            lightChecks.put("maximum", levels.stream().max(Integer::compareTo).orElseThrow());
            lightChecks.put("depthChecks", depthChecks);
            lightChecks.put("restoredStateChecks", stateChecks);
            lightChecks.put("readableYaws", new ArrayList<>(READABLE_YAWS));
            if (name.equals("light-block")) {
                require((boolean) field(Class.forName("org.polyfrost.fullbright.config.FullBrightConfig"), "enable").get(Class.forName("org.polyfrost.fullbright.FullBright").getField("config").get(null)), "FullBright was not enabled during the light-on test");
                brightenedCells = List.copyOf(cells);
                BlockPos torch = new BlockPos(5, 4, 12);
                lightChecks.put("clientTorchPresent", mc.world.getBlockState(torch).getBlock() == Blocks.TORCH);
                lightChecks.put("clientStoredTorchLight", mc.world.getChunk(torch).getSections()[0].getBlockLight(5, 4, 12));
                lightChecks.put("serverStoredTorchLight", mc.getServer().getWorld(0).getChunk(torch).getSections()[0].getBlockLight(5, 4, 12));
            }
            RESULT.put(name, lightChecks);
            require(READABLE_YAWS.contains(targetYaw()), "Visible digits 10..14 were not verified at yaw " + targetYaw());
            if (name.equals("light-block-unbrightened")) {
                require(brightenedCells != null && brightenedCells.equals(cells), "FullBright toggle changed measured physical light levels");
                lightChecks.put("sameLevelsWithFullBrightOff", true);
            }
            require(levels.stream().allMatch(value -> value >= 0 && value <= 15), "Light Level has values outside 0..15");
            if (name.equals("light-block")) {
                require(levels.stream().anyMatch(value -> value > 7) && levels.stream().anyMatch(value -> value <= 7), "Torch and dark ground were not distinguished");
                Method scan = method(overlay, "scan", World.class, BlockPos.class, int.class, boolean.class);
                require(((List<?>) scan.invoke(null, mc.world, new BlockPos(1024, 4, 1024), 16, false)).isEmpty(), "Light Level scanned unloaded chunks");
            }
            if (name.equals("light-sky")) require(levels.contains(15), "Sky light was not included");
            require(depthChecks > 0 && stateChecks > 0, "Light Level draw-state probe did not run");
        } else if (name.startsWith("tab-")) {
            int alpha = name.endsWith("transparent") ? 0 : 255;
            for (String getter : List.of("getHeaderBgArgb", "getBodyBgArgb", "getFooterBgArgb", "getTabWidgetArgb")) require(((int) call(tab, getter) >>> 24) == alpha, "Tab alpha mismatch for " + getter);
            RESULT.put(name, Map.of("alpha", alpha));
        } else if (name.startsWith("inventory-")) {
            float expected = name.endsWith("transparent") ? 0f : 100f;
            float actual = (float) Class.forName(FEATURES + "ContainerBackgroundOpacity").getMethod("percent").invoke(null);
            require(actual == expected, "BetterScreens container opacity was not honored");
            Class<?> blur = Class.forName("eu.midnightdust.blur.BlurRenderer");
            setStatic(blur.getName(), "backgroundProgress", 1f);
            for (boolean bottom : List.of(false, true)) {
                int color = (int) method(blur, "color", boolean.class).invoke(null, bottom);
                int alpha = expected == 0f ? 0 : bottom ? 255 : 239;
                require((color >>> 24) == alpha, "Blur container opacity mismatch: expected " + alpha + ", got " + (color >>> 24));
            }
            RESULT.put(name, Map.of("percent", actual));
        } else if (name.equals("resource-packs")) {
            require(mc.screen instanceof ResourcePacksScreen, "Native resource pack screen did not open");
            require(((ResourcePacksScreen) mc.screen).getAppliedPacks().size() >= 3, "Folder and ZIP did not both appear beside default in native screen");
        } else if (name.equals("server-list-drag")) {
            require(mc.screen instanceof MultiplayerScreen, "Dragging joined a server or closed the server list");
            ServerList persisted = new ServerList(mc);
            require(persisted.size() == 3 && persisted.get(0).name.equals("QA B") && persisted.get(1).name.equals("QA C") && persisted.get(2).name.equals("QA A"), "Dragged server order was not saved");
            RESULT.put(name, Map.of("persistedOrder", List.of(persisted.get(0).name, persisted.get(1).name, persisted.get(2).name), "screenStayedOpen", true));
        } else RESULT.put(name, true);
    }

    private static void prepareServerDrag(Minecraft mc) {
        MultiplayerScreen screen = new MultiplayerScreen(null);
        mc.openScreen(screen);
        ServerList servers = screen.getServerList();
        while (servers.size() > 0) servers.remove(0);
        for (String name : List.of("QA A", "QA B", "QA C")) {
            ServerListEntry entry = new ServerListEntry(name, "127.0.0.1:9", false);
            entry.loaded = true;
            entry.motd = "Local test fixture";
            entry.status = "0/0";
            servers.add(entry);
        }
        servers.save();
        var list = ((MultiplayerProbeAccess) screen).qa028$list();
        list.setServers(servers);
        var bounds = (ListWidgetProbeAccess) list;
        int x = bounds.qa028$left() + (bounds.qa028$width() - list.getRowWidth()) / 2 + 82;
        int firstY = bounds.qa028$top() + 4 + bounds.qa028$headerHeight() - list.getScrollAmount() + list.getEntryHeight() / 2;
        int lastY = firstY + 2 * list.getEntryHeight();
        var input = (ScreenProbeAccess) screen;
        input.arcadium$click(x, firstY, 0);
        input.arcadium$drag(x, lastY, 0, 120);
        input.arcadium$release(x, lastY, 0);
        require(servers.get(2).name.equals("QA A") && list.getCurrentServerIndex() == 2, "Native mouse drag did not move the selected server to its new index");
        List<String> before = List.of(servers.get(0).name, servers.get(1).name, servers.get(2).name);
        input.arcadium$click(x, firstY, 0);
        input.arcadium$release(0, lastY, 0);
        require(before.equals(List.of(servers.get(0).name, servers.get(1).name, servers.get(2).name)), "Dropping outside the server list changed its order");
    }

    private static void preparePacks(Minecraft mc) throws Exception {
        ResourcePacks repository = mc.getResourcePacks();
        originalPacks = new ArrayList<>(repository.getApplied());
        Path directory = repository.getDirectory().toPath();
        Files.createDirectories(directory.resolve("qa-028-folder"));
        String metadata = "{\"pack\":{\"pack_format\":1,\"description\":\"Arcadium QA folder pack\"}}";
        Files.writeString(directory.resolve("qa-028-folder/pack.mcmeta"), metadata);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(directory.resolve("qa-028-zip.zip")))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(metadata.replace("folder", "ZIP").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        repository.load();
        List<ResourcePacks.Entry> selected = repository.getAvailable().stream().filter(entry -> entry.getName().startsWith("qa-028-")).toList();
        require(selected.size() == 2, "Native resource pack discovery failed folder + ZIP");
        require(selected.stream().anyMatch(entry -> entry.get() instanceof DirectoryResourcePack), "Folder was not loaded as a directory resource pack");
        require(selected.stream().anyMatch(entry -> entry.get() instanceof ZippedResourcePack), "ZIP was not loaded as a zipped resource pack");
        repository.apply(selected);
        Object sharing = singleton("io.github.arcadiumclient.runtime.client.resourcepack.SharedResourcePack");
        Method build = Arrays.stream(sharing.getClass().getMethods()).filter(candidate -> candidate.getName().startsWith("buildFromEquipped")).findFirst().orElseThrow();
        Object prepared = build.invoke(sharing);
        require(prepared instanceof List<?> && ((List<?>) prepared).size() == 2, "Sharing did not prepare both equipped pack formats: " + prepared);
        for (Object pack : (List<?>) prepared) {
            boolean foundMetadata = false;
            try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream((byte[]) call(pack, "getBytes")))) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) if (entry.getName().equals("pack.mcmeta")) foundMetadata = true;
            }
            require(foundMetadata, "Shared folder or ZIP lost pack.mcmeta");
        }
        RESULT.put("resource-pack-discovery", Map.of("nativeNames", selected.stream().map(ResourcePacks.Entry::getName).toList(), "sharingPrepared", 2));
        mc.openScreen(new ResourcePacksScreen(null));
    }

    public static void drawTabOverlay() {
        if (!enabled() || done || stage < 0 || !STAGES[stage].startsWith("tab-")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.getPlayerTabOverlay().render(new Window(mc).getWidth(), mc.world.getScoreboard(), null);
    }

    public static void beginLight() {
        if (!enabled() || done) return;
        inLight = true;
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        lightMatrix = new float[16];
        for (int i = 0; i < 16; i++) lightMatrix[i] = MATRIX.get(i);
    }

    public static void endLight() {
        if (!enabled() || done || !inLight) return;
        try {
            inLight = false;
            if (lightDraws == 0) return;
            MATRIX.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
            for (int i = 0; i < 16; i++) require(Math.abs(MATRIX.get(i) - lightMatrix[i]) < 0.0001f, "Light Level leaked its world matrix");
            require(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Light Level did not restore depth writes");
            require(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "Light Level disabled depth test after drawing");
            stateChecks++;
            lightDraws = 0;
        } catch (Throwable error) { fail(error); }
    }

    public static void text(String text, float x, float y, int color) {
        if (!enabled() || done || !inLight || text == null || !text.matches("[0-9]{1,2}")) return;
        try {
            require(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "Ground numbers were drawn through walls with depth test disabled");
            require(!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), "Ground numbers wrote into the scene depth buffer");
            int value = Integer.parseInt(text);
            require((color & 0xFFFFFF) == (value <= 7 ? 0xFF5555 : 0x55FF55), "Ground number color did not match its light level");
            depthChecks++;
            lightDraws++;
            int yaw = targetYaw();
            if (value >= 10 && value <= 14 && !READABLE_YAWS.contains(yaw)) {
                GLYPH_MATRIX.clear(); PROJECTION.clear();
                GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, GLYPH_MATRIX);
                GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
                double[] origin = projectGlyph(x, y);
                double[] right = projectGlyph(x + 1, y);
                if (!RESULT.containsKey("glyph-projection")) {
                    List<Float> model = new ArrayList<>();
                    List<Float> projection = new ArrayList<>();
                    for (int i = 0; i < 16; i++) { model.add(GLYPH_MATRIX.get(i)); projection.add(PROJECTION.get(i)); }
                    RESULT.put("glyph-projection", Map.of("x", x, "y", y, "origin", origin, "right", right, "model", model, "projection", projection));
                }
                if (origin[2] > 0 && Math.abs(origin[0]) < 0.95 && Math.abs(origin[1]) < 0.95) {
                    require(right[0] > origin[0], "Ground digits are horizontally mirrored at yaw " + yaw);
                    READABLE_YAWS.add(yaw);
                }
            }
        } catch (Throwable error) { fail(error); }
    }

    private static int targetYaw() {
        if (stage < 0) return 0;
        return switch (STAGES[stage]) { case "light-yaw90" -> 90; case "light-yaw180" -> 180; case "light-yaw270" -> 270; default -> 0; };
    }

    private static double[] projectGlyph(float x, float y) {
        double[] view = new double[4];
        double[] clip = new double[4];
        for (int row = 0; row < 4; row++) view[row] = GLYPH_MATRIX.get(row) * x + GLYPH_MATRIX.get(4 + row) * y + GLYPH_MATRIX.get(12 + row);
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) clip[row] += PROJECTION.get(column * 4 + row) * view[column];
        return new double[]{clip[0] / clip[3], clip[1] / clip[3], clip[3]};
    }

    public static void afterFrame() {
        if (!enabled() || done || stage < 0) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (capture != null) {
                Screenshot.take(mc.gameDir, "feature-qa-028-" + capture, mc.width, mc.height, mc.getRenderTarget());
                CAPTURES.put(capture, System.currentTimeMillis());
                capture = null;
            }
            for (String name : new ArrayList<>(CAPTURES.keySet())) {
                Path input = new File(mc.gameDir, "screenshots/feature-qa-028-" + name).toPath();
                if (!Files.isRegularFile(input)) continue;
                byte[] bytes = Files.readAllBytes(input);
                int n = bytes.length;
                if (n >= 12 && bytes[n - 8] == 'I' && bytes[n - 7] == 'E' && bytes[n - 6] == 'N' && bytes[n - 5] == 'D') {
                    Files.createDirectories(output(mc));
                    Files.write(output(mc).resolve(name), bytes);
                    CAPTURES.remove(name);
                } else require(System.currentTimeMillis() - CAPTURES.get(name) < 15000, "028 screenshot write timed out");
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void complete(Minecraft mc) throws Exception {
        done = true;
        restore(mc);
        RESULT.put("passed", true);
        write(mc);
        System.out.println("Arcadium feature QA 028: completed");
        if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
    }

    private static void fail(Throwable error) {
        if (done) return;
        done = true;
        error.printStackTrace();
        RESULT.put("passed", false);
        RESULT.put("failure", error.toString());
        RESULT.put("stage", stage >= 0 ? STAGES[stage] : "startup");
        try { Minecraft mc = Minecraft.getInstance(); restore(mc); write(mc); if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop(); }
        catch (Exception secondary) { secondary.printStackTrace(); }
    }

    private static void restore(Minecraft mc) throws Exception {
        for (var entry : ORIGINALS.entrySet()) entry.getKey().field.set(entry.getKey().target, entry.getValue());
        if (originalPacks != null) mc.getResourcePacks().apply(originalPacks);
    }
    private static Path output(Minecraft mc) { return mc.gameDir.toPath().resolve("feature-qa-028"); }
    private static void write(Minecraft mc) throws Exception { Files.createDirectories(output(mc)); Files.writeString(output(mc).resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static Object singleton(String name) throws Exception { return Class.forName(name).getField("INSTANCE").get(null); }
    private static int argb(Object color) throws Exception { require(color != null, "HUD has no value color"); return (int) call(color, "getArgb"); }
    private static Object state(Object target, String name) throws Exception { return call(field(target.getClass(), name).get(target), "getValue"); }
    private static List<String> rowTexts(Object target) throws Exception { List<String> text = new ArrayList<>(); for (Object row : (List<?>) state(target, "arcadium$rows")) text.add((String) call(row, "getText")); return text; }
    private static void requireCounter(boolean reversed) throws Exception {
        List<?> rows = (List<?>) state(counter, "arcadium$rows");
        require(rows.size() == 2, "Multiple item counter did not create two rows");
        int[] counts = reversed ? new int[]{67, 12} : new int[]{12, 67};
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            ItemStack stack = (ItemStack) call(row, "getStack");
            boolean diamond = reversed ? i == 1 : i == 0;
            require(stack.getItem() == (diamond ? Items.DIAMOND : Items.GOLDEN_APPLE), "Counter order did not follow item selection");
            require(((String) call(row, "getText")).endsWith(": " + counts[i]), "Multiple item count mismatch: " + rowTexts(counter));
        }
    }
    private static void setConfig(String name, String property, Object value) throws Exception { setStatic(FEATURES + name, property, value); }
    private static void setStatic(String name, String property, Object value) throws Exception { setField(null, Class.forName(name), property, value); }
    private static void setField(Object target, String property, Object value) throws Exception { setField(target, target.getClass(), property, value); }
    private static void setField(Object target, Class<?> type, String property, Object value) throws Exception {
        Field field = field(type, property);
        SavedField key = new SavedField(target, field);
        if (!ORIGINALS.containsKey(key)) ORIGINALS.put(key, field.get(target));
        field.set(target, value);
    }
    private static Field field(Class<?> type, String name) throws Exception { for (Class<?> current = type; current != null; current = current.getSuperclass()) { try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException absent) {} } throw new NoSuchFieldException(name); }
    private static Method method(Class<?> type, String name, Class<?>... signature) throws Exception { for (Class<?> current = type; current != null; current = current.getSuperclass()) { try { Method method = current.getDeclaredMethod(name, signature); method.setAccessible(true); return method; } catch (NoSuchMethodException absent) {} } throw new NoSuchMethodException(name); }
    private static Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[]{}); }
    private static Object call(Object target, String name, Class<?>[] signature, Object... args) throws Exception { return method(target.getClass(), name, signature).invoke(target, args); }
}
