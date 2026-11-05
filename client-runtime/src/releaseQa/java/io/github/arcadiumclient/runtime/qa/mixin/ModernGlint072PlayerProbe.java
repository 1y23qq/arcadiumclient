// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class ModernGlint072PlayerProbe {
    @Inject(method="render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V",at=@At("HEAD"))
    private void modern$entry(ClientPlayerEntity entity,double x,double y,double z,float yaw,float delta,CallbackInfo callback){ModernGlint072Qa.playerEntry(entity,x,y,z);}
}
