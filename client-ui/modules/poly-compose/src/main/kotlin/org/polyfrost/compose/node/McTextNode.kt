package org.polyfrost.compose.node

import org.polyfrost.compose.layout.PolySize
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext

class McTextNode : PolyNode() {
    var text: String = ""
    var color: PolyColor = PolyColor.WHITE
    var shadow: Boolean = true
    var scale: Float = 1f
    private var lines = emptyList<McFontQueue.Line>()
    private var lineHeight = 9f

    fun remeasure() {
        lines = McFontQueue.layoutLines(text, scale)
        lineHeight = McFontQueue.measureTextHeight(scale)
        style.width = PolySize.Px(maxOf(lines.maxOfOrNull { it.width } ?: 0f, style.textWidth ?: 0f))
        style.height = PolySize.Px(lines.size * lineHeight)
    }

    override fun render(ctx: RenderContext) {
        val renderer = McFontQueue.renderer
        // read color.argb at draw time so chroma colours keep cycling every frame
        if (renderer == null) return
        if (style.textAlign == 0) renderer(ctx.canvas, text, x, y, color.argb, shadow, scale)
        else lines.forEachIndexed { index, line ->
            renderer(ctx.canvas, line.text, x + McFontQueue.lineOffset(width, line.width, style.textAlign),
                y + index * lineHeight, color.argb, shadow, scale)
        }
    }
}
