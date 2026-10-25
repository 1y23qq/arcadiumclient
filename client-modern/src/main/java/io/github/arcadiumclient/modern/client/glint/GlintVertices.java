// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

import com.mojang.blaze3d.vertex.VertexConsumer;

public final class GlintVertices implements VertexConsumer {
    private final VertexConsumer target;
    private final int color;
    public GlintVertices(VertexConsumer target, int color) { this.target = target; this.color = color; }
    public VertexConsumer addVertex(float x, float y, float z) { target.addVertex(x, y, z); return this; }
    public VertexConsumer setColor(int r, int g, int b, int a) { target.setColor(color); GlintDrawDebug.vertex(color); return this; }
    public VertexConsumer setColor(int color) { target.setColor(this.color); GlintDrawDebug.vertex(this.color); return this; }
    public VertexConsumer setUv(float u, float v) { target.setUv(u, v); return this; }
    public VertexConsumer setUv1(int u, int v) { target.setUv1(u, v); return this; }
    public VertexConsumer setUv2(int u, int v) { target.setUv2(u, v); return this; }
    public VertexConsumer setNormal(float x, float y, float z) { target.setNormal(x, y, z); return this; }
    public VertexConsumer setLineWidth(float width) { target.setLineWidth(width); return this; }
}
