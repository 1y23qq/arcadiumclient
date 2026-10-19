package io.github.arcadiumclient.featuretest;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.menu.SurvivalInventoryScreen;

public final class ArmorEvidenceScreen extends Screen {
    private final QaPlayer player;
    private final boolean hidden;

    public ArmorEvidenceScreen(QaPlayer player, boolean hidden) {
        this.player = player;
        this.hidden = hidden;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        drawCenteredString(textRenderer, hidden ? "Armor hidden" : "Armor visible", width / 2, height / 2 - 120, -1);
        SurvivalInventoryScreen.renderEntity(width / 2, height / 2 + 90, 90, 0, 0, player);
        drawCenteredString(textRenderer, "Defense: " + player.inventory.getArmorProtection(), width / 2, height / 2 + 115, -1);
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override public boolean shouldPauseGame() { return false; }
}
