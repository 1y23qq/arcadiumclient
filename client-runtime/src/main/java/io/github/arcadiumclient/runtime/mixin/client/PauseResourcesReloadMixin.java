// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.LiteralText;
import org.apache.logging.log4j.LogManager;
import org.polyfrost.oneconfig.internal.legacy.compat.MinecraftCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class PauseResourcesReloadMixin {
    @Unique private static final int ARCADIUM_RELOAD_RESOURCES = 36078;
    @Unique private boolean arcadium$reloadPending;
    @Unique private ButtonWidget arcadium$reloadButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void arcadium$reloadButton(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        arcadium$reloadButton = new ButtonWidget(ARCADIUM_RELOAD_RESOURCES, 8, Math.max(8, screen.height - 28),
            Math.min(140, Math.max(60, screen.width - 16)), 20, "Reload Resources");
        arcadium$reloadButton.active = !arcadium$reloadPending;
        ((ScreenAccess) this).arcadium$buttons().add(arcadium$reloadButton);
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void arcadium$reloadResources(ButtonWidget button, CallbackInfo ci) {
        if (button.id != ARCADIUM_RELOAD_RESOURCES) return;
        ci.cancel();
        if (!button.active || arcadium$reloadPending) return;
        arcadium$reloadPending = true;
        button.active = false;
        button.message = "Reloading...";
        Minecraft client = Minecraft.getInstance();
        ((MinecraftCompat) (Object) client).tell(() -> {
            try {
                client.reloadResources();
            } catch (RuntimeException error) {
                LogManager.getLogger("Arcadium").error("Could not reload resources", error);
                client.gui.getChat().addMessage(new LiteralText("Could not reload resources. Check the game log for details."));
            } finally {
                arcadium$reloadPending = false;
                arcadium$reloadButton.active = true;
                arcadium$reloadButton.message = "Reload Resources";
            }
        });
    }
}
