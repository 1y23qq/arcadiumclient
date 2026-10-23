// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.emoji.EmojiPicker;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.input.Keyboard;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatScreen.class, priority = 1400)
public abstract class EmojiChatPickerMixin extends Screen {
    @Shadow protected TextFieldWidget chatField;
    @Unique private final EmojiPicker arcadium$emojiPicker = new EmojiPicker();
    @Unique private boolean arcadium$showEmojiPicker() { return EmojiText.enabled() && ChatTweaksConfig.getShowEmojiPicker(); }

    @Inject(method = "render", at = @At("TAIL"))
    private void arcadium$emojiPicker(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (arcadium$showEmojiPicker()) arcadium$emojiPicker.render(width, height, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void arcadium$chooseEmoji(int x, int y, int button, CallbackInfo ci) {
        if (button != 0 || chatField == null || !arcadium$showEmojiPicker()) return;
        String token = arcadium$emojiPicker.click(x, y, width, height);
        if (token == null) return;
        if (!token.isEmpty()) chatField.write(token);
        ci.cancel();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void arcadium$emojiShortcut(char character, int key, CallbackInfo ci) {
        if (!arcadium$showEmojiPicker()) return;
        if (key == Keyboard.KEY_SPACE && Screen.isControlDown()) { arcadium$emojiPicker.toggle(); ci.cancel(); }
        else if (key == Keyboard.KEY_ESCAPE && arcadium$emojiPicker.close()) ci.cancel();
    }
}
