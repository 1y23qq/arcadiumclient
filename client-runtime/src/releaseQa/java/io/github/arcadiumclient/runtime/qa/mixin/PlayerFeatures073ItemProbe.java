// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import net.minecraft.client.render.entity.layer.ItemInHandLayer;
import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandLayer.class)
public abstract class PlayerFeatures073ItemProbe {
    @Inject(method="render(Lnet/minecraft/entity/living/LivingEntity;FFFFFFF)V",at=@At("HEAD"))
    private void features073$item(LivingEntity entity,float walk,float speed,float delta,float age,float yaw,float pitch,float scale,CallbackInfo callback){PlayerFeatures073Qa.item();}
}
