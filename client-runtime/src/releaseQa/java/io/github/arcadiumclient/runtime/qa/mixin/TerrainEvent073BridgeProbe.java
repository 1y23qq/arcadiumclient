// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentInventoryHooks;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainEvent073Evidence;
import org.spongepowered.asm.mixin.Mixin;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=TerrainResidentInventoryHooks.class,remap=false)
public abstract class TerrainEvent073BridgeProbe {
 @Inject(method={"attach","region","storage","deletedStorage","deletedRegion","published","bindings","loadTime","retired","shutdown"},at=@At("RETURN"))
 private static void eventReturned(CallbackInfo callback){TerrainEvent073Evidence.hook(callback.getId());}
 @Inject(method="section",at=@At("RETURN")) private static void eventSection(SectionRenderDataStorage storage,int section,CallbackInfo callback){TerrainEvent073Evidence.source("section",storage,section);}
 @Inject(method="relocated",at=@At("RETURN")) private static void eventRelocated(SectionRenderDataStorage storage,CallbackInfo callback){TerrainEvent073Evidence.source("relocated",storage,-1);}
}
