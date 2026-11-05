// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinUploadFacts;
import java.awt.image.BufferedImage;
import net.minecraft.client.render.texture.HttpTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HttpTexture.class)
public abstract class MixinGpuVoxelSkinUpload {
    @WrapOperation(method="upload",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/texture/TextureUtil;uploadTexture(ILjava/awt/image/BufferedImage;)I"))
    private int arcadium$uploadAlpha(int texture,BufferedImage image,Operation<Integer> original){
        return GpuVoxelSkinUploadFacts.upload(this,texture,image,()->original.call(texture,image));
    }
}
