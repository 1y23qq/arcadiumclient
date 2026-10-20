package dev.microcontrollers.shaketweaks.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.microcontrollers.shaketweaks.config.ShakeTweaksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @WrapWithCondition(method = "setupCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;applyViewBobbing(F)V"))
    private boolean disableScreenBobbing(GameRenderer instance, float tickDelta) {
        return !ShakeTweaksConfig.disableScreenBobbing;
    }

    @WrapWithCondition(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;applyViewBobbing(F)V"))
    private boolean disableHandBobbing(GameRenderer instance, float tickDelta) {
        if (ShakeTweaksConfig.disableHandBobbing) return false;
        if (ShakeTweaksConfig.disableMapBobbing) {
            LocalClientPlayerEntity entity = Minecraft.getInstance().player;
            if (entity != null) {
                ItemStack hand = entity.inventory.getSelectedItem();
                if (hand == null) return true;
                return !(hand.getItem() instanceof FilledMapItem);
            }
        }
        return true;
    }

    @WrapWithCondition(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;applyHurtCam(F)V"))
    private boolean disableHandDamageTilt(GameRenderer instance, float tickDelta) {
        return !ShakeTweaksConfig.disableHandDamage;
    }

    @WrapWithCondition(method = "setupCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;applyHurtCam(F)V"))
    private boolean disableScreenDamageTilt(GameRenderer instance, float tickDelta) {
        return !ShakeTweaksConfig.disableScreenDamage;
    }
}
