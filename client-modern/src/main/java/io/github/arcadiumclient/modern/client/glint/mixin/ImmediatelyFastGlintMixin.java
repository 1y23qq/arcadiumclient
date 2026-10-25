// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.client.glint.GlintBufferAccess;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.raphimc.immediatelyfast.feature.core.BatchableBufferSource", remap = false)
public class ImmediatelyFastGlintMixin {
    @Inject(method = "method_73477", at = @At("HEAD"), remap = false)
    private void arcadium$fixGlintOrder(RenderType type, CallbackInfoReturnable<VertexConsumer> callback) {
        ((GlintBufferAccess) this).arcadium$fixGlintBuffer(type);
    }
}
