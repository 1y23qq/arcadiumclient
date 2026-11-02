// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl;
import io.github.arcadiumclient.runtime.mixin.client.access.LivingPipelineAccess;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.entity.layer.ArmorLayer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class LayerOperation066Qa {
    private static final int SIZE = 256;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Object> events = new ArrayList<>();
    private static int ticks, phase, wait, depth, sequence, calls;
    private static boolean done, armorEntered;
    private static Object owner, capture, config;
    private static Class<?> captureType;
    private static Constructor<?> captureConstructor;
    private static PlayerRenderer renderer;
    private static RemoteClientPlayerEntity player;
    private static Mode mode;
    private record Mode(String name, boolean capture, boolean ready, boolean finished, boolean armor, int throwing, boolean nested, float delta, boolean nativeLayers) { }
    private record Scene(byte[] pixels, List<Object> events, List<Object> tail, String thrown, int calls) { }

    private LayerOperation066Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.layerOperations066.output") != null; }

    public static void tick() {
        if (done) return; Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use the isolated synthetic account");
            require(++ticks < 1800, "Layer operation QA timed out at phase " + phase); wait++;
            if (phase == 0 && ticks > 65 && mc.screen != null) {
                ClientPreferences.setSetupComplete(true); mc.options.pauseOnUnfocus = false; mc.options.vsync = false;
                Display.setVSyncEnabled(false); org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                mc.startGame("arcadium-layer-066", "Layer Operation QA", new WorldSettings(66067, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 80) { mc.openScreen(null); report.putAll(verify()); finish(null); }
        } catch (Throwable failure) { finish(failure); }
    }

    private static Map<String, Object> verify() throws Exception {
        int sites = (int) RenderHookAllocationControl.class.getField("layerSites").get(null);
        require(sites == 1, "Expected one actual typed layer site, got " + sites);
        require(Arrays.stream(LivingEntityRenderer.class.getDeclaredMethods()).filter(method -> method.getName().equals("arcadium$typedLayerCapture")).count() == 1, "The loaded native renderer has no typed layer method");
        Minecraft mc = Minecraft.getInstance(); Object worldRenderer = Class.forName("dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer").getMethod("instanceNullable").invoke(null);
        require(worldRenderer != null, "Native Argentum world renderer is missing");
        owner = worldRenderer.getClass().getMethod("getEntityInstancing").invoke(worldRenderer);
        require(field(owner, "activeCapture") == null && (int) field(owner, "layerDepth") == 0, "A native capture scope is already active");
        captureType = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture"); captureConstructor = captureType.getDeclaredConstructor(owner.getClass()); captureConstructor.setAccessible(true);
        config = Class.forName("dev.rdh.argentum.extras.ArgentumExtras").getField("CONFIG").get(null);
        boolean armor = (boolean) field(config, "armor"), oldEnabled = RenderHookAllocationControl.enabled;
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer viewport = BufferUtils.createIntBuffer(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int target = GL30.glGenFramebuffers(), texture = GL11.glGenTextures();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        for (int matrix : new int[]{GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE}) { GL11.glMatrixMode(matrix); GL11.glPushMatrix(); }
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Layer QA framebuffer is incomplete");
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0); GL11.glViewport(0, 0, SIZE, SIZE); GL20.glUseProgram(0);
            player = new RemoteClientPlayerEntity(mc.world, new GameProfile(UUID.fromString("a3f11ad9-7869-3443-923e-f57284db7b7e"), "ArcadiumLayer"));
            List<Mode> modes = List.of(
                new Mode("no capture", false, false, false, true, 0, false, .125f, false),
                new Mode("supported armor capture", true, true, false, true, 0, false, .125f, false),
                new Mode("unready capture declines", true, false, false, true, 0, false, .125f, false),
                new Mode("finished capture declines", true, true, true, true, 0, false, .125f, false),
                new Mode("live armor filter", true, true, false, false, 0, false, .125f, false),
                new Mode("nested native layer calls", true, true, false, true, 0, true, .125f, false),
                new Mode("armor exception", true, true, false, true, 1, false, .125f, false),
                new Mode("unsupported layer exception", true, true, false, true, 2, false, .125f, false),
                new Mode("current animation values", true, true, false, true, 0, false, .875f, false),
                new Mode("original equipped player layers", false, false, false, true, 0, false, .375f, true));
            List<Map<String, Object>> checks = new ArrayList<>(); Scene stable = null;
            for (Mode fixture : modes) {
                Scene original = scene(false, fixture), typed = scene(true, fixture);
                require(original.events.equals(typed.events), fixture.name + " changed virtual layer calls, arguments or pass-state order");
                require(original.tail.equals(typed.tail), fixture.name + " changed final native capture/GL state");
                require(Objects.equals(original.thrown, typed.thrown), fixture.name + " changed exception propagation");
                require(Arrays.equals(original.pixels, typed.pixels), fixture.name + " changed native RGBA pixels");
                require(original.calls == typed.calls, fixture.name + " skipped a layer");
                if (fixture.throwing != 0) require(original.thrown != null, fixture.name + " did not exercise its native exception");
                else require(original.thrown == null, fixture.name + " threw unexpectedly: " + original.thrown);
                require(visible(original.pixels) > 20, fixture.name + " had no rendered reference");
                if (!fixture.nativeLayers) require(original.calls > 0, fixture.name + " did not call its virtual layers");
                if (fixture.name.equals("supported armor capture")) stable = original;
                if (fixture.name.equals("current animation values")) require(stable != null && !Arrays.equals(stable.pixels, original.pixels), "Live animation arguments did not change the native scene");
                checks.add(Map.of("mode", fixture.name, "virtualProbeCalls", original.calls, "stateEvents", original.events.size(), "visiblePixels", visible(original.pixels), "exactPixelsAndState", true));
            }
            report.put("cases", checks); report.put("actualTypedSites", sites); report.put("allocationProbe", allocations());
            report.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); report.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
            return report;
        } finally {
            capture = null; set(owner, "activeCapture", null); set(owner, "layerDepth", 0); set(config, "armor", armor); RenderHookAllocationControl.enabled = oldEnabled;
            for (int matrix : new int[]{GL11.GL_TEXTURE, GL11.GL_PROJECTION, GL11.GL_MODELVIEW}) { GL11.glMatrixMode(matrix); GL11.glPopMatrix(); }
            GL11.glMatrixMode(matrixMode); GL11.glPopAttrib(); GL20.glUseProgram(program); GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3)); GL30.glDeleteFramebuffers(target); GL11.glDeleteTextures(texture);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Scene scene(boolean fast, Mode fixture) throws Exception {
        mode = fixture; RenderHookAllocationControl.enabled = fast; set(owner, "activeCapture", null); set(owner, "layerDepth", 0); reset();
        set(config, "armor", fixture.armor); renderer = new PlayerRenderer(Minecraft.getInstance().getEntityRenderDispatcher());
        List layers = ((LivingPipelineAccess) renderer).arcadium$layers();
        if (!fixture.nativeLayers) { layers.clear(); layers.add(new ProbeLayer("before")); layers.add(new ProbeArmor(renderer)); layers.add(new ProbeLayer("after")); }
        else { player.setEquipment(4, new ItemStack(Items.DIAMOND_HELMET)); player.setEquipment(0, new ItemStack(Items.DIAMOND)); renderer.getModel().prepare(player, .3f, .4f, fixture.delta); renderer.getModel().setupAnimation(.3f, .4f, 41, 12, -7, .0625f, player); }
        capture = fixture.capture ? freshCapture(fixture.ready, fixture.finished) : null; set(owner, "activeCapture", capture);
        events.clear(); calls = sequence = depth = 0; armorEntered = false; String thrown = null;
        try { render(); } catch (LayerFailure failure) { thrown = failure.getClass().getName() + ": " + failure.getMessage(); }
        List<Object> tail = state(); require((int) field(owner, "layerDepth") == 0, "Native layer depth leaked");
        if (armorEntered) {
            require(!(boolean) field(capture, "modelActive") && !(boolean) field(capture, "armorLayer"), "Native finally did not end its active layer");
            require("NORMAL".equals(field(capture, "pass").toString()), "Native finally did not restore its pass");
            Vector4fc color = (Vector4fc) field(capture, "color"); require(color.x() == 1 && color.y() == 1 && color.z() == 1 && color.w() == 1, "Native finally did not reset capture color");
        }
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4); GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] rgba = new byte[pixels.remaining()]; pixels.get(rgba); set(owner, "activeCapture", null);
        return new Scene(rgba, List.copyOf(events), tail, thrown, calls);
    }

    private static void render() {
        depth++;
        try { ((LivingPipelineAccess) renderer).arcadium$renderLayers(player, .31f + mode.delta, .43f, mode.delta, 41 + mode.delta, 12, -7, .0625f); }
        finally { depth--; }
    }

    private static void probe(String id, LivingEntity entity, float a, float b, float c, float d, float e, float f, float g) {
        try {
            calls++; List<Object> entry = new ArrayList<>(); entry.add(id); entry.add(depth); entry.add(entity == player);
            for (float value : new float[]{a, b, c, d, e, f, g}) entry.add(Float.floatToRawIntBits(value)); entry.add(state()); events.add(entry);
            require((int) field(owner, "layerDepth") == depth, "Native per-layer scope depth was not preserved");
            if (id.equals("armor") && capture != null && mode.ready && !mode.finished) {
                armorEntered = true; require((boolean) field(capture, "modelActive") && (boolean) field(capture, "armorLayer"), "Typed dispatch bypassed native beginLayer");
                require((int) field(capture, "itemGlintPass") == 0 && ((Collection<?>) field(capture, "glintParts")).isEmpty(), "Native beginLayer did not reset its glint state");
                require((int) field(field(capture, "model"), "partIndex") == 0, "Native beginLayer did not begin its geometry");
                ((Vector4f) field(capture, "color")).set(.2f, .4f, .6f, .8f); set(capture, "itemGlintPass", 5); ((Collection<Object>) field(capture, "glintParts")).add(new Object());
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glColor4f(.2f + (sequence % 3) * .3f, .3f + c * .5f, .8f, 1);
            float x = -.9f + sequence++ * .27f + c * .11f, y = -.45f + (depth - 1) * .5f;
            GL11.glBegin(GL11.GL_QUADS); GL11.glVertex2f(x, y); GL11.glVertex2f(x + .24f, y); GL11.glVertex2f(x + .24f, y + .32f); GL11.glVertex2f(x, y + .32f); GL11.glEnd();
            if (mode.nested && depth == 1 && id.equals("armor")) render();
            events.add(state());
            if (mode.throwing == 1 && id.equals("armor") || mode.throwing == 2 && id.equals("before")) throw new LayerFailure("synthetic " + id);
        } catch (LayerFailure failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static final class ProbeLayer implements EntityRenderLayer<LivingEntity> {
        private final String id; private ProbeLayer(String id) { this.id = id; }
        @Override public void render(LivingEntity entity, float a, float b, float c, float d, float e, float f, float g) { probe(id, entity, a, b, c, d, e, f, g); }
        @Override public boolean colorsWhenDamaged() { return false; }
    }
    private static final class ProbeArmor extends ArmorLayer {
        private ProbeArmor(PlayerRenderer renderer) { super(renderer); }
        @Override public void render(LivingEntity entity, float a, float b, float c, float d, float e, float f, float g) { probe("armor", entity, a, b, c, d, e, f, g); }
    }
    private static final class NoopLayer implements EntityRenderLayer<LivingEntity> {
        @Override public void render(LivingEntity entity, float a, float b, float c, float d, float e, float f, float g) { calls++; }
        @Override public boolean colorsWhenDamaged() { return false; }
    }
    private static final class LayerFailure extends RuntimeException { private LayerFailure(String message) { super(message); } }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> allocations() throws Exception {
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) return Map.of("available", false);
        bean.setThreadAllocatedMemoryEnabled(true); set(owner, "activeCapture", null); set(owner, "layerDepth", 0); capture = null;
        renderer = new PlayerRenderer(Minecraft.getInstance().getEntityRenderDispatcher()); List layers = ((LivingPipelineAccess) renderer).arcadium$layers(); layers.clear(); layers.add(new NoopLayer());
        mode = new Mode("allocation", false, false, false, true, 0, false, .25f, false); long[] allocated = new long[2]; int count = 30000;
        for (int fast = 0; fast < 2; fast++) {
            RenderHookAllocationControl.enabled = fast == 1; for (int i = 0; i < 5000; i++) render();
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()); for (int i = 0; i < count; i++) render();
            allocated[fast] = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        }
        require(allocated[1] <= allocated[0], "Typed native layers increased allocations");
        return Map.of("available", true, "layerCallsPerMode", count, "originalBytes", allocated[0], "typedBytes", allocated[1]);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object freshCapture(boolean ready, boolean finished) throws Exception {
        Object value = captureConstructor.newInstance(owner); set(value, "modelPassSeen", ready); set(value, "finished", finished); set(value, "matrixMode", GL11.GL_MODELVIEW);
        Object geometry = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.ModelGeometry").getConstructor().newInstance(); set(geometry, "partIndex", 9); set(value, "model", geometry);
        set(value, "pass", Enum.valueOf((Class) Class.forName("dev.rdh.argentum.impl.render.entity.instancing.InstanceRenderPass"), "TRANSLUCENT"));
        ((Vector4f) field(value, "overlayColor")).set(.3f, .4f, .5f, .6f); ((Vector4f) field(value, "color")).set(.4f, .3f, .2f, .1f);
        ((Collection<Object>) field(value, "glintParts")).add(new Object()); set(value, "itemGlintPass", 3); return value;
    }
    private static List<Object> state() throws Exception {
        List<Object> values = new ArrayList<>(); values.add((int) field(owner, "layerDepth"));
        if (capture != null) {
            for (String name : new String[]{"modelActive", "armorLayer", "itemGlintPass", "matrixMode", "finished"}) values.add(field(capture, name));
            values.add(field(capture, "pass").toString()); values.add(((Collection<?>) field(capture, "glintParts")).size()); values.add(field(field(capture, "model"), "partIndex"));
            for (String name : new String[]{"color", "overlayColor", "currentOverlayColor"}) { Vector4fc vector = (Vector4fc) field(capture, name); for (float component : new float[]{vector.x(), vector.y(), vector.z(), vector.w()}) values.add(Float.floatToRawIntBits(component)); }
            for (float component : ((Matrix4fc) field(capture, "matrices")).get(new float[16])) values.add(Float.floatToRawIntBits(component));
        }
        for (int matrix : new int[]{GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX}) { FloatBuffer buffer = BufferUtils.createFloatBuffer(16); GL11.glGetFloatv(matrix, buffer); for (int i = 0; i < 16; i++) values.add(Float.floatToRawIntBits(buffer.get(i))); }
        for (int name : new int[]{GL11.GL_MODELVIEW_STACK_DEPTH, GL11.GL_MATRIX_MODE, GL20.GL_CURRENT_PROGRAM, GL13.GL_ACTIVE_TEXTURE, GL11.GL_TEXTURE_BINDING_2D, GL11.GL_DEPTH_WRITEMASK, GL11.GL_BLEND_SRC, GL11.GL_BLEND_DST}) values.add(GL11.glGetInteger(name));
        for (int flag : new int[]{GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_ALPHA_TEST, GL11.GL_CULL_FACE, GL11.GL_TEXTURE_2D}) values.add(GL11.glIsEnabled(flag));
        FloatBuffer color = BufferUtils.createFloatBuffer(4); GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, color); for (int i = 0; i < 4; i++) values.add(Float.floatToRawIntBits(color.get(i)));
        values.add(GL11.glGetError()); return values;
    }
    private static void reset() {
        GlStateManager.activeTexture(GL13.GL_TEXTURE1); GlStateManager.enableTexture(); GlStateManager.disableTexture();
        GlStateManager.activeTexture(GL13.GL_TEXTURE0); GlStateManager.enableTexture(); GlStateManager.disableTexture();
        for (int matrix : new int[]{GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE}) { GlStateManager.matrixMode(matrix); GlStateManager.loadIdentity(); }
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.ortho(-1.5, 1.5, -1.6, 1.6, -8, 8); GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.disableDepthTest(); GlStateManager.disableBlend(); GlStateManager.disableAlphaTest(); GlStateManager.disableCull(); GlStateManager.disableLighting(); GlStateManager.disableFog();
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color4f(0, 0, 0, 0); GlStateManager.color4f(1, 1, 1, 1); GlStateManager.clearColor(.025f, .05f, .075f, 1); GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        while (GL11.glGetError() != GL11.GL_NO_ERROR) { }
    }
    private static Object field(Object instance, String name) throws Exception { Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
    private static void set(Object instance, String name, Object value) throws Exception { Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); field.set(instance, value); }
    private static int visible(byte[] rgba) { int result = 0; for (int i = 0; i < rgba.length; i += 4) if (rgba[i] != rgba[0] || rgba[i + 1] != rgba[1] || rgba[i + 2] != rgba[2]) result++; return result; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void finish(Throwable failure) {
        done = true; report.put("passed", failure == null); report.put("syntheticOfflineFixture", true);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); }
        try { Path output = Path.of(System.getProperty("arcadium.layerOperations066.output")); Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
