package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.SessionUiQa;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.client.resource.manager.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundReloadProbe {
    @Inject(method = "reload", at = @At("HEAD"))
    private void release$soundReload(ResourceManager manager, CallbackInfo ci) {
        if (SessionUiQa.enabled()) SessionUiQa.soundReloads++;
    }
}
