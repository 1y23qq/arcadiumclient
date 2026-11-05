// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GameRenderer.class)
public abstract class CeraAdmission072WorldProbe {
    @WrapMethod(method="renderWorld") private void cera$world(float delta,long deadline,Operation<Void> original){CeraAdmission072Qa.world(delta,deadline,original);}
}
