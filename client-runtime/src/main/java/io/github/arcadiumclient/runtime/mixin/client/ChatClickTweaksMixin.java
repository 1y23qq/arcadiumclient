// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.chat.ChatImagePreviews;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ChatClickTweaksMixin {
    @Shadow public int width;
    @Shadow public int height;
    @Shadow protected abstract void renderTooltip(List<String> lines, int x, int y);

    @Inject(method = "renderTextHoverEffect", at = @At("TAIL"))
    private void arcadium$hoverDestination(Text text, int x, int y, CallbackInfo ci) {
        if (text == null) return;
        ClickEvent click = text.getStyle().getClickEvent();
        if (ChatTweaksConfig.getSafeChatClicks() && click != null) {
            String value = click.getValue();
            if (value != null && !value.isBlank()) {
                String label = switch (click.getAction()) {
                    case OPEN_URL -> "Open link";
                    case OPEN_FILE -> "Open file";
                    case RUN_COMMAND -> "Run command";
                    case SUGGEST_COMMAND -> "Insert command";
                    default -> "Chat action";
                };
                renderTooltip(List.of(label, value.substring(0, Math.min(value.length(), 300))), x, y);
            }
        }
        ChatImagePreviews.render(text, x, y, width, height);
    }

    @Inject(method = "handleClickEvent", at = @At("HEAD"))
    private void arcadium$rememberClickedCommand(Text text, CallbackInfoReturnable<Boolean> cir) {
        if (!ChatTweaksConfig.getSafeChatClicksHistory() || text == null || Screen.isShiftDown()) return;
        ClickEvent click = text.getStyle().getClickEvent();
        Minecraft client = Minecraft.getInstance();
        if (click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND && client.gui != null)
            client.gui.getChat().addRecentMessage(click.getValue());
    }
}
