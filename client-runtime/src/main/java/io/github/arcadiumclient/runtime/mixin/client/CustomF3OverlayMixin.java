// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.debug.DebugInfoSource;
import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import net.minecraft.client.gui.overlay.DebugOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

@Mixin(DebugOverlay.class)
public abstract class CustomF3OverlayMixin implements DebugInfoSource {
    @Invoker("getGameInfo") public abstract List<String> arcadium$gameInfo();
    @Invoker("getSystemInfo") public abstract List<String> arcadium$systemInfo();
    @Inject(method = {"drawGameInfo", "drawSystemInfo"}, at = @At("HEAD"), cancellable = true)
    private void arcadium$customText(CallbackInfo callback) { if (CustomF3Config.getEnabled()) callback.cancel(); }
}
