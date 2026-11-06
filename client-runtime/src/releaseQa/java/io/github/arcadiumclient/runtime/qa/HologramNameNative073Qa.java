// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import io.github.arcadiumclient.runtime.mixin.client.access.LivingPipelineAccess;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.item.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.WorldGeneratorType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class HologramNameNative073Qa {
    private static final int WIDTH = 960, HEIGHT = 540, COUNT = 12;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> cases = new ArrayList<>();
    private static final List<Map<String, Object>> diagnosticPairs = new ArrayList<>();
    private static final List<Map<String, Object>> callbackInputs = new ArrayList<>();
    private static int ticks, phase, wait, framebuffer, color, depth, begins, ends, displays, levelHeads, customLayers;
    private static int currentChange, imageSerial;
    private static boolean done, verifying, nested, nestedDone, throwBegin, nestedRestored;
    private static EntityRenderDispatcher dispatcher;
    private static ArmorStandEntity child;
    private static Object batch;
    private static Method beginBatch, endBatch;

    private HologramNameNative073Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.hologramNames073.output") != null; }
    public static void noteBegin(Entity entity, String label) {
        if (!verifying) return;
        begins++;
        if (currentChange == 3) callbackInputs.add(Map.of("ordinal", begins, "label", label, "ownerActive", HologramNamePreparation.active(entity), "modelView", matrix(GL11.GL_MODELVIEW_MATRIX), "projection", matrix(GL11.GL_PROJECTION_MATRIX)));
        if (throwBegin) { throwBegin = false; throw new IllegalStateException("hologram073 deliberate native callback"); }
        if (nested && !nestedDone) {
            nestedDone = true; boolean active = HologramNamePreparation.active(entity);
            require(dispatcher.render(child, 0, 2.5, 0, 0, 0, false), "Nested original callback was rejected");
            nestedRestored = HologramNamePreparation.active(entity) == active && !HologramNamePreparation.active(child);
        }
    }
    public static void noteEnd() { if (verifying) ends++; }
    public static void noteDisplay() { if (verifying) displays++; }
    public static void noteLevelHead() { if (verifying) levelHeads++; }

    public static void tick() {
        if (done) return; Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Requires the owned synthetic offline fixture");
            require(++ticks < 1800, "Hologram name QA timed out in phase " + phase); wait++;
            if (phase == 0 && ticks > 55 && mc.screen != null) {
                ClientPreferences.setSetupComplete(true); mc.options.pauseOnUnfocus = false; mc.options.vsync = false; Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                mc.startGame("arcadium-hologram-names-073", "Hologram names QA", new WorldSettings(73073, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 75) {
                mc.openScreen(null); verify(); finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private record Image(byte[] rgba, byte[] depth, byte[] depthUnsigned, List<Integer> state, long accelerated, long batchTyped, long layoutTyped,
                         int begins, int ends, int displays, int levelHeads, int customLayers, Map<String, Object> input) { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void verify() throws Exception {
        Minecraft mc = Minecraft.getInstance(); dispatcher = mc.getEntityRenderDispatcher();
        boolean oldFast = BetaOptimizationsConfig.getFastInvisibleHolograms(), oldHooks = BetaOptimizationsConfig.getReduceRenderHookAllocations(), oldControl = RenderHookAllocationControl.enabled;
        boolean oldGpu = BetaOptimizationsConfig.getGpuNameTags(), oldRetained = BetaOptimizationsConfig.getRetainedNameTagScenes(), oldCompact = BetaOptimizationsConfig.getCompactNameTagPreparation();
        int oldMinimum = BetaOptimizationsConfig.getMinimumGpuLabels(); boolean oldNick = NickHiderConfig.getEnabled(); String oldNickname = NickHiderConfig.getNickname();
        boolean oldNoRender = NoRenderConfig.getEnabled(), oldHideNames = NoRenderConfig.getNameTags();
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING), previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        IntBuffer viewport = BufferUtils.createIntBuffer(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); float yaw = dispatcher.cameraYaw, pitch = dispatcher.cameraPitch;
        List<ArmorStandEntity> stands = fixtures(); child = stand("§eNested native name", false);
        ArmorStandRenderer renderer = (ArmorStandRenderer) (Object) dispatcher.getRenderer(stands.getFirst()); List layers = ((LivingPipelineAccess) renderer).arcadium$layers();
        List originalLayers = new ArrayList(layers);
        report.put("cases", cases); report.put("diagnosticPairs", diagnosticPairs);
        try {
            require(HologramNamePreparation.preparedSites == 2, "Both production name sites must be prepared: " + HologramNamePreparation.preparedSites);
            NoRenderConfig.setEnabled(false); NoRender.refresh(); NickHiderConfig.setEnabled(false);
            BetaOptimizationsConfig.setRetainedNameTagScenes(false); BetaOptimizationsConfig.setCompactNameTagPreparation(false); BetaOptimizationsConfig.setMinimumGpuLabels(1);
            createFramebuffer(); Object worldRenderer = Class.forName("dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer").getMethod("instanceNullable").invoke(null);
            require(worldRenderer != null, "Argentum world renderer is unavailable"); batch = worldRenderer.getClass().getMethod("getNameTagBatch").invoke(worldRenderer);
            beginBatch = batch.getClass().getMethod("begin", Matrix4fc.class); endBatch = batch.getClass().getMethod("end");
            Image originalBody = image(stands, false, false, false, false, 0), originalName = image(stands, true, false, false, false, 0), typedName = image(stands, true, true, false, false, 0);
            compare(originalBody, originalName, "original living entry", COUNT); compare(originalName, typedName, "native text", COUNT);
            require(visible(typedName) > 200, "Native reference must contain visible name pixels"); write("native-off.png", originalName); write("native-on.png", typedName);
            for (boolean gpu : new boolean[]{false, true}) {
                image(stands, true, false, true, gpu, 0);
                long packets = GpuNameTags.packetsDrawn; Image before = image(stands, true, false, true, gpu, 0), after = image(stands, true, true, true, gpu, 0);
                compare(before, after, gpu ? "GPU batched names" : "Argentum batched names", COUNT);
                if (gpu) require(GpuNameTags.packetsDrawn > packets, "GPU packet path was not used");
                write(gpu ? "gpu-off.png" : "batch-off.png", before); write(gpu ? "gpu-on.png" : "batch-on.png", after);
            }
            for (int change = 1; change <= 3; change++) {
                Image before = image(stands, true, false, false, false, change), after = image(stands, true, true, false, false, change);
                boolean controlsExact = change != 3 || diagnose(stands, before, after);
                compare(before, after, "same-entity current name/camera/sneak " + change, COUNT);
                require(controlsExact, "Repeated/order-reversed change3 controls were not exact; inspect diagnosticPairs");
                require(!Arrays.equals(originalName.rgba, before.rgba), "Changed current-frame scene still matched the old frame");
            }
            NickHiderConfig.setEnabled(true); NickHiderConfig.setNickname("Mask073");
            compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "live NickHider", COUNT);
            NickHiderConfig.setNickname("Changed073");
            compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "changed nickname", COUNT); NickHiderConfig.setEnabled(false);
            NoRenderConfig.setEnabled(true); NoRenderConfig.setNameTags(true); NoRender.refresh();
            Image hidden = image(stands, true, false, false, false, 0), hiddenTyped = image(stands, true, true, false, false, 0); compare(hidden, hiddenTyped, "native NoRender names hidden", COUNT);
            require(visible(hidden) == 0, "NoRender did not suppress every fixture label"); NoRenderConfig.setEnabled(false); NoRender.refresh();
            ArmorStandEntity first = stands.getFirst();
            for (int slot = 0; slot < 5; slot++) {
                first.setEquipment(slot, new ItemStack(Items.DIAMOND)); compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "immediate equipment slot " + slot, COUNT - 1); first.setEquipment(slot, null);
            }
            first.setInvisible(false); compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "visible stand body", COUNT - 1); first.setInvisible(true);
            first.setCustomNameVisible(false); compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "hidden name admission", COUNT - 1); first.setCustomNameVisible(true);
            first.damagedTimer = 1; compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "hurt native overlay", COUNT - 1); first.damagedTimer = 0;
            first.deathTicks = 1; compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "death native state", COUNT - 1); first.deathTicks = 0;
            EntityRenderLayer<ArmorStandEntity> custom = new EntityRenderLayer<>() {
                @Override public void render(ArmorStandEntity entity, float a, float b, float c, float d, float e, float f, float g) { if (verifying) customLayers++; }
                @Override public boolean colorsWhenDamaged() { return false; }
            };
            layers.add(custom); Image customOff = image(stands, true, false, false, false, 0), customOn = image(stands, true, true, false, false, 0); compare(customOff, customOn, "unknown layer native callback", 0);
            require(customOn.customLayers == COUNT && customOff.customLayers == COUNT, "Unknown layers were skipped"); layers.remove(custom);
            require(!FastInvisibleHolograms.render(new ArmorStandRenderer(dispatcher) { }, first, 0, 0, 0, 0, 0), "Unknown renderer entered acceleration");
            nested = true; compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "nested real layout callback", COUNT + 1); nested = false;
            require(nestedRestored, "Nested native callback did not restore its owner");
            for (boolean hooks : new boolean[]{false, true}) {
                throwBegin = true; Throwable thrown = null;
                try { image(stands, true, hooks, true, false, 0); } catch (Throwable failure) { thrown = failure; }
                finally { throwBegin = false; }
                require(thrown != null && stack(thrown).contains("hologram073 deliberate native callback"), "Original callback failure was swallowed"); require(owner() == null, "A throwing name callback retained its entity");
                require(Class.forName("dev.rdh.argentum.impl.render.entity.NameTagBatch").getMethod("active").invoke(null) == null, "Throwing callback leaked an active batch");
            }
            compare(image(stands, true, false, false, false, 0), image(stands, true, true, false, false, 0), "recovered after native callback failure", COUNT);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after exact native name proof");
            report.put("preparedSites", HologramNamePreparation.preparedSites); report.put("batchTypedCalls", HologramNamePreparation.batchTypedCalls); report.put("layoutTypedCalls", HologramNamePreparation.layoutTypedCalls);
            report.put("cases", cases); report.put("exactRgbaAndDepth", true); report.put("nativeCallbacksAndCallerStateEquivalent", true); report.put("scopeOwnerAfter", owner()); report.put("exceptionAndNestedCleanup", true);
            report.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); report.put("glVersion", GL11.glGetString(GL11.GL_VERSION)); report.put("hologramStatus", FastInvisibleHolograms.status());
        } finally {
            verifying = false; nested = throwBegin = false; layers.clear(); layers.addAll(originalLayers);
            if (batch != null && endBatch != null && batch.getClass().getMethod("active").invoke(null) == batch) endBatch.invoke(batch);
            BetaOptimizationsConfig.setFastInvisibleHolograms(oldFast); BetaOptimizationsConfig.setReduceRenderHookAllocations(oldHooks); RenderHookAllocationControl.enabled = oldControl;
            BetaOptimizationsConfig.setGpuNameTags(oldGpu); BetaOptimizationsConfig.setRetainedNameTagScenes(oldRetained); BetaOptimizationsConfig.setCompactNameTagPreparation(oldCompact); BetaOptimizationsConfig.setMinimumGpuLabels(oldMinimum);
            NickHiderConfig.setEnabled(oldNick); NickHiderConfig.setNickname(oldNickname); NoRenderConfig.setEnabled(oldNoRender); NoRenderConfig.setNameTags(oldHideNames); NoRender.refresh();
            dispatcher.cameraYaw = yaw; dispatcher.cameraPitch = pitch; dispatcher.setSolidRender(false);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo); if (depth != 0) GL30.glDeleteRenderbuffers(depth); if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer); if (color != 0) GlStateManager.deleteTextures(color);
            framebuffer = color = depth = 0; GL20.glUseProgram(previousProgram); GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            report.put("settingsRestored", BetaOptimizationsConfig.getFastInvisibleHolograms() == oldFast && BetaOptimizationsConfig.getReduceRenderHookAllocations() == oldHooks && RenderHookAllocationControl.enabled == oldControl);
        }
    }

    private static List<ArmorStandEntity> fixtures() {
        List<ArmorStandEntity> result = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) { ArmorStandEntity entity = stand("§bArcadiumTest " + i + " §f250✫", i % 3 == 0); if (i == 1) entity.width = entity.height = 0; result.add(entity); }
        return result;
    }
    private static ArmorStandEntity stand(String name, boolean sneaking) { Minecraft mc = Minecraft.getInstance(); ArmorStandEntity result = new ArmorStandEntity(mc.world, mc.player.x, mc.player.y, mc.player.z + 8); result.setInvisible(true); result.setCustomNameVisible(true); result.setCustomName(name); result.setSneaking(sneaking); return result; }

    private static Image image(List<ArmorStandEntity> stands, boolean fast, boolean hooks, boolean batched, boolean gpu, int change) throws Exception {
        currentChange = change; callbackInputs.clear(); int serial = ++imageSerial;
        BetaOptimizationsConfig.setFastInvisibleHolograms(fast); BetaOptimizationsConfig.setReduceRenderHookAllocations(hooks); RenderHookAllocationControl.enabled = hooks; BetaOptimizationsConfig.setGpuNameTags(gpu);
        GL20.glUseProgram(0); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer); GlStateManager.viewport(0, 0, WIDTH, HEIGHT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.loadIdentity(); GlStateManager.ortho(-8, 8, -4.5, 4.5, -100, 100);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.loadIdentity(); GlStateManager.rotatef(180, 0, 1, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GlStateManager.colorMask(true, true, true, true); GlStateManager.clearColor(.15f, .18f, .22f, 1); GlStateManager.clearDepth(1); GlStateManager.depthMask(true); GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepthTest(); GlStateManager.depthFunc(GL11.GL_LEQUAL); GlStateManager.enableAlphaTest(); GlStateManager.alphaFunc(GL11.GL_GREATER, .1f); GlStateManager.disableLighting(); GlStateManager.disableFog(); GlStateManager.disableBlend(); GlStateManager.enableCull(); GlStateManager.disableRescaleNormal();
        Minecraft.getInstance().gameRenderer.enableLightMap(); GlStateManager.activeTexture(GLX.GL_TEXTURE1); GlStateManager.enableTexture(); GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240, 240); GlStateManager.activeTexture(GLX.GL_TEXTURE0); GlStateManager.enableTexture(); GlStateManager.color4f(1, 1, 1, 1);
        dispatcher.cameraYaw = change == 0 ? 0 : 17; dispatcher.cameraPitch = change == 0 ? 0 : -9;
        List<Integer> initialModelView = matrix(GL11.GL_MODELVIEW_MATRIX), initialProjection = matrix(GL11.GL_PROJECTION_MATRIX);
        List<Integer> initialDepthState = depthState();
        if (batched) beginBatch.invoke(batch, new Matrix4f().rotateY((float) Math.PI));
        long accelerated = FastInvisibleHolograms.accelerated, typedBatch = HologramNamePreparation.batchTypedCalls, typedLayout = HologramNamePreparation.layoutTypedCalls;
        begins = ends = displays = levelHeads = customLayers = 0; nestedDone = false; nestedRestored = !nested; verifying = true;
        try {
            for (int i = 0; i < stands.size(); i++) {
                ArmorStandEntity entity = stands.get(i); entity.setSneaking(change == 3 ? i % 2 == 0 : i % 3 == 0);
                entity.setCustomName((change == 0 ? "§bArcadiumTest " : "§aChanged073 ") + i + " §f250✫→ :3.7" + (change == 2 ? " 玩家" : ""));
                require(dispatcher.render(entity, -6 + i % 4 * 4 + change * .17, -3.5 + i / 4 * 2 + change * .09, change * .13, 0, 0, false), "Dispatcher rejected a synthetic fixture");
                require(owner() == null, "Name callback retained a current entity after dispatcher return");
            }
        } finally { verifying = false; if (batched) endBatch.invoke(batch); }
        ByteBuffer rgba = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4), z = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4), unsigned = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4); GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba); GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, z); GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, unsigned);
        byte[] pixels = new byte[rgba.capacity()], depths = new byte[z.capacity()], depthsUnsigned = new byte[unsigned.capacity()]; rgba.get(pixels); z.get(depths); unsigned.get(depthsUnsigned);
        List<Integer> state = List.of(GL11.glIsEnabled(GL11.GL_ALPHA_TEST) ? 1 : 0, GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0, GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL) ? 1 : 0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH), GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH), GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK), GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        Map<String, Object> input = new LinkedHashMap<>(); input.put("serial", serial); input.put("change", change); input.put("fast", fast); input.put("hooks", hooks); input.put("preparedSites", HologramNamePreparation.preparedSites); input.put("batched", batched); input.put("gpu", gpu); input.put("initialModelView", initialModelView); input.put("initialProjection", initialProjection); input.put("initialDepthState", initialDepthState); input.put("finalDepthState", depthState()); input.put("callbackInputs", List.copyOf(callbackInputs));
        return new Image(pixels, depths, depthsUnsigned, state, FastInvisibleHolograms.accelerated - accelerated, HologramNamePreparation.batchTypedCalls - typedBatch, HologramNamePreparation.layoutTypedCalls - typedLayout, begins, ends, displays, levelHeads, customLayers, input);
    }

    private static void compare(Image original, Image typed, String name, int expectedAccelerated) {
        Map<String, Object> row = summary(name, original, typed); cases.add(row);
        if (!Arrays.equals(original.rgba, typed.rgba) || !Arrays.equals(original.depth, typed.depth) || !Arrays.equals(original.depthUnsigned, typed.depthUnsigned) || !original.state.equals(typed.state)) dump(name, original, typed, row);
        require(Arrays.equals(original.rgba, typed.rgba), name + " RGBA bytes differ: " + differences(original.rgba, typed.rgba)); require(Arrays.equals(original.depth, typed.depth), name + " depth differs: " + differences(original.depth, typed.depth)); require(original.state.equals(typed.state), name + " caller GL state differs");
        require(Arrays.equals(original.depthUnsigned, typed.depthUnsigned), name + " integer depth differs: " + differences(original.depthUnsigned, typed.depthUnsigned));
        require(original.begins == typed.begins && original.ends == typed.ends && original.displays == typed.displays && original.levelHeads == typed.levelHeads && original.customLayers == typed.customLayers, name + " native callback counts differ");
        require(original.batchTyped == 0 && original.layoutTyped == 0, name + " OFF path used typed preparation");
        if (typed.accelerated == expectedAccelerated && typed.batchTyped != 0) require(typed.batchTyped == expectedAccelerated && typed.layoutTyped == expectedAccelerated, name + " typed site counts differ");
        else if (expectedAccelerated != 0 && !name.equals("original living entry")) require(typed.batchTyped == expectedAccelerated && typed.layoutTyped == expectedAccelerated, name + " typed paths did not activate");
        if (!name.equals("original living entry")) require(typed.accelerated == expectedAccelerated, name + " accelerated admission count differs: " + typed.accelerated);
        if (expectedAccelerated == 0) require(typed.batchTyped == 0 && typed.layoutTyped == 0, name + " unsupported scene used typed preparation");
        row.put("passed", true);
    }
    private static boolean diagnose(List<ArmorStandEntity> stands, Image off, Image on) throws Exception {
        Image offRepeat = image(stands, true, false, false, false, 3), onRepeat = image(stands, true, true, false, false, 3);
        Image offReverse = image(stands, true, false, false, false, 3), offReverseRepeat = image(stands, true, false, false, false, 3);
        Image onReverse = image(stands, true, true, false, false, 3), onReverseRepeat = image(stands, true, true, false, false, 3);
        boolean exact = diagnostic("change3 native OFF/OFF", off, offRepeat);
        exact &= diagnostic("change3 typed ON/ON", on, onRepeat);
        exact &= diagnostic("change3 ON then OFF reversal", onRepeat, offReverse);
        exact &= diagnostic("change3 repeated native after reversal", offReverse, offReverseRepeat);
        exact &= diagnostic("change3 OFF then ON reversal", offReverseRepeat, onReverse);
        exact &= diagnostic("change3 repeated typed after reversal", onReverse, onReverseRepeat);
        int prepared = HologramNamePreparation.preparedSites;
        try {
            HologramNamePreparation.preparedSites = 0;
            Image scopeOff = image(stands, true, true, false, false, 3), scopeOffRepeat = image(stands, true, true, false, false, 3);
            HologramNamePreparation.preparedSites = prepared;
            Image scopeOn = image(stands, true, true, false, false, 3), scopeOnRepeat = image(stands, true, true, false, false, 3);
            exact &= diagnostic("change3 isolated names native OFF/OFF, other hooks ON", scopeOff, scopeOffRepeat);
            exact &= diagnostic("change3 isolated names OFF/ON, other hooks ON", scopeOffRepeat, scopeOn);
            exact &= diagnostic("change3 isolated names typed ON/ON, other hooks ON", scopeOn, scopeOnRepeat);
        } finally { HologramNamePreparation.preparedSites = prepared; }
        return exact;
    }
    private static boolean diagnostic(String name, Image first, Image second) {
        Map<String, Object> row = summary(name, first, second); diagnosticPairs.add(row);
        boolean exact = Arrays.equals(first.rgba, second.rgba) && Arrays.equals(first.depth, second.depth) && Arrays.equals(first.depthUnsigned, second.depthUnsigned) && first.state.equals(second.state) && callbacksEqual(first, second);
        row.put("passed", exact); if (!exact) dump(name, first, second, row); return exact;
    }
    private static Map<String, Object> summary(String name, Image first, Image second) {
        Map<String, Object> row = new LinkedHashMap<>(); row.put("name", name); row.put("passed", false); row.put("rgbaBytes", first.rgba.length); row.put("depthBytes", first.depth.length); row.put("rgbaDifferentBytes", differences(first.rgba, second.rgba)); row.put("depthDifferentBytes", differences(first.depth, second.depth)); row.put("integerDepthDifferentBytes", differences(first.depthUnsigned, second.depthUnsigned)); row.put("callbacksEqual", callbacksEqual(first, second));
        row.put("visiblePixels", visible(second)); row.put("accelerated", second.accelerated); row.put("batchTyped", second.batchTyped); row.put("layoutTyped", second.layoutTyped); row.put("firstCallbacks", List.of(first.begins, first.ends, first.displays, first.levelHeads, first.customLayers)); row.put("secondCallbacks", List.of(second.begins, second.ends, second.displays, second.levelHeads, second.customLayers)); row.put("firstState", first.state); row.put("secondState", second.state); row.put("firstInput", first.input); row.put("secondInput", second.input); return row;
    }
    private static void dump(String name, Image first, Image second, Map<String, Object> row) {
        try {
            String prefix = String.format(java.util.Locale.ROOT, "difference-%02d-%02d", (int) first.input.get("serial"), (int) second.input.get("serial"));
            Files.write(output().resolveSibling(prefix + "-first.rgba"), first.rgba); Files.write(output().resolveSibling(prefix + "-second.rgba"), second.rgba);
            Files.write(output().resolveSibling(prefix + "-first.depth-f32"), first.depth); Files.write(output().resolveSibling(prefix + "-second.depth-f32"), second.depth); write(prefix + "-first.png", first); write(prefix + "-second.png", second);
            Files.write(output().resolveSibling(prefix + "-first.depth-u32"), first.depthUnsigned); Files.write(output().resolveSibling(prefix + "-second.depth-u32"), second.depthUnsigned);
            ByteBuffer firstDepth = ByteBuffer.wrap(first.depth).order(ByteOrder.nativeOrder()), secondDepth = ByteBuffer.wrap(second.depth).order(ByteOrder.nativeOrder());
            ByteBuffer firstUnsigned = ByteBuffer.wrap(first.depthUnsigned).order(ByteOrder.nativeOrder()), secondUnsigned = ByteBuffer.wrap(second.depthUnsigned).order(ByteOrder.nativeOrder());
            List<Map<String, Object>> pixels = new ArrayList<>(); int changedPixels = 0;
            for (int pixel = 0; pixel < WIDTH * HEIGHT; pixel++) {
                int at = pixel * 4, one = firstDepth.getInt(at), two = secondDepth.getInt(at); boolean rgba = false;
                for (int channel = 0; channel < 4; channel++) rgba |= first.rgba[at + channel] != second.rgba[at + channel];
                if (one == two && firstUnsigned.getInt(at) == secondUnsigned.getInt(at) && !rgba) continue; changedPixels++;
                if (pixels.size() < 128) { Map<String, Object> sample = new LinkedHashMap<>(); sample.put("x", pixel % WIDTH); sample.put("yGl", pixel / WIDTH); sample.put("yImage", HEIGHT - pixel / WIDTH - 1); sample.put("firstDepthBits", String.format("%08x", one)); sample.put("secondDepthBits", String.format("%08x", two)); sample.put("firstDepth", Float.toString(Float.intBitsToFloat(one))); sample.put("secondDepth", Float.toString(Float.intBitsToFloat(two))); sample.put("firstUnsignedDepthBits", String.format("%08x", firstUnsigned.getInt(at))); sample.put("secondUnsignedDepthBits", String.format("%08x", secondUnsigned.getInt(at))); sample.put("firstRgba", List.of(first.rgba[at] & 255, first.rgba[at + 1] & 255, first.rgba[at + 2] & 255, first.rgba[at + 3] & 255)); sample.put("secondRgba", List.of(second.rgba[at] & 255, second.rgba[at + 1] & 255, second.rgba[at + 2] & 255, second.rgba[at + 3] & 255)); pixels.add(sample); }
            }
            row.put("changedPixels", changedPixels); row.put("differenceSamples", pixels); row.put("rawDumpPrefix", prefix); row.put("depthByteOrder", ByteOrder.nativeOrder().toString());
            Files.writeString(output().resolveSibling(prefix + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(row));
        } catch (Exception error) { row.put("diagnosticWriteFailure", stack(error)); }
    }
    private static List<Integer> matrix(int kind) { FloatBuffer buffer = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(kind, buffer); List<Integer> result = new ArrayList<>(16); for (int i = 0; i < 16; i++) result.add(Float.floatToRawIntBits(buffer.get(i))); return result; }
    private static List<Integer> depthState() { FloatBuffer value = BufferUtils.createFloatBuffer(4); GL11.glGetFloat(GL11.GL_DEPTH_RANGE, value); int near = Float.floatToRawIntBits(value.get(0)), far = Float.floatToRawIntBits(value.get(1)); value.clear(); GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR, value); int factor = Float.floatToRawIntBits(value.get(0)); value.clear(); GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS, value); int units = Float.floatToRawIntBits(value.get(0)); return List.of(near, far, factor, units, GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL) ? 1 : 0, GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_LINE) ? 1 : 0, GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_POINT) ? 1 : 0, GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK)); }
    private static int differences(byte[] a, byte[] b) { int count = 0; for (int i = 0; i < a.length; i++) if (a[i] != b[i]) count++; return count; }
    private static boolean callbacksEqual(Image a, Image b) { return a.begins == b.begins && a.ends == b.ends && a.displays == b.displays && a.levelHeads == b.levelHeads && a.customLayers == b.customLayers; }
    private static int visible(Image image) { int count = 0; for (int i = 4; i < image.rgba.length; i += 4) if (image.rgba[i] != image.rgba[0] || image.rgba[i + 1] != image.rgba[1] || image.rgba[i + 2] != image.rgba[2]) count++; return count; }
    private static Object owner() throws Exception { Field field = HologramNamePreparation.class.getDeclaredField("CURRENT"); field.setAccessible(true); Object scope = field.get(null); Field current = scope.getClass().getDeclaredField("current"); current.setAccessible(true); return current.get(scope); }
    private static void createFramebuffer() {
        framebuffer = GL30.glGenFramebuffers(); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer); color = GL11.glGenTextures(); GlStateManager.bindTexture(color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, WIDTH, HEIGHT, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST); GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        depth = GL30.glGenRenderbuffers(); GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth); GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, WIDTH, HEIGHT); GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth); require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Native QA framebuffer is incomplete");
    }
    private static void write(String name, Image image) throws Exception { BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB); for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) { int i = ((HEIGHT - y - 1) * WIDTH + x) * 4; result.setRGB(x, y, (image.rgba[i + 3] & 255) << 24 | (image.rgba[i] & 255) << 16 | (image.rgba[i + 1] & 255) << 8 | image.rgba[i + 2] & 255); } ImageIO.write(result, "png", output().resolveSibling(name).toFile()); }
    private static String stack(Throwable failure) { java.io.StringWriter text = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(text)); return text.toString(); }
    private static Path output() { return Path.of(System.getProperty("arcadium.hologramNames073.output")); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void finish(Throwable failure) { done = true; report.put("cases", cases); report.put("diagnosticPairs", diagnosticPairs); report.put("preparedSites", HologramNamePreparation.preparedSites); report.put("batchTypedCalls", HologramNamePreparation.batchTypedCalls); report.put("layoutTypedCalls", HologramNamePreparation.layoutTypedCalls); report.put("passed", failure == null); report.put("syntheticOffline", true); report.put("livingOrEntityRendererQaInstrumentation", false); report.put("performanceClaim", false); if (failure != null) { report.put("error", stack(failure)); failure.printStackTrace(); } try { Files.createDirectories(output().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report)); } catch (Exception error) { error.printStackTrace(); } Minecraft.getInstance().stop(); }
}
