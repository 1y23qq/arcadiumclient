// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.debug.*;
import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.overlay.DebugOverlay;
import net.minecraft.client.render.Window;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
public abstract class CustomF3HudMixin {
    @Shadow @Final private DebugOverlay debugOverlay;
    @Inject(method = "render(F)V", at = @At("TAIL"))
    private void arcadium$draw(float delta, CallbackInfo callback) {
        if (CustomF3Renderer.needsFrame()) CustomF3Renderer.render(new Window(Minecraft.getInstance()), (DebugInfoSource)debugOverlay);
    }
    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void arcadium$sidebar(CallbackInfo callback) { if (CustomF3Renderer.showing() && CustomF3Config.getHideSidebar()) callback.cancel(); }
    @Inject(method = "renderBossBars", at = @At("HEAD"), cancellable = true)
    private void arcadium$bossbar(CallbackInfo callback) { if (CustomF3Renderer.showing() && CustomF3Config.getHideBossbar()) callback.cancel(); }
}
