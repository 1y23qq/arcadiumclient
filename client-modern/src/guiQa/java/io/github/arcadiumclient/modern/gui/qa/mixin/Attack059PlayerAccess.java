package io.github.arcadiumclient.modern.gui.qa.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface Attack059PlayerAccess {
    @Accessor("attackStrengthTicker") void release$attackTicks(int ticks);
}
