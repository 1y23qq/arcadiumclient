package dev.microcontrollers.rendertweaks.mixin;

//? if >=1.8.9 {

/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.rendertweaks.config.RenderTweaksConfig;
import net.minecraft.client.render.entity.LightningBoltRenderer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.global.LightningBoltEntity;
import org.polyfrost.compose.render.PolyColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBoltRenderer.class)
public class LightningBoltRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/global/LightningBoltEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private static void cancelLightningRendering(LightningBoltEntity entity, double dx, double dy, double dz, float yaw, float tickDelta, CallbackInfo ci) {
        if (RenderTweaksConfig.disableLightning || RenderTweaksConfig.lightningColor.getAlpha() == 0) ci.cancel();
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/global/LightningBoltEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(FFFF)Lnet/minecraft/client/render/vertex/BufferBuilder;"))
    private static BufferBuilder changeLightningColors(BufferBuilder instance, float r, float g, float b, float a, Operation<BufferBuilder> original) {
        PolyColor color = RenderTweaksConfig.lightningColor;
        return original.call(instance, color.getRedF(), color.getGreenF(), color.getBlueF(), color.getAlphaF());
    }
}

*///?}
