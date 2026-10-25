// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.mixin.compat.chatting;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@IfModLoaded("chatting")
@Mixin(targets = "org.polyfrost.chatting.config.ChattingConfig", remap = false)
public abstract class ExcludedChatTabsMixin {
    @Inject(method = "getChatTabs", at = @At("HEAD"), cancellable = true)
    private void arcadium$selectedScope(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(false); }
}
