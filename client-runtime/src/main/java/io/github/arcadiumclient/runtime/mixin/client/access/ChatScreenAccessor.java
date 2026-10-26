// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.mixin.client.access;

import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
    @Accessor("chatField") TextFieldWidget arcadium$chatField();
}
