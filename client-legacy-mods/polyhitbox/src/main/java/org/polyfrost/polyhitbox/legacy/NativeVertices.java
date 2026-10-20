// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.polyhitbox.legacy;

import net.minecraft.client.render.vertex.Tesselator;

public final class NativeVertices {
    public static final NativeVertices INSTANCE = new NativeVertices();
    private float x, y, z;
    private boolean drawing;
    private NativeVertices() {}
    public void begin(int mode) {
        Tesselator.INSTANCE.begin(mode);
        drawing = true;
    }
    public NativeVertices addVertex(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        return this;
    }
    public NativeVertices setColor(int argb) {
        Tesselator.INSTANCE.color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
        Tesselator.INSTANCE.vertex(x, y, z);
        return this;
    }
    public void finish() {
        if (drawing) {
            drawing = false;
            Tesselator.INSTANCE.end();
        }
    }
}
