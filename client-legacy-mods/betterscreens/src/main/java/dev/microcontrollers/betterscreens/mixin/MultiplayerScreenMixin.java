package dev.microcontrollers.betterscreens.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenMixin extends Screen {
    @Inject(method = {"connect()V", "connect(Lnet/minecraft/client/options/ServerListEntry;)V"}, at = @At("HEAD"))
    private void disconnectFromCurrentServer(CallbackInfo ci) {
        if (this.minecraft.world != null) {
            this.minecraft.world.disconnect();
            this.minecraft.setWorld(null);
        }
    }
}
