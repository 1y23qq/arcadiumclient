// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.gui.screen.Screen
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import io.github.arcadiumclient.runtime.client.RuntimeServices
import io.github.arcadiumclient.runtime.client.resourcepack.CustomPackMaker
import java.nio.file.Path

class CustomTexturePackScreen(
    private val parent: Screen,
    private val directory: Path,
    private val sources: List<CustomPackMaker.Source>,
    initial: CustomPackMaker.Source?,
    private val onCreated: () -> Unit,
) : ComposeScreen(RenderMode.CONTINUOUS) {
    private var name by mutableStateOf("My Custom Pack")
    private var base by mutableStateOf(initial ?: sources.firstOrNull())
    private var choices by mutableStateOf<Map<CustomPackMaker.Element, CustomPackMaker.Source>>(emptyMap())
    private var picking by mutableStateOf(false)
    private var pickingBase = false
    private var pickingElement: CustomPackMaker.Element? = null
    private var query by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var saved by mutableStateOf<String?>(null)

    private fun close() { if (!busy) client.tell { client.openScreen(parent) } }

    private fun create() {
        if (busy) return
        busy = true
        error = null
        saved = null
        val selectedBase = base
        val selectedChoices = choices.toMap()
        val selectedName = name
        RuntimeServices.SCOPE.launch {
            runCatching {
                withContext(Dispatchers.IO) { CustomPackMaker.create(directory, selectedName, selectedBase, selectedChoices) }
            }.onSuccess { result -> client.tell {
                busy = false
                saved = "Saved ${result.path().fileName} (${result.files()} files)."
                onCreated()
            } }.onFailure { failure -> client.tell {
                busy = false
                error = failure.message ?: "Could not create the resource pack."
            } }
        }
    }

    private fun choose(source: CustomPackMaker.Source?) {
        if (pickingBase) base = source
        else pickingElement?.let { element -> choices = if (source == null) choices - element else choices + (element to source) }
        picking = false
        saved = null
    }

    @Composable
    override fun compose() {
        Theme {
            Box(Modifier.fillMaxSize()) {
                ArcadiumMenuWindow("Custom Texture Pack Maker", error, ::close, maxWidth = 980.dp) {
                    UiTextField(name, { name = it; saved = null }, Modifier.fillMaxWidth(), "Pack name", maxLength = 80)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        UiText("Base pack", modifier = Modifier.width(140.dp))
                        SourceButton(base, "Choose a pack", Modifier.weight(1f), enabled = !busy) {
                            pickingBase = true; pickingElement = null; query = ""; picking = true
                        }
                    }
                    LazyColumn(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CustomPackMaker.Element.values().toList()) { element ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                UiText(element.label, modifier = Modifier.width(140.dp))
                                SourceButton(choices[element], "Use base pack", Modifier.weight(1f), enabled = !busy) {
                                    pickingBase = false; pickingElement = element; query = ""; picking = true
                                }
                            }
                        }
                    }
                    saved?.let { UiText(it, 13.sp) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiButton("Reset elements", enabled = !busy) { choices = emptyMap(); saved = null }
                        Spacer(Modifier.weight(1f))
                        UiButton("Done", enabled = !busy) { close() }
                        UiButton(if (busy) "Creating pack..." else "Create pack", filled = true,
                            enabled = !busy && base != null && name.isNotBlank()) { create() }
                    }
                }
                if (picking) UiModalScrim({ picking = false }) {
                    ModalPanel(560.dp) {
                        UiText(if (pickingBase) "Base pack" else pickingElement?.label.orEmpty(), 18.sp)
                        UiTextField(query, { query = it }, Modifier.fillMaxWidth(), "Search packs")
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!pickingBase) item { UiButton("Use base pack", Modifier.fillMaxWidth()) { choose(null) } }
                            items(sources.filter { it.name().replace(Regex("§."), "").contains(query, ignoreCase = true) }, key = { it.path() }) { source ->
                                SourceButton(source, "", Modifier.fillMaxWidth()) { choose(source) }
                            }
                        }
                        UiButton("Cancel") { picking = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceButton(source: CustomPackMaker.Source?, fallback: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit) {
    if (source == null) UiButton(fallback, modifier, enabled = enabled, onClick = onClick)
    else Box(modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
        UiButton("", Modifier.matchParentSize(), enabled = enabled, onClick = onClick)
        MinecraftText(source.name(), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
