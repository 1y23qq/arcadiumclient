// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public abstract class BadlionPlayerMixin {
    @Inject(method = "getMainArm", at = @At("RETURN"), cancellable = true)
    private void arcadium$hand(CallbackInfoReturnable<HumanoidArm> cir) {
        var settings = AnimationsConfig.badlion();
        if ((Object) this == Minecraft.getInstance().player && settings != null && settings.leftHanded()) cir.setReturnValue(cir.getReturnValue().getOpposite());
    }
}
