package org.polyfrost.overflowparticles.mixin.client;

import net.minecraft.world.World;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.client.utils.ParticleSpawner;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = World.class, priority = 999)
public class Mixin_CustomParticleSpawner {
    @Shadow @Final public boolean isClient;

    @Inject(method = "addParticle(Ljava/lang/String;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void overflowparticles$spawn(String name, double x, double y, double z,
                                        double dx, double dy, double dz, CallbackInfo ci) {
        if (!isClient) return;
        boolean multiplied = ParticleSpawner.isMultiplied();
        ParticleSpawner.setMultiplied(false);
        ParticleConfig config = PerParticleConfigManager.getConfigByType(NativeParticleType.fromName(name));
        if (config == null) return;
        if (!config.getEnabled()) { ci.cancel(); return; }
        if (multiplied || config.getMultiplier() == 1f) return;
        ParticleSpawner.spawn(name, config, (World) (Object) this, x, y, z, dx, dy, dz);
        ci.cancel();
    }
}
