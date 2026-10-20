package org.polyfrost.overflowparticles.mixin.client;

//? if <=1.12.2 {
import net.minecraft.client.ParticleManager;
import net.minecraft.client.entity.particle.BlockParticle;
import net.minecraft.client.entity.particle.Particle;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import org.polyfrost.overflowparticles.utils.ParticleIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class Mixin_FixBlockBreakingId {
    @Inject(
            method = "add(Lnet/minecraft/client/entity/particle/Particle;)V",
            at = @At("HEAD")
    )
    private void overflowparticles$checkDiggingEffects(Particle effect, CallbackInfo ci) {
        if (effect instanceof BlockParticle) {
            ParticleIdentifier.set(effect, NativeParticleType.BLOCK_CRACK);
        }
    }
}
//?} else {
/*import net.minecraft.client.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParticleManager.class)
public class Mixin_FixBlockBreakingId {
}
*///?}
