package dev.microcontrollers.confirmdisconnect;

import dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;

public class ConfirmDisconnectScreen extends Screen {
    public Screen parent;
    private int delayTicker;

    public ConfirmDisconnectScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.delayTicker = 0;

        boolean isLocal = Minecraft.getInstance().isSingleplayer();
        boolean isConfirmOnLeft = ConfirmDisconnectConfig.confirmOnLeft;

        if (isConfirmOnLeft) {
            this.buttons.add(new ButtonWidget(0, width / 2 - 120, this.height / 4 + 104, 100, 20, isLocal ? I18n.translate("confirm-disconnect.quit-local") : I18n.translate("confirm-disconnect.disconnect-multiplayer")));
            this.buttons.add(new ButtonWidget(1, width / 2 + 20, this.height / 4 + 104, 100, 20, I18n.translate("confirm-disconnect.back")));
        } else {
            this.buttons.add(new ButtonWidget(1, width / 2 - 120, this.height / 4 + 104, 100, 20, I18n.translate("confirm-disconnect.back")));
            this.buttons.add(new ButtonWidget(0, width / 2 + 20, this.height / 4 + 104, 100, 20, isLocal ? I18n.translate("confirm-disconnect.quit-local") : I18n.translate("confirm-disconnect.disconnect-multiplayer")));
        }

        if (ConfirmDisconnectConfig.confirmDelay != 0) {
            ((ButtonWidget) this.buttons.get(0)).active = false;
            ((ButtonWidget) this.buttons.get(1)).active = false;
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) {
            boolean singleplayer = this.minecraft.isIntegratedServerRunning();
            button.active = false;
            this.minecraft.world.disconnect();
            this.minecraft.setWorld(null);
            if (singleplayer) {
                this.minecraft.openScreen(new TitleScreen());
            } else {
                this.minecraft.openScreen(new MultiplayerScreen(new TitleScreen()));
            }
        } else {
            this.minecraft.openScreen(this.parent);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.delayTicker >= 20 * ConfirmDisconnectConfig.confirmDelay) {
            ((ButtonWidget) this.buttons.get(0)).active = true;
            ((ButtonWidget) this.buttons.get(1)).active = true;
        }
        ++this.delayTicker;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        boolean isLocal = Minecraft.getInstance().isSingleplayer();
        this.drawCenteredString(this.textRenderer, isLocal ? I18n.translate("confirm-disconnect.are-you-sure-local") : I18n.translate("confirm-disconnect.are-you-sure-multiplayer"), this.width / 2, this.height / 4 + 8, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }
}
