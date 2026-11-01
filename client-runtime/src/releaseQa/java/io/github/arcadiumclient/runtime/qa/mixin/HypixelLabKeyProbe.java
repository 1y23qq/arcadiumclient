// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.arcadiumclient.runtime.qa.HypixelLabQa;
import net.minecraft.client.options.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyBinding.class)
public abstract class HypixelLabKeyProbe {
    @ModifyReturnValue(method="isPressed()Z",at=@At("RETURN"))
    private boolean hypixel$ordinaryInput(boolean original){return HypixelLabQa.keyState((KeyBinding)(Object)this,original);}
}
