package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.ReleaseQa;
import io.github.arcadiumclient.runtime.qa.SessionUiQa;
import io.github.arcadiumclient.runtime.qa.StartupQa;
import io.github.arcadiumclient.runtime.qa.Feature058Qa;
import io.github.arcadiumclient.runtime.qa.Ime058Qa;
import io.github.arcadiumclient.runtime.qa.Chat059Qa;
import io.github.arcadiumclient.runtime.qa.Settings060Qa;
import io.github.arcadiumclient.runtime.qa.Scoreboard060Qa;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ReleaseQaMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void release$testTick(CallbackInfo ci) { if (Scoreboard060Qa.enabled()) Scoreboard060Qa.tick(); else if (Settings060Qa.enabled()) Settings060Qa.tick(); else if (Chat059Qa.enabled()) Chat059Qa.tick(); else if (Ime058Qa.enabled()) Ime058Qa.tick(); else if (Feature058Qa.enabled()) Feature058Qa.tick(); else if (StartupQa.enabled()) StartupQa.tick(); else if (SessionUiQa.enabled()) SessionUiQa.tick(); else ReleaseQa.tick(); }
    @Inject(method = "runGame", at = @At("TAIL"))
    private void release$testFrame(CallbackInfo ci) { if (Scoreboard060Qa.enabled()) Scoreboard060Qa.frame(); else if (Settings060Qa.enabled()) return; else if (Chat059Qa.enabled()) Chat059Qa.frame(); else if (Ime058Qa.enabled()) Ime058Qa.frame(); else if (Feature058Qa.enabled()) Feature058Qa.frame(); else if (StartupQa.enabled()) StartupQa.frame(); else if (!SessionUiQa.enabled()) ReleaseQa.frame(); }
    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void release$resourceReload(CallbackInfo ci) { if (SessionUiQa.enabled()) SessionUiQa.resourceReloads++; }
}
