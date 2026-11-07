// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentInventoryHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.embeddedt.embeddium.impl.render.chunk.lists.RenderListManager;
@Pseudo
@IfModLoaded("argentum")
@Mixin(value=RenderListManager.class,remap=false)
public abstract class MixinTerrainInventoryLists {
 @WrapMethod(method="finishPreviousGraphUpdate") private void inventoryPublish(Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call();TerrainResidentInventoryHooks.published((RenderListManager)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
}
