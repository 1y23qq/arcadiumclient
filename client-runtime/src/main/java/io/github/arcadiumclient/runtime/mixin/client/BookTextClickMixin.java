// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client;

import io.github.arcadiumclient.runtime.client.features.LegacyNickHiderText;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BookEditScreen.class)
public abstract class BookTextClickMixin {
    @ModifyExpressionValue(method = "getHoveredText", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;next()Ljava/lang/Object;"))
    private Object arcadium$clickableComponent(Object component) {
        return component instanceof Text text ? LegacyNickHiderText.interactionComponent(text) : component;
    }
}
