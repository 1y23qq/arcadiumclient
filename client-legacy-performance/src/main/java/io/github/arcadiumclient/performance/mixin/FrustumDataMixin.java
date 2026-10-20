// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.mixin;

import io.github.arcadiumclient.performance.FrustumIntersection;
import net.minecraft.client.render.FrustumData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FrustumData.class)
public abstract class FrustumDataMixin {
    @Shadow public float[][] frustum;

    /**
     * @author Arcadium
     * @reason Preserve plane visibility while avoiding redundant corner projections.
     */
    @Overwrite
    public boolean contains(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return FrustumIntersection.contains(frustum, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
