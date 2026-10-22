// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import io.github.arcadiumclient.forge1710.platform.ForgeInputEvents;
import net.minecraft.client.Minecraft;
import org.lwjglx.opengl.Display;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.WindowFocusEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ForgeMinecraftInputMixin {
    @Unique private boolean arcadium$focused;

    @Inject(method = "func_71411_J", at = @At("HEAD"), remap = false, require = 1)
    private void arcadium$windowFocus(CallbackInfo callback) {
        boolean focused = Display.isActive();
        if (arcadium$focused == focused) return;
        arcadium$focused = focused;
        EventManager.INSTANCE.post(focused ? WindowFocusEvent.Gained.INSTANCE : WindowFocusEvent.Lost.INSTANCE);
    }

    @ModifyExpressionValue(method = "func_71407_l", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z"), remap = false, require = 1)
    private boolean arcadium$keyboard(boolean available) { return ForgeInputEvents.keyboard(available); }

    @ModifyExpressionValue(method = "func_71407_l", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z"), remap = false, require = 1)
    private boolean arcadium$mouse(boolean available) { return ForgeInputEvents.mouse(available); }

    @WrapWithCondition(method = "func_71411_J", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_71352_k()V"), remap = false, require = 1)
    private boolean arcadium$keepFullscreenOnFocusLoss(Minecraft minecraft) { return false; }
}
