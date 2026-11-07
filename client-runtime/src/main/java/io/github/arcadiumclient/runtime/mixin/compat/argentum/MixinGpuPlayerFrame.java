// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPackets;
import io.github.arcadiumclient.runtime.client.optimizations.GpuResidentPlayerState;
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.util.List;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfModLoaded("argentum")
@Mixin(GameRenderer.class)
public abstract class MixinGpuPlayerFrame {
    @Inject(method="render(FJ)V",at=@At("HEAD"))
    private void arcadium$visiblePlayers(float delta,long nanoTime,CallbackInfo callback){
        ModernArmorGlintPipeline.beginFrame();
        Minecraft mc=Minecraft.getInstance();
        GpuPlayerPackets.beginActorFrame();
        GpuPlayerPackets.prepareActors(mc.world,mc.world==null?List.of():mc.world.players,
                BetaOptimizationsConfig.getGpuPlayerPipeline()&&(GpuResidentPlayerState.canRetainActors()||GpuPlayerPackets.actorSceneReady())&&!GpuPlayerPipeline.hasForgeHooks());
        if(Minecraft.getInstance().world==null)GpuModelTransforms.clearFrameBudget();
        else GpuModelTransforms.beginFrame();
    }
}
