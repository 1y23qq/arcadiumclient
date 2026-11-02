package org.polyfrost.oneconfig.internal.ui.navigation.graph

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import org.polyfrost.oneconfig.internal.ui.screens.Optimizations
import org.polyfrost.oneconfig.internal.ui.screens.OptimizationDiagnostics

@Serializable data object OptimizationsGraph
@Serializable data object OptimizationsRoute
@Serializable data object OptimizationDiagnosticsRoute

fun NavGraphBuilder.optimizationsGraph() {
    navigation<OptimizationsGraph>(startDestination = OptimizationsRoute) {
        composable<OptimizationsRoute> { Optimizations() }
        composable<OptimizationDiagnosticsRoute> { OptimizationDiagnostics() }
    }
}
