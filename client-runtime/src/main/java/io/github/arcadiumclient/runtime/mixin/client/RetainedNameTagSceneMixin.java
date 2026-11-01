// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class RetainedNameTagSceneMixin {
    @Inject(method="renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/platform/GlStateManager;pushMatrix()V"),cancellable=true)
    private void arcadium$retainedScene(Entity entity,String name,double x,double y,double z,int distance,CallbackInfo callback){
        if(RetainedNameTags.begin(entity,name,x,y,z))callback.cancel();
    }
    @Inject(method="renderNameTag(Lnet/minecraft/entity/Entity;Ljava/lang/String;DDDI)V",at=@At("RETURN"))
    private void arcadium$finishScene(Entity entity,String name,double x,double y,double z,int distance,CallbackInfo callback){RetainedNameTags.end();}
}
