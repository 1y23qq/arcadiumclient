package io.github.arcadiumclient.featuretest.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatScreen.class)
public interface ChatDispatchInputAccess {
    @Invoker("keyPressed") void arcadium$chatDispatchEnter(char character, int key);
}
