// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arcadiumclient.shared.debug.DebugLayout
import io.github.arcadiumclient.modern.client.features.CustomF3Config
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ui.themes.Theme

class ArcadiumCustomF3Screen(private val parent: Screen?) : ArcadiumScreen() {
    override fun onClose() = close()
    private fun close() { client.execute { client.setScreen(parent) } }
    override fun removed() { CustomF3Config.preview = false; super.removed() }

    @Composable override fun compose() {
        var revision by remember { mutableStateOf(0) }
        var right by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<String?>(null) }
        var addKind by remember { mutableStateOf("coords") }
        var preview by remember { mutableStateOf(CustomF3Config.preview) }
        val layout = remember(revision, CustomF3Config.layoutJson) { CustomF3Config.layout() }
        val column = if (right) layout.right else layout.left
        val module = column.firstOrNull { it.key == selected }
        fun persist() { CustomF3Config.storeLayout(layout); revision++ }
        Theme {
            ArcadiumMenuWindow("Custom F3 / Modules", CustomF3Config.layoutError, ::close, maxWidth = 1040.dp, maxHeight = 760.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Left", filled = !right) { right = false; selected = null }
                    UiButton("Right", filled = right) { right = true; selected = null }
                    Spacer(Modifier.weight(1f))
                    UiToggle("Live preview", preview) { preview = !preview; CustomF3Config.preview = preview }
                    UiButton("Reset layout") { CustomF3Config.storeLayout(DebugLayout.defaults()); selected = null; revision++ }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UiDropdown("", DebugLayout.catalog().map { it.id() }, addKind, { id -> DebugLayout.catalog().first { it.id() == id }.title() }, Modifier.weight(1f)) { addKind = it }
                            UiButton("Add", enabled = column.size < 64) { val added = DebugLayout.Module(addKind); column.add(added); selected = added.key; persist() }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(column, key = { it.key }) { entry ->
                                UiButton(entry.definition().title() + if (entry.enabled) "" else " (off)", filled = selected == entry.key, modifier = Modifier.fillMaxWidth()) { selected = entry.key }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val index = column.indexOf(module)
                            UiButton("Up", enabled = index > 0, modifier = Modifier.weight(1f)) { java.util.Collections.swap(column, index, index - 1); persist() }
                            UiButton("Down", enabled = index >= 0 && index < column.lastIndex, modifier = Modifier.weight(1f)) { java.util.Collections.swap(column, index, index + 1); persist() }
                            UiButton("Move " + if (right) "left" else "right", enabled = module != null && (if (right) layout.left else layout.right).size < 64, modifier = Modifier.weight(1f)) {
                                module?.let { column.remove(it); (if (right) layout.left else layout.right).add(it); selected = null; persist() }
                            }
                        }
                    }
                    LazyColumn(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (module == null) item { UiText("Select a module to edit.", 14.sp, color = UiTextSecondary) }
                        else {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UiText(module.definition().title(), 18.sp, Modifier.weight(1f))
                                    UiButton("Reset") { val index = column.indexOf(module); val replacement = DebugLayout.Module(module.kind); replacement.key = module.key; column[index] = replacement; persist() }
                                    UiButton("Delete", danger = true) { column.remove(module); selected = null; persist() }
                                }
                                UiToggle("Enabled", module.enabled, Modifier.fillMaxWidth()) { module.enabled = !module.enabled; persist() }
                            }
                            if (module.kind == "empty") item {
                                UiTextField(module.emptyLines.toString(), { value -> value.toIntOrNull()?.takeIf { it in 0..20 }?.let { module.emptyLines = it; persist() } }, Modifier.fillMaxWidth(), "Empty lines (0-20)", maxLength = 2)
                            }
                            items(module.colors.keys.toList(), key = { "color-" + it }) { name ->
                                var value by remember(module.key, name, revision) { mutableStateOf("#%06X".format(module.colors[name])) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    UiText(DebugLayout.colorLabel(name).replaceFirstChar { it.uppercase() }, 13.sp, Modifier.weight(1f))
                                    UiTextField(value, { next -> value = next; next.removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { module.colors[name] = it; persist() } }, Modifier.width(130.dp), "#RRGGBB", maxLength = 7)
                                }
                            }
                            if (module.kind == "system") item {
                                UiToggle("Memory color toggle", module.memoryColorToggle, Modifier.fillMaxWidth()) { module.memoryColorToggle = !module.memoryColorToggle; persist() }
                                var time by remember(module.key) { mutableStateOf(module.timeFormat) }
                                UiTextField(time, { next -> time = next; runCatching { java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(next)) }.onSuccess { module.timeFormat = next; persist() } }, Modifier.fillMaxWidth(), "Time format", maxLength = 80)
                            }
                            items(module.lines.keys.toList(), key = { "line-" + it }) { id ->
                                UiToggle(module.definition().lines()[id] ?: id, module.lines[id] != false, Modifier.fillMaxWidth()) { module.lines[id] = module.lines[id] == false; persist() }
                            }
                        }
                    }
                }
                UiButton("Done", filled = true, modifier = Modifier.fillMaxWidth()) { close() }
            }
        }
    }
}
