package org.polyfrost.oneconfig.internal.ui.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object BetaOptimizationPage {
    var configId: String? by mutableStateOf(null)
        private set
    var diagnostics: () -> List<String> = { emptyList() }
        private set
    val available: Boolean get() = configId != null

    fun register(id: String, status: () -> List<String>) {
        diagnostics = status
        configId = id
    }
}
