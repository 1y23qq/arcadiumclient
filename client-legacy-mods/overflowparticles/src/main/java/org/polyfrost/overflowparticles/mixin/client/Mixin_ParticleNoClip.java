package org.polyfrost.overflowparticles.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.entity.Entity;
import org.polyfrost.overflowparticles.client.config.OverflowParticlesConfig;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public class Mixin_ParticleNoClip {
    @WrapMethod(method = "move(DDD)V")
    private void overflowparticles$move(double x, double y, double z, Operation<Void> original) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Particle) || self.world == null || !self.world.isClient
            || !OverflowParticlesConfig.INSTANCE.getParticleNoClip()) {
            original.call(x, y, z);
            return;
        }
        boolean previous = self.noClip;
        self.noClip = true;
        try {
            original.call(x, y, z);
        } finally {
            self.noClip = previous;
        }
    }
}
