// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.qa.ModernGlintPixelTrace072Qa;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class ModernGlint072ShadowProbe {
    @WrapMethod(method="renderShadow")
    private void modern$shadow(Entity entity,double x,double y,double z,float first,float second,Operation<Void> original){
        int token=ModernGlintPixelTrace072Qa.beginImmediateShadow(this,entity,x,y,z,first,second);
        try{original.call(entity,x,y,z,first,second);}finally{ModernGlintPixelTrace072Qa.endImmediateShadow(token);}
    }

    @WrapOperation(method="renderShadow",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/vertex/Tesselator;end()V"))
    private void modern$draw(Tesselator tesselator,Operation<Void> original){
        int token=ModernGlintPixelTrace072Qa.immediateShadowDraw();
        try{original.call(tesselator);}finally{ModernGlintPixelTrace072Qa.after(token);}
    }
}
