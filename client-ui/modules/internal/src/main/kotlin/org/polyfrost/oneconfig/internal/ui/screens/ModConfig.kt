package org.polyfrost.oneconfig.internal.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.asRenderText
import org.polyfrost.oneconfig.internal.ui.shell.ShellState
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.api.ChatModuleGroup
import org.polyfrost.oneconfig.internal.ui.api.modCardToggle
import org.polyfrost.oneconfig.internal.ui.components.settings.SwitchControl
import org.polyfrost.oneconfig.internal.ui.components.Chip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun ModConfig(id: String, initialCategory: String? = null) {
    if (id == ChatModuleGroup.id || ChatModuleGroup.contains(id)) {
        ChatSettings(id, initialCategory)
        return
    }
    val revision = ConfigRegistry.revision
    val config = ConfigRegistry.findById(id)
    val tree = ConfigRegistry.findTree(id)

    DisposableEffect(id, config?.title, config?.authors, config?.credits, config?.version, tree) {
        val title = config?.title?.asRenderText()
        ShellState.title = title
        ShellState.titleInfoForTitle = title
        ShellState.titleAuthors = config?.authors
        ShellState.titleCredits = config?.credits
        ShellState.titleVersion = config?.version
        ShellState.openOriginalScreen = tree?.getMetadata<Runnable>("open_original_screen")
        onDispose {
            ShellState.titleInfoForTitle = null
            ShellState.titleAuthors = null
            ShellState.titleCredits = null
            ShellState.titleVersion = null
            ShellState.openOriginalScreen = null
        }
    }

    val externalOpen = config?.onOpen

    when {
        config == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Unknown mod: $id", color = LocalTheme.current.textColorSecondary)
        }
        tree?.getMetadata<Boolean>("ui_only") == true && externalOpen != null -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("This mod opens its own settings screen.", color = LocalTheme.current.textColorSecondary)
        }
        tree == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This mod manages its own configuration.", color = LocalTheme.current.textColorSecondary)
        }
        else -> key(revision, tree) {
            ConfigScreen(tree, initialCategory, pageKey = id)
        }
    }
}

@Composable
private fun ChatSettings(id: String, initialCategory: String?) {
    val revision = ConfigRegistry.revision
    val children = ChatModuleGroup.children()
    var selected by remember(id) { mutableStateOf(children.firstOrNull { it.id == id }?.id ?: children.firstOrNull()?.id) }
    DisposableEffect(id) {
        ShellState.title = "Chat"
        ShellState.titleInfoForTitle = null
        onDispose {
            ShellState.titleAuthors = null
            ShellState.titleCredits = null
            ShellState.titleVersion = null
            ShellState.openOriginalScreen = null
        }
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            children.forEach { child ->
                Chip(child.title.asRenderText(), selected == child.id, onClick = { selected = child.id })
            }
        }
        val active = children.firstOrNull { it.id == selected } ?: children.firstOrNull()
        val tree = active?.let { ConfigRegistry.findTree(it.id) }
        val toggle = active?.let(::modCardToggle)
        if (toggle != null && active != null) {
            var checked by remember(active.id, toggle.enabled) { mutableStateOf(toggle.enabled) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(active.title, color = LocalTheme.current.textColor)
                SwitchControl(checked) { value -> toggle.setEnabled(value); checked = toggle.enabled }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tree != null && active != null) key(revision, tree) { ConfigScreen(tree, initialCategory, pageKey = active.id) }
        }
    }
}
