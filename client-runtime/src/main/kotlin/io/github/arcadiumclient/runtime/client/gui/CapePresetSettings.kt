// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arcadiumclient.runtime.client.capes.CapeCatalog
import io.github.arcadiumclient.runtime.client.features.CapeConfig
import java.util.Locale

@Composable
fun CapePresetSettings() {
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf(CapeConfig.preset) }
    val selected = CapeCatalog.find(selectedId)
    val entries = remember(query) {
        val term = query.trim().lowercase(Locale.ROOT)
        CapeCatalog.entries().filter { term.isEmpty() || "${it.title()} ${it.group()} ${it.id()}".lowercase(Locale.ROOT).contains(term) }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("Visible only to you. Your account cape stays unchanged.", color = UiTextSecondary)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UiTextField(query, { query = it }, Modifier.fillMaxWidth(), "Search capes", maxLength = 100)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(entries, key = { it.id() }) { entry ->
                        UiButton(entry.title(), filled = selectedId == entry.id(), modifier = Modifier.fillMaxWidth()) {
                            selectedId = entry.id()
                            CapeConfig.select(entry.id())
                        }
                    }
                    if (entries.isEmpty()) item { UiText("No capes found.", color = UiTextSecondary) }
                }
            }
            Column(Modifier.width(208.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selected != null) {
                    UiText(selected.title(), 17.sp)
                    UiText(selected.group(), color = UiTextSecondary)
                    val image = remember(selected.id()) {
                        runCatching {
                            CapeCatalog::class.java.classLoader.getResourceAsStream(selected.resource())?.use { menuImage(it.readBytes()) }
                        }.getOrNull()
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            LocalCapePreview(image, 1, Modifier.fillMaxWidth().height(160.dp))
                            UiText("Back", color = UiTextSecondary)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            LocalCapePreview(image, 12, Modifier.fillMaxWidth().height(160.dp))
                            UiText("Front", color = UiTextSecondary)
                        }
                    }
                    if (image == null) UiText("Preview unavailable.", color = UiTextSecondary)
                }
                UiText("${CapeCatalog.entries().size} capes", color = UiTextSecondary)
            }
        }
    }
}

@Composable
private fun LocalCapePreview(image: ImageBitmap?, x: Int, modifier: Modifier) {
    Canvas(modifier.background(UiControlBackground).border(1.dp, UiBorderColor)) {
        if (image == null) return@Canvas
        val factor = image.width / 64
        val unit = minOf(size.width / 12f, size.height / 18f)
        drawImage(image, srcOffset = IntOffset(x * factor, factor), srcSize = IntSize(10 * factor, 16 * factor),
            dstOffset = IntOffset(((size.width - 10 * unit) / 2).toInt(), ((size.height - 16 * unit) / 2).toInt()),
            dstSize = IntSize((10 * unit).toInt().coerceAtLeast(1), (16 * unit).toInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.None)
    }
}
