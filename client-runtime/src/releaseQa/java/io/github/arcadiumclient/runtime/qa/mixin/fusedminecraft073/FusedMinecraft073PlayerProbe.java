// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.fusedminecraft073;
import io.github.arcadiumclient.runtime.client.optimizations.FusedMinecraft073Evidence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(PlayerRenderer.class)
public abstract class FusedMinecraft073PlayerProbe {
 @WrapMethod(method="render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V")
 private void fused073$player(ClientPlayerEntity player,double x,double y,double z,float yaw,float partial,Operation<Void> original){int token=FusedMinecraft073Evidence.enter(player);try{original.call(player,x,y,z,yaw,partial);}finally{FusedMinecraft073Evidence.leave(token);}}
}
