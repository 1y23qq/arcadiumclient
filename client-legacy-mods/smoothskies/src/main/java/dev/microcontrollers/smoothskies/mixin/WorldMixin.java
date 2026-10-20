package dev.microcontrollers.smoothskies.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.microcontrollers.smoothskies.config.SmoothConfig;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public class WorldMixin {
    @ModifyReturnValue(method = "getHorizonHeight", at = @At("RETURN"))
    private double forceHorizonHeight(double original) {
        return SmoothConfig.lowerSkyVoidDarkness ? 0.0D : original;
    }
}
