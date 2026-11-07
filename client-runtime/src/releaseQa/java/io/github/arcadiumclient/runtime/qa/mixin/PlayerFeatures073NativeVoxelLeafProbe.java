// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073NativeVoxelEvidence;
import net.minecraft.client.render.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(GlStateManager.class)
public abstract class PlayerFeatures073NativeVoxelLeafProbe {
    @WrapOperation(method="callList",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glCallList(I)V",remap=false))
    private static void features073$nativeLeaf(int list,Operation<Void> original){PlayerFeatures073NativeVoxelEvidence.nativeList(list);original.call(list);}
}
