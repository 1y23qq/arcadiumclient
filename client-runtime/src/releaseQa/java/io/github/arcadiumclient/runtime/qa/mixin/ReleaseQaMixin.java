package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ReleaseQa;
import io.github.arcadiumclient.runtime.qa.SessionUiQa;
import io.github.arcadiumclient.runtime.qa.StartupQa;
import io.github.arcadiumclient.runtime.qa.Feature058Qa;
import io.github.arcadiumclient.runtime.qa.Ime058Qa;
import io.github.arcadiumclient.runtime.qa.Chat059Qa;
import io.github.arcadiumclient.runtime.qa.Settings060Qa;
import io.github.arcadiumclient.runtime.qa.Scoreboard060Qa;
import io.github.arcadiumclient.runtime.qa.Features062Qa;
import io.github.arcadiumclient.runtime.qa.Settings063Qa;
import io.github.arcadiumclient.runtime.qa.OptimizationsBetaQa;
import io.github.arcadiumclient.runtime.qa.BetaBaselineQa;
import io.github.arcadiumclient.runtime.qa.NextRenderingQa;
import io.github.arcadiumclient.runtime.qa.HypixelLabQa;
import io.github.arcadiumclient.runtime.qa.ResourcePackSearch065Qa;
import io.github.arcadiumclient.runtime.qa.ParticlePackets065Qa;
import io.github.arcadiumclient.runtime.qa.CategoryColorNativeQa;
import io.github.arcadiumclient.runtime.qa.Settings065Qa;
import io.github.arcadiumclient.runtime.qa.NamePreparation065Qa;
import io.github.arcadiumclient.runtime.qa.HologramNative065Qa;
import io.github.arcadiumclient.runtime.qa.ScoreboardPreparation065Qa;
import io.github.arcadiumclient.runtime.qa.FastFramePresentationQa;
import io.github.arcadiumclient.runtime.qa.OptimizationsUi066Qa;
import io.github.arcadiumclient.runtime.qa.RenderHookAllocation066Qa;
import io.github.arcadiumclient.runtime.qa.MotionBlur066Qa;
import io.github.arcadiumclient.runtime.qa.ParticleOperation066Qa;
import io.github.arcadiumclient.runtime.qa.LayerOperation066Qa;
import io.github.arcadiumclient.runtime.qa.CeraCosmetics066Qa;
import io.github.arcadiumclient.runtime.client.bedwars.Keyless061Qa;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ReleaseQaMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void release$testTick(CallbackInfo ci) { if (CeraCosmetics066Qa.enabled()) CeraCosmetics066Qa.tick(); else if (LayerOperation066Qa.enabled()) LayerOperation066Qa.tick(); else if (ParticleOperation066Qa.enabled()) ParticleOperation066Qa.tick(); else if (MotionBlur066Qa.enabled()) MotionBlur066Qa.tick(); else if (RenderHookAllocation066Qa.enabled()) RenderHookAllocation066Qa.tick(); else if (OptimizationsUi066Qa.enabled()) OptimizationsUi066Qa.tick(); else if (FastFramePresentationQa.enabled()) FastFramePresentationQa.tick(); else if (ScoreboardPreparation065Qa.enabled()) ScoreboardPreparation065Qa.tick(); else if (HologramNative065Qa.enabled()) HologramNative065Qa.tick(); else if (NamePreparation065Qa.enabled()) NamePreparation065Qa.tick(); else if (Settings065Qa.enabled()) Settings065Qa.tick(); else if (CategoryColorNativeQa.enabled()) CategoryColorNativeQa.tick(); else if (ParticlePackets065Qa.enabled()) ParticlePackets065Qa.tick(); else if (ResourcePackSearch065Qa.enabled()) ResourcePackSearch065Qa.tick(); else if (HypixelLabQa.enabled()) HypixelLabQa.tick(); else if (NextRenderingQa.enabled()) NextRenderingQa.tick(); else if (BetaBaselineQa.enabled()) BetaBaselineQa.tick(); else if (OptimizationsBetaQa.enabled()) OptimizationsBetaQa.tick(); else if (Settings063Qa.enabled()) Settings063Qa.tick(); else if (Features062Qa.enabled()) Features062Qa.tick(); else if (Keyless061Qa.enabled()) Keyless061Qa.tick(); else if (Scoreboard060Qa.enabled()) Scoreboard060Qa.tick(); else if (Settings060Qa.enabled()) Settings060Qa.tick(); else if (Chat059Qa.enabled()) Chat059Qa.tick(); else if (Ime058Qa.enabled()) Ime058Qa.tick(); else if (Feature058Qa.enabled()) Feature058Qa.tick(); else if (StartupQa.enabled()) StartupQa.tick(); else if (SessionUiQa.enabled()) SessionUiQa.tick(); else ReleaseQa.tick(); }
    @Inject(method = "runGame", at = @At("TAIL"))
    private void release$testFrame(CallbackInfo ci) { if (CeraCosmetics066Qa.enabled()||LayerOperation066Qa.enabled()||ParticleOperation066Qa.enabled()||MotionBlur066Qa.enabled()||RenderHookAllocation066Qa.enabled()||OptimizationsUi066Qa.enabled()) return; else if (FastFramePresentationQa.enabled()) FastFramePresentationQa.frame(); else if (ScoreboardPreparation065Qa.enabled()||HologramNative065Qa.enabled()||NamePreparation065Qa.enabled()||Settings065Qa.enabled()) return; else if (CategoryColorNativeQa.enabled()) CategoryColorNativeQa.frame(); else if (ParticlePackets065Qa.enabled()||ResourcePackSearch065Qa.enabled()) return; else if (HypixelLabQa.enabled()) HypixelLabQa.frame(); else if (NextRenderingQa.enabled()) NextRenderingQa.frame(); else if (BetaBaselineQa.enabled()) BetaBaselineQa.frame(); else if (OptimizationsBetaQa.enabled()) OptimizationsBetaQa.frame(); else if (Settings063Qa.enabled()) return; else if (Features062Qa.enabled()) Features062Qa.frame(); else if (Keyless061Qa.enabled()) return; else if (Scoreboard060Qa.enabled()) Scoreboard060Qa.frame(); else if (Settings060Qa.enabled()) return; else if (Chat059Qa.enabled()) Chat059Qa.frame(); else if (Ime058Qa.enabled()) Ime058Qa.frame(); else if (Feature058Qa.enabled()) Feature058Qa.frame(); else if (StartupQa.enabled()) StartupQa.frame(); else if (!SessionUiQa.enabled()) ReleaseQa.frame(); }
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void release$resourceReload(CallbackInfo ci) { if (SessionUiQa.enabled()) SessionUiQa.resourceReloads++; }
}
