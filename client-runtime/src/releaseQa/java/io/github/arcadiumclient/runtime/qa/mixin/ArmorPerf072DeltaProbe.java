// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.ArmorPerf072Qa;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(GameRenderer.class)
public abstract class ArmorPerf072DeltaProbe {
    @ModifyVariable(method="renderWorld(FJ)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float armor$delta(float original){return ArmorPerf072Qa.delta(original);}
}
