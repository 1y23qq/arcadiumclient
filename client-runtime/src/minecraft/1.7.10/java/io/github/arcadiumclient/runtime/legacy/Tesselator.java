// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import net.minecraft.client.render.vertex.LegacyTesselatorBackend;

public final class Tesselator {
    private static final Tesselator INSTANCE = new Tesselator();
    private final BufferBuilder buffer = new BufferBuilder();
    private Tesselator() {}
    public static Tesselator getInstance() { return INSTANCE; }
    public BufferBuilder getBuffer() { return buffer; }
    public int end() { return LegacyTesselatorBackend.INSTANCE.end(); }
}
