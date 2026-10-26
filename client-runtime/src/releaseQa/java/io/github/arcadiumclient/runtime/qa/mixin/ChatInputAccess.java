package io.github.arcadiumclient.runtime.qa.mixin;

import net.minecraft.client.gui.screen.game.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatScreen.class)
public interface ChatInputAccess {
    @Invoker("keyPressed") void release$key(char character, int key);
}
