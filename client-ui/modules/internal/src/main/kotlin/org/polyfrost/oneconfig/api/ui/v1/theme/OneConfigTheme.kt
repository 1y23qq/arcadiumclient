// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.oneconfig.internal.ui.themes.ThemeRegistry
import org.polyfrost.oneconfig.internal.ui.themes.UITheme

class OneConfigUITheme internal constructor(private val theme: UITheme) {
    val pageBackground: Color get() = theme.pageBackground
    val sidebarBackground: Color get() = theme.sidebarBackground
    val modCardBackground: Color get() = theme.modCardBackground
    val componentBackground: Color get() = theme.componentBackground
    val popupBackground: Color get() = theme.popupBackground
    val borderColor: Color get() = theme.borderColor
    val textColor: Color get() = theme.textColor
    val textColorSecondary: Color get() = theme.textColorSecondary
    val accentTextColor: Color get() = theme.accentTextColor
    val backgroundShape: Shape get() = theme.backgroundShape
    val sideBarNavigationEntryShape: Shape get() = theme.sideBarNavigationEntryShape
    val modCardShape: Shape get() = theme.modCardShape
    val buttonShape: Shape get() = theme.buttonShape
    val popupShape: Shape get() = theme.popupShape
    val fontFamily: FontFamily get() = theme.typography.family
}

object OneConfigTheme {
    @JvmStatic
    fun ensureLoaded() = ThemeRegistry.init()

    val current: OneConfigUITheme
        @Composable get() {
            val theme = LocalTheme.current
            return remember(theme) { OneConfigUITheme(theme) }
        }

    @get:JvmName("getAccent")
    val accent: Color
        @Composable get() = Accent
}

@Composable
fun ProvideOneConfigTheme(content: @Composable () -> Unit) = Theme(content)
