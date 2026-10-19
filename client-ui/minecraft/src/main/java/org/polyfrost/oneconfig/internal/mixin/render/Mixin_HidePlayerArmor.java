package org.polyfrost.oneconfig.internal.mixin.render;

//? if = 1.8.9 {
/*import net.minecraft.client.render.entity.layer.AbstractArmorLayer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractArmorLayer.class)
public abstract class Mixin_HidePlayerArmor {
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
}
*///?}
