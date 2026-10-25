// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.mixin.render;

//? if >= 1.21.11 && < 26.3 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class Mixin_HideModernPlayerArmor {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void arcadium$hidePlayerArmor(PoseStack pose, SubmitNodeCollector collector, ItemStack item,
                                          EquipmentSlot slot, int light, HumanoidRenderState state, CallbackInfo callback) {
        if (!(state instanceof AvatarRenderState)) return;
        boolean hidden = switch (slot) {
            case HEAD -> OneConfigConfig.hideHelmet;
            case CHEST -> OneConfigConfig.hideChestplate;
            case LEGS -> OneConfigConfig.hideLeggings;
            case FEET -> OneConfigConfig.hideBoots;
            default -> false;
        };
        if (hidden) callback.cancel();
    }
}
*///?}
