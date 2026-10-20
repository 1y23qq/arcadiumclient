// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.chat.ChatImagePreviews;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.ClickEvent__Action;
import net.minecraft.text.Text;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatClickTweaksMixin extends Screen {
    @Inject(method = "render", at = @At("TAIL"))
    private void arcadium$hoverDestination(int x, int y, float delta, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) return;
        Text text = client.gui.getChat().getMessageAt(Mouse.getX(), Mouse.getY());
        if (text == null) return;
        ClickEvent click = text.getStyle().getClickEvent();
        if (ChatTweaksConfig.getSafeChatClicks() && click != null) {
            String value = click.getValue();
            if (value != null && !value.isBlank()) {
                ClickEvent__Action action = click.getAction();
                String label = action == ClickEvent__Action.OPEN_URL ? "Open link"
                    : action == ClickEvent__Action.OPEN_FILE ? "Open file"
                    : action == ClickEvent__Action.RUN_COMMAND ? "Run command"
                    : action == ClickEvent__Action.SUGGEST_COMMAND ? "Insert command" : "Chat action";
                renderTooltip(List.of(label, value.substring(0, Math.min(value.length(), 300))), x, y);
            }
        }
        ChatImagePreviews.render(text, x, y, width, height);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void arcadium$rememberClickedCommand(int x, int y, int button, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (!ChatTweaksConfig.getSafeChatClicksHistory() || button != 0 || Screen.isShiftDown()
            || client.gui == null || !client.options.chatLinks) return;
        Text text = client.gui.getChat().getMessageAt(Mouse.getX(), Mouse.getY());
        ClickEvent click = text == null ? null : text.getStyle().getClickEvent();
        if (click != null && click.getAction() == ClickEvent__Action.RUN_COMMAND) client.gui.getChat().addRecentMessage(click.getValue());
    }
}
