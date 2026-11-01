// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.mixin.client.access.FastFrameBuilderAccess;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public final class FastFramePresentation {
    private static RenderTarget expected;
    private static int expectedWidth, expectedHeight, expectedFramebuffer;
    private static int geometry, geometryWidth, geometryHeight, textureUnits;
    private static GLCapabilities context;
    private static boolean failed;
    private static String state = "Off";
    private static final FloatBuffer coordinates = BufferUtils.createFloatBuffer(4);
    private static final IntBuffer polygonMode = BufferUtils.createIntBuffer(2);
    public static long cachedFrames, nativeFrames, geometryBuilds;

    private FastFramePresentation() { }

    public static void begin(RenderTarget target, int width, int height) {
        expected = BetaOptimizationsConfig.getDirectFramebufferPresentation() ? target : null;
        expectedWidth = width;
        expectedHeight = height;
        expectedFramebuffer = 0;
        if (expected == null) state = "Off";
    }

    public static void end() { expected = null; }

    public static void beginVerification(RenderTarget target, int width, int height, int framebuffer) {
        if (System.getProperty("arcadium.fastPresentation.output") == null)
            throw new IllegalStateException("Presentation verification is not enabled");
        expected = target;
        expectedWidth = width;
        expectedHeight = height;
        expectedFramebuffer = framebuffer;
    }

    public static boolean draw(RenderTarget target, int width, int height, boolean resetBlend) {
        if (expected == null || expected != target) return false;
        if (!resetBlend || width != expectedWidth || height != expectedHeight || width < 1 || height < 1
                || target.getClass() != RenderTarget.class || target.frameBufferId <= 0
                || target.viewWidth != width || target.viewHeight != height || target.width != width || target.height != height) {
            return nativePath("Native framebuffer size or renderer");
        }
        if (GpuPlayerPipeline.hasForgeHooks()) return nativePath("Native Forge render hooks");
        GLCapabilities caps = GL.getCapabilities();
        if (failed || !caps.OpenGL30 || caps.OpenGL32
                && (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK) & GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) == 0)
            return nativePath("Native graphics compatibility");
        if (context != caps) {
            context = caps;
            geometry = 0;
            textureUnits = Math.max(1, Math.min(32, GL11.glGetInteger(GL20.GL_MAX_TEXTURE_COORDS)));
        }
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != expectedFramebuffer
                || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0
                || GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) != 0
                || GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != 0
                || GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != GL13.GL_TEXTURE0
                || GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != target.colorTextureId)
            return nativePath("Native custom graphics state");
        if ((caps.OpenGL41 || caps.GL_ARB_separate_shader_objects)
                && GL11.glGetInteger(GL41.GL_PROGRAM_PIPELINE_BINDING) != 0
                || caps.GL_ARB_vertex_program && GL11.glIsEnabled(ARBVertexProgram.GL_VERTEX_PROGRAM_ARB)
                || caps.GL_ARB_fragment_program && GL11.glIsEnabled(ARBFragmentProgram.GL_FRAGMENT_PROGRAM_ARB))
            return nativePath("Native custom shader");
        if (GL11.glIsEnabled(GL11.GL_NORMAL_ARRAY) || GL11.glIsEnabled(GL11.GL_EDGE_FLAG_ARRAY)
                || GL11.glIsEnabled(GL14.GL_SECONDARY_COLOR_ARRAY) || GL11.glIsEnabled(GL14.GL_FOG_COORDINATE_ARRAY))
            return nativePath("Native extra vertex arrays");
        polygonMode.clear();
        GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, polygonMode);
        if (polygonMode.get(0) != GL11.GL_FILL || polygonMode.get(1) != GL11.GL_FILL)
            return nativePath("Native polygon mode");
        BufferBuilder builder = Tesselator.getInstance().getBuffer();
        if (!(builder instanceof FastFrameBuilderAccess access) || access.arcadium$presentBuilding() || builder.getVertexCount() != 0)
            return nativePath("Native active vertex builder");
        int clientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        try {
            for (int unit = 1; unit < textureUnits; unit++) {
                GL13.glClientActiveTexture(GL13.GL_TEXTURE0 + unit);
                if (GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY)) return nativePath("Native extra texture coordinates");
            }
        } finally {
            GL13.glClientActiveTexture(clientTexture);
        }
        try {
            ensureGeometry(width, height);
            coordinates.clear();
            GL11.glGetFloatv(GL11.GL_CURRENT_TEXTURE_COORDS, coordinates);
            GL11.glCallList(geometry);
            GL13.glMultiTexCoord4f(GL13.GL_TEXTURE0, coordinates.get(0), coordinates.get(1), coordinates.get(2), coordinates.get(3));
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            cachedFrames++;
            state = "Cached final quad";
            return true;
        } catch (RuntimeException error) {
            failed = true;
            release();
            LogManager.getLogger("Arcadium/Presentation").warn("Cached frame presentation disabled", error);
            return nativePath("Native graphics recovery");
        }
    }

    private static boolean nativePath(String reason) { nativeFrames++; state = reason; return false; }

    private static void ensureGeometry(int width, int height) {
        if (geometry != 0 && geometryWidth == width && geometryHeight == height) return;
        if (geometry != 0) GL11.glDeleteLists(geometry, 1);
        geometry = GL11.glGenLists(1);
        if (geometry == 0) throw new IllegalStateException("Could not allocate a frame display list");
        geometryWidth = width;
        geometryHeight = height;
        GL11.glNewList(geometry, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(0, height, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(width, height, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(width, 0, 0);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(0, 0, 0);
        GL11.glEnd();
        GL11.glEndList();
        geometryBuilds++;
    }

    public static void release() {
        if (geometry != 0 && context == GL.getCapabilities()) GL11.glDeleteLists(geometry, 1);
        geometry = 0;
    }

    public static String status() {
        return "Frame presentation: " + state + " | " + cachedFrames + " cached / " + nativeFrames + " native";
    }
}
