// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.NickInputProjection073Qa;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class NickInput073Driver {
    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$nickInput(CallbackInfo callback) {
        if (NickInputProjection073Qa.enabled()) NickInputProjection073Qa.tick();
    }
}
