// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.arcadiumclient.modern.gui.ArcadiumScreen
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

class PotionOverridesQaScreen(private val property: Property<*>) : ArcadiumScreen() {
    private val visualizer = when (val metadata = property.getMetadata<Any>("visualizer")) {
        is Visualizer -> metadata
        is Class<*> -> metadata.getDeclaredConstructor().newInstance() as Visualizer
        else -> error("Native Potion Effects visualizer metadata is missing")
    }

    init {
        check(visualizer.javaClass.name == "org.polyfrost.evergreenhud.client.hud.potion.OverrideOptionsVisualizer") {
            "Native Potion Effects setup did not attach its own visualizer: ${visualizer.javaClass.name}"
        }
    }

    @Composable override fun compose() {
        Theme {
            Column(Modifier.fillMaxSize().padding(32.dp)) {
                Text("Potion Effect Overrides", color = LocalTheme.current.textColor)
                visualizer.visualize(property)
            }
        }
    }
}
