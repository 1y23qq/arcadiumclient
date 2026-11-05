// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.HudReuse072Qa;
import io.github.arcadiumclient.runtime.qa.HudReuse072BufferAccess;
import net.minecraft.client.render.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
public abstract class HudReuse072BufferProbe implements HudReuse072BufferAccess {
    @Override @Accessor("id") public abstract int arcadium$hudBufferId();
    @Override @Accessor("vertexCount") public abstract int arcadium$hudVertexCount();
    @Inject(method = "delete", at = @At("HEAD"))
    private void arcadium$beforeNativeDelete(CallbackInfo callback) { HudReuse072Qa.deleting((VertexBuffer)(Object)this); }
    @Inject(method = "delete", at = @At("RETURN"))
    private void arcadium$afterNativeDelete(CallbackInfo callback) { HudReuse072Qa.deleted((VertexBuffer)(Object)this); }
}
