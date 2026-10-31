// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class CustomF3HotkeyMixin {
    @Unique private boolean arcadium$debugShortcut;
    @Inject(method = "tick", at = @At("TAIL"))
    private void arcadium$editor(CallbackInfo callback) {
        if (!CustomF3Config.getEnabled()) { arcadium$debugShortcut = false; return; }
        boolean down = Keyboard.isKeyDown(Keyboard.KEY_F3) && Keyboard.isKeyDown(Keyboard.KEY_M);
        if (down && !arcadium$debugShortcut && Minecraft.getInstance().screen == null) CustomF3Config.INSTANCE.edit();
        arcadium$debugShortcut = down;
    }
}
