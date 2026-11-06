// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.runtime.client.features.StarHider;
import net.minecraft.client.gui.GameGui;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public abstract class StarHiderMixin {
    @ModifyExpressionValue(method="renderXpBar",at=@At(value="FIELD",target="Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;xpLevel:I",opcode=Opcodes.GETFIELD))
    private int arcadium$displayedExperienceLevel(int original){return StarHider.display(original);}
}
