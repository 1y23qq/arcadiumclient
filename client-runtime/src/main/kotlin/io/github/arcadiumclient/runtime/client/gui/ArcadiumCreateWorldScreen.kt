// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen
import net.minecraft.client.gui.widget.ButtonWidget
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import io.github.arcadiumclient.runtime.mixin.client.access.CreateWorldScreenAccessor
import io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess

class ArcadiumCreateWorldScreen(private val delegate: CreateWorldScreen) : ComposeScreen(RenderMode.CONTINUOUS) {
    private val access get() = delegate as CreateWorldScreenAccessor
    private val controls get() = (delegate as ScreenAccess).`arcadium$buttons`()
    private var name by mutableStateOf("")
    private var seed by mutableStateOf("")
    private var revision by mutableIntStateOf(0)

    override fun init() {
        super.init()
        delegate.init(client, width, height)
        name = access.`arcadium$nameField`().text
        seed = access.`arcadium$seedField`().text
        revision++
    }

    override fun removed() {
        delegate.removed()
        super.removed()
    }

    private fun click(button: ButtonWidget) = client.tell {
        access.`arcadium$click`(button)
        revision++
    }

    private fun close() { controls.firstOrNull { it.id == 1 }?.let(::click) }

    private fun create() { if (name.isNotBlank()) controls.firstOrNull { it.id == 0 && it.active }?.let(::click) }

    private fun changeName(value: String) {
        name = value
        access.`arcadium$nameField`().text = value
        access.`arcadium$worldName`(value)
        access.`arcadium$updateSaveName`()
        controls.firstOrNull { it.id == 0 }?.active = value.isNotBlank()
        revision++
    }

    private fun changeSeed(value: String) {
        seed = value
        access.`arcadium$seedField`().text = value
        access.`arcadium$seed`(value)
    }

    @Composable
    override fun compose() {
        Theme {
            @Suppress("UNUSED_VARIABLE") val generation = revision
            val more = access.`arcadium$moreOptions`()
            ArcadiumMenuWindow("Create world", null, onEscape = ::close) {
                Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!more) {
                        UiText("World name", 13.sp)
                        UiTextField(name, ::changeName, Modifier.fillMaxWidth(), "World name", maxLength = 32, onSubmit = ::create)
                        UiText("Saved as: ${access.`arcadium$saveName`()}", 11.sp, color = UiTextSecondary)
                    } else {
                        UiText("Seed", 13.sp)
                        UiTextField(seed, ::changeSeed, Modifier.fillMaxWidth(), "Leave blank for a random seed", maxLength = 32, onSubmit = ::create)
                    }
                    val visible = controls.filter { it.visible && it.id !in 0..1 }
                    visible.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { button ->
                                UiButton(button.message, enabled = button.active, modifier = Modifier.weight(1f)) { click(button) }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    if (!more) {
                        UiText(access.`arcadium$modeDescription`().orEmpty(), 12.sp, color = UiTextSecondary)
                        UiText(access.`arcadium$modeDescriptionExtra`().orEmpty(), 12.sp, color = UiTextSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Back", modifier = Modifier.weight(1f)) { close() }
                    val create = controls.firstOrNull { it.id == 0 }
                    UiButton("Create world", filled = true, enabled = create?.active == true && name.isNotBlank(), modifier = Modifier.weight(1f)) {
                        create?.let(::click)
                    }
                }
            }
        }
    }
}
