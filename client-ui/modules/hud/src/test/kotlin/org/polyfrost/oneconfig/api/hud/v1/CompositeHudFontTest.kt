package org.polyfrost.oneconfig.api.hud.v1

import androidx.compose.runtime.Composable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.node.McTextNode

class CompositeHudFontTest {
    private class CompositeHud : Hud("composite-font", "Composite", Category.INFO) {
        override fun update() = true
        @Composable override fun Content() {
            PolyText("AAAA", fontSize = 16f, font = "poppins")
        }
    }

    @Test fun globalOverrideReachesCompositeTextWithoutChangingSavedFont() {
        val previousOverride = McFontQueue.overrideModFont
        val previousWidth = McFontQueue.measureWidth
        val previousHeight = McFontQueue.measureHeight
        val hud = CompositeHud().apply { font = Font.Poppins }
        try {
            McFontQueue.overrideModFont = { true }
            McFontQueue.measureWidth = { _, scale -> 37f * scale }
            McFontQueue.measureHeight = { scale -> 9f * scale }
            val runtime = hud.runtime
            runtime.frame(960f, 540f)
            assertEquals(Font.Poppins, hud.font)
            val node = runtime.root.children.single()
            assertTrue(node is McTextNode)
            assertEquals(74f, node.width, 0.001f)
            assertEquals(18f, node.height, 0.001f)
        } finally {
            hud.runtimeOrNull?.dispose()
            McFontQueue.overrideModFont = previousOverride
            McFontQueue.measureWidth = previousWidth
            McFontQueue.measureHeight = previousHeight
        }
    }
}
