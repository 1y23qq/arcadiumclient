// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentScene;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@IfModLoaded("argentum")
@Mixin(targets="org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage",remap=false)
public abstract class MixinTerrainResidentStorage {
    @Inject(method={"setMeshes","removeMeshes","removeIndexBuffer","replaceIndexBuffer"},at=@At("TAIL"),remap=false)
    private void terrain072$sectionPublished(CallbackInfo callback,@Local(argsOnly=true,ordinal=0) int slot){if(GpuTerrainCommands.residentAttached())TerrainResidentScene.sourceChanged(this,slot);}
    @Inject(method="onBufferResized",at=@At("TAIL"),remap=false)
    private void terrain072$arenaRelocated(CallbackInfo callback){if(GpuTerrainCommands.residentAttached())TerrainResidentScene.sourceChanged(this);}
    @Inject(method="delete",at=@At("HEAD"),remap=false)
    private void terrain072$storageDeleted(CallbackInfo callback){if(GpuTerrainCommands.residentAttached())TerrainResidentScene.released(this);}
}
