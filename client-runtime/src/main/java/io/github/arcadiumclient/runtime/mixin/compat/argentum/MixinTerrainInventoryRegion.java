// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentInventoryHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
@Pseudo
@IfModLoaded("argentum")
@Mixin(value=RenderRegion.class,remap=false)
public abstract class MixinTerrainInventoryRegion {
 @WrapMethod(method="createStorage") private SectionRenderDataStorage inventoryStorage(TerrainRenderPass pass,RenderPassConfiguration<?> config,Operation<SectionRenderDataStorage> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{var result=original.call(pass,config);TerrainResidentInventoryHooks.storage((RenderRegion)(Object)this,pass,result);ok=true;return result;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="delete") private void inventoryDelete(CommandList device,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(device);TerrainResidentInventoryHooks.deletedRegion((RenderRegion)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="refresh") private void inventoryRefresh(CommandList device,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(device);TerrainResidentInventoryHooks.bindings((RenderRegion)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="invalidateCachedBatches") private void inventoryBindings(Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call();TerrainResidentInventoryHooks.bindings((RenderRegion)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="updateSectionLoadTime") private void inventoryLoad(RenderSection section,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(section);TerrainResidentInventoryHooks.loadTime((RenderRegion)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="removeEmptyStorages") private void inventoryPrune(Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call();ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="removeMeshes") private void inventoryRemoveMeshes(int section,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(section);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method={"addSection","removeSection"}) private void inventoryMembership(RenderSection section,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(section);TerrainResidentInventoryHooks.loadTime((RenderRegion)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
}
