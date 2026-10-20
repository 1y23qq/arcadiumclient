package dev.microcontrollers.rendertweaks.mixin.legacy;

//? if =1.7.10 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.rendertweaks.config.RenderTweaksConfig;
import net.minecraft.client.render.entity.LightningBoltRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.polyfrost.compose.render.PolyColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightningBoltRenderer.class)
public class LightningBoltRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/global/LightningBoltEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void hideBolt(CallbackInfo ci) {
        if (RenderTweaksConfig.disableLightning || RenderTweaksConfig.lightningColor.getAlpha() == 0) ci.cancel();
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/global/LightningBoltEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;color(FFFF)V"))
    private void colorBolt(Tesselator vertices, float r, float g, float b, float a, Operation<Void> original) {
        PolyColor color = RenderTweaksConfig.lightningColor;
        original.call(vertices, color.getRedF(), color.getGreenF(), color.getBlueF(), color.getAlphaF());
    }
}
//?}
