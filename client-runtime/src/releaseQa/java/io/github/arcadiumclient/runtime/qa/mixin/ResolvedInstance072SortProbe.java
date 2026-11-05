// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.qa.ResolvedInstance072Qa;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
@Pseudo
@Mixin(targets = "dev.rdh.argentum.api.render.instancing.InstanceDataBuffer", remap = false)
public abstract class ResolvedInstance072SortProbe {
    @WrapMethod(method = "sortBackToFront")
    private void resolved$sort(Operation<Void> original) { ResolvedInstance072Qa.sorted(); original.call(); }
}
