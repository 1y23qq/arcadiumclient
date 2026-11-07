// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentInventoryHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import java.util.Collection;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.render.chunk.region.*;
@Pseudo
@IfModLoaded("argentum")
@Mixin(value=RenderRegionManager.class,remap=false)
public abstract class MixinTerrainInventoryRegionManager {
 @WrapMethod(method="create") private RenderRegion inventoryCreate(int x,int y,int z,Operation<RenderRegion> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{RenderRegion result=original.call(x,y,z);TerrainResidentInventoryHooks.region((RenderRegionManager)(Object)this,result);ok=true;return result;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="uploadMeshes") private void inventoryUpload(CommandList device,Collection<?> results,Runnable callback,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(device,results,callback);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="update") private void inventoryUpdate(Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call();ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
 @WrapMethod(method="delete") private void inventoryDelete(CommandList device,Operation<Void> original){var scope=TerrainResidentInventoryHooks.begin(this);boolean ok=false;try{original.call(device);ok=true;}finally{TerrainResidentInventoryHooks.end(scope,ok);}}
}
