package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface NoHitDelayProbeAccess {
    @Accessor("attackCooldown") int arcadium$attackCooldown();
    @Accessor("attackCooldown") void arcadium$attackCooldown(int value);
    @Accessor("useKeyCooldown") int arcadium$useCooldown();
    @Accessor("useKeyCooldown") void arcadium$useCooldown(int value);
    @Invoker("doAttack") void arcadium$attack();
}
