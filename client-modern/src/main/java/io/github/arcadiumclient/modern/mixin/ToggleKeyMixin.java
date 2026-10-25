// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.ToggleSprintController;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public abstract class ToggleKeyMixin {
    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true)
    private void arcadium$toggle(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        if ((Object) this == mc.options.keySprint) cir.setReturnValue(ToggleSprintController.sprint(cir.getReturnValue()));
        else if ((Object) this == mc.options.keyShift) cir.setReturnValue(ToggleSprintController.sneak(cir.getReturnValue()));
    }
}
