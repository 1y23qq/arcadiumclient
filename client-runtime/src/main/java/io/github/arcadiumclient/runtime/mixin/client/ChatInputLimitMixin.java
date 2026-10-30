// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import com.google.common.base.Predicate;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.arcadiumclient.runtime.client.chat.ChatInputLimit;
import io.github.arcadiumclient.runtime.client.features.ChatTweaksConfig;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextFieldWidget.class)
public abstract class ChatInputLimitMixin {
    @Unique private char arcadium$pendingHigh;
    @Unique private String arcadium$pendingText;
    @Unique private int arcadium$pendingCursor, arcadium$pendingSelection;

    @Unique private boolean arcadium$chat() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.screen instanceof ChatScreen chat
            && ((ChatScreenAccessor) chat).arcadium$chatField() == (Object) this;
    }

    @WrapMethod(method = "write")
    private void arcadium$boundedInsertion(String input, Operation<Void> original) {
        if (!arcadium$chat() || input == null) { original.call(input); return; }
        TextFieldWidget field = (TextFieldWidget) (Object) this;
        if (input.length() == 1 && Character.isHighSurrogate(input.charAt(0))) {
            arcadium$pendingHigh = input.charAt(0); arcadium$pendingText = field.getText();
            arcadium$pendingCursor = field.getCursor(); arcadium$pendingSelection = field.getSelectionEnd();
            return;
        }
        if (arcadium$pendingHigh != 0 && input.length() == 1 && Character.isLowSurrogate(input.charAt(0))
                && field.getText().equals(arcadium$pendingText) && field.getCursor() == arcadium$pendingCursor
                && field.getSelectionEnd() == arcadium$pendingSelection) input = "" + arcadium$pendingHigh + input;
        arcadium$pendingHigh = 0; arcadium$pendingText = null;
        String filtered = SharedConstants.stripInvalidChars(input);
        boolean bypass = ChatTweaksConfig.getBypassCommandLimit();
        String accepted = ChatInputLimit.insertion(field.getText(), field.getCursor(), field.getSelectionEnd(), filtered, bypass);
        if (!filtered.isEmpty() && accepted.isEmpty()) return;
        String candidate = field.getText().substring(0, Math.min(field.getCursor(), field.getSelectionEnd())) + accepted
            + field.getText().substring(Math.max(field.getCursor(), field.getSelectionEnd()));
        if (ChatInputLimit.localCommand(candidate, bypass) && field.getMaxLength() < ChatInputLimit.STRING_BYTES)
            field.setMaxLength(ChatInputLimit.STRING_BYTES);
        original.call(accepted);
    }

    @ModifyVariable(method = "setText", at = @At("HEAD"), argsOnly = true)
    private String arcadium$boundedText(String value) {
        if (!arcadium$chat() || value == null) return value;
        String accepted = ChatInputLimit.text(value, ChatTweaksConfig.getBypassCommandLimit());
        if (ChatInputLimit.localCommand(accepted, ChatTweaksConfig.getBypassCommandLimit())
                && ((TextFieldWidget) (Object) this).getMaxLength() < ChatInputLimit.STRING_BYTES)
            ((TextFieldWidget) (Object) this).setMaxLength(ChatInputLimit.STRING_BYTES);
        return accepted;
    }

    @WrapOperation(method = {"write", "setText", "eraseCharacters"}, at = @At(value = "INVOKE", target = "Lcom/google/common/base/Predicate;apply(Ljava/lang/Object;)Z"))
    private boolean arcadium$validateMessage(Predicate<String> filter, Object value, Operation<Boolean> original) {
        return original.call(filter, value) && (!arcadium$chat() || ChatInputLimit.fits((String) value, ChatTweaksConfig.getBypassCommandLimit()));
    }

    @ModifyVariable(method = "eraseCharacters", at = @At("HEAD"), argsOnly = true)
    private int arcadium$wholeCharacterDeletion(int count) {
        if (!arcadium$chat()) return count;
        TextFieldWidget field = (TextFieldWidget) (Object) this;
        if (field.getCursor() != field.getSelectionEnd()) return count;
        return ChatInputLimit.boundary(field.getText(), field.getCursor() + count, field.getCursor()) - field.getCursor();
    }

    @ModifyVariable(method = "setCursor", at = @At("HEAD"), argsOnly = true)
    private int arcadium$wholeCharacterCursor(int target) {
        TextFieldWidget field = (TextFieldWidget) (Object) this;
        return arcadium$chat() ? ChatInputLimit.boundary(field.getText(), target, field.getCursor()) : target;
    }

    @ModifyVariable(method = "setSelectionEnd", at = @At("HEAD"), argsOnly = true)
    private int arcadium$wholeCharacterSelection(int target) {
        TextFieldWidget field = (TextFieldWidget) (Object) this;
        return arcadium$chat() ? ChatInputLimit.boundary(field.getText(), target, field.getSelectionEnd()) : target;
    }

    @Inject(method = "setMaxLength", at = @At("TAIL"))
    private void arcadium$normalizeChangedLimit(int maximum, CallbackInfo ci) {
        if (!arcadium$chat()) return;
        TextFieldWidget field = (TextFieldWidget) (Object) this;
        if (!ChatInputLimit.fits(field.getText(), ChatTweaksConfig.getBypassCommandLimit()))
            field.setText(ChatInputLimit.text(field.getText(), ChatTweaksConfig.getBypassCommandLimit()));
    }
}
