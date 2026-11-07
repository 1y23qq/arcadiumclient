// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentInventoryHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@IfModLoaded("argentum")
@Mixin(value=RenderSectionManager.class,remap=false)
public abstract class MixinTerrainInventoryManager {
 @Inject(method="<init>*",at=@At("RETURN")) private void inventoryAttach(CallbackInfo callback){TerrainResidentInventoryHooks.attach((RenderSectionManager)(Object)this);}
 @WrapMethod(method="destroy") private void inventoryRetire(Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call();ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);if(ok)TerrainResidentInventoryHooks.retired((RenderSectionManager)(Object)this);}}
}
