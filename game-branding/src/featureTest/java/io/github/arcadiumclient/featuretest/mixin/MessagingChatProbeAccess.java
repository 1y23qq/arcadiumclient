package io.github.arcadiumclient.featuretest.mixin;

import java.util.List;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.chat.ChatGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatGui.class)
public interface MessagingChatProbeAccess {
    @Accessor("messages") List<ChatMessage> arcadium$rawMessages();
    @Accessor("trimmedMessages") List<ChatMessage> arcadium$visibleMessages();
}
