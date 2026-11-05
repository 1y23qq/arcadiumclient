// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.SkinLayersBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;

public final class GpuSkinLayers {
    public static long hybridPlayers, voxelParts;

    private GpuSkinLayers() { }

    public static String status() {
        return BetaOptimizationsConfig.getGpuPlayerPipeline()
                ? "Skin layer hybrid: " + hybridPlayers + " players | Native voxel parts: " + voxelParts
                : "Skin layer hybrid: Off";
    }

    static int mask(ModelPart[] parts) {
        if (!Skin3DConfig.getEnabled()) return 0;
        int result = 0;
        for (int i = 0; i < parts.length; i++) {
            ModelPart part = parts[i];
            if (part.visible && !part.invisible && SkinLayersBridge.hasMesh(part)) result |= 1 << i;
        }
        return result;
    }

    static void render(ModelPart[] parts, int mask, Identifier texture, boolean sneaking) {
        if (mask == 0) return;
        Minecraft.getInstance().getTextureManager().bind(texture);
        GlStateManager.color4f(1, 1, 1, 1);
        GlStateManager.pushMatrix();
        try {
            if (sneaking && (PlayerPoseOptions.flags() & PlayerPoseOptions.OLD_SNEAK) == 0)
                GlStateManager.translatef(0, .2f, 0);
            for (int i = 0; i < parts.length; i++) {
                if ((mask & (1 << i)) == 0) continue;
                parts[i].render(.0625f);
                voxelParts++;
            }
            hybridPlayers++;
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
