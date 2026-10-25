// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI

class ArcadiumPauseScreen : PauseScreen(true) {
    override fun init() {
        super.init()
        val buttons = children().filterIsInstance<Button>()
        val bottom = buttons.maxOfOrNull { it.y + it.height } ?: return
        val shift = (bottom + 48 - height + 8).coerceAtLeast(0)
        if (shift > 0) buttons.forEach { it.y -= shift }
        val y = bottom - shift + 4
        addRenderableWidget(Button.builder(Component.literal("Arcadium Options")) { OneConfigUI.open() }
            .bounds(width / 2 - 102, y, 98, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Skins")) { minecraft.setScreen(SkinChangerScreen(this)) }
            .bounds(width / 2 + 4, y, 98, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Accounts")) { openAccounts() }
            .bounds(width / 2 - 102, y + 24, 204, 20).build())
    }

    private fun openAccounts() {
        minecraft.setScreen(ConfirmScreen({ confirmed ->
            if (confirmed) minecraft.reportingContext.draftReportHandled(minecraft, this, {
                minecraft.disconnectFromWorld(Component.translatable("menu.savingLevel"))
                minecraft.setScreen(ArcadiumMainMenu(true))
            }, false) else minecraft.setScreen(this)
        }, Component.literal("Switch accounts?"), Component.literal("Leave the current world to open the account manager.")))
    }
}
