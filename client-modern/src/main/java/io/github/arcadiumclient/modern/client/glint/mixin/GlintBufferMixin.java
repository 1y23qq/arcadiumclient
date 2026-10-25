// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint.mixin;

import com.mojang.blaze3d.vertex.*;
import io.github.arcadiumclient.modern.client.glint.*;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiBufferSource.BufferSource.class)
public class GlintBufferMixin implements GlintBufferAccess {
    @Shadow @Final @Mutable protected SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
    @Unique private GlintBuffers.Owner arcadium$owner;
    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void arcadium$fixed(RenderType type, CallbackInfoReturnable<VertexConsumer> callback) {
        arcadium$fixGlintBuffer(type);
    }
    public void arcadium$fixGlintBuffer(RenderType type) {
        if (!ModernGlint.owns(type) || fixedBuffers.containsKey(type)) return;
        if (arcadium$owner == null) { arcadium$owner = GlintBuffers.owner(this); fixedBuffers = new LinkedHashMap<>(fixedBuffers); }
        fixedBuffers.put(type, arcadium$owner.allocate());
        GlintDrawDebug.start(type, this);
    }
}
