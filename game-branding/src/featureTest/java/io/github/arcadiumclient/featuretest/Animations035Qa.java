package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.object;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.featuretest.mixin.CreativeProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.GameGuiAnimationProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.PlayerRendererAnimationProbeAccess;
import java.nio.FloatBuffer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.menu.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.inventory.menu.SurvivalInventoryScreen;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.resource.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class Animations035Qa {
    private static final String CONFIG = "io.github.arcadiumclient.runtime.client.features.AnimationsConfig";
    private static final String NATIVE = "org.polyfrost.overflowanimations.config.OverflowAnimationsConfig";
    private static final String MANAGER = "org.polyfrost.oneconfig.api.config.v1.ConfigManager";
    private static final String REGISTRY = "org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry";
    private static final int PEER_ID = -2035;
    private static final String[] FLAGS = {"itemHeld", "itemSwitch", "hitBox", "inventory", "blockHit", "heartAnimation",
            "damageAnimation", "sneakingAnimation", "eatAnimation", "thrownItems", "fishingRod", "swordSwing",
            "bowAnimation", "alwaysSwing", "removeTitles", "oldEnchantGlint", "leftHanded", "noSmallArms", "noShake",
            "fixLiquidPlace", "movableTitles"};
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Object> OWN = new LinkedHashMap<>(), OVERFLOW = new LinkedHashMap<>();
    private static final Map<Path, byte[]> FILES = new LinkedHashMap<>();
    private static final List<Map<String, Object>> DRAWS = new ArrayList<>();
    private static final Map<String, List<Float>> SCALES = new LinkedHashMap<>();
    private static final Map<String, String> DRAW_SOURCES = new LinkedHashMap<>();
    private static final Map<String, Integer> LAYER_CALLS = new LinkedHashMap<>(), ITEM_CALLS = new LinkedHashMap<>();
    private static final ArrayDeque<LivingEntity> HELD_LAYERS = new ArrayDeque<>();
    private static Object config, nativeConfig, ownTree, nativeTree, originalItems, shell;
    private static String profile, createdProfile, search;
    private static Screen screen;
    private static ClientWorld fixtureWorld;
    private static Titles titles;
    private static ItemStack held, using;
    private static int slot, useTimer, view, stage, ticks, drawStage, winding, handDepth, handChecks;
    private static int damagedTimer, damagedTime, hurtChecks, creativeTab, worldDrawDepth;
    private static int worldPasses, capturedSubmissions;
    private static float damagedDir;
    private static float[] handMatrix;
    private static Field runtimeStateField;
    private static Object lastHurtState, baselineHurtState;
    private static final Map<String, Map<String, Object>> HURT_SAMPLES = new LinkedHashMap<>();
    private static float health, fallDistance;
    private static boolean hideGui, renderHitboxes, begun, done, pendingDraw, restored;
    private static boolean bowReferencePending;
    private static List<Float> bowReference;
    private static boolean noHit;
    private static RemoteClientPlayerEntity peer;
    private static Throwable renderFailure;

    private Animations035Qa() {}

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.release035Qa") || done) return true;
        require(Boolean.getBoolean("arcadium.forgeQa") && "ArcadiumTest".equals(mc.getSession().getUsername())
                && "0".equals(mc.getSession().getAccessToken()), "Animations QA requires the isolated offline fixture");
        require(mc.world != null && mc.player != null && mc.isSingleplayer(), "Animations QA requires its local world");
        try {
            if (!begun) begin(mc, checks);
            require(mc.world == fixtureWorld, "Animations QA left its original isolated client world");
            if (renderFailure != null) throw new IllegalStateException("Animation rendering probe failed", renderFailure);
            require(++ticks < 180, "Animations QA timed out: " + diagnostics(mc));
            if (peer != null) peer.setPositionAndAngles(mc.player.x - 1.4, mc.player.y, mc.player.z, mc.player.yaw, 0);
            switch (stage) {
                case 0 -> {
                    if (ticks < 12) return false;
                    require(mc.screen != null && Visual033Qa.hasText(mc.screen, "Animations"), "The unified Animations settings did not open");
                    capture(mc, "animations035-overflow-settings.png");
                    setting("mode", 1);
                    inspectBadlionSettings();
                    profileIsolation(mc);
                    open("Badlion Animations");
                }
                case 1 -> {
                    if (ticks < 10) return false;
                    require(Visual033Qa.hasText(mc.screen, "Item Hold"), "The Badlion settings did not render their original controls");
                    capture(mc, "animations035-badlion-settings.png");
                    mc.openScreen(null);
                    mc.options.hideGui = false;
                    drawStage = 0;
                    selectDraw(mc);
                }
                case 2 -> {
                    if (pendingDraw && ticks == 30) {
                        RESULT.put("pendingDraw", diagnostics(mc));
                        capture(mc, "animations035-pending-" + drawStage + ".png");
                    }
                    if (ticks < 7 || pendingDraw) return false;
                    if (drawStage == 6 && bowReferencePending) {
                        require(SCALES.containsKey("FIRST_PERSON"), "The unchanged bow reference did not render: " + diagnostics(mc));
                        bowReference = List.copyOf(SCALES.get("FIRST_PERSON"));
                        RESULT.put("bowFirstPersonReference", Map.of("animationsEnabled", false, "matrixScale", bowReference,
                                "bundledRendererSettingsUnchanged", true));
                        capture(mc, "animations035-bow-reference.png");
                        bowReferencePending = false;
                        setting("enabled", true);
                        mc.player.clearItemInUse();
                        mc.player.setItemInUse(mc.player.inventory.items[slot], 71980);
                        mc.player.damagedTimer = 7;
                        mc.player.damagedTime = 10;
                        SCALES.clear(); DRAW_SOURCES.clear();
                        LAYER_CALLS.clear(); ITEM_CALLS.clear();
                        capturedSubmissions = worldPasses = handChecks = 0;
                        pendingDraw = true;
                        ticks = 0;
                        return false;
                    }
                    String type = drawStage == 2 || drawStage == 3 || drawStage == 5 ? "THIRD_PERSON" : "FIRST_PERSON";
                    if (drawStage != 8) require(SCALES.containsKey(type), "The actual " + type + " held item did not render at animation case " + drawStage);
                    if (type.equals("THIRD_PERSON")) require(SCALES.containsKey("peer"), "The remote player's F5 held block did not render");
                    DRAWS.add(Map.of("case", drawStage, "mode", field(config, "mode"), "perspective", mc.options.perspective,
                            "scale", new LinkedHashMap<>(SCALES), "sources", new LinkedHashMap<>(DRAW_SOURCES),
                            "captureSubmissions", capturedSubmissions));
                    capture(mc, "animations035-held-" + drawStage + ".png");
                    if (drawStage == 6) {
                        List<Float> ratio = new ArrayList<>();
                        for (int i = 0; i < 3; i++) ratio.add(SCALES.get("FIRST_PERSON").get(i) / bowReference.get(i));
                        RESULT.put("bowFirstPersonRelativeScale", Map.of("baseline", bowReference, "selected", SCALES.get("FIRST_PERSON"), "ratio", ratio));
                    }
                    if (++drawStage < 9) {
                        selectDraw(mc);
                        ticks = 0;
                        return false;
                    }
                    RESULT.put("actualHeldItemDraws", DRAWS);
                    RESULT.put("leftHandFrontFaceAndMatrixRestored", true);
                    RESULT.put("bowEatingAndInventoryDraws", true);
                    require(hurtChecks > 0, "The live hurt camera intensity probe did not run");
                    require(HURT_SAMPLES.containsKey("intensity37") && HURT_SAMPLES.containsKey("noShake"), "The paired hurt camera checks did not cover 37% and No Shake");
                    RESULT.put("actualHurtCameraChecks", hurtChecks);
                    RESULT.put("hurtCameraAgainstBundledBaseline", HURT_SAMPLES);
                    restore(mc);
                }
                case 3 -> {
                    if (ticks < 6) return false;
                    require(mc.player.getHealth() == health && mc.player.isAlive(), "Animation QA changed player health");
                    require(field(config, "mode").equals(OWN.get("mode")), "Animation mode did not restore");
                    require(snapshot(ownTree).equals(OWN), "Badlion settings did not restore");
                    require(snapshot(nativeTree).equals(OVERFLOW), "Overflow settings did not restore");
                    require(noHit == (Boolean) stat("io.github.arcadiumclient.runtime.client.features.NoHitDelayConfig", "getEnabled"), "NoHitDelay alias did not restore");
                    RESULT.put("restored", true);
                    RESULT.put("completed", true);
                    done = true;
                    return true;
                }
                default -> throw new IllegalStateException("Unknown animations QA stage");
            }
            stage++;
            ticks = 0;
            return false;
        } catch (Exception failure) {
            if (begun) try { restore(mc); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        Path game = mc.gameDir.toPath().toRealPath();
        require(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath().equals(game), "Animations QA must use its exact isolated game directory");
        config = object(CONFIG);
        fixtureWorld = mc.world;
        nativeConfig = stat(NATIVE, "instance");
        ownTree = call(config, "getTree");
        nativeTree = call(nativeConfig, "getTree");
        OWN.putAll(snapshot(ownTree));
        OVERFLOW.putAll(snapshot(nativeTree));
        require(field(config, "mode").equals(0) && Boolean.TRUE.equals(field(config, "enabled")), "Fresh installs must retain Overflow Animations as the enabled default");
        originalItems = field(nativeConfig, "items");
        Object unified = stat(REGISTRY, "findTree", "arcadium-animations.json");
        Map<?, ?> all = (Map<?, ?>) field(nativeTree, "map"), shown = (Map<?, ?>) field(unified, "map");
        require(all.size() >= 100 && shown.keySet().containsAll(all.keySet()), "The unified route omitted native Overflow settings");
        require(Boolean.FALSE.equals(stat(REGISTRY, "shouldShowModCardId", "overflowanimations.json")), "The duplicate Overflow module card is still visible");
        RESULT.put("overflowNativePropertyCount", all.size());
        RESULT.put("defaultModePreserved", true);
        profile = (String) stat(MANAGER, "activeProfile");
        Path folder = ((Path) call(stat(MANAGER, "active"), "getFolder")).toAbsolutePath().normalize();
        for (String name : List.of("arcadium-animations.json", "overflowanimations.json", "arcadium-no-hit-delay.json")) {
            Path file = folder.resolve(name);
            require(file.startsWith(game) && !Files.isSymbolicLink(file), "Animation QA config must stay inside its game directory");
            FILES.put(file, Files.exists(file) ? Files.readAllBytes(file) : null);
        }
        noHit = (Boolean) stat("io.github.arcadiumclient.runtime.client.features.NoHitDelayConfig", "getEnabled");
        screen = mc.screen;
        shell = object("org.polyfrost.oneconfig.internal.ui.shell.ShellState");
        search = (String) call(shell, "getSearchQuery");
        slot = mc.player.inventory.selectedSlot;
        held = mc.player.inventory.items[slot];
        using = mc.player.getItemInUse();
        useTimer = mc.player.getItemUseTimer();
        view = mc.options.perspective;
        hideGui = mc.options.hideGui;
        renderHitboxes = mc.getEntityRenderDispatcher().shouldRenderHitboxes();
        health = mc.player.getHealth();
        fallDistance = mc.player.fallDistance;
        damagedTimer = mc.player.damagedTimer;
        damagedTime = mc.player.damagedTime;
        damagedDir = mc.player.damagedSwingDir;
        creativeTab = CreativeProbeAccess.arcadium$selectedTab();
        titles = Titles.capture((GameGuiAnimationProbeAccess) mc.gui);
        checks.put("animations035", RESULT);
        begun = true;
        call(shell, "setSearchQuery", "");
        open("General");
    }

    private static void inspectBadlionSettings() throws Exception {
        Object displayed = stat(REGISTRY, "findTree", "arcadium-animations.json");
        List<String> exercised = new ArrayList<>();
        for (String flag : FLAGS) {
            Object property = call(ownTree, "getProp", flag);
            require(property != null && call(displayed, "getProp", flag) == property, "Missing original Badlion setting " + flag);
            Object previous = call(property, "get");
            for (boolean value : new boolean[]{false, true}) {
                call(property, "setAs", value);
                Object active = stat(CONFIG, "badlion");
                require(active != null && Boolean.valueOf(value).equals(call(active, flag)), "Setting did not reach its active renderer: " + flag);
                require(snapshot(nativeTree).equals(OVERFLOW), "Changing " + flag + " overwrote native Overflow preferences");
            }
            call(property, "setAs", previous);
            exercised.add(flag);
        }
        setting("shakeIntensity", 37f);
        require(Math.abs(((Number) call(stat(CONFIG, "badlion"), "hurtCameraScale")).floatValue() - 0.37f) < 0.001f, "Shake Intensity did not change the hurt camera");
        setting("shakeIntensity", 100f);
        Object alias = call(displayed, "getProp", "removeHitDelay");
        require(alias != null, "The original Remove Hit Delay setting is missing");
        call(alias, "setAs", !noHit);
        require((Boolean) stat("io.github.arcadiumclient.runtime.client.features.NoHitDelayConfig", "getEnabled") == !noHit, "Remove Hit Delay does not control the existing shared setting");
        call(alias, "setAs", noHit);
        exercised.add("shakeIntensity"); exercised.add("removeHitDelay");
        RESULT.put("settingsReachLiveState", exercised);
        RESULT.put("independentNativePreferences", true);
        call(config, "save");
        Path file = ((Path) call(stat(MANAGER, "active"), "getFolder")).resolve("arcadium-animations.json");
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for (String flag : FLAGS) require(saved.has(flag), "The saved animation profile omitted " + flag);
        require(saved.get("mode").getAsInt() == 1 && saved.get("shakeIntensity").getAsFloat() == 100f, "Badlion mode did not persist");
        RESULT.put("allSettingsPersisted", true);
    }

    private static void profileIsolation(Minecraft mc) throws Exception {
        setting("swordSwing", false);
        call(call(nativeTree, "getProp", "itemOffsetX"), "setAs", 0.27f);
        Map<String, Object> customized = snapshot(nativeTree);
        call(nativeConfig, "save"); call(config, "save");
        createdProfile = "Animations035-" + Long.toUnsignedString(System.nanoTime(), 36);
        Path generated = mc.gameDir.toPath().resolve("profiles").resolve(createdProfile).toAbsolutePath().normalize();
        require(generated.startsWith(mc.gameDir.toPath().toAbsolutePath().normalize()) && !Files.exists(generated), "The animation profile fixture already exists");
        stat(MANAGER, "createProfile", createdProfile);
        require(field(config, "mode").equals(0), "A fresh profile inherited the Badlion mode");
        Object freshNative = call(nativeConfig, "getTree");
        require(!snapshot(freshNative).equals(customized), "A fresh profile inherited custom Overflow values");
        setting("mode", 1);
        setting("bowAnimation", false);
        stat(MANAGER, "openProfile", profile);
        ownTree = call(config, "getTree"); nativeTree = call(nativeConfig, "getTree");
        require(field(config, "mode").equals(1) && Boolean.FALSE.equals(field(config, "swordSwing")), "Returning to the original profile lost its Badlion settings");
        require(snapshot(nativeTree).equals(customized), "Returning to the original profile lost its Overflow settings");
        require(field(nativeConfig, "items") != originalItems, "The saved Badlion mode was not reactivated after a profile change");
        stat(MANAGER, "deleteProfile", createdProfile);
        createdProfile = null;
        setting("mode", 0);
        require(field(nativeConfig, "items") == originalItems, "Overflow mode did not restore the native category identity");
        restoreProperties(nativeTree, OVERFLOW);
        setting("swordSwing", OWN.get("swordSwing"));
        setting("mode", 1);
        RESULT.put("profileSwitchPreservesBothModes", true);
    }

    private static void selectDraw(Minecraft mc) throws Exception {
        SCALES.clear();
        DRAW_SOURCES.clear();
        LAYER_CALLS.clear();
        ITEM_CALLS.clear();
        capturedSubmissions = worldPasses = 0;
        bowReferencePending = drawStage == 6;
        if (drawStage == 6) { bowReference = null; setting("enabled", false); }
        setting("mode", drawStage == 0 || drawStage == 5 ? 0 : 1);
        setting("leftHanded", drawStage == 4);
        setting("noSmallArms", drawStage == 3);
        setting("hitBox", drawStage != 3);
        setting("noShake", drawStage == 6);
        setting("shakeIntensity", drawStage == 4 || drawStage == 7 ? 37f : 100f);
        setting("removeTitles", drawStage == 6);
        setting("movableTitles", drawStage == 4);
        setting("titleX", 0.1f);
        setting("titleY", -0.15f);
        mc.getEntityRenderDispatcher().setRenderHitboxes(drawStage == 2 || drawStage == 3);
        mc.player.damagedTimer = drawStage == 4 || drawStage == 6 || drawStage == 7 ? 7 : 0;
        mc.player.damagedTime = 10;
        mc.player.damagedSwingDir = 60f;
        if (drawStage == 4) mc.gui.setTitles("Animations QA", "", 0, 80, 0);
        mc.options.perspective = drawStage == 2 || drawStage == 3 || drawStage == 5 ? 1 : 0;
        mc.player.clearItemInUse();
        mc.player.inventory.items[slot] = drawStage == 6 ? new ItemStack(Items.BOW) : drawStage == 7 ? new ItemStack(Items.APPLE)
                : drawStage == 0 || drawStage == 1 || drawStage == 4 ? new ItemStack(Items.DIAMOND_SWORD) : new ItemStack(Blocks.STONE);
        if (peer == null) {
            require(mc.world == fixtureWorld && fixtureWorld.getEntity(PEER_ID) == null, "The owned animation peer ID is already in use");
            peer = new RemoteClientPlayerEntity(mc.world, new GameProfile(new UUID(0, 2035), "AnimationsPeer")) {
                @Override public String getModelType() { return "slim"; }
                @Override public Identifier getSkinTextureLocation() { return new Identifier("textures/entity/alex.png"); }
                @Override public Identifier getCapeTextureLocation() { return null; }
                @Override public boolean isSpectator() { return false; }
            };
            peer.inventory.items[0] = new ItemStack(Blocks.STONE);
            peer.inventory.selectedSlot = 0;
            peer.setPositionAndAngles(mc.player.x - 1.4, mc.player.y, mc.player.z, mc.player.yaw, 0);
            peer.prevX = peer.lastX = peer.x;
            peer.prevY = peer.lastY = peer.y;
            peer.prevZ = peer.lastZ = peer.z;
            fixtureWorld.forceEntity(PEER_ID, peer);
            require(fixtureWorld.getEntity(PEER_ID) == peer, "The animation peer was not registered in its client world");
        }
        if (drawStage == 2 || drawStage == 3) {
            require(((PlayerRendererAnimationProbeAccess) mc.getEntityRenderDispatcher().getRenderer(peer)).arcadium$thinArms() == (drawStage != 3), "No Small Arms did not select the correct live player model");
        }
        for (int i = 0; i < 4; i++) mc.gameRenderer.itemInHandRenderer.tick();
        if (drawStage == 6 || drawStage == 7) mc.player.setItemInUse(mc.player.inventory.items[slot], drawStage == 6 ? 71980 : 20);
        if (drawStage == 8) {
            mc.openScreen(new SurvivalInventoryScreen(mc.player));
            if (mc.screen instanceof CreativeInventoryScreen creative) ((CreativeProbeAccess) creative).arcadium$selectTab(CreativeModeTab.INVENTORY);
        }
        handChecks = 0;
        pendingDraw = true;
    }

    public static void afterFrame() {
        if (!begun || done || restored || !pendingDraw) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            if (drawStage == 8) {
                require(mc.screen instanceof SurvivalInventoryScreen || mc.screen instanceof CreativeInventoryScreen, "The natural inventory player preview did not open");
                require(Boolean.FALSE.equals(stat("io.github.arcadiumclient.runtime.client.features.BadlionAnimationContext", "inInventory")), "The inventory preview context leaked into world rendering");
            } else if (mc.options.perspective == 0) {
                if (handChecks == 0) return;
            } else {
                if (!SCALES.containsKey("THIRD_PERSON") || !SCALES.containsKey("peer")) return;
            }
            pendingDraw = false;
        } catch (Throwable failure) { renderFailure = failure; }
    }

    public static void beforeHandDraw() {
        if (!begun || done || restored || stage != 2) return;
        winding = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        handDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        handMatrix = modelMatrix();
    }

    public static void enterWorldDraw() { worldDrawDepth++; if (begun && stage == 2) worldPasses++; }
    public static void exitWorldDraw() { worldDrawDepth = Math.max(0, worldDrawDepth - 1); }

    public static void enterHeldLayer(LivingEntity entity) {
        HELD_LAYERS.push(entity);
        if (begun && !done && !restored && stage == 2 && worldDrawDepth > 0) {
            String who = entity == Minecraft.getInstance().player ? "local" : entity == peer ? "peer" : "other";
            LAYER_CALLS.merge(who, 1, Integer::sum);
        }
    }

    public static void exitHeldLayer() { if (!HELD_LAYERS.isEmpty()) HELD_LAYERS.pop(); }

    public static void afterHandDraw() {
        if (!begun || done || restored || stage != 2 || handMatrix == null) return;
        try {
            require(GL11.glGetInteger(GL11.GL_FRONT_FACE) == winding, "Left Handed leaked the front-face winding");
            require(GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH) == handDepth, "An animation leaked a modelview matrix");
            float[] after = modelMatrix();
            for (int i = 0; i < 16; i++) require(Math.abs(after[i] - handMatrix[i]) < 0.0001f, "An animation leaked a modelview transform");
            handChecks++;
        } catch (Throwable failure) { renderFailure = failure; }
        handMatrix = null;
    }

    private static float[] modelMatrix() {
        FloatBuffer values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, values);
        float[] result = new float[16];
        values.get(result);
        return result;
    }

    public static void probeHurtCamera(float tickDelta, Operation<Void> original) {
        if (!begun || done || restored || stage != 2 || Minecraft.getInstance().player.damagedTimer <= 0
                || GL11.glGetInteger(GL11.GL_MATRIX_MODE) != GL11.GL_MODELVIEW) {
            original.call(tickDelta);
            return;
        }
        Object active;
        float scale;
        try {
            active = stat(CONFIG, "badlion");
            if (active == null) { original.call(tickDelta); return; }
            scale = ((Number) call(active, "hurtCameraScale")).floatValue();
            if (runtimeStateField == null) runtimeStateField = Visual033Qa.findField(config.getClass(), "runtimeState");
            if (lastHurtState != active) {
                var components = active.getClass().getRecordComponents();
                Object[] values = new Object[components.length];
                Class<?>[] types = new Class<?>[components.length];
                for (int i = 0; i < components.length; i++) {
                    types[i] = components[i].getType();
                    values[i] = components[i].getName().equals("noShake") ? false
                            : components[i].getName().equals("shakeIntensity") ? 100f : components[i].getAccessor().invoke(active);
                }
                baselineHurtState = active.getClass().getDeclaredConstructor(types).newInstance(values);
                lastHurtState = active;
            }
        } catch (Throwable failure) {
            renderFailure = failure;
            original.call(tickDelta);
            return;
        }
        double baseline = Double.NaN;
        float[] beforeBaseline = modelMatrix();
        GlStateManager.pushMatrix();
        try {
            runtimeStateField.set(null, baselineHurtState);
            original.call(tickDelta);
            baseline = rotationAngle(beforeBaseline, modelMatrix());
        } catch (Throwable failure) { renderFailure = failure; }
        finally {
            try { runtimeStateField.set(null, active); }
            catch (Throwable failure) { renderFailure = failure; }
            GlStateManager.popMatrix();
        }
        float[] beforeSelected = modelMatrix();
        original.call(tickDelta);
        if (!Double.isFinite(baseline)) return;
        try {
            double actual = rotationAngle(beforeSelected, modelMatrix());
            double expected = baseline * scale;
            require(Math.abs(actual - expected) < 0.12, "The live hurt camera did not scale its bundled baseline: " + actual + " vs " + expected + " (baseline " + baseline + ")");
            hurtChecks++;
            if (baseline > 0.4) HURT_SAMPLES.put(scale == 0f ? "noShake" : "intensity" + Math.round(scale * 100),
                    Map.of("baselineScale", 1f, "baselineDegrees", baseline, "selectedScale", scale, "actualDegrees", actual, "expectedDegrees", expected,
                            "samePartialTick", tickDelta, "bundledSettingsUnchanged", true));
        } catch (Throwable failure) { renderFailure = failure; }
    }

    private static double rotationAngle(float[] before, float[] after) {
        double trace = 0;
        for (int column = 0; column < 3; column++) {
            double dot = 0, beforeLength = 0, afterLength = 0;
            for (int row = 0; row < 3; row++) {
                int i = column * 4 + row;
                dot += before[i] * after[i];
                beforeLength += before[i] * before[i];
                afterLength += after[i] * after[i];
            }
            trace += dot / Math.sqrt(beforeLength * afterLength);
        }
        return Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2))) * 180 / Math.PI;
    }

    public static void recordHeldItem(ItemStack stack, BakedModel model, ModelTransformations.Type transform) {
        if (!begun || done || restored || stage != 2 || stack == null || Minecraft.getInstance().screen != null) return;
        ITEM_CALLS.merge(transform.toString() + ":world=" + (worldDrawDepth > 0), 1, Integer::sum);
        if (worldDrawDepth == 0 && handMatrix == null) return;
        if (transform != ModelTransformations.Type.FIRST_PERSON && transform != ModelTransformations.Type.THIRD_PERSON) return;
        boolean local = HELD_LAYERS.peek() == Minecraft.getInstance().player || handMatrix != null;
        boolean remote = peer != null && HELD_LAYERS.peek() == peer;
        if (!local && !remote) return;
        ItemStack expected = remote ? peer.getDisplayItemInHand() : Minecraft.getInstance().player.inventory.items[slot];
        if (!sameItem(stack, expected)) return;
        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        List<Float> scales = new ArrayList<>();
        for (int column = 0; column < 3; column++) {
            int base = column * 4;
            float x = matrix.get(base), y = matrix.get(base + 1), z = matrix.get(base + 2);
            float scale = (float) Math.sqrt(x * x + y * y + z * z);
            scales.add(scale);
        }
        String key = remote ? "peer" : transform == ModelTransformations.Type.FIRST_PERSON ? "FIRST_PERSON" : "THIRD_PERSON";
        if (!"argentum-final-submit".equals(DRAW_SOURCES.get(key))) storeScales(key, scales, "vanilla-baked-draw");
    }

    public static void recordCapturedHeldItem(ItemStack stack, Object matrix) {
        if (!begun || done || restored || stage != 2 || worldDrawDepth == 0 || stack == null || Minecraft.getInstance().screen != null) return;
        LivingEntity owner = HELD_LAYERS.peek();
        boolean local = owner == Minecraft.getInstance().player;
        boolean remote = peer != null && owner == peer;
        if (!local && !remote) return;
        ItemStack expected = owner.getDisplayItemInHand();
        if (!sameItem(stack, expected)) return;
        try {
            List<Float> scales = new ArrayList<>();
            for (int column = 0; column < 3; column++) {
                float sum = 0;
                for (int row = 0; row < 3; row++) {
                    float value = ((Number) matrix.getClass().getMethod("m" + column + row).invoke(matrix)).floatValue();
                    sum += value * value;
                }
                scales.add((float) Math.sqrt(sum));
            }
            capturedSubmissions++;
            storeScales(remote ? "peer" : "THIRD_PERSON", scales, "argentum-final-submit");
        } catch (Throwable failure) { renderFailure = failure; }
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        return first != null && second != null && first.getItem() == second.getItem() && first.getMetadata() == second.getMetadata();
    }

    private static void storeScales(String key, List<Float> scales, String source) {
        boolean bow = drawStage == 6 && key.equals("FIRST_PERSON");
        for (int i = 0; i < scales.size(); i++) {
            float scale = scales.get(i);
            if (!Float.isFinite(scale) || scale < 0.01f || (!bow && scale > 1.5f))
                renderFailure = new IllegalStateException("Held item scale escaped its expected range: " + source + " " + key + " " + scales);
            if (bow && !bowReferencePending && bowReference != null) {
                float ratio = scale / bowReference.get(i);
                // Legacy item poses rescale by 1.5; bow nocking can add 20% stretch.
                if (!Float.isFinite(ratio) || ratio < 0.25f || ratio > 1.5f * 1.2f + 0.05f)
                    renderFailure = new IllegalStateException("The animated bow scale escaped its measured reference: " + scales + " vs " + bowReference);
            }
        }
        SCALES.put(key, scales);
        DRAW_SOURCES.put(key, source);
    }

    private static Map<String, Object> diagnostics(Minecraft mc) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stage", stage); result.put("drawStage", drawStage); result.put("pending", pendingDraw);
        result.put("perspective", mc.options.perspective); result.put("worldDepth", worldDrawDepth); result.put("worldPasses", worldPasses);
        result.put("scales", new LinkedHashMap<>(SCALES)); result.put("sources", new LinkedHashMap<>(DRAW_SOURCES));
        result.put("layerCalls", new LinkedHashMap<>(LAYER_CALLS)); result.put("itemCalls", new LinkedHashMap<>(ITEM_CALLS));
        result.put("captureSubmissions", capturedSubmissions);
        result.put("bowReferencePending", bowReferencePending); result.put("bowReference", String.valueOf(bowReference));
        result.put("playerPose", List.of(mc.player.x, mc.player.y, mc.player.z, mc.player.yaw, mc.player.pitch));
        result.put("playerDisplayStack", String.valueOf(mc.player.getDisplayItemInHand()));
        result.put("selectedStack", String.valueOf(mc.player.inventory.items[slot]));
        result.put("playerInvisible", mc.player.isInvisible()); result.put("cameraIsPlayer", mc.getCamera() == mc.player);
        if (peer != null) {
            result.put("peerPose", List.of(peer.x, peer.y, peer.z));
            result.put("peerDisplayStack", String.valueOf(peer.getDisplayItemInHand()));
            result.put("peerInvisible", peer.isInvisible());
            result.put("peerInWorld", mc.world.getEntity(peer.getNetworkId()) == peer);
        }
        return result;
    }

    private static void restore(Minecraft mc) throws Exception {
        if (restored) return;
        if (!profile.equals(stat(MANAGER, "activeProfile"))) stat(MANAGER, "openProfile", profile);
        if (createdProfile != null) { stat(MANAGER, "deleteProfile", createdProfile); createdProfile = null; }
        ownTree = call(config, "getTree"); nativeTree = call(nativeConfig, "getTree");
        restoreProperties(ownTree, OWN);
        restoreProperties(nativeTree, OVERFLOW);
        stat("io.github.arcadiumclient.runtime.client.features.NoHitDelayConfig", "setEnabled", noHit);
        call(config, "save"); call(nativeConfig, "save");
        for (Map.Entry<Path, byte[]> entry : FILES.entrySet()) {
            if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
            else Files.write(entry.getKey(), entry.getValue());
        }
        mc.player.inventory.selectedSlot = slot;
        mc.player.inventory.items[slot] = held;
        for (int i = 0; i < 4; i++) mc.gameRenderer.itemInHandRenderer.tick();
        if (using == null) mc.player.clearItemInUse(); else mc.player.setItemInUse(using, useTimer);
        mc.player.fallDistance = fallDistance;
        mc.player.damagedTimer = damagedTimer;
        mc.player.damagedTime = damagedTime;
        mc.player.damagedSwingDir = damagedDir;
        mc.options.perspective = view;
        mc.options.hideGui = hideGui;
        mc.getEntityRenderDispatcher().setRenderHitboxes(renderHitboxes);
        if (peer != null) {
            if (fixtureWorld.getEntity(PEER_ID) == peer) fixtureWorld.removeEntity(PEER_ID);
            else fixtureWorld.removeEntity(peer);
            peer = null;
        }
        titles.restore((GameGuiAnimationProbeAccess) mc.gui);
        call(shell, "setSearchQuery", search);
        mc.openScreen(screen);
        CreativeProbeAccess.arcadium$selectedTab(creativeTab);
        restored = true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> snapshot(Object tree) throws Exception { return new LinkedHashMap<>((Map<String, Object>) call(tree, "unpack")); }
    private static void setting(String name, Object value) throws Exception { call(call(call(config, "getTree"), "getProp", name), "setAs", value); }
    private static void restoreProperties(Object tree, Map<String, Object> values) throws Exception {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object property = call(tree, "getProp", entry.getKey());
            if (property != null) call(property, "setAs", entry.getValue());
        }
    }
    private static void open(String category) throws Exception {
        Object route = Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute")
                .getConstructor(String.class, String.class).newInstance("arcadium-animations.json", category);
        stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", route);
    }
    private static void capture(Minecraft mc, String name) { Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget()); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private record Titles(int time, String title, String subtitle, int fadeIn, int duration, int fadeOut) {
        static Titles capture(GameGuiAnimationProbeAccess gui) {
            return new Titles(gui.arcadium$titleTime(), gui.arcadium$title(), gui.arcadium$subtitle(),
                    gui.arcadium$titleFadeInTime(), gui.arcadium$titleDuration(), gui.arcadium$titleFadeOutTime());
        }
        void restore(GameGuiAnimationProbeAccess gui) {
            gui.arcadium$titleTime(time);
            gui.arcadium$title(title);
            gui.arcadium$subtitle(subtitle);
            gui.arcadium$titleFadeInTime(fadeIn);
            gui.arcadium$titleDuration(duration);
            gui.arcadium$titleFadeOutTime(fadeOut);
        }
    }
}
