// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.runtime.client.debug.CustomF3Renderer;
import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.render.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class CustomF3HudMixin {
    @ModifyExpressionValue(method = "render(FZII)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;debugEnabled:Z"))
    private boolean arcadium$nativeDebug(boolean original) { return original && !CustomF3Config.getEnabled(); }
    @Inject(method = "render(FZII)V", at = @At("TAIL"))
    private void arcadium$draw(float delta, boolean mouse, int x, int y, CallbackInfo callback) {
        if (CustomF3Renderer.needsFrame()) {
            Minecraft mc = Minecraft.getInstance(); CustomF3Renderer.render(new Window(mc, mc.width, mc.height), null);
        }
    }
    @Inject(method = "renderSidebarObjective", at = @At("HEAD"), cancellable = true)
    private void arcadium$sidebar(CallbackInfo callback) { if (CustomF3Renderer.showing() && CustomF3Config.getHideSidebar()) callback.cancel(); }
    @Inject(method = "renderBossBars", at = @At("HEAD"), cancellable = true)
    private void arcadium$bossbar(CallbackInfo callback) { if (CustomF3Renderer.showing() && CustomF3Config.getHideBossbar()) callback.cancel(); }
}
