// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.forge1710.platform.ForgeInputEvents;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiScreen.class)
public abstract class ForgeScreenInputMixin {
    @ModifyExpressionValue(method = "func_146269_k", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z"), remap = false, require = 1)
    private boolean arcadium$keyboard(boolean available) { return ForgeInputEvents.keyboard(available); }

    @ModifyExpressionValue(method = "func_146269_k", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z"), remap = false, require = 1)
    private boolean arcadium$mouse(boolean available) { return ForgeInputEvents.mouse(available); }
}
