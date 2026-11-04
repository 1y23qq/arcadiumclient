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
import org.polyfrost.oneconfig.internal.ui.api.PerformanceCapturePage
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.onClick
import org.polyfrost.oneconfig.internal.ui.components.rememberInteractionSource
import org.polyfrost.oneconfig.internal.ui.themes.accentBackground
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (query.isEmpty()) item { PerformanceCaptureControls() }
            if (visible.isEmpty()) item {
                Text("No matching diagnostics.", color = LocalTheme.current.textColorSecondary, fontSize = 14.sp)
            }
            items(visible) { Text(it, color = LocalTheme.current.textColorSecondary, fontSize = 14.sp) }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun PerformanceCaptureControls() {
    val controller = PerformanceCapturePage.controller ?: return
    var status by remember(controller) { mutableStateOf(controller.snapshot()) }
    var duration by remember { mutableStateOf(60) }
    LaunchedEffect(controller) {
        while (true) { status = controller.snapshot(); delay(500) }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Performance report", color = LocalTheme.current.textColor, fontSize = 18.sp)
        Text("Join the scene where performance is poor, then record while playing normally. Recording closes this menu and starts after 5 seconds.",
            color = LocalTheme.current.textColorSecondary, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!status.busy) {
                for (seconds in listOf(30, 60, 120)) CaptureButton(if (seconds == duration) "$seconds s (selected)" else "$seconds s") { duration = seconds }
                CaptureButton("Record") { controller.start(duration); status = controller.snapshot() }
            } else {
                if (status.state == "Recording") CaptureButton("Finish and save") { controller.stop() }
                if (status.state != "Saving report" && status.state != "Cancelling") CaptureButton("Cancel") { controller.cancel() }
            }
            CaptureButton("Open reports folder") { controller.openFolder() }
        }
        Text(if (status.state == "Recording") "Recording: ${status.elapsedSeconds} / ${status.durationSeconds} s" else status.state,
            color = LocalTheme.current.textColor, fontSize = 14.sp)
        Text(status.detail, color = LocalTheme.current.textColorSecondary, fontSize = 13.sp)
        status.archiveName?.let { Text(it, color = LocalTheme.current.textColor, fontSize = 13.sp) }
        Text("The ZIP contains hardware, numeric settings, frame timings and sampled code stacks. It does not include chat, tokens, player names, server addresses, screenshots or raw logs. Nothing is uploaded automatically.",
            color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun CaptureButton(label: String, action: () -> Unit) {
    Box(Modifier.accentBackground(LocalTheme.current.buttonShape)
        .onClick(rememberInteractionSource(), action).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = LocalTheme.current.accentTextColor, fontSize = 13.sp)
    }
}
