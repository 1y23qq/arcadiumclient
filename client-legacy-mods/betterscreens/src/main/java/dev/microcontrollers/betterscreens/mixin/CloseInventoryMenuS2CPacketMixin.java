package dev.microcontrollers.betterscreens.mixin;

import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.packet.s2c.play.CloseInventoryMenuS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloseInventoryMenuS2CPacket.class)
public class CloseInventoryMenuS2CPacketMixin {
    // I've never really experienced all the other stuff that Moulberry's had and I don't really want to port those either
    @Inject(method = "handle(Lnet/minecraft/client/network/handler/ClientPlayPacketHandler;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/handler/ClientPlayPacketHandler;handleCloseInventoryMenu(Lnet/minecraft/network/packet/s2c/play/CloseInventoryMenuS2CPacket;)V"), cancellable = true)
    private void handleChatClose(ClientPlayPacketHandler clientPlayPacketHandler, CallbackInfo ci) {
        if (BetterScreensConfig.preventClosingScreens && Minecraft.getInstance().screen instanceof ChatScreen) ci.cancel();
    }
}
