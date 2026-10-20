package dev.microcontrollers.overlaytweaks.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderInWaterEffect", at = @At("HEAD"), cancellable = true)
    private void cancelWaterOverlay(float tickDelta, CallbackInfo ci) {
        if (OverlayTweaksConfig.removeWaterOverlay) ci.cancel();
    }

    @WrapMethod(method = "renderOnFireEffect")
    private void modifyFireOverlay(float tickDelta, Operation<Void> original) {
        Minecraft client = Minecraft.getInstance();
        if ((OverlayTweaksConfig.removeFireOverlay && client.player != null
                && (client.player.abilities.creativeMode || client.player.hasStatusEffect(StatusEffect.FIRE_RESISTANCE)))
                || OverlayTweaksConfig.customFireOverlayOpacity <= 0f) return;
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0f, OverlayTweaksConfig.fireOverlayHeight, 0f);
            original.call(tickDelta);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    @ModifyArg(method = "renderOnFireEffect", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0), index = 3)
    private float fireOpacity(float nativeAlpha) {
        return nativeAlpha * Math.max(0f, Math.min(1f, OverlayTweaksConfig.customFireOverlayOpacity / 100f));
    }

    @ModifyArgs(method = "renderInWallEffect", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glColor4f(FFFF)V", ordinal = 0))
    private void suffocationBrightness(Args args) {
        float brightness = Math.max(0f, Math.min(1f, OverlayTweaksConfig.suffocationOverlayBrightness / 100f));
        args.set(0, brightness); args.set(1, brightness); args.set(2, brightness);
    }

    @ModifyExpressionValue(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;isInvisible()Z"))
    private boolean showArmWhileInvisible(boolean invisible) {
        return invisible && OverlayTweaksConfig.handInvisibilityOpacity <= 0f;
    }

    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/PlayerRenderer;renderRightHand(Lnet/minecraft/entity/living/player/PlayerEntity;)V"))
    private void invisibleArmOpacity(PlayerRenderer renderer, PlayerEntity player, Operation<Void> original) {
        if (!player.isInvisible()) { original.call(renderer, player); return; }
        float opacity = Math.max(0f, Math.min(1f, OverlayTweaksConfig.handInvisibilityOpacity / 100f));
        if (opacity <= 0f) return;
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1f, 1f, 1f, opacity);
            original.call(renderer, player);
        } finally { GL11.glPopAttrib(); }
    }
}
