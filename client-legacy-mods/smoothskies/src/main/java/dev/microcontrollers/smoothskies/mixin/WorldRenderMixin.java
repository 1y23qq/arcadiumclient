package dev.microcontrollers.smoothskies.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.microcontrollers.smoothskies.config.SmoothConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.world.dimension.Dimension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRenderMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void removeSkyInVoid(float tickDelta, /*? if >=1.8.9 {*//*int anaglyphRenderPass,*//*?}*/ CallbackInfo ci) {
        //~ if =1.7.10 'getEyePosition' -> 'getPosition'
        if (SmoothConfig.removeSkyInVoid && this.minecraft.player.getPosition(tickDelta).y < -1.5D) ci.cancel();
    }

    @Definition(id = "d", local = @Local(type = double.class))
    @Expression("d < 0.0")
    @ModifyExpressionValue(method = "renderSky", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean removeVoidBox(boolean original) {
        return !SmoothConfig.removeVoidBox && original;
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;getSunriseColor(FF)[F"))
    private float[] removeSkyDisk(Dimension instance, float timeOfDay, float tickDelta, Operation<float[]> original) {
        return SmoothConfig.removeSunriseAndSunsetColoring ? null : original.call(instance, timeOfDay, tickDelta);
    }
}
