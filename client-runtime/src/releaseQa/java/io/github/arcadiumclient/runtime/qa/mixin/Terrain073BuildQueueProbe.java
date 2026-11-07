// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.TerrainBuildEligibility073;
import org.embeddedt.embeddium.impl.render.chunk.*;
import org.embeddedt.embeddium.impl.render.chunk.compile.executor.ChunkJobCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=RenderSectionManager.class,remap=false)
public abstract class Terrain073BuildQueueProbe {
    @Inject(method="submitRebuildTasks",at=@At("HEAD"),remap=false)
    private void terrain073$queue(ChunkJobCollector collector,ChunkUpdateType type,CallbackInfo callback){TerrainBuildEligibility073.queue(this,type);}
}
