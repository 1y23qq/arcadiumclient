// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.client.features.AnimationsConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
public abstract class BadlionItemMixin {
    @WrapMethod(method = "submitArmWithItem")
    private void arcadium$transform(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                                    PoseStack pose, SubmitNodeCollector collector, int light, Operation<Void> original) {
        var settings = AnimationsConfig.badlion();
        if (settings == null) { original.call(player, partialTick, pitch, hand, swing, stack, equip, pose, collector, light); return; }
        pose.pushPose();
        try {
            if (settings.swordSwing() && stack.is(ItemTags.SWORDS)) { pose.scale(.85f, .85f, .85f); pose.translate(-.078f, .003f, .05f); }
            if (settings.bowAnimation() && stack.is(Items.BOW)) pose.translate(-.01f, .05f, -.06f);
            original.call(player, partialTick, pitch, hand, swing, stack, equip, pose, collector, light);
        } finally { pose.popPose(); }
    }
}
