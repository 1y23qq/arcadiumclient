// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.chat.ChatHistoryAccess;
import io.github.arcadiumclient.runtime.client.chat.ChatHistory;
import io.github.arcadiumclient.runtime.client.chat.ChatTweaks;
import io.github.arcadiumclient.runtime.client.features.ChatFiltersConfig;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatGui.class, priority = 900)
public abstract class ChatTweaksMixin implements ChatHistoryAccess {
    @Shadow @Final private List<ChatMessage> messages;
    @Shadow @Final private List<ChatMessage> trimmedMessages;
    @Shadow private void addMessage(Text message, int id, int created, boolean refresh) { throw new AssertionError(); }
    @Shadow public abstract void resetScroll();
    @Unique private final ChatTweaks arcadium$decorations = new ChatTweaks();
    @Unique private ChatTweaks.Display arcadium$current;

    @WrapMethod(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V")
    private void arcadium$prepareDisplay(Text text, int id, int tick, boolean refreshing, Operation<Void> original) {
        ChatHistory.observe(text, refreshing);
        ChatTweaks.Display previous = arcadium$current;
        arcadium$current = text == null || ChatFiltersConfig.hide(text.getString()) ? null
                : arcadium$decorations.prepare(text, id, tick, trimmedMessages);
        try { original.call(text, id, tick, refreshing); }
        finally { arcadium$current = previous; }
    }

    @WrapOperation(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/TextRenderUtils;wrapText(Lnet/minecraft/text/Text;ILnet/minecraft/client/render/TextRenderer;ZZ)Ljava/util/List;"))
    private List<Text> arcadium$decorate(Text text, int width, TextRenderer font, boolean forceColors, boolean ignoreColors,
                                        Operation<List<Text>> original) {
        ChatTweaks.Display display = arcadium$current;
        if (display != null && display.hidden()) return List.of();
        List<Text> lines = new ArrayList<>(original.call(display == null ? text : display.text(), width, font, forceColors, ignoreColors));
        if (display != null) arcadium$decorations.wrapped(display, lines);
        return lines;
    }

    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int arcadium$historyCapacity(int vanilla) { return ChatTweaks.historyLimit(); }

    @Inject(method = "reset", at = @At("HEAD"))
    private void arcadium$resetDisplay(CallbackInfo ci) { arcadium$decorations.reset(); }

    @Inject(method = "clear", at = @At("HEAD"))
    private void arcadium$keepHistory(CallbackInfo ci) {
        ChatHistory.saveBeforeClear(this);
        arcadium$decorations.reset();
    }

    @Override public List<ChatMessage> arcadium$history() { return List.copyOf(messages); }
    @Override public void arcadium$clearHistory() {
        messages.clear(); trimmedMessages.clear(); arcadium$decorations.reset(); resetScroll();
    }
    @Override public void arcadium$restore(Text text, long time, int tick, int id) {
        ChatTweaks.received(text, time);
        addMessage(text, id, tick, false);
    }
}
