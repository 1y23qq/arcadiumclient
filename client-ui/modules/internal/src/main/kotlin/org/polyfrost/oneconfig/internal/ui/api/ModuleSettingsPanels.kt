// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.ui.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf

object ModuleSettingsPanels {
    class Panel(val title: String, val content: @Composable () -> Unit)
    private val panels = mutableStateMapOf<String, List<Panel>>()
    private val selected = mutableStateMapOf<String, String?>()

    fun register(id: String, title: String, content: @Composable () -> Unit) {
        require(title.isNotBlank())
        val key = id.removeSuffix(".json")
        panels[key] = panels[key].orEmpty().filterNot { it.title == title } + Panel(title, content)
    }

    fun forModule(id: String): List<Panel> = panels[id.removeSuffix(".json")].orEmpty()

    fun selectedTitle(id: String): String? = selected[id.removeSuffix(".json")]

    fun selectPanel(id: String, title: String?) {
        require(title == null || forModule(id).any { it.title == title })
        selected[id.removeSuffix(".json")] = title
    }
}
