// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arcadiumclient.modern.mixin.gui.CreateWorldAccess
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode
import net.minecraft.world.Difficulty
import org.polyfrost.oneconfig.internal.ui.themes.Theme

class ArcadiumCreateWorldScreen(private val delegate: CreateWorldScreen) : ArcadiumScreen() {
    private val model = delegate.uiState
    private var revision by mutableIntStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var creating by mutableStateOf(false)

    override fun init() {
        delegate.init(width, height)
        revision++
        super.init()
    }

    override fun onClose() { if (!creating) delegate.onClose() }

    private fun change(action: () -> Unit) {
        action()
        revision++
    }

    @Composable override fun compose() {
        revision
        Theme {
            ArcadiumMenuWindow("Create world", error, ::onClose, maxWidth = 940.dp, maxHeight = 640.dp) {
                Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    UiTextField(model.name, { change { model.name = it } }, Modifier.fillMaxWidth(), "World name", maxLength = 255)
                    UiText("Saved in: ${model.targetFolder}", 11.sp, color = UiTextSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            UiDropdown("Game mode", listOf(SelectedGameMode.SURVIVAL, SelectedGameMode.HARDCORE, SelectedGameMode.CREATIVE), model.gameMode,
                                { it.displayName.string }, Modifier.fillMaxWidth()) { change { model.gameMode = it } }
                            UiDropdown("Difficulty", Difficulty.entries, model.difficulty,
                                { it.displayName.string }, Modifier.fillMaxWidth()) { change { model.difficulty = it } }
                            UiText(model.gameMode.info.string, 12.sp, color = UiTextSecondary, maxLines = 4)
                            if (!model.isHardcore && !model.isDebug) {
                                UiToggle("Allow commands", model.isAllowCommands, Modifier.fillMaxWidth()) { change { model.setAllowCommands(!model.isAllowCommands) } }
                            }
                            UiButton("Game rules", Modifier.fillMaxWidth()) {
                                client.setScreen(EditGameRulesScreen(model.gameRules) { changed ->
                                    changed.ifPresent { model.gameRules = it }
                                    client.setScreen(delegate)
                                })
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            UiDropdown("World type", model.normalPresetList, model.worldType,
                                { it.describePreset().string }, Modifier.fillMaxWidth()) { change { model.worldType = it } }
                            UiTextField(model.seed, { change { model.seed = it } }, Modifier.fillMaxWidth(), "Seed (leave empty for random)", maxLength = 128)
                            if (!model.isDebug) {
                                UiToggle("Generate structures", model.isGenerateStructures, Modifier.fillMaxWidth()) { change { model.setGenerateStructures(!model.isGenerateStructures) } }
                                if (!model.isHardcore) UiToggle("Bonus chest", model.isBonusChest, Modifier.fillMaxWidth()) { change { model.setBonusChest(!model.isBonusChest) } }
                            }
                            model.presetEditor?.let { editor ->
                                UiButton("Customize world type", Modifier.fillMaxWidth()) { client.setScreen(editor.createEditScreen(delegate, model.settings)) }
                            }
                            UiButton("Data packs", Modifier.fillMaxWidth()) {
                                (delegate as CreateWorldAccess).`arcadium$dataPacks`(model.settings.dataConfiguration())
                            }
                            UiButton("Experiments", Modifier.fillMaxWidth()) {
                                (delegate as CreateWorldAccess).`arcadium$experiments`(model.settings.dataConfiguration())
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    UiButton("Cancel", Modifier.weight(1f), enabled = !creating) { onClose() }
                    UiButton(if (creating) "Creating world..." else "Create world", Modifier.weight(2f), filled = true,
                        enabled = !creating && model.name.isNotBlank()) {
                        creating = true
                        try { (delegate as CreateWorldAccess).`arcadium$create`() }
                        catch (failure: Exception) { creating = false; error = failure.message ?: "Could not create the world." }
                    }
                }
            }
        }
    }
}
