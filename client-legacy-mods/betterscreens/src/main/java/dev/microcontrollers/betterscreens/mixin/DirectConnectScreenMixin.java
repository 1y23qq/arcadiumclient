package dev.microcontrollers.betterscreens.mixin;

import dev.microcontrollers.betterscreens.ServerManager;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import dev.microcontrollers.betterscreens.mixin.accessor.ServerListEntryWidgetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.DirectConnectScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.options.ServerListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectConnectScreen.class)
public class DirectConnectScreenMixin extends Screen {
    @Shadow @Final private Screen parent;
    @Shadow private TextFieldWidget serverField;

    @Unique private ServerListEntryWidget betterscreens$serverPreview;

    @Inject(method = "init", at = @At("TAIL"))
    private void initServerPreview(CallbackInfo ci) {
        if (BetterScreensConfig.showServerPreview) {
            MultiplayerScreen screen;
            if (this.parent instanceof MultiplayerScreen multiplayerScreen) {
                screen = multiplayerScreen;
            } else {
                screen = new MultiplayerScreen(this.parent);
            }
            String ip = this.serverField.getText();
            String name = !ip.isEmpty() ? ServerManager.getServerName(ip) : "Server Preview";
            betterscreens$serverPreview = ServerListEntryWidgetAccessor.betterscreens$init(
                    screen,
                    new ServerListEntry(name, ip, Minecraft.getInstance().isLocalServer())
            );
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;render()V"))
    private void drawServerPreview(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if (BetterScreensConfig.showServerPreview && betterscreens$serverPreview != null) {
            ServerListEntryWidgetAccessor accessor = (ServerListEntryWidgetAccessor) betterscreens$serverPreview;
            ServerListEntry entry = accessor.betterscreens$getEntry();
            String text = this.serverField.getText();
            if (!entry.ip.equals(text)) {
                entry.ip = text;
                entry.name = ServerManager.getServerName(text);
                entry.setIcon(null);
            }

            GuiElement.fill(this.width / 2 - 305 / 2 - 4, 50 - 4, this.width / 2 - 305 / 2 + 305 + 4, 50 + 35 + 4, 0x80000000);
            betterscreens$serverPreview.render(0, this.width / 2 - 305 / 2, 50, 305, 35,
                    net.minecraft.client.render.vertex.Tesselator.INSTANCE, mouseX, mouseY, false);
        }
    }
}
