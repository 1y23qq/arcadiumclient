// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen
import net.minecraft.client.gui.screens.achievement.StatsScreen
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen
import net.minecraft.network.chat.Component
import dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.internal.ui.themes.Theme

class ArcadiumPauseScreen : ArcadiumScreen() {
    private var accounts by mutableStateOf(false)
    private var confirming by mutableStateOf(false)
    private var discrete by mutableStateOf(false)
    private var confirmTicks by mutableIntStateOf(0)
    private val confirmationConfig get() = ConfirmDisconnectConfig.CONFIG.instance()
    private val confirmationReady get() = !shouldConfirm() || confirmTicks >= confirmationConfig.confirmDelay.coerceAtLeast(0.0) * 20

    override fun isPauseScreen(): Boolean = true
    override fun init() {
        super.init()
        discrete = false
        confirmTicks = 0
    }
    override fun tick() {
        super.tick()
        if ((confirming || discrete || accounts) && confirmTicks < Int.MAX_VALUE) confirmTicks++
    }
    override fun onClose() {
        when {
            confirming -> confirming = false
            accounts -> accounts = false
            else -> { discrete = false; client.gui.setScreen(null) }
        }
    }

    private fun shouldConfirm(): Boolean {
        val config = confirmationConfig
        return config.confirmEnabled && if (client.hasSingleplayerServer()) config.enableInSingleplayer else config.enableInMultiplayer
    }

    private fun requestDisconnect() {
        if (!shouldConfirm()) { disconnect(false); return }
        if (confirmationConfig.useDiscreteConfirmation) {
            if (discrete && confirmationReady) disconnect(false)
            else if (!discrete) { discrete = true; confirmTicks = 0 }
        } else {
            confirming = true
            confirmTicks = 0
        }
    }

    private fun disconnect(showAccounts: Boolean) {
        client.reportingContext.draftReportHandled(client, this, {
            client.disconnectFromWorld(Component.translatable("menu.savingLevel"))
            client.gui.setScreen(ArcadiumMainMenu(showAccounts))
        }, false)
    }

    @Composable private fun ConfirmationButtons(confirmText: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val confirm: @Composable () -> Unit = {
                UiButton(confirmText, Modifier.weight(1f), filled = true, danger = true, enabled = confirmationReady, onClick = onConfirm)
            }
            val cancel: @Composable () -> Unit = {
                UiButton("Cancel", Modifier.weight(1f), enabled = confirmationReady, onClick = onCancel)
            }
            if (confirmationConfig.confirmOnLeft) { confirm(); cancel() } else { cancel(); confirm() }
        }
    }

    @Composable override fun compose() {
        Theme {
            ArcadiumMenuWindow("Game menu", null, ::onClose, maxWidth = 510.dp, maxHeight = 560.dp) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    UiButton("Back to game", Modifier.fillMaxWidth(), filled = true) { client.gui.setScreen(null) }
                    UiButton("Arcadium Options", Modifier.fillMaxWidth()) { OneConfigUI.open() }
                    UiButton("Options", Modifier.fillMaxWidth()) { client.gui.setScreen(OptionsScreen(this@ArcadiumPauseScreen, client.options, true)) }
                    UiButton("Resource Packs", Modifier.fillMaxWidth()) { client.gui.setScreen(ArcadiumResourcePacksScreen(this@ArcadiumPauseScreen)) }
                    UiButton("Skins", Modifier.fillMaxWidth()) { client.gui.setScreen(SkinChangerScreen(this@ArcadiumPauseScreen)) }
                    UiButton("Accounts", Modifier.fillMaxWidth()) { accounts = true; confirmTicks = 0 }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UiButton("Advancements", Modifier.weight(1f), enabled = client.connection != null) {
                            client.connection?.let { client.gui.setScreen(AdvancementsScreen(it.advancements, this@ArcadiumPauseScreen)) }
                        }
                        UiButton("Statistics", Modifier.weight(1f), enabled = client.player != null) {
                            client.player?.let { client.gui.setScreen(StatsScreen(this@ArcadiumPauseScreen, it.stats)) }
                        }
                    }
                    if (client.singleplayerServer != null) UiButton("Multiplayer options", Modifier.fillMaxWidth()) {
                        client.gui.setScreen(MultiplayerOptionsScreen(this@ArcadiumPauseScreen))
                    }
                    UiButton(if (discrete) "Confirm disconnect" else "Disconnect", Modifier.fillMaxWidth(), danger = true,
                        enabled = !discrete || confirmationReady, onClick = ::requestDisconnect)
                }
            }
            if (accounts) UiModalScrim({ accounts = false }) {
                ModalPanel(440.dp) {
                    UiText("Switch accounts?")
                    UiText("Leave the current world to open the account manager.")
                    ConfirmationButtons("Leave world", { disconnect(true) }, { accounts = false })
                }
            }
            if (confirming) UiModalScrim({ confirming = false }) {
                ModalPanel(440.dp) {
                    UiText(Component.translatable(if (client.hasSingleplayerServer()) "confirm-disconnect.are-you-sure-local"
                        else "confirm-disconnect.are-you-sure-multiplayer").string)
                    ConfirmationButtons(if (client.hasSingleplayerServer()) "Quit" else "Disconnect", { if (confirmationReady) disconnect(false) }, { confirming = false })
                }
            }
        }
    }
}
