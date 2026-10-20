package org.polyfrost.oneconfig.internal.mixin.render;

//? if <= 1.8.9 {
/*//? if = 1.7.10 {
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
//?}
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if = 1.7.10 {
@Mixin(PlayerRenderer.class)
//?} else {
@Mixin(AbstractArmorLayer.class)
//?}
public abstract class Mixin_HidePlayerArmor {
    //? if = 1.7.10 {
    @Inject(method = "bindTexture(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;IF)I", at = @At("HEAD"), cancellable = true)
    private void arcadium$hideLegacyArmor(ClientPlayerEntity player, int slot, float partialTicks, CallbackInfoReturnable<Integer> ci) {
        boolean hidden = switch (slot) {
            case 0 -> OneConfigConfig.hideHelmet;
            case 1 -> OneConfigConfig.hideChestplate;
            case 2 -> OneConfigConfig.hideLeggings;
            case 3 -> OneConfigConfig.hideBoots;
            default -> false;
        };
        if (hidden) ci.setReturnValue(-1);
    }
    //?} else {
    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private void arcadium$hideArmor(LivingEntity entity, float limbAngle, float limbDistance, float delta,
                                   float animationProgress, float yaw, float pitch, float scale, int slot, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) return;
        boolean hidden = switch (slot) {
            case 4 -> OneConfigConfig.hideHelmet;
            case 3 -> OneConfigConfig.hideChestplate;
            case 2 -> OneConfigConfig.hideLeggings;
            case 1 -> OneConfigConfig.hideBoots;
            default -> false;
        };
        if (hidden) ci.cancel();
    }
    //?}
}
*///?}
