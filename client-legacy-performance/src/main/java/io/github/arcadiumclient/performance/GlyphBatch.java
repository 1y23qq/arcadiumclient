// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.resource.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.system.MemoryUtil;

public final class GlyphBatch {
    private static final int GLYPH_FLOATS = 6 * GlyphVertices.STRIDE;
    private static final int STRIDE_BYTES = GlyphVertices.STRIDE * Float.BYTES;
    private final GlyphVertices glyph = new GlyphVertices();
    private final FloatBuffer vertices = ByteBuffer.allocateDirect(256 * GLYPH_FLOATS * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private boolean active;
    private Identifier texture;
    private float red, green, blue, alpha, u, v;
    private long submittedGlyphs;
    private long submissions;

    public void begin(float r, float g, float b, float a) {
        active = LegacyPerformance.fontBatchEnabled();
        texture = null;
        vertices.clear();
        setColor(r, g, b, a);
    }

    public void finish() {
        if (!active) return;
        flush();
        active = false;
        texture = null;
        GL11.glTexCoord2f(u, v);
    }

    public void bind(TextureManager manager, Identifier identifier) {
        if (!active) {
            manager.bind(identifier);
        } else if (!identifier.equals(texture)) {
            flush();
            manager.bind(identifier);
            texture = identifier;
        }
    }

    public void color(float r, float g, float b, float a) {
        setColor(r, g, b, a);
        GL11.glColor4f(r, g, b, a);
    }

    private void setColor(float r, float g, float b, float a) {
        red = r; green = g; blue = b; alpha = a;
        glyph.color(r, g, b, a);
    }

    public void beginGlyph(int mode) {
        if (!active) {
            GL11.glBegin(mode);
            return;
        }
        if (mode != GL11.GL_TRIANGLE_STRIP) throw new IllegalStateException("Unsupported font primitive");
        if (vertices.remaining() < GLYPH_FLOATS) flush();
        glyph.begin();
    }

    public void texture(float nextU, float nextV) {
        if (!active) GL11.glTexCoord2f(nextU, nextV);
        u = nextU; v = nextV;
        glyph.texture(u, v);
    }

    public void vertex(float x, float y, float z) {
        if (active) glyph.vertex(x, y, z);
        else GL11.glVertex3f(x, y, z);
    }

    public void endGlyph() {
        if (active) {
            glyph.appendTo(vertices);
            submittedGlyphs++;
        } else GL11.glEnd();
    }

    public void disable(int capability) {
        if (active) flush();
        GL11.glDisable(capability);
    }

    public void flush() {
        if (vertices.position() == 0) return;
        int vertexCount = vertices.position() / GlyphVertices.STRIDE;
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            if (previousBuffer != 0) GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GLX.clientActiveTexture(GLX.GL_TEXTURE1);
            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GLX.clientActiveTexture(GLX.GL_TEXTURE0);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            vertices.flip();
            vertices.position(0);
            GL11.glVertexPointer(3, STRIDE_BYTES, vertices);
            vertices.position(3);
            GL11.glTexCoordPointer(2, STRIDE_BYTES, vertices);
            vertices.position(5);
            GL11.glColorPointer(4, GL11.GL_FLOAT, STRIDE_BYTES, MemoryUtil.memAddress(vertices));
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
            submissions++;
        } finally {
            vertices.clear();
            GL11.glPopClientAttrib();
            GLX.clientActiveTexture(previousClientTexture);
            if (previousBuffer != 0) GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            GL11.glColor4f(red, green, blue, alpha);
        }
    }

    public long submittedGlyphs() {
        return submittedGlyphs;
    }

    public long submissions() {
        return submissions;
    }
}
