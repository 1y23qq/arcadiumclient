// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentInventoryHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.util.Map;
import org.embeddedt.embeddium.impl.gl.arena.GlBufferSegment;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
@Pseudo
@IfModLoaded("argentum")
@Mixin(value=SectionRenderDataStorage.class,remap=false)
public abstract class MixinTerrainInventoryStorage {
 @WrapMethod(method="setMeshes") private void inventorySet(int section,GlBufferSegment vertices,GlBufferSegment indices,Map<ModelQuadFacing,VertexRange> ranges,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(section,vertices,indices,ranges);TerrainResidentInventoryHooks.section((SectionRenderDataStorage)(Object)this,section);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method={"removeMeshes","removeIndexBuffer"}) private void inventoryRemove(int section,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(section);TerrainResidentInventoryHooks.section((SectionRenderDataStorage)(Object)this,section);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="replaceIndexBuffer") private void inventoryIndex(int section,GlBufferSegment indices,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(section,indices);TerrainResidentInventoryHooks.section((SectionRenderDataStorage)(Object)this,section);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="onBufferResized") private void inventoryResize(Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call();TerrainResidentInventoryHooks.relocated((SectionRenderDataStorage)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="delete") private void inventoryDelete(Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call();TerrainResidentInventoryHooks.deletedStorage((SectionRenderDataStorage)(Object)this);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
}
