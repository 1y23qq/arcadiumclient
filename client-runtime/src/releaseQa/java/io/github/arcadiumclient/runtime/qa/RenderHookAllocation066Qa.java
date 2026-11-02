// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class RenderHookAllocation066Qa {
    private static final int SIZE = 192;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static int ticks, phase, wait, snapshots;
    private static boolean done;
    private static Object owner, batch;
    private static Class<?> captureType, guiType;
    private static Constructor<?> captureConstructor;

    private RenderHookAllocation066Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.renderHooks066.output") != null; }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Use the isolated synthetic account");
            require(++ticks < 1800, "Render hook QA timed out at phase " + phase); wait++;
            if (phase == 0 && ticks > 65 && mc.screen != null) {
                ClientPreferences.setSetupComplete(true); mc.options.pauseOnUnfocus = false;
                mc.options.vsync = false; Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps = false;
                mc.startGame("arcadium-render-hooks-066", "Render Hook QA", new WorldSettings(66066,
                    WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 80) {
                mc.openScreen(null); report.putAll(verify()); finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private record Mode(String name, boolean capture, boolean suppress, int matrixMode, boolean names, boolean finished, boolean nested) { }
    private record Scene(byte[] pixels, List<List<Integer>> states) { }

    private static Map<String, Object> verify() throws Exception {
        require(RenderHookAllocationControl.fusedSites == 7, "Expected seven actual transformed capture gates, got " + RenderHookAllocationControl.fusedSites);
        long typed = Arrays.stream(GlStateManager.class.getDeclaredMethods()).filter(method -> method.getName().startsWith("arcadium$typedRenderGate$")).count();
        require(typed == 7, "The loaded GlStateManager does not contain all typed predicates: " + typed);
        Object renderer = Class.forName("dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer").getMethod("instanceNullable").invoke(null);
        require(renderer != null, "Native Argentum renderer is unavailable");
        owner = renderer.getClass().getMethod("getEntityInstancing").invoke(renderer);
        batch = renderer.getClass().getMethod("getNameTagBatch").invoke(renderer);
        captureType = Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture");
        captureConstructor = captureType.getDeclaredConstructor(owner.getClass()); captureConstructor.setAccessible(true);
        guiType = Class.forName("dev.rdh.argentum.impl.render.gui.hud.item.GuiItemIcons");
        require(field(owner, "activeCapture") == null && !(boolean) field(batch, "active"), "A native render scope is already active");
        boolean previous = RenderHookAllocationControl.enabled;
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING), program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        IntBuffer viewport = BufferUtils.createIntBuffer(4); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int target = GL30.glGenFramebuffers(), texture = GL11.glGenTextures();
        float[][] guiScales = scales(); int[] guiDepths = ((int[]) field(guiType, "DEPTHS")).clone();
        int guiMode = (int) field(guiType, "matrixMode");
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        for (int mode : new int[]{GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE}) { GL11.glMatrixMode(mode); GL11.glPushMatrix(); }
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target); GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "QA framebuffer is incomplete");
            GL20.glUseProgram(0); GL11.glViewport(0, 0, SIZE, SIZE);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0); GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_FOG);
            GL11.glColorMask(true, true, true, true);
            List<Mode> modes = List.of(
                new Mode("native", false, false, GL11.GL_MODELVIEW, false, false, false),
                new Mode("name capture", false, false, GL11.GL_MODELVIEW, true, false, false),
                new Mode("entity declines", true, false, GL11.GL_MODELVIEW, false, false, false),
                new Mode("entity consumes", true, true, GL11.GL_MODELVIEW, false, false, false),
                new Mode("entity and names decline", true, false, GL11.GL_MODELVIEW, true, false, false),
                new Mode("entity and names consume", true, true, GL11.GL_MODELVIEW, true, false, false),
                new Mode("texture capture", true, true, GL11.GL_TEXTURE, true, false, false),
                new Mode("projection fallback", true, true, GL11.GL_PROJECTION, true, false, false),
                new Mode("finished capture fallback", true, true, GL11.GL_MODELVIEW, true, true, false),
                new Mode("nested captures", true, true, GL11.GL_MODELVIEW, true, false, true));
            List<Map<String, Object>> cases = new ArrayList<>();
            for (Mode mode : modes) {
                Scene original = scene(false, mode), accelerated = scene(true, mode);
                require(original.states.equals(accelerated.states), mode.name + " changed capture, GUI, name, or GL state");
                require(Arrays.equals(original.pixels, accelerated.pixels), mode.name + " changed native pixels");
                int visible = visible(original.pixels); require(visible > 30, mode.name + " produced no visible reference");
                cases.add(Map.of("mode", mode.name, "snapshots", original.states.size(), "visiblePixels", visible, "pixelBytesExact", true));
            }
            report.put("cases", cases); report.put("stateSnapshotsCompared", snapshots / 2);
            report.put("actualFusedSites", RenderHookAllocationControl.fusedSites); report.put("loadedTypedPredicates", typed);
            report.put("exceptionCases", exceptions());
            report.put("allocationProbe", allocations());
            report.put("gpu", GL11.glGetString(GL11.GL_RENDERER)); report.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native GL state produced an error");
            return report;
        } finally {
            set(owner, "activeCapture", null); discardNames(); RenderHookAllocationControl.enabled = previous;
            for (int mode : new int[]{GL11.GL_TEXTURE, GL11.GL_PROJECTION, GL11.GL_MODELVIEW}) { GL11.glMatrixMode(mode); GL11.glPopMatrix(); }
            GL11.glMatrixMode(matrixMode); GL11.glPopAttrib(); GL20.glUseProgram(program);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer); GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GL30.glDeleteFramebuffers(target); GL11.glDeleteTextures(texture);
            float[][] actual = (float[][]) field(guiType, "SCALES");
            for (int i = 0; i < actual.length; i++) System.arraycopy(guiScales[i], 0, actual[i], 0, actual[i].length);
            System.arraycopy(guiDepths, 0, (int[]) field(guiType, "DEPTHS"), 0, guiDepths.length); set(guiType, "matrixMode", guiMode);
        }
    }

    private static Scene scene(boolean fast, Mode mode) throws Exception {
        RenderHookAllocationControl.enabled = fast; set(owner, "activeCapture", null); discardNames(); resetMatrices();
        if (mode.names) {
            batch.getClass().getMethod("begin", Matrix4fc.class).invoke(batch, new Matrix4f().translate(.03f, -.04f, 0));
            set(batch, "active", true); batch.getClass().getMethod("enter").invoke(batch);
            require(batch.getClass().getMethod("capturing").invoke(null) == batch, "Native name capture did not activate");
        }
        Object capture = mode.capture ? capture(mode.suppress, mode.matrixMode, mode.finished) : null;
        Object child = mode.nested ? capture(false, GL11.GL_MODELVIEW, false) : null;
        set(owner, "activeCapture", capture); GlStateManager.matrixMode(mode.matrixMode);
        require(captureType.getMethod("current").invoke(null) == capture, "Real capture dispatch did not use the fixture scope");
        List<List<Integer>> states = new ArrayList<>();
        step(states, capture, child, 0);
        GlStateManager.pushMatrix(); step(states, capture, child, 1);
        GlStateManager.translatef(.31f, -.12f, .02f); step(states, capture, child, 2);
        GlStateManager.translated(.10000000001, -.15000000001, -.01); step(states, capture, child, 3);
        GlStateManager.scalef(.83f, 1.17f, 1.01f); step(states, capture, child, 4);
        GlStateManager.scaled(1.13000000001, .91000000001, 1.0); step(states, capture, child, 5);
        GlStateManager.rotatef(29.25f, .2f, .4f, 1); step(states, capture, child, 6);
        if (child != null) set(owner, "activeCapture", child);
        GlStateManager.pushMatrix(); step(states, capture, child, 7);
        GlStateManager.translatef(-.24f, .18f, 0); step(states, capture, child, 8);
        GlStateManager.popMatrix(); step(states, capture, child, 9);
        if (child != null) set(owner, "activeCapture", capture);
        GlStateManager.popMatrix(); step(states, capture, child, 10);
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.remaining()]; pixels.get(result);
        set(owner, "activeCapture", null); discardNames();
        return new Scene(result, states);
    }

    private static void step(List<List<Integer>> states, Object capture, Object child, int index) throws Exception {
        states.add(state(capture, child)); snapshots++;
        GL11.glColor4f((index % 3 + 1) / 3f, (index % 4 + 1) / 4f, (index % 5 + 1) / 5f, 1);
        float x = -.8f + index * .11f, y = -.65f + (index % 3) * .41f;
        GL11.glBegin(GL11.GL_QUADS); GL11.glVertex2f(x, y); GL11.glVertex2f(x + .24f, y);
        GL11.glVertex2f(x + .24f, y + .25f); GL11.glVertex2f(x, y + .25f); GL11.glEnd();
    }

    private static List<Integer> state(Object capture, Object child) throws Exception {
        List<Integer> values = new ArrayList<>();
        for (int matrix : new int[]{GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX}) {
            FloatBuffer floats = BufferUtils.createFloatBuffer(16); GL11.glGetFloatv(matrix, floats);
            for (int i = 0; i < 16; i++) values.add(Float.floatToRawIntBits(floats.get(i)));
        }
        for (int name : new int[]{GL11.GL_MATRIX_MODE, GL11.GL_MODELVIEW_STACK_DEPTH, GL11.GL_PROJECTION_STACK_DEPTH, GL11.GL_TEXTURE_STACK_DEPTH,
                GL20.GL_CURRENT_PROGRAM, GL13.GL_ACTIVE_TEXTURE, GL11.GL_TEXTURE_BINDING_2D, GL11.GL_DEPTH_WRITEMASK, GL11.GL_BLEND_SRC, GL11.GL_BLEND_DST}) values.add(GL11.glGetInteger(name));
        for (int flag : new int[]{GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_TEXTURE_2D, GL11.GL_CULL_FACE, GL11.GL_ALPHA_TEST, GL11.GL_LIGHTING}) values.add(GL11.glIsEnabled(flag) ? 1 : 0);
        FloatBuffer color = BufferUtils.createFloatBuffer(4); GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, color);
        for (int i = 0; i < 4; i++) values.add(Float.floatToRawIntBits(color.get(i)));
        for (Object object : new Object[]{capture, child, batch}) {
            if (object == null) continue;
            matrix(values, (Matrix4fc) field(object, "matrices"));
            if (object != batch) matrix(values, (Matrix4fc) field(object, "textureMatrices"));
        }
        values.add((int) field(batch, "pushes")); values.add((boolean) field(batch, "lost") ? 1 : 0);
        values.add((int) field(batch, "depth")); values.add((int) field(batch, "matrixMode"));
        for (float[] row : scales()) for (float value : row) values.add(Float.floatToRawIntBits(value));
        for (int value : (int[]) field(guiType, "DEPTHS")) values.add(value);
        values.add((int) field(guiType, "matrixMode")); values.add(GL11.glGetError());
        return values;
    }

    private static List<Map<String, Object>> exceptions() throws Exception {
        List<Map<String, Object>> cases = new ArrayList<>();
        for (boolean overflow : new boolean[]{false, true}) {
            String expected = null; List<Integer> original = null;
            for (boolean fast : new boolean[]{false, true}) {
                RenderHookAllocationControl.enabled = fast; resetMatrices(); discardNames();
                Object capture = capture(true, GL11.GL_MODELVIEW, false); set(owner, "activeCapture", capture);
                if (overflow) { Matrix4fStack matrices = (Matrix4fStack) field(capture, "matrices"); for (int i = 0; i < 63; i++) matrices.pushMatrix(); }
                String thrown = null;
                try { if (overflow) GlStateManager.pushMatrix(); else GlStateManager.popMatrix(); }
                catch (IllegalStateException failure) { thrown = failure.getClass().getName() + ": " + failure.getMessage(); }
                require(thrown != null, "Natural capture stack failure did not occur");
                List<Integer> after = state(capture, null);
                if (!fast) { expected = thrown; original = after; }
                else require(expected.equals(thrown) && original.equals(after), "Fusion changed exception order or side effects");
                set(owner, "activeCapture", null);
            }
            cases.add(Map.of("case", overflow ? "capture overflow" : "capture underflow", "sameExceptionAndPrefixState", true));
        }
        return cases;
    }

    private static Map<String, Object> allocations() throws Exception {
        if (!(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) return Map.of("available", false);
        bean.setThreadAllocatedMemoryEnabled(true); set(owner, "activeCapture", null); discardNames(); resetMatrices();
        long[] allocated = new long[2]; int count = 20000;
        for (int mode = 0; mode < 2; mode++) {
            RenderHookAllocationControl.enabled = mode == 1;
            for (int i = 0; i < 4000; i++) { GlStateManager.pushMatrix(); GlStateManager.translatef(0, 0, 0); GlStateManager.scalef(1, 1, 1); GlStateManager.popMatrix(); }
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            for (int i = 0; i < count; i++) { GlStateManager.pushMatrix(); GlStateManager.translatef(0, 0, 0); GlStateManager.scalef(1, 1, 1); GlStateManager.popMatrix(); }
            allocated[mode] = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
        }
        require(allocated[1] < allocated[0], "The native typed path did not reduce thread allocations: " + Arrays.toString(allocated));
        return Map.of("available", true, "matrixCallsPerMode", count * 4, "originalAllocatedBytes", allocated[0], "typedAllocatedBytes", allocated[1]);
    }

    private static Object capture(boolean suppress, int mode, boolean finished) throws Exception {
        Object capture = captureConstructor.newInstance(owner);
        set(capture, "suppressFixedFunction", suppress); set(capture, "matrixMode", mode); set(capture, "finished", finished);
        return capture;
    }
    private static void resetMatrices() throws Exception {
        for (int mode : new int[]{GL11.GL_MODELVIEW, GL11.GL_PROJECTION, GL11.GL_TEXTURE}) { GL11.glMatrixMode(mode); GL11.glLoadIdentity(); }
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glOrtho(-1.7, 1.7, -1.7, 1.7, -8, 8); GL11.glMatrixMode(GL11.GL_MODELVIEW);
        float[][] scales = (float[][]) field(guiType, "SCALES"); for (float[] row : scales) Arrays.fill(row, 1);
        Arrays.fill((int[]) field(guiType, "DEPTHS"), 0); set(guiType, "matrixMode", GL11.GL_MODELVIEW);
        GL11.glColor4f(1, 1, 1, 1); GL11.glClearColor(.025f, .05f, .075f, 1); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); while (GL11.glGetError() != GL11.GL_NO_ERROR) { }
    }
    private static void discardNames() throws Exception { if (batch != null) batch.getClass().getMethod("discard").invoke(batch); }
    private static void matrix(List<Integer> output, Matrix4fc matrix) { for (float value : matrix.get(new float[16])) output.add(Float.floatToRawIntBits(value)); }
    private static float[][] scales() throws Exception { return Arrays.stream((float[][]) field(guiType, "SCALES")).map(float[]::clone).toArray(float[][]::new); }
    private static Object field(Object instance, String name) throws Exception { Field field = (instance instanceof Class<?> type ? type : instance.getClass()).getDeclaredField(name); field.setAccessible(true); return field.get(instance instanceof Class<?> ? null : instance); }
    private static void set(Object instance, String name, Object value) throws Exception { Field field = (instance instanceof Class<?> type ? type : instance.getClass()).getDeclaredField(name); field.setAccessible(true); field.set(instance instanceof Class<?> ? null : instance, value); }
    private static int visible(byte[] pixels) { int count = 0; for (int i = 0; i < pixels.length; i += 4) if (pixels[i] != pixels[0] || pixels[i + 1] != pixels[1] || pixels[i + 2] != pixels[2]) count++; return count; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private static void finish(Throwable failure) {
        done = true; report.put("passed", failure == null);
        if (failure != null) { failure.printStackTrace(); report.put("error", failure.toString()); }
        try { Path output = Path.of(System.getProperty("arcadium.renderHooks066.output")); Files.createDirectories(output.toAbsolutePath().getParent()); Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
