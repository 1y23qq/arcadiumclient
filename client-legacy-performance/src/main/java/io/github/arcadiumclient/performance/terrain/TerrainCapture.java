// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.terrain;

public record TerrainCapture(int[] vertices, int vertexCount, int drawMode, int format) {
    public static final int TEXTURE = 1;
    public static final int LIGHT = 2;
    public static final int COLOR = 4;
    public static final int NORMAL = 8;

    public int byteSize() {
        return Math.multiplyExact(vertexCount, TerrainVertices.STRIDE);
    }
}
