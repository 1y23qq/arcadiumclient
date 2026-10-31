// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.CustomF3Config;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public abstract class CustomF3KeyboardMixin {
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void arcadium$debugKeys(KeyEvent key, CallbackInfoReturnable<Boolean> callback) {
        if (!CustomF3Config.getEnabled()) return;
        if (key.key() == 77) { CustomF3Config.INSTANCE.edit(); callback.setReturnValue(true); }
        else if (key.key() == 70) {
            var options = Minecraft.getInstance().options;
            var distance = key.hasControlDown() ? options.simulationDistance() : options.renderDistance();
            distance.set(Math.max(key.hasControlDown() ? 5 : 2, Math.min(32, distance.get() + (key.hasShiftDown() ? -1 : 1))));
            callback.setReturnValue(true);
        }
    }
}
