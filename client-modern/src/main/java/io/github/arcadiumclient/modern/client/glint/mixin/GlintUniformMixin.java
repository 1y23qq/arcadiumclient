// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import io.github.arcadiumclient.modern.client.glint.*;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.MeshData;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderType.class)
public class GlintUniformMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void arcadium$trace(MeshData mesh, CallbackInfo callback) { GlintDrawDebug.capture((RenderType) (Object) this, mesh); }
    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DynamicUniforms;writeTransform(Lorg/joml/Matrix4fc;Lorg/joml/Vector4fc;Lorg/joml/Vector3fc;Lorg/joml/Matrix4fc;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"), index = 1)
    private Vector4fc arcadium$armorTint(Vector4fc original) {
        return ModernGlint.uniform((RenderType) (Object) this, original);
    }
}
