//? if =1.7.10 {
package dev.microcontrollers.shaketweaks.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.microcontrollers.shaketweaks.config.ShakeTweaksConfig;
import net.minecraft.client.render.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public abstract class HandSway1710Mixin {
    @WrapWithCondition(method = "renderHand", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 2, remap = false))
    private boolean shaketweaks$verticalSway(float angle, float x, float y, float z) {
        return !ShakeTweaksConfig.disableHandViewSway;
    }

    @WrapWithCondition(method = "renderHand", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 3, remap = false))
    private boolean shaketweaks$horizontalSway(float angle, float x, float y, float z) {
        return !ShakeTweaksConfig.disableHandViewSway && !ShakeTweaksConfig.disableHorizontalBobbing;
    }
}
//?}
