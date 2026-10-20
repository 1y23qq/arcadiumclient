package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.world.WorldRenderer;
import org.polyfrost.overflowparticles.client.config.ParticleConfig;
import org.polyfrost.overflowparticles.client.config.PerParticleConfigManager;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.polyfrost.overflowparticles.utils.ParticleIdentifier;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldRenderer.class)
public class Mixin_SetParticleIds {
    @WrapMethod(method = "tryAddParticle(Ljava/lang/String;DDDDDD)Lnet/minecraft/client/entity/particle/Particle;")
    private Particle overflowparticles$identify(String name, double x, double y, double z,
                                               double dx, double dy, double dz, Operation<Particle> original) {
        NativeParticleType type = NativeParticleType.fromName(name);
        ParticleConfig config = PerParticleConfigManager.getConfigByType(type);
        if (config != null && !config.getEnabled()) return null;
        NativeParticleType previous = NativeParticleType.current();
        NativeParticleType.current(type);
        try {
            Particle result = original.call(name, x, y, z, dx, dy, dz);
            if (result != null && type != null) ParticleIdentifier.set(result, type);
            return result;
        } finally {
            NativeParticleType.current(previous);
        }
    }
}
