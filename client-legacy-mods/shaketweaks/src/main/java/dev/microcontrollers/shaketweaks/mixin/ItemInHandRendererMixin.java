//? if >1.7.10 {
/*package dev.microcontrollers.shaketweaks.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.microcontrollers.shaketweaks.config.ShakeTweaksConfig;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @WrapWithCondition(method = "renderInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ItemInHandRenderer;applyHandSway(Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;F)V"))
    private boolean removeHandSway(ItemInHandRenderer instance, LocalClientPlayerEntity player, float tickDelta) {
        return !ShakeTweaksConfig.disableHandViewSway;
    }
}
*///?}
