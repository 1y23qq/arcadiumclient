// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

import com.mojang.blaze3d.vertex.MeshData;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class GlintDrawDebug {
    private static final boolean ENABLED = Boolean.getBoolean("arcadium.glint.debug");
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Arcadium/Glint");
    private static int vertexSamples;
    private static final Set<RenderType> SEEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private GlintDrawDebug() { }
    public static void start(RenderType type, Object owner) {
        if (ENABLED) LOGGER.info("Glint fixed buffer allocated type={} source={}", type, owner.getClass().getName());
    }
    public static void vertex(int color) {
        if (ENABLED && vertexSamples++ < 8) LOGGER.info("Glint vertex tint={}", Integer.toHexString(color));
    }
    public static void capture(RenderType type, MeshData mesh) {
        if (!ENABLED || !ModernGlint.owns(type) || !SEEN.add(type)) return;
        var bytes = mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());
        String vertex = bytes.remaining() >= 24 ? "pos=" + bytes.getFloat(0) + "," + bytes.getFloat(4) + "," + bytes.getFloat(8) + " uv=" + bytes.getFloat(12) + "," + bytes.getFloat(16) + " packed=" + Integer.toHexString(bytes.getInt(20)) : "short";
        var color = ModernGlint.uniform(type, new org.joml.Vector4f(1));
        LOGGER.info("Glint draw {} vertices={} {} uniform={} depth={} blend={} format={}", type, mesh.drawState().vertexCount(), vertex, color, type.pipeline().getDepthTestFunction(), type.pipeline().getBlendFunction(), type.format());
    }
}
