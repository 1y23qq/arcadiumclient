// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance;

import java.nio.FloatBuffer;

public final class GlyphVertices {
    public static final int STRIDE = 9;
    private static final int[] TRIANGLE_ORDER = {0, 1, 2, 2, 1, 3};
    private final float[] glyph = new float[4 * STRIDE];
    private int count;
    private float u, v;
    private float red, green, blue, alpha;

    public void begin() {
        count = 0;
    }

    public void color(float r, float g, float b, float a) {
        red = r; green = g; blue = b; alpha = a;
    }

    public void texture(float nextU, float nextV) {
        u = nextU; v = nextV;
    }

    public void vertex(float x, float y, float z) {
        if (count >= 4) throw new IllegalStateException("A font glyph has more than four vertices");
        int offset = count++ * STRIDE;
        glyph[offset] = x; glyph[offset + 1] = y; glyph[offset + 2] = z;
        glyph[offset + 3] = u; glyph[offset + 4] = v;
        glyph[offset + 5] = red; glyph[offset + 6] = green; glyph[offset + 7] = blue; glyph[offset + 8] = alpha;
    }

    public void appendTo(FloatBuffer output) {
        if (count != 4) throw new IllegalStateException("A font glyph does not have four vertices");
        for (int vertex : TRIANGLE_ORDER) output.put(glyph, vertex * STRIDE, STRIDE);
    }
}
