// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.colorsaturation.CategoryColorRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class CategoryColorLifecycleMixin {
    @Inject(method = {"reloadResources()V", "shutdown()V", "setWorld(Lnet/minecraft/client/world/ClientWorld;)V"}, at = @At("HEAD"))
    private void arcadium$releaseCategoryColors(CallbackInfo callback) { CategoryColorRenderer.free(); }
}
