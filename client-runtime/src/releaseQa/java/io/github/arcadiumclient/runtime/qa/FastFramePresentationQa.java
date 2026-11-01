// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.FastFramePresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FastFramePresentationQa {
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static int ticks, phase, frames, checks;
    private static boolean done;
    private static long beforeFrames;
    private static final FloatBuffer floats = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer ints = BufferUtils.createIntBuffer(4);

    private FastFramePresentationQa() { }
    public static boolean enabled() { return System.getProperty("arcadium.fastPresentation.output") != null; }
    private static void require(boolean pass, String message) { checks++; if (!pass) throw new IllegalStateException(message); }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(++ticks < 900, "Frame presentation QA timed out");
            if (phase == 0 && ticks > 75 && mc.screen != null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
                        "Use an isolated offline synthetic account");
                require(!BetaOptimizationsConfig.getDirectFramebufferPresentation(), "Fast presentation default was enabled");
                var property = BetaOptimizationsConfig.INSTANCE.getTree().getProp("directFramebufferPresentation");
                require(property != null && property.getMetadata("visualizer") != null, "Fast presentation GUI setting missing");
                verify(mc);
                property.setAs(true);
                beforeFrames = FastFramePresentation.cachedFrames;
                phase = 1;
                ticks = 0;
            } else if (phase == 1 && ticks > 25) {
                require(FastFramePresentation.cachedFrames > beforeFrames, "Actual main present did not use the cache: " + FastFramePresentation.status());
                report.put("actualMainPresentFrames", FastFramePresentation.cachedFrames - beforeFrames);
                report.put("cachedGeometryBuilds", FastFramePresentation.geometryBuilds);
                report.put("renderedFrames", frames);
                finish(null);
            }
        } catch (Throwable failure) { finish(failure); }
    }

    public static void frame() { if (!done) frames++; }

    private static void verify(Minecraft mc) throws Exception {
        int width = mc.width, height = mc.height;
        require(GL.getCapabilities().OpenGL30, "OpenGL 3 compatibility required for this QA");
        RenderTarget source = null, destination = null;
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = 0;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix();
        try {
            source = new RenderTarget(width, height, true);
            destination = new RenderTarget(width, height, true);
            ByteBuffer sourcePixels = pattern(width, height, false), background = pattern(width, height, true);
            upload(source, GL11.GL_RGBA8, sourcePixels);
            List<Object> cases = new ArrayList<>();
            for (String scenario : List.of("rgba-alpha", "srgb-encode", "srgb-source", "texture-matrix", "scissor", "front-cull", "wireframe-fallback", "custom-shader-fallback", "size-fallback")) {
                if (scenario.equals("custom-shader-fallback") && program == 0) program = customProgram();
                int sourceFormat = scenario.equals("srgb-source") ? GL21.GL_SRGB8_ALPHA8 : GL11.GL_RGBA8;
                int outputFormat = scenario.equals("srgb-encode") ? GL21.GL_SRGB8_ALPHA8 : GL11.GL_RGBA8;
                upload(source, sourceFormat, sourcePixels);
                upload(destination, outputFormat, background);
                configure(scenario, destination.frameBufferId, width, height, program);
                if (scenario.equals("size-fallback")) source.viewWidth = width - 1;
                FastFramePresentation.end();
                source.draw(width, height);
                State nativeState = State.capture();
                byte[] nativePixels = read(destination.frameBufferId, width, height);
                byte[] sourceBefore = read(source.frameBufferId, width, height);
                upload(destination, outputFormat, background);
                configure(scenario, destination.frameBufferId, width, height, program);
                long before = FastFramePresentation.cachedFrames;
                FastFramePresentation.beginVerification(source, width, height, destination.frameBufferId);
                try { source.draw(width, height); } finally { FastFramePresentation.end(); }
                State cachedState = State.capture();
                byte[] cachedPixels = read(destination.frameBufferId, width, height);
                require(Arrays.equals(nativePixels, cachedPixels), "RGBA mismatch in " + scenario + ": " + difference(nativePixels, cachedPixels));
                require(Arrays.equals(sourceBefore, read(source.frameBufferId, width, height)), "Source framebuffer changed in " + scenario);
                require(nativeState.equals(cachedState), "GL tail mismatch in " + scenario + ": " + nativeState.difference(cachedState));
                boolean expectedCache = !scenario.endsWith("fallback");
                require((FastFramePresentation.cachedFrames > before) == expectedCache, "Wrong cache/fallback selection in " + scenario + ": " + FastFramePresentation.status());
                cases.add(Map.of("scenario", scenario, "rgbaBytes", nativePixels.length, "samePixels", true, "sameState", true, "cached", expectedCache));
                source.viewWidth = width;
                errorFree(scenario);
            }
            upload(source, GL11.GL_RGBA8, sourcePixels);
            configure("window", 0, width, height, program);
            GL11.glClearColor(.2f, .4f, .6f, .37f); GL11.glColorMask(true, true, true, true); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            FastFramePresentation.end(); source.draw(width, height);
            State windowNativeState = State.capture();
            byte[] windowNative = read(0, width, height);
            configure("window", 0, width, height, program);
            GL11.glClearColor(.2f, .4f, .6f, .37f); GL11.glColorMask(true, true, true, true); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            long before = FastFramePresentation.cachedFrames;
            FastFramePresentation.beginVerification(source, width, height, 0);
            try { source.draw(width, height); } finally { FastFramePresentation.end(); }
            State windowCachedState = State.capture();
            byte[] windowCached = read(0, width, height);
            require(FastFramePresentation.cachedFrames > before, "Window present did not use cache");
            require(Arrays.equals(windowNative, windowCached), "Window RGBA mismatch: " + difference(windowNative, windowCached));
            require(windowNativeState.equals(windowCachedState), "Window GL tail mismatch: " + windowNativeState.difference(windowCachedState));
            writeImage(windowNative, width, height, "present-native.png");
            writeImage(windowCached, width, height, "present-cached.png");
            report.put("cases", cases);
            report.put("windowRgbaBytes", windowCached.length);
            report.put("windowAlphaBits", GL11.glGetInteger(GL11.GL_ALPHA_BITS));
            report.put("windowPixelsAndStateEqual", true);
            report.put("resolution", width + "x" + height);
            report.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
            errorFree("window");
        } finally {
            FastFramePresentation.end();
            GL20.glUseProgram(0);
            if (program != 0) GL20.glDeleteProgram(program);
            if (source != null) source.destroyBuffers();
            if (destination != null) destination.destroyBuffers();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            GL11.glPopClientAttrib(); GL11.glPopAttrib();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GL11.glMatrixMode(previousMode);
        }
    }

    private static void configure(String scenario, int framebuffer, int width, int height, int program) {
        GL20.glUseProgram(scenario.equals("custom-shader-fallback") ? program : 0);
        GL30.glBindVertexArray(0); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0); GlStateManager.bindTexture(0);
        GL11.glEnable(GL11.GL_TEXTURE_2D); GlStateManager.enableTexture();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_FOG); GlStateManager.disableFog();
        GL11.glDisable(GL11.GL_CULL_FACE); GlStateManager.disableCull();
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glEnable(GL11.GL_ALPHA_TEST); GlStateManager.enableAlphaTest();
        GL11.glEnable(GL11.GL_DEPTH_TEST); GlStateManager.enableDepthTest();
        GL11.glEnable(GL11.GL_BLEND); GlStateManager.enableBlend();
        GL11.glEnable(GL11.GL_LIGHTING); GlStateManager.enableLighting();
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_EDGE_FLAG_ARRAY);
        GL11.glDisableClientState(GL14.GL_FOG_COORDINATE_ARRAY);
        GL11.glDisableClientState(GL14.GL_SECONDARY_COLOR_ARRAY);
        int units = Math.max(1, Math.min(32, GL11.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS)));
        for (int i = 0; i < units; i++) { GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + i); GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY); }
        GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY); GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glLoadIdentity();
        if (scenario.equals("texture-matrix")) { GL11.glTranslatef(.125f, 0, 0); GL11.glScalef(.75f, 1, 1); }
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        if (scenario.equals("srgb-encode") || scenario.equals("srgb-source")) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
        if (scenario.equals("scissor")) { GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(width / 4, height / 5, width / 3, height / 2); }
        if (scenario.equals("front-cull")) { GL11.glEnable(GL11.GL_CULL_FACE); GL11.glCullFace(GL11.GL_FRONT); }
        if (scenario.equals("wireframe-fallback")) GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glViewport(0, 0, width, height);
    }

    private static ByteBuffer pattern(int width, int height, boolean background) {
        ByteBuffer data = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            data.put((byte) (background ? 17 : x * 13 + y * 7));
            data.put((byte) (background ? 81 : x * 3 - y * 11));
            data.put((byte) (background ? 143 : x * 17 ^ y * 5));
            data.put((byte) (background ? x + y * 3 : x * 7 + y * 9));
        }
        return data.flip();
    }

    private static void upload(RenderTarget target, int format, ByteBuffer bytes) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0); GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(target.colorTextureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, target.width, target.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes.duplicate());
        target.setFilterMode(GL11.GL_NEAREST);
    }

    private static byte[] read(int framebuffer, int width, int height) {
        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        ByteBuffer bytes = BufferUtils.createByteBuffer(width * height * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        } finally { GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous); }
        byte[] out = new byte[bytes.remaining()]; bytes.get(out); return out;
    }

    private static String difference(byte[] expected, byte[] actual) {
        for (int i = 0; i < expected.length; i++) if (expected[i] != actual[i])
            return "byte=" + i + " expected=" + Byte.toUnsignedInt(expected[i]) + " actual=" + Byte.toUnsignedInt(actual[i]);
        return "length " + expected.length + "/" + actual.length;
    }

    private static int customProgram() {
        int vertex = GL20.glCreateShader(GL20.GL_VERTEX_SHADER), fragment = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER), program = GL20.glCreateProgram();
        GL20.glShaderSource(vertex, "#version 120\nvoid main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;}");
        GL20.glShaderSource(fragment, "#version 120\nuniform sampler2D source;void main(){gl_FragColor=texture2D(source,gl_TexCoord[0].xy).bgra;}");
        GL20.glCompileShader(vertex); GL20.glCompileShader(fragment);
        GL20.glAttachShader(program, vertex); GL20.glAttachShader(program, fragment); GL20.glLinkProgram(program);
        require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, "QA custom shader did not link");
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment);
        return program;
    }

    private static void writeImage(byte[] bytes, int width, int height, String name) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int p = (y * width + x) * 4;
            int color = Byte.toUnsignedInt(bytes[p + 3]) << 24 | Byte.toUnsignedInt(bytes[p]) << 16
                    | Byte.toUnsignedInt(bytes[p + 1]) << 8 | Byte.toUnsignedInt(bytes[p + 2]);
            image.setRGB(x, height - 1 - y, color);
        }
        ImageIO.write(image, "png", output().getParent().resolve(name).toFile());
    }

    private record State(int[] scalar, float[] matrices) {
        static State capture() {
            int[] queries = {GL30.GL_READ_FRAMEBUFFER_BINDING, GL30.GL_DRAW_FRAMEBUFFER_BINDING, GL20.GL_CURRENT_PROGRAM,
                    GL30.GL_VERTEX_ARRAY_BINDING, GL15.GL_ARRAY_BUFFER_BINDING, GL13.GL_ACTIVE_TEXTURE, GL13.GL_CLIENT_ACTIVE_TEXTURE,
                    GL11.GL_TEXTURE_BINDING_2D, GL11.GL_MATRIX_MODE, GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB,
                    GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA};
            List<Integer> values = new ArrayList<>();
            for (int query : queries) values.add(GL11.glGetInteger(query));
            for (int query : new int[] {GL11.GL_DEPTH_TEST, GL11.GL_ALPHA_TEST, GL11.GL_BLEND, GL11.GL_LIGHTING, GL11.GL_COLOR_MATERIAL,
                    GL11.GL_CULL_FACE, GL11.GL_SCISSOR_TEST, GL11.GL_STENCIL_TEST, GL30.GL_FRAMEBUFFER_SRGB,
                    GL11.GL_VERTEX_ARRAY, GL11.GL_COLOR_ARRAY, GL11.GL_TEXTURE_COORD_ARRAY}) values.add(GL11.glIsEnabled(query) ? 1 : 0);
            values.add(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0);
            for (int query : new int[] {GL11.GL_VIEWPORT, GL11.GL_COLOR_WRITEMASK}) {
                ints.clear(); GL11.glGetIntegerv(query, ints);
                for (int i = 0; i < 4; i++) values.add(ints.get(i));
            }
            float[] matrices = new float[52]; int out = 0;
            for (int query : new int[] {GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX, GL11.GL_TEXTURE_MATRIX}) {
                floats.clear(); GL11.glGetFloatv(query, floats);
                for (int i = 0; i < 16; i++) matrices[out++] = floats.get(i);
            }
            floats.clear(); GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, floats);
            for (int i = 0; i < 4; i++) matrices[out++] = floats.get(i);
            return new State(values.stream().mapToInt(Integer::intValue).toArray(), matrices);
        }
        @Override public boolean equals(Object other) { return other instanceof State state && Arrays.equals(scalar, state.scalar) && Arrays.equals(matrices, state.matrices); }
        @Override public int hashCode() { return Arrays.hashCode(scalar) * 31 + Arrays.hashCode(matrices); }
        String difference(State other) {
            for (int i = 0; i < scalar.length; i++) if (scalar[i] != other.scalar[i]) return "scalar " + i + ": " + scalar[i] + "/" + other.scalar[i];
            for (int i = 0; i < matrices.length; i++) if (matrices[i] != other.matrices[i]) return "float " + i + ": " + matrices[i] + "/" + other.matrices[i];
            return "unknown";
        }
    }

    private static void errorFree(String where) { int error = GL11.glGetError(); require(error == GL11.GL_NO_ERROR, where + " GL error=" + error); }
    private static Path output() { return Path.of(System.getProperty("arcadium.fastPresentation.output")).toAbsolutePath(); }
    private static void finish(Throwable failure) {
        if (done) return; done = true;
        report.put("passed", failure == null); report.put("checks", checks);
        if (failure != null) { report.put("error", failure.toString()); failure.printStackTrace(); }
        try { Files.createDirectories(output().getParent()); Files.writeString(output(), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
}
