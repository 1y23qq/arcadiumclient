package org.polyfrost.oneconfig.internal.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.internal.ui.api.BetaOptimizationPage
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.shell.ShellState
import org.polyfrost.oneconfig.internal.ui.shell.rememberRestorableLazyListState
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

@Composable
fun Optimizations() {
    val revision = ConfigRegistry.revision
    val id = BetaOptimizationPage.configId
    val tree = id?.let(ConfigRegistry::findTree)
    DisposableEffect(Unit) {
        ShellState.title = "Optimizations (Beta)"
        ShellState.titleInfoForTitle = null
        onDispose { }
    }
    if (id != null && tree != null) key(revision, tree) {
        ConfigScreen(tree, initialCategory = "Hypixel", pageKey = id)
    }
}

@Composable
fun OptimizationDiagnostics() {
    val provider = BetaOptimizationPage.diagnostics
    var diagnostics by remember(provider) { mutableStateOf(provider()) }
    val query = ShellState.searchQuery.trim()
    val visible = diagnostics.filter { it.contains(query, ignoreCase = true) }
    val listState = rememberRestorableLazyListState("optimizations/diagnostics", query)
    DisposableEffect(Unit) {
        ShellState.title = "Optimization Diagnostics"
        ShellState.titleInfoForTitle = null
        onDispose { }
    }
    LaunchedEffect(provider) {
        while (true) {
            diagnostics = provider()
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching diagnostics.", color = LocalTheme.current.textColorSecondary, fontSize = 14.sp)
            }
            return@Box
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visible) { Text(it, color = LocalTheme.current.textColorSecondary, fontSize = 14.sp) }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
