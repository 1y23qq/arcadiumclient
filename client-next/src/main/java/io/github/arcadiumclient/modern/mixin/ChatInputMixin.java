// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin;

import io.github.arcadiumclient.modern.client.chat.EmojiPicker;
import io.github.arcadiumclient.modern.client.chat.ChatImagePreviews;
import io.github.arcadiumclient.modern.client.features.ChatTweaksConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatInputMixin {
    @Shadow protected EditBox input;
    @Unique private final EmojiPicker arcadium$picker = new EmojiPicker();
    @Inject(method = {"init", "onEdited"}, at = @At("TAIL"))
    private void arcadium$localLength(CallbackInfo ci) {
        if (input != null) input.setMaxLength(ChatTweaksConfig.getBypassCommandLimit() && input.getValue().stripLeading().startsWith("/") ? 32767 : 256);
    }
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void arcadium$picker(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ChatScreen current = (ChatScreen) (Object) this;
        ChatImagePreviews.render(graphics, ((GuiRenderAccess) graphics).arcadium$clickableStyle(), mouseX, mouseY, current.width, current.height);
        if (ChatTweaksConfig.getEmoji() && ChatTweaksConfig.getShowEmojiPicker()) {
            ChatScreen screen = (ChatScreen) (Object) this;
            arcadium$picker.render(graphics, screen.width, screen.height, mouseX, mouseY);
        }
    }
    @Inject(method = "handleComponentClicked", at = @At("RETURN"))
    private void arcadium$clickedHistory(Style style, boolean insert, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !ChatTweaksConfig.getSafeChatClicksHistory() || style == null) return;
        String command = switch (style.getClickEvent()) {
            case ClickEvent.RunCommand run -> run.command();
            case ClickEvent.SuggestCommand suggest -> suggest.command();
            case null, default -> null;
        };
        if (command != null) Minecraft.getInstance().gui.hud.getChat().addRecentChat(command);
    }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void arcadium$key(KeyEvent key, CallbackInfoReturnable<Boolean> cir) {
        if (ChatTweaksConfig.getBypassCommandLimit() && key.key() == GLFW.GLFW_KEY_V && (key.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 &&
            Minecraft.getInstance().keyboardHandler.getClipboard().stripLeading().startsWith("/")) input.setMaxLength(32767);
        if (ChatTweaksConfig.getEmoji() && ChatTweaksConfig.getShowEmojiPicker()) {
            if (key.key() == GLFW.GLFW_KEY_SPACE && (key.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) { arcadium$picker.toggle(); cir.setReturnValue(true); return; }
            if (key.key() == GLFW.GLFW_KEY_ESCAPE && arcadium$picker.close()) { cir.setReturnValue(true); return; }
        }
        if ((key.key() == GLFW.GLFW_KEY_ENTER || key.key() == GLFW.GLFW_KEY_KP_ENTER) && (key.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 && ChatTweaksConfig.getShiftChat()) {
            ((ChatScreen) (Object) this).handleChatInput(input.getValue(), true);
            input.setValue("");
            cir.setReturnValue(true);
        }
    }
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void arcadium$longLocalCommand(String value, boolean addHistory, CallbackInfo ci) {
        if (!ChatTweaksConfig.getBypassCommandLimit() || value.length() <= 256) return;
        String command = value.strip().startsWith("/") ? value.strip().substring(1) : "";
        var dispatcher = ClientCommands.getActiveDispatcher();
        var mc = Minecraft.getInstance();
        String root = command.split("\\s+", 2)[0];
        if (dispatcher != null && dispatcher.getRoot().getChild(root) != null && mc.getConnection() != null) {
            try {
                dispatcher.execute(command, (FabricClientCommandSource) mc.getConnection().getSuggestionsProvider());
                if (addHistory) mc.gui.hud.getChat().addRecentChat(value);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(failure.getMessage()).withStyle(net.minecraft.ChatFormatting.RED));
            }
        } else mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Server messages are limited to 256 characters.").withStyle(net.minecraft.ChatFormatting.RED));
        ci.cancel();
    }
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void arcadium$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!ChatTweaksConfig.getEmoji() || !ChatTweaksConfig.getShowEmojiPicker() || event.button() != 0) return;
        ChatScreen screen = (ChatScreen) (Object) this;
        String value = arcadium$picker.click((int) event.x(), (int) event.y(), screen.width, screen.height);
        if (value != null) { if (!value.isEmpty()) input.insertText(value); cir.setReturnValue(true); }
    }
}
