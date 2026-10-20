package dev.microcontrollers.rendertweaks.mixin;

//? if >=1.8.9 {

/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.rendertweaks.config.RenderTweaksConfig;
import net.minecraft.client.render.entity.FishingBobberRenderer;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.FishingBobberEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingBobberRenderer.class)
public class FishingHookRendererMixin {
    @Inject(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private static void cancelFishingLineRendering(FishingBobberEntity entity, double dx, double dy, double dz, float yaw, float tickDelta, CallbackInfo ci) {
        if (RenderTweaksConfig.disableFishingLine || RenderTweaksConfig.fishingLineColor.getAlpha() == 0) ci.cancel();
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferBuilder;color(IIII)Lnet/minecraft/client/render/vertex/BufferBuilder;"))
    private static BufferBuilder changeFishingLineColor(BufferBuilder instance, int r, int g, int b, int a, Operation<BufferBuilder> original) {
        return original.call(instance, RenderTweaksConfig.fishingLineColor.getRed(), RenderTweaksConfig.fishingLineColor.getGreen(), RenderTweaksConfig.fishingLineColor.getBlue(), RenderTweaksConfig.fishingLineColor.getAlpha());
    }

    @Inject(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;popMatrix()V", shift = At.Shift.AFTER))
    private static void changeFishingLineWidthPre(FishingBobberEntity entity, double dx, double dy, double dz, float yaw, float tickDelta, CallbackInfo ci) {
        GL11.glLineWidth(RenderTweaksConfig.fishingLineWidthMultiplier);
    }

    @Inject(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private static void changeFishingLineWidthPost(FishingBobberEntity entity, double dx, double dy, double dz, float yaw, float tickDelta, CallbackInfo ci) {
        GL11.glLineWidth(1.0F);
    }
}

*///?}
