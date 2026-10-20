// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.terrain;

import java.nio.IntBuffer;

public final class TerrainVertices {
    public static final int STRIDE = 32;
    public static final int INTS_PER_VERTEX = STRIDE / Integer.BYTES;
    public static final float SECTION_SCALE = 1.000001F;

    private TerrainVertices() {}

    public static float transform(float position, int offset) {
        // Matches T(offset) * T(-8) * S(1.000001) * T(8).
        return (float) ((double) position * SECTION_SCALE + (offset - 8.0 + 8.0 * SECTION_SCALE));
    }

    public static void write(int[] source, int vertexCount, int x, int y, int z, IntBuffer target) {
        if (vertexCount < 0 || (long) vertexCount * INTS_PER_VERTEX > source.length) {
            throw new IllegalArgumentException("Invalid terrain vertex count");
        }
        for (int i = 0, end = vertexCount * INTS_PER_VERTEX; i < end; i += INTS_PER_VERTEX) {
            target.put(Float.floatToRawIntBits(transform(Float.intBitsToFloat(source[i]), x)));
            target.put(Float.floatToRawIntBits(transform(Float.intBitsToFloat(source[i + 1]), y)));
            target.put(Float.floatToRawIntBits(transform(Float.intBitsToFloat(source[i + 2]), z)));
            target.put(source, i + 3, INTS_PER_VERTEX - 3);
        }
    }
}
