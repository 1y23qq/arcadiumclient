package io.github.arcadiumclient.featuretest;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;

final class ForgeRenderQa {
    private static final int[][] SIZES = {{1280, 720}, {854, 480}, {1920, 1080}, {1280, 720}};
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static final List<Map<String, Object>> RESIZES = new ArrayList<>();
    private static int step, ticks, savedSlot;
    private static boolean begun, done, firstPerson;
    private static ItemStack savedStack;
    private static Map<String, Object> heldModel;
    private static Map<String, Object> hotbarDraw;
    private static int packStage, packTicks;
    private static List<net.minecraft.client.resource.pack.ResourcePacks.Entry> savedPacks;
    private static float hotbarOffsetX, hotbarOffsetY;
    private static int presetStage, presetTicks;
    private static Object itemSettings;
    private static Field itemPositions;
    private static boolean savedPositions;

    private ForgeRenderQa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        if (!begun) {
            begun = true;
            checks.put("renderCompatibility", CHECKS);
            CHECKS.put("resizes", RESIZES);
            savedSlot = mc.player.inventory.selectedSlot;
            savedStack = mc.player.inventory.items[2];
            mc.player.inventory.items[2] = new ItemStack(Blocks.STONE, 64);
            mc.player.inventory.selectedSlot = 2;
            mc.options.guiScale = 2;
            requestSize();
        }
        if (step == SIZES.length) return finish(mc);
        ticks++;
        int[] size = SIZES[step];
        require(ticks < 80, "The owned game window did not resize to " + size[0] + "x" + size[1]);
        if (mc.width != size[0] || mc.height != size[1] || ticks < 20) return false;
        Window window = new Window(mc);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("pixels", List.of(mc.width, mc.height));
        sample.put("gui", List.of(window.getWidth(), window.getHeight()));
        sample.put("modelViewDepth", GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH));
        sample.put("projectionDepth", GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH));
        require(hotbarDraw != null, "The actual hotbar slots did not render");
        sample.put("hotbarDraw", hotbarDraw);
        if (FabricLoader.getInstance().isModLoaded("vanillahud")) {
            Object manager = Class.forName("org.polyfrost.oneconfig.api.hud.v1.HudManager").getField("INSTANCE").get(null);
            Object hotbar = ((Collection<?>) invoke(manager, "getActiveInstances")).stream()
                .filter(hud -> hud.getClass().getName().equals("org.polyfrost.vanillahud.hud.HotbarHud")).findFirst().orElseThrow();
            float x = ((Number) invoke(hotbar, "getX")).floatValue();
            float y = ((Number) invoke(hotbar, "getY")).floatValue();
            float expectedX = ((Number) hotbar.getClass().getMethod("defaultOriginX", int.class, int.class)
                .invoke(hotbar, window.getWidth(), window.getHeight())).floatValue();
            float expectedY = ((Number) hotbar.getClass().getMethod("defaultOriginY", int.class, int.class)
                .invoke(hotbar, window.getWidth(), window.getHeight())).floatValue();
            sample.put("hotbar", List.of(x, y));
            sample.put("expectedHotbar", List.of(expectedX, expectedY));
            if (step == 0) {
                hotbarOffsetX = x - expectedX;
                hotbarOffsetY = y - expectedY;
            }
            sample.put("savedHotbarOffset", List.of(hotbarOffsetX, hotbarOffsetY));
            require(Math.abs(x - expectedX - hotbarOffsetX) <= 1f && Math.abs(y - expectedY - hotbarOffsetY) <= 1f,
                "The hotbar did not preserve its anchor offset after resizing: " + sample);
            @SuppressWarnings("unchecked") List<Float> drawn = (List<Float>) hotbarDraw.get("drawn");
            require(Math.abs(drawn.get(0) - (window.getWidth() / 2 - 88 + hotbarOffsetX)) <= 1f
                && Math.abs(drawn.get(1) - (window.getHeight() - 19 + hotbarOffsetY)) <= 1f,
                "The rendered hotbar did not follow its HUD position after resizing: " + sample);
        }
        require(heldModel != null, "The real first-person stone model did not render");
        sample.put("heldItem", heldModel);
        RESIZES.add(sample);
        if (++step < SIZES.length) {
            ticks = 0;
            requestSize();
            return false;
        }
        return finish(mc);
    }

    private static boolean finish(Minecraft mc) throws Exception {
        if (!verifyItemPreset(mc)) return false;
        if (!ForgeEntityStressQa.tick(mc, CHECKS)) return false;
        CHECKS.put("returnedFirstPersonItem", heldModel);
        if (!verifyResourceTransform(mc)) return false;
        verifyKeyboardAndChat(mc);
        mc.player.inventory.items[2] = savedStack;
        mc.player.inventory.selectedSlot = savedSlot;
        CHECKS.put("completed", true);
        done = true;
        return true;
    }

    private static boolean verifyItemPreset(Minecraft mc) throws Exception {
        if (presetStage >= 3 || !FabricLoader.getInstance().isModLoaded("overflowanimations")) return true;
        if (presetStage == 0) {
            itemSettings = Class.forName("org.polyfrost.overflowanimations.legacy.firstperson.LegacyFirstPerson").getMethod("items").invoke(null);
            itemPositions = itemSettings.getClass().getField("itemPositions");
            savedPositions = itemPositions.getBoolean(itemSettings);
            CHECKS.put("configuredItemPositions", savedPositions);
            CHECKS.put("configuredPresetItem", heldModel);
            net.minecraft.client.Screenshot.take(mc.gameDir, "forge-item-preset-configured.png", mc.width, mc.height, mc.getRenderTarget());
            itemPositions.setBoolean(itemSettings, false);
            presetStage = 1;
            presetTicks = 0;
            return false;
        }
        if (++presetTicks < 20) return false;
        if (presetStage == 1) {
            CHECKS.put("itemPositionsDisabledItem", heldModel);
            net.minecraft.client.Screenshot.take(mc.gameDir, "forge-item-preset-disabled.png", mc.width, mc.height, mc.getRenderTarget());
            itemPositions.setBoolean(itemSettings, savedPositions);
            presetStage = 2;
            presetTicks = 0;
            return false;
        }
        require(itemPositions.getBoolean(itemSettings) == savedPositions, "The QA did not restore the native item-position setting");
        CHECKS.put("restoredPresetItem", heldModel);
        presetStage = 3;
        return true;
    }

    private static boolean verifyResourceTransform(Minecraft mc) throws Exception {
        if (packStage >= 3) return true;
        if (packStage == 0) {
            var packs = mc.getResourcePacks();
            savedPacks = List.copyOf(packs.getApplied());
            var file = packs.getDirectory().toPath().resolve("Arcadium Render QA.zip");
            var entries = Map.of("pack.mcmeta", "{\"pack\":{\"pack_format\":1,\"description\":\"Renderer QA\"}}",
                "assets/minecraft/models/item/stone.json", "{\"parent\":\"block/stone\",\"display\":{\"firstperson\":{\"rotation\":[0,0,0],\"translation\":[0,0,0],\"scale\":[0.5,0.5,0.5]}}}");
            if (Files.exists(file)) {
                require(Boolean.getBoolean("arcadium.resourcePackQa")
                    && Files.isRegularFile(mc.gameDir.toPath().resolve("forge-qa/resource-pack-restart.json")),
                    "The renderer resource pack fixture already exists");
                try (var zip = new java.util.zip.ZipFile(file.toFile())) {
                    require(zip.size() == entries.size(), "The saved renderer fixture has unexpected contents");
                    for (var entry : entries.entrySet()) {
                        var source = zip.getEntry(entry.getKey());
                        require(source != null, "The saved renderer fixture is incomplete");
                        try (var input = zip.getInputStream(source)) {
                            require(new String(input.readAllBytes(), StandardCharsets.UTF_8).equals(entry.getValue()),
                                "The saved renderer fixture changed");
                        }
                    }
                }
            } else try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
                for (var entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            packs.load();
            var selected = new ArrayList<>(savedPacks);
            selected.add(packs.getAvailable().stream().filter(pack -> pack.getName().equals(file.getFileName().toString())).findFirst().orElseThrow());
            packs.apply(selected);
            mc.reloadResources();
            packStage = 1;
            packTicks = 0;
            return false;
        }
        if (++packTicks < 20) return false;
        @SuppressWarnings("unchecked") List<Float> scale = (List<Float>) heldModel.get("matrixScale");
        if (packStage == 1) {
            CHECKS.put("customPackItem", heldModel);
            require(scale.stream().allMatch(value -> Math.abs(value - .4f) < .001f),
                "The resource pack first-person scale did not reach the held item: " + heldModel);
            mc.getResourcePacks().apply(savedPacks);
            mc.reloadResources();
            packStage = 2;
            packTicks = 0;
            return false;
        }
        CHECKS.put("restoredPackItem", heldModel);
        require(scale.stream().allMatch(value -> Math.abs(value - .8f) < .001f),
            "The default held-item size did not return after removing the resource pack: " + heldModel);
        packStage = 3;
        return true;
    }

    private static void requestSize() throws Exception {
        if (step == 2) {
            Class<?> display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
            long handle = (long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
            require(Boolean.TRUE.equals(Class.forName("org.lwjgl.sdl.SDLVideo")
                .getMethod("SDL_SetWindowSize", long.class, int.class, int.class)
                .invoke(null, handle, SIZES[step][0], SIZES[step][1])), "The owned window rejected the native resize request");
        } else {
            Display.setDisplayMode(new DisplayMode(SIZES[step][0], SIZES[step][1]));
        }
    }

    static void beginItem(ModelTransformations.Type transform) { firstPerson = transform == ModelTransformations.Type.FIRST_PERSON; }
    static void endItem() { firstPerson = false; }

    static void recordHotbar(int slot, int x, int y) {
        if (!begun || done || slot != 0) return;
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        hotbarDraw = Map.of("input", List.of(x, y), "drawn", List.of(
            x * MATRIX.get(0) + y * MATRIX.get(4) + MATRIX.get(12),
            x * MATRIX.get(1) + y * MATRIX.get(5) + MATRIX.get(13)),
            "scale", List.of(MATRIX.get(0), MATRIX.get(5)));
    }

    static void recordItem(ItemStack stack, BakedModel model) {
        if (!begun || done || !firstPerson || stack == null || stack.getItem() != net.minecraft.item.Item.byBlock(Blocks.STONE)) return;
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        List<Float> scale = new ArrayList<>(3);
        for (int column = 0; column < 3; column++) {
            int base = column * 4;
            float x = MATRIX.get(base), y = MATRIX.get(base + 1), z = MATRIX.get(base + 2);
            scale.add((float) Math.sqrt(x * x + y * y + z * z));
        }
        List<Float> matrix = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) matrix.add(MATRIX.get(i));
        var transform = model.getTransformations().firstPerson;
        heldModel = Map.of("model", model.getClass().getName(), "matrixScale", scale,
            "cameraScale", List.of(transform.scale.x, transform.scale.y, transform.scale.z),
            "matrix", matrix, "projection", projection(), "modelViewDepth", GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH));
    }

    private static List<Float> projection() {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, MATRIX);
        List<Float> values = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) values.add(MATRIX.get(i));
        return values;
    }

    private static void verifyKeyboardAndChat(Minecraft mc) throws Exception {
        if (!Boolean.getBoolean("arcadium.forge.prepared")) return;
        Class<?> keyboard = Class.forName("org.lwjgl.input.Keyboard");
        Field keys = keyboard.getDeclaredField("keyDownBuffer");
        keys.setAccessible(true);
        int capacity = ((java.nio.ByteBuffer) keys.get(null)).limit();
        Class<?> safe = Class.forName("io.github.arcadiumclient.forgecompat.LegacyKeyboard");
        var check = safe.getMethod("isKeyDown", int.class);
        var keyName = safe.getMethod("getKeyName", int.class);
        var nativeName = keyboard.getMethod("getKeyName", int.class);
        Object none = nativeName.invoke(null, 0);
        for (int key : new int[] {-1, capacity, Integer.MAX_VALUE}) {
            require(Boolean.FALSE.equals(check.invoke(null, key)), "An out-of-range legacy key was reported as pressed");
            require(java.util.Objects.equals(none, keyName.invoke(null, key)), "An out-of-range legacy key did not use the host unbound name");
        }
        for (int key : new int[] {0, 54, Math.min(320, capacity - 1)}) {
            require(check.invoke(null, key).equals(keyboard.getMethod("isKeyDown", int.class).invoke(null, key)),
                "A valid legacy key changed state");
            require(java.util.Objects.equals(nativeName.invoke(null, key), keyName.invoke(null, key)), "A valid legacy key name changed");
        }
        int modules = 0, invalid = 0;
        if (java.util.Arrays.asList(System.getProperty("arcadium.forgeQa.expected", "").split(",")).contains("keystrokes")) {
            Collection<?> raven = (Collection<?>) Class.forName("keystrokesmod.module.ModuleManager").getField("modules").get(null);
            for (Object module : raven) {
                int before = ((Number) invoke(module, "getKeycode")).intValue();
                if (before < 0 || before >= capacity) invalid++;
                invoke(module, "onKeyBind");
                require(before == ((Number) invoke(module, "getKeycode")).intValue(), "Raven cleared a key binding after polling it");
                modules++;
            }
            Object module = raven.stream().filter(value -> {
                try { return Integer.valueOf(0).equals(invoke(value, "getKeycode")); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }).findFirst().orElseThrow();
            var bind = module.getClass().getMethod("setBind", int.class);
            Object bindRow = null;
            for (Object category : (Collection<?>) Class.forName("keystrokesmod.clickgui.ClickGui").getField("categories").get(null)) {
                for (Object component : (Collection<?>) category.getClass().getField("modules").get(category)) {
                    if (component.getClass().getField("mod").get(component) != module) continue;
                    for (Object setting : (Collection<?>) component.getClass().getField("settings").get(component)) {
                        if (setting.getClass().getName().equals("keystrokesmod.clickgui.components.impl.BindComponent")
                                && setting.getClass().getField("keySetting").get(setting) == null) bindRow = setting;
                    }
                }
            }
            require(bindRow != null, "Raven did not create the existing module's GUI binding row");
            var displayedName = bindRow.getClass().getMethod("getKeyAsStr", boolean.class);
            Map<String, String> displayedNames = new LinkedHashMap<>();
            try {
                for (int key : new int[] {-1, capacity, Integer.MAX_VALUE}) {
                    bind.invoke(module, key);
                    invoke(module, "onKeyBind");
                    require(Integer.valueOf(key).equals(invoke(module, "getKeycode")), "Raven discarded an unsupported saved binding");
                }
                for (int key : new int[] {-1, capacity, 54, Math.min(320, capacity - 1)}) {
                    bind.invoke(module, key);
                    Object actual = displayedName.invoke(bindRow, false);
                    Object expected = key < 0 || key >= capacity ? none : nativeName.invoke(null, key);
                    require(java.util.Objects.equals(expected, actual), "Raven's real GUI binding row changed or rejected a key name: " + key);
                    require(Integer.valueOf(key).equals(invoke(module, "getKeycode")), "Raven's GUI changed the saved key binding");
                    displayedNames.put(Integer.toString(key), String.valueOf(actual));
                }
            } finally { bind.invoke(module, 0); }
            CHECKS.put("ravenGuiKeyNames", Map.of("component", bindRow.getClass().getName(), "names", displayedNames,
                "unboundName", String.valueOf(none), "bindingRestored", Integer.valueOf(0).equals(invoke(module, "getKeycode"))));
        }
        CHECKS.put("legacyKeyboard", Map.of("runtimeKeys", capacity, "modulesPolled", modules, "outOfRangeBindings", invalid));
        GameMenuScreen chat = new GameMenuScreen();
        chat.init(mc, new Window(mc).getWidth(), new Window(mc).getHeight());
        chat.sendChatMessage("/arcadium_unknown_qa_command", false);
        CHECKS.put("chatCommand", Map.of("nativeScreenSendCompleted", true));
    }

    private static Object invoke(Object object, String method) throws Exception { return object.getClass().getMethod(method).invoke(object); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
