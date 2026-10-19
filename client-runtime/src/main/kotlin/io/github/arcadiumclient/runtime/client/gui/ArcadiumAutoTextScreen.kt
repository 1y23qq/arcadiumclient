// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.gui.screen.Screen
import java.util.function.Predicate
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.internal.ui.api.settings.KeybindOptionData
import org.polyfrost.oneconfig.internal.ui.components.settings.KeybindOption
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import io.github.arcadiumclient.runtime.client.features.AutoText
import io.github.arcadiumclient.runtime.client.features.AutoTextConfig

class ArcadiumAutoTextScreen(private val parent: Screen?) : ComposeScreen(RenderMode.CONTINUOUS) {
    private fun close() {
        AutoText.save()
        client.tell { client.openScreen(parent) }
    }

    @Composable
    override fun compose() {
        var moduleEnabled by remember { mutableStateOf(AutoTextConfig.enabled) }
        @Suppress("UNCHECKED_CAST")
        val enabledProperty = remember { AutoTextConfig.getTree()?.getProp("enabled") as? Property<Boolean> }
        DisposableEffect(enabledProperty) {
            val callback = Predicate<Boolean> { changed -> moduleEnabled = changed; false }
            enabledProperty?.addCallback(callback)
            onDispose { enabledProperty?.removeCallback(callback) }
        }
        Theme {
            ArcadiumMenuWindow("AutoText", AutoText.error, ::close, maxWidth = 980.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UiButton(if (moduleEnabled) "Enabled" else "Disabled", filled = moduleEnabled) {
                        val next = !AutoTextConfig.enabled
                        if (enabledProperty == null) AutoTextConfig.enabled = next else enabledProperty.set(next)
                        moduleEnabled = AutoTextConfig.enabled
                        AutoTextConfig.save()
                    }
                    Spacer(Modifier.weight(1f))
                }
                LazyColumn(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (AutoText.entries.isEmpty()) item {
                        UiText("No messages.", 14.sp, color = UiTextSecondary)
                    }
                    items(AutoText.entries, key = { it.id }) { entry ->
                        val binding = remember(entry.id) { KeybindOptionData(AutoText.binding(entry.id)) }
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UiTextField(entry.name, { AutoText.rename(entry.id, it) }, Modifier.weight(1f), "Name", maxLength = 48)
                                KeybindOption(binding)
                                UiButton(if (entry.enabled) "On" else "Off", modifier = Modifier.width(62.dp), filled = entry.enabled) {
                                    AutoText.toggle(entry.id)
                                }
                                UiButton("Delete", modifier = Modifier.width(84.dp), danger = true) { AutoText.remove(entry.id) }
                            }
                            UiTextField(entry.text, { AutoText.setText(entry.id, it) }, Modifier.fillMaxWidth(), "Message or /command", maxLength = 100)
                            if (entry.text.isNotEmpty() && !AutoText.validMessage(entry.text)) {
                                UiText("Use a single line without control characters or color codes.", 12.sp, color = UiDangerColor)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Add message", enabled = AutoText.canAdd(), filled = true) { AutoText.add() }
                    Spacer(Modifier.weight(1f))
                    UiButton("Done", modifier = Modifier.width(110.dp)) { close() }
                }
            }
        }
    }
}
