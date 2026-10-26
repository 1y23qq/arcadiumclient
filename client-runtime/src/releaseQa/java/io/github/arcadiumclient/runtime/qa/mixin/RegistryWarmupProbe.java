package io.github.arcadiumclient.runtime.qa.mixin;

import io.github.arcadiumclient.runtime.qa.StartupQa;
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConfigRegistry.class, remap = false)
public abstract class RegistryWarmupProbe {
    @Unique private long startup$registryStarted;

    @Inject(method = "loadFrom", at = @At("HEAD"))
    private void startup$registryLoad(CallbackInfo info) {
        if (StartupQa.enabled()) {
            StartupQa.registryLoads.incrementAndGet();
            startup$registryStarted = System.nanoTime();
        }
    }

    @Inject(method = "loadFrom", at = @At("RETURN"))
    private void startup$registryLoaded(CallbackInfo info) {
        if (StartupQa.enabled()) StartupQa.registryLoadNanos.addAndGet(System.nanoTime() - startup$registryStarted);
    }
}
