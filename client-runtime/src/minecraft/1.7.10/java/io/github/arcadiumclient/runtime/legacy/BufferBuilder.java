// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import net.minecraft.client.render.vertex.LegacyTesselatorBackend;

public final class BufferBuilder {
    private double x, y, z;
    public void begin(int mode, DefaultVertexFormat format) { LegacyTesselatorBackend.INSTANCE.begin(mode); }
    public BufferBuilder vertex(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
    public BufferBuilder color(int r, int g, int b, int a) { LegacyTesselatorBackend.INSTANCE.color(r, g, b, a); return this; }
    public BufferBuilder color(float r, float g, float b, float a) { LegacyTesselatorBackend.INSTANCE.color(r, g, b, a); return this; }
    public void nextVertex() { LegacyTesselatorBackend.INSTANCE.vertex(x, y, z); }
}
