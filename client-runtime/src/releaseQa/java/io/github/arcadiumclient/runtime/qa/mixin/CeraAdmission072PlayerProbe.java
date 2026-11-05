// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerRenderer.class)
public abstract class CeraAdmission072PlayerProbe {
    @Inject(method="render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V",at=@At("HEAD"))
    private void cera$enter(ClientPlayerEntity entity,double x,double y,double z,float yaw,float delta,CallbackInfo callback){CeraAdmission072Qa.player(entity,true);}
    @Inject(method="render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V",at=@At("RETURN"))
    private void cera$exit(ClientPlayerEntity entity,double x,double y,double z,float yaw,float delta,CallbackInfo callback){CeraAdmission072Qa.player(entity,false);}
}
