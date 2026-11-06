// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(PlayerRenderer.class)
public abstract class PlayerFeatures073PlayerProbe {
    @WrapMethod(method="render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V")
    private void features073$player(ClientPlayerEntity entity,double x,double y,double z,float yaw,float delta,Operation<Void> original){
        if(!PlayerFeatures073Qa.enabled()){original.call(entity,x,y,z,yaw,delta);return;}
        int actor=PlayerFeatures073Qa.player(entity);
        if(actor<0){original.call(entity,x,y,z,yaw,delta);return;}
        long[] before=PlayerFeatures073Qa.playerMetrics();
        try{original.call(entity,x,y,z,yaw,delta);}finally{PlayerFeatures073Qa.playerEnd(actor,before);}
    }
}
