package org.polyfrost.oneconfig.internal.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.internal.ui.api.BetaOptimizationPage
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.shell.ShellState
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

@Composable
fun Optimizations() {
    val revision = ConfigRegistry.revision
    val id = BetaOptimizationPage.configId
    val tree = id?.let(ConfigRegistry::findTree)
    var diagnostics by remember { mutableStateOf(BetaOptimizationPage.diagnostics()) }
    DisposableEffect(Unit) {
        ShellState.title = "Optimizations (Beta)"
        onDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            diagnostics = BetaOptimizationPage.diagnostics()
            delay(1000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            diagnostics.forEach { Text(it, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp) }
        }
        if (tree != null) key(revision, tree) {
            Box(Modifier.weight(1f)) { ConfigScreen(tree, pageKey = id!!) }
        }
    }
}
