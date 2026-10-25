// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class NativeLines {
    private NativeLines() {}
    public static void segment(VertexConsumer vertices, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int color, float width) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0 || !Float.isFinite(length)) return;
        float nx = dx / length, ny = dy / length, nz = dz / length;
        vertices.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
        vertices.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }
}
