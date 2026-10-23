// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

public final class ForgeFeature045Qa {
    private static final String BASE = "io.github.arcadiumclient.runtime.client.";
    private static final String FEATURES = BASE + "features.";
    private static final UUID PLAYER = UUID.fromString("12345678-1234-4234-9234-000000000045");
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> captures = new ArrayList<>();
    private final List<Object> sources = new ArrayList<>();
    private final AtomicInteger statisticsRequests = new AtomicInteger();
    private Object packs, maker, skins, library, savedSkin, data, http, previousTransport;
    private Field transportField;
    private QaPlayer dummy;
    private ServerData previousServer;
    private Path output;
    private int stage, frames, uploads;
    private long started, after, worldReadyAt;
    private double anchorX, anchorZ;
    private boolean done;

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || dummy == null) return;
        Minecraft mc = Minecraft.func_71410_x();
        if (mc.field_71441_e != null && mc.field_71439_g != null) pin(mc);
    }

    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        if (started == 0) started = System.nanoTime();
        if (++frames < 160 || System.nanoTime() < after) return;
        Minecraft mc = Minecraft.func_71410_x();
        try {
            require(System.nanoTime() - started < 180_000_000_000L, "Native feature QA timed out at stage " + stage);
            require(Files.isRegularFile(mc.field_71412_D.toPath().resolve(".arcadium-forge-probe")), "Native feature QA requires its isolated profile marker");
            require("ArcadiumForgeQA".equals(mc.func_110432_I().func_111285_a()) && "0".equals(mc.func_110432_I().func_148254_d()),
                    "Native feature QA requires its synthetic offline account");
            switch (stage) {
                case 0 -> {
                    verifyChatGroup();
                    verifyEmoji(mc);
                    makeSources(mc);
                    packs = Class.forName(BASE + "gui.ArcadiumResourcePacksScreen").getConstructor(GuiScreen.class).newInstance(mc.field_71462_r);
                    mc.func_147108_a((GuiScreen) packs);
                }
                case 1 -> {
                    capture(mc, "forge-features045-packs");
                    Class<?> function = Class.forName("kotlin.jvm.functions.Function0");
                    Object created = Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[]{function}, (proxy, method, args) -> switch (method.getName()) {
                        case "toString" -> "Native QA pack callback";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
                    });
                    maker = Class.forName(BASE + "gui.CustomTexturePackScreen").getConstructors()[0].newInstance(packs,
                            mc.field_71412_D.toPath().resolve("resourcepacks"), sources, sources.getFirst(), created);
                    Map<Object, Object> choices = new LinkedHashMap<>();
                    Class<? extends Enum> element = (Class<? extends Enum>) Class.forName(BASE + "resourcepack.CustomPackMaker$Element");
                    String[] values = {"SWORDS", "BLOCKS", "SKY", "FIREBALL"};
                    for (int index = 0; index < values.length; index++) choices.put(Enum.valueOf(element, values[index]), sources.get(index));
                    call(maker, "setChoices", choices); call(maker, "setName", "Native QA Combined");
                    mc.func_147108_a((GuiScreen) maker);
                }
                case 2 -> { capture(mc, "forge-features045-maker"); call(maker, "create"); }
                case 3 -> {
                    if (Boolean.TRUE.equals(call(maker, "getBusy"))) return;
                    require(call(maker, "getError") == null, "Native pack maker failed: " + call(maker, "getError"));
                    output = mc.field_71412_D.toPath().resolve("resourcepacks/Native QA Combined.zip");
                    require(Files.isRegularFile(output), "The native combined pack ZIP was not created");
                    try (ZipFile zip = new ZipFile(output.toFile())) {
                        for (String asset : assets()) require(zip.getEntry("assets/minecraft/" + asset) != null, "The combined pack lost " + asset);
                    }
                    mc.func_110438_M().func_110611_a();
                    boolean discovered = false;
                    for (Object entry : mc.func_110438_M().func_110609_b())
                        if (String.valueOf(call(entry, "func_110515_d")).contains("Native QA Combined")) discovered = true;
                    require(discovered, "The native resource pack repository did not discover the combined pack");
                    report.put("customPackCreatedAndDiscovered", true);
                    Object changer = object(BASE + "skins.SkinChanger");
                    require(Boolean.TRUE.equals(stat(BASE + "skins.SkinChanger", "isOffline")), "The QA profile UUID must match OfflinePlayer:ArcadiumForgeQA");
                    library = call(changer, "getLibrary");
                    uploads = ((Number) stat(BASE + "skins.SkinUploadClient", "requestCount")).intValue();
                    Path png = mc.field_71412_D.toPath().resolve("native-qa-skin.png");
                    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, 0xFF3388DD);
                    ImageIO.write(image, "PNG", png.toFile());
                    savedSkin = call(library, "add", png, "Native local skin", "slim");
                    skins = Class.forName(BASE + "gui.SkinChangerScreen").getConstructor(GuiScreen.class).newInstance(packs);
                    mc.func_147108_a((GuiScreen) skins);
                }
                case 4 -> {
                    if (call(skins, "getBusy") != null) return;
                    require(call(skins, "getError") == null, "Native skin library failed: " + call(skins, "getError"));
                    String id = String.valueOf(find(savedSkin.getClass(), "id").get(savedSkin));
                    Object selected = null;
                    for (Object entry : (List<?>) call(skins, "getEntries"))
                        if (id.equals(String.valueOf(find(call(entry, "getEntry").getClass(), "id").get(call(entry, "getEntry"))))) selected = entry;
                    require(selected != null, "The imported native skin is missing from its screen");
                    call(skins, "pick", selected); call(skins, "applySkin");
                }
                case 5 -> {
                    if (call(skins, "getBusy") != null) return;
                    require(call(skins, "getError") == null, "Native cracked skin apply failed: " + call(skins, "getError"));
                    UUID owner = mc.func_110432_I().func_148256_e().getId();
                    require(stat(BASE + "skins.SkinChanger", "textureFor", owner) != null, "The native local skin texture was not installed");
                    require("slim".equals(stat(BASE + "skins.SkinChanger", "modelFor", owner)), "The native local skin model was not stored");
                    Object selected = call(library, "localSelection", owner);
                    require(selected != null && "slim".equals(find(selected.getClass(), "variant").get(selected)), "The native local skin selection was not persisted");
                    require(((Number) stat(BASE + "skins.SkinUploadClient", "requestCount")).intValue() == uploads, "The cracked skin path attempted an account upload");
                    report.put("crackedSkinAppliedAndPersisted", true);
                    report.put("crackedSkinDidNotUpload", true);
                    capture(mc, "forge-features045-local-skin");
                    mc.func_147108_a(null);
                    mc.field_71474_y.field_74350_i = 120;
                    mc.field_71474_y.field_151451_c = 4;
                    mc.field_71474_y.field_74320_O = 0;
                    mc.field_71474_y.field_74335_Z = 2;
                    mc.field_71474_y.field_74352_v = false;
                    mc.field_71474_y.field_82881_y = false;
                    mc.field_71474_y.field_74347_j = false;
                    report.put("vignetteDisabledForPixelChecks", true);
                    mc.func_71371_a("arcadium-features045", "Native Features QA", new WorldSettings(45,
                            WorldSettings.func_77161_a(1), false, false, WorldType.field_77138_c).func_77166_b());
                }
                case 6 -> {
                    if (mc.field_71441_e == null || mc.field_71439_g == null || mc.field_71462_r != null) return;
                    if (worldReadyAt == 0) {
                        worldReadyAt = System.nanoTime();
                        anchorX = Math.floor(mc.field_71439_g.field_70165_t) + .5;
                        anchorZ = Math.floor(mc.field_71439_g.field_70161_v) + .5;
                        report.put("worldAnchor", Map.of("x", anchorX, "y", 6, "z", anchorZ));
                    }
                    int x = (int) Math.floor(anchorX), z = (int) Math.floor(anchorZ);
                    if (System.nanoTime() - worldReadyAt < 4_000_000_000L
                            || !mc.field_71441_e.func_72899_e(x, 4, z + 4)
                            || mc.field_71441_e.func_147439_a(x, 3, z) == Blocks.field_150350_a) return;
                    report.put("loadedGroundAndDummyChunk", true);
                    setupLevelHead(mc);
                }
                case 7 -> { capture(mc, "forge-features045-levelhead-hypixel-on"); stat(FEATURES + "LevelHeadConfig", "setBackground", false); }
                case 8 -> {
                    capture(mc, "forge-features045-levelhead-hypixel-off");
                    stat(FEATURES + "LevelHeadConfig", "setLevel", 1); stat(FEATURES + "LevelHeadConfig", "setBackground", true);
                }
                case 9 -> { capture(mc, "forge-features045-levelhead-bedwars-on"); stat(FEATURES + "LevelHeadConfig", "setBackground", false); }
                case 10 -> {
                    capture(mc, "forge-features045-levelhead-bedwars-off");
                    verifyLevelHeadPixels(mc);
                    require(statisticsRequests.get() == 0 && ((Number) call(data, "pendingCount")).intValue() == 0,
                            "The native cached LevelHead fixture attempted external statistics requests");
                    report.put("levelHeadCachedWithoutNetwork", true);
                    stat(FEATURES + "LevelHeadConfig", "setEnabled", false);
                    transportField.set(http, previousTransport);
                    mc.func_71351_a(previousServer);
                    stat(FEATURES + "ChatTweaksConfig", "setShowEmojiPicker", true);
                    mc.func_147108_a(new GuiChat(":smile:"));
                    Field picker = java.util.Arrays.stream(mc.field_71462_r.getClass().getDeclaredFields())
                            .filter(field -> field.getType().getName().equals(BASE + "emoji.EmojiPicker")).findFirst().orElseThrow();
                    picker.setAccessible(true); call(picker.get(mc.field_71462_r), "toggle");
                }
                case 11 -> {
                    capture(mc, "forge-features045-emoji-chat-picker");
                    verifyEmojiPixels(mc);
                    report.put("ok", true); report.put("finished", true); write(mc); done = true; mc.func_71400_g(); return;
                }
                default -> throw new IllegalStateException("Invalid native feature QA stage");
            }
            stage++; after = System.nanoTime() + 750_000_000L; write(mc);
        } catch (Throwable failure) {
            failure.printStackTrace(); report.put("ok", false); report.put("finished", true); report.put("error", failure.toString());
            try { if (transportField != null && previousTransport != null) transportField.set(http, previousTransport); write(mc); }
            catch (Exception ignored) { }
            done = true; mc.func_71400_g();
        }
    }

    private void verifyChatGroup() throws Exception {
        Object registry = object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
        Collection<?> cards = (Collection<?>) call(registry, "getModCardConfigs");
        List<String> childIds = List.of("chatting", "chattweaks", "arcadium-chat-filters", "arcadium-auto-text");
        long grouped = 0;
        for (Object card : cards) {
            String id = String.valueOf(call(card, "getId"));
            if (id.equals("arcadium-chat")) grouped++;
            require(!childIds.contains(id.replace(".json", "")), "A duplicate chat module card remains: " + id);
        }
        require(grouped == 1, "The native Chat group must have exactly one module card");
        List<String> trees = new ArrayList<>();
        for (Object config : (Collection<?>) call(registry, "getConfigs")) {
            String id = String.valueOf(call(config, "getId"));
            if (childIds.contains(id.replace(".json", ""))) {
                require(call(config, "getUiTree") != null, "A grouped chat module lost its settings tree"); trees.add(id.replace(".json", ""));
            }
        }
        require(trees.containsAll(childIds), "Native Chat group child trees are missing: " + trees);
        report.put("chatGroupPreservesFourTrees", trees);
    }

    private void verifyEmoji(Minecraft mc) throws Exception {
        String type = "org.polyfrost.oneconfig.internal.emoji.EmojiText";
        stat(type, "setEnabled", true);
        require(mc.field_71466_p.func_78256_a(":smile:") == 9, "Native emoji width is not9pixels");
        require(mc.field_71466_p.func_78256_a("§l:smile:") == 10, "Native bold emoji width is not10pixels");
        String trimmedEmoji = mc.field_71466_p.func_78269_a(":smile:XYZ", 9);
        report.put("trimmedEmoji", trimmedEmoji);
        require(trimmedEmoji.equals(":smile:"), "Native trim split an emoji token: " + trimmedEmoji);
        require(Class.forName(type).getResource("/assets/oneconfig/emoji/twemoji-atlas.png") != null, "Native Twemoji atlas is missing");
        report.put("twemojiWidthBoldAndTrim", true);
    }

    private void makeSources(Minecraft mc) throws Exception {
        require(!Files.exists(mc.field_71412_D.toPath().resolve("resourcepacks/Native QA Combined.zip")), "Use a fresh native feature QA profile");
        Class<?> source = Class.forName(BASE + "resourcepack.CustomPackMaker$Source");
        for (int index = 0; index < assets().length; index++) {
            Path root = mc.field_71412_D.toPath().resolve("resourcepacks/Native QA/Pack " + index);
            Files.createDirectories(root);
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":1,\"description\":\"Native QA\"}}");
            Path asset = root.resolve("assets/minecraft/" + assets()[index]); Files.createDirectories(asset.getParent());
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, 0xFF00AA44 + index * 0x00220022);
            ImageIO.write(image, "PNG", asset.toFile());
            sources.add(source.getConstructor(String.class, Path.class).newInstance("Native Pack " + index, root));
        }
    }

    private static String[] assets() { return new String[]{"textures/items/diamond_sword.png", "textures/blocks/stone.png", "mcpatcher/sky/world0/sky1.png", "textures/items/fire_charge.png"}; }

    private void setupLevelHead(Minecraft mc) throws Exception {
        data = stat(BASE + "bedwars.HypixelDataService", "data"); call(data, "clear");
        http = stat(BASE + "bedwars.HypixelDataService", "http");
        transportField = find(http.getClass(), "transport"); previousTransport = transportField.get(http);
        Class<?> transport = Class.forName(BASE + "bedwars.OverlayHttp$Transport");
        transportField.set(http, Proxy.newProxyInstance(transport.getClassLoader(), new Class<?>[]{transport}, (proxy, method, args) -> {
            statisticsRequests.incrementAndGet(); throw new IOException("External statistics requests are disabled in native QA");
        }));
        dummy = new QaPlayer(mc.field_71441_e, new GameProfile(PLAYER, "LevelHeadDummy"));
        mc.field_71441_e.func_73027_a(4545, dummy);
        cache(dummy.func_110124_au(), "LevelHeadDummy"); cache(mc.field_71439_g.func_110124_au(), "ArcadiumForgeQA");
        previousServer = mc.func_147104_D(); mc.func_71351_a(new ServerData("Offline LevelHead fixture", "qa.hypixel.net"));
        stat(FEATURES + "NoRenderConfig", "setEnabled", false); stat(FEATURES + "NickHiderConfig", "setEnabled", false);
        stat(FEATURES + "LevelHeadConfig", "setLevel", 0); stat(FEATURES + "LevelHeadConfig", "setBackground", true);
        stat(FEATURES + "LevelHeadConfig", "setScale", 1.5f); stat(FEATURES + "LevelHeadConfig", "setHeightOffset", 0.08f);
        stat(FEATURES + "LevelHeadConfig", "setEnabled", true);
        pin(mc);
    }

    @SuppressWarnings("unchecked") private void cache(UUID id, String name) throws Exception {
        Class<?> stats = Class.forName(BASE + "bedwars.BedwarsStats");
        Object value = stats.getConstructor(UUID.class, String.class, int.class, double.class, int.class, int.class, boolean.class,
                boolean.class, String.class, String.class, String.class, String.class, String.class, String.class, int.class)
                .newInstance(id, name, 245, 7.5d, 30, 4, false, false, null, null, null, null, null, null, 50);
        var constructor = Class.forName(BASE + "bedwars.BedwarsData$Cached").getDeclaredConstructor(stats, long.class); constructor.setAccessible(true);
        synchronized (data) { ((Map<UUID, Object>) find(data.getClass(), "cache").get(data)).put(id, constructor.newInstance(value, Long.MAX_VALUE)); }
    }

    private void pin(Minecraft mc) {
        mc.field_71441_e.func_72877_b(6000);
        mc.field_71439_g.field_71075_bZ.field_75100_b = true;
        pin(mc.field_71439_g, anchorX, 6, anchorZ, 0); pin(dummyEntity(mc), anchorX, 6, anchorZ + 4, 180);
    }

    private static Entity dummyEntity(Minecraft mc) { return mc.field_71441_e.func_73045_a(4545); }

    private static void pin(Entity entity, double x, double y, double z, float yaw) {
        if (entity == null) return;
        entity.func_70080_a(x, y, z, yaw, 0);
        entity.field_70169_q = entity.field_70142_S = x; entity.field_70167_r = entity.field_70137_T = y; entity.field_70166_s = entity.field_70136_U = z;
        entity.field_70126_B = yaw; entity.field_70127_C = 0;
    }

    private void verifyLevelHeadPixels(Minecraft mc) throws Exception {
        List<Map<String, Object>> pixels = new ArrayList<>();
        for (String stem : captures) if (stem.contains("levelhead")) {
            BufferedImage image = ImageIO.read(mc.field_71412_D.toPath().resolve(stem + ".png").toFile());
            int aqua = 0, yellow = 0, top = image.getHeight(), bottom = -1;
            for (int y = 0; y < image.getHeight() / 2; y++) for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
                int color = image.getRGB(x, y), red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
                boolean prefix = Math.abs(red - 85) < 18 && green > 230 && blue > 230;
                boolean suffix = red > 230 && green > 230 && Math.abs(blue - 85) < 18;
                if (prefix) aqua++;
                if (suffix) yellow++;
                if (prefix || suffix) { top = Math.min(top, y); bottom = Math.max(bottom, y); }
            }
            require(aqua >= 8 && yellow >= 8, "Native LevelHead glyph colors are missing: " + stem + ", aqua=" + aqua + ", yellow=" + yellow);
            pixels.add(Map.of("screenshot", stem + ".png", "aquaPixels", aqua, "yellowPixels", yellow, "glyphTop", top, "glyphBottom", bottom));
        }
        report.put("levelHeadRenderedPixels", pixels);
    }

    private void verifyEmojiPixels(Minecraft mc) throws Exception {
        BufferedImage image = ImageIO.read(mc.field_71412_D.toPath().resolve("forge-features045-emoji-chat-picker.png").toFile());
        int input = 0, picker = 0;
        for (int y = Math.max(0, image.getHeight() - 48); y < image.getHeight(); y++) for (int x = 0; x < Math.min(180, image.getWidth()); x++)
            if (emojiPixel(image.getRGB(x, y))) input++;
        for (int y = Math.max(0, image.getHeight() - 470); y < image.getHeight() - 60; y++)
            for (int x = Math.max(0, image.getWidth() - 360); x < image.getWidth() - 12; x++) if (emojiPixel(image.getRGB(x, y))) picker++;
        require(input >= 4 && picker >= 32, "Native emoji rendering is missing: input=" + input + ", picker=" + picker);
        report.put("nativeEmojiChatAndPickerRendered", Map.of("inputPixels", input, "pickerPixels", picker));
    }

    private static boolean emojiPixel(int color) {
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        return red > 160 && green > 90 && blue < 150 && red > blue * 1.2;
    }

    private void capture(Minecraft mc, String stem) throws Exception { ForgeRuntimeQa.capture(mc, stem, frames); captures.add(stem); }

    private void write(Minecraft mc) throws Exception {
        report.put("stage", stage); report.put("captures", captures); report.put("statisticsRequests", statisticsRequests.get());
        Files.writeString(mc.field_71412_D.toPath().resolve("forge-features045-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }

    private static Object object(String name) throws Exception { return Class.forName(name).getField("INSTANCE").get(null); }
    private static Object stat(String type, String name, Object... args) throws Exception { return invoke(Class.forName(type), null, name, args); }
    private static Object call(Object owner, String name, Object... args) throws Exception { return invoke(owner.getClass(), owner, name, args); }
    private static Object invoke(Class<?> type, Object owner, String name, Object[] args) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) for (Method method : current.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean compatible = true;
            for (int index = 0; index < args.length; index++) if (args[index] != null && !wrap(method.getParameterTypes()[index]).isInstance(args[index])) compatible = false;
            if (compatible) { method.setAccessible(true); return method.invoke(owner, args); }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static Class<?> wrap(Class<?> type) {
        if (type == boolean.class) return Boolean.class; if (type == int.class) return Integer.class;
        if (type == float.class) return Float.class; if (type == double.class) return Double.class; if (type == long.class) return Long.class;
        return type;
    }
    private static Field find(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass())
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class QaPlayer extends EntityOtherPlayerMP {
        QaPlayer(World world, GameProfile profile) { super(world, profile); }
        @Override public ResourceLocation func_110306_p() { return new ResourceLocation("textures/entity/steve.png"); }
        @Override public ResourceLocation func_110303_q() { return null; }
        @Override public int func_70070_b(float partialTicks) { return 0x00F000F0; }
        @Override public float func_70013_c(float partialTicks) { return 1f; }
    }
}
