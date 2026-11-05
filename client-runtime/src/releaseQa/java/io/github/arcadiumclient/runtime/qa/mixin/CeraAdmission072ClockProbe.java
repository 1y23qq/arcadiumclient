// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(AbstractArmorLayer.class)
public abstract class CeraAdmission072ClockProbe {
    @ModifyExpressionValue(method="renderEnchantmentGlint",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;getTime()J"),require=0)
    private long cera$clock(long original){return CeraAdmission072Qa.clock(original);}
}
