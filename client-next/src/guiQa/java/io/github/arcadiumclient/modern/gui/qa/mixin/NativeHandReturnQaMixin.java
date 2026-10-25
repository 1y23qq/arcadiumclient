// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa.mixin;

import io.github.arcadiumclient.modern.gui.qa.NativeRuntimeProbe;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class NativeHandReturnQaMixin {
    @Inject(method = "submitHandsWithItems", at = @At("RETURN"))
    private void arcadium$completed(CallbackInfo ci) { NativeRuntimeProbe.handReturn(); }
}
