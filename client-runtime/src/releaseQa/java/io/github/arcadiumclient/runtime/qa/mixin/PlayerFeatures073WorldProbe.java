// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.PlayerFeatures073Qa;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GameRenderer.class)
public abstract class PlayerFeatures073WorldProbe {
    @WrapMethod(method="renderWorld") private void features073$world(float delta,long deadline,Operation<Void> original){PlayerFeatures073Qa.world(delta,deadline,original);}
}
