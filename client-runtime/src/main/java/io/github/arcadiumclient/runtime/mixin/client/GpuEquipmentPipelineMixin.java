// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.arcadiumclient.runtime.client.optimizations.GpuEquipmentPipeline;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArmorLayer.class)
public abstract class GpuEquipmentPipelineMixin {
    @WrapWithCondition(method={"renderArmor","renderEnchantmentGlint"},at=@At(value="INVOKE",target="Lnet/minecraft/client/render/model/Model;render(Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private boolean arcadium$compactArmor(Model model,Entity entity,float walk,float speed,float age,float yaw,float pitch,float scale){
        return !GpuEquipmentPipeline.capture(model,entity,walk,speed,age,yaw,pitch,scale);
    }
}
