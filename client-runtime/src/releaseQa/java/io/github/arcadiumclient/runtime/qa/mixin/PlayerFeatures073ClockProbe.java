// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import net.minecraft.client.render.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ItemRenderer.class)
public abstract class PlayerFeatures073ClockProbe {
    @ModifyExpressionValue(method="renderEnchantmentGlint",at=@At(value="INVOKE",target="Lnet/minecraft/client/Minecraft;getTime()J"),require=2)
    private long features073$clock(long original){return PlayerFeatures073Qa.clock(original);}
}
