// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyInsets
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.TextHud

abstract class ColoredTextHud(id: String, title: String, category: Category) : TextHud(id, title, category, "") {
    data class Run(val text: String, val color: Int? = null, val minecraftFont: Boolean = false)
    private var runs: MutableState<List<Run>> = mutableStateOf(emptyList())

    protected abstract fun valueRuns(): List<Run>
    override fun getText(): String = valueRuns().joinToString("") { it.text }

    override fun update(): Boolean {
        runs.value = buildList {
            if (brackets) add(Run("["))
            if (prefix.isNotEmpty()) add(Run(prefix))
            addAll(valueRuns())
            if (suffix.isNotEmpty()) add(Run(suffix))
            if (brackets) add(Run("]"))
        }
        return true
    }

    @Composable
    override fun Content() {
        val overrideFont = McFontQueue.revision.let { McFontQueue.overrideModFont?.invoke() == true }
        val skiaFont = if (font == Font.Poppins && !overrideFont) FontManager.getFont(8f * textScale, getPoppinsFontName()) else null
        val formatted = runs.value.map { run ->
            val text = when (caseType) { 1 -> run.text.uppercase(); 2 -> run.text.lowercase(); else -> run.text }
            run.copy(text = text)
        }
        val lines = arrayListOf(arrayListOf<Run>())
        for (run in formatted) {
            run.text.split('\n').forEachIndexed { index, text ->
                if (index > 0) lines.add(arrayListOf())
                lines.last().add(run.copy(text = text))
            }
        }
        fun nativeText(run: Run) = buildString {
            if (textBold) append("§l")
            if (textItalic) append("§o")
            if (textUnderline) append("§n")
            append(run.text)
        }
        fun native(run: Run) = skiaFont == null || run.minecraftFont
        fun width(run: Run) = if (native(run)) McFontQueue.measureTextWidth(nativeText(run), textScale)
            else skiaFont!!.measureTextWidth(run.text)
        val widths = lines.map { line -> line.sumOf { width(it).toDouble() }.toFloat() }
        val nativeHeight = McFontQueue.measureTextHeight(textScale)
        val height = if (skiaFont == null) nativeHeight else maxOf(nativeHeight, skiaFont.metrics.descent - skiaFont.metrics.ascent)
        val static = staticWidth && staticW > 0f && staticH > 0f
        val blockWidth = maxOf(widths.maxOrNull() ?: 0f, if (static) staticW - padLeft - padRight else 0f)
        val blockHeight = height * lines.size
        val background = hudBackground().let { if (static) it.size(staticW, staticH) else it }
            .padding(PolyInsets(padLeft, padTop, padRight, padBottom))
        val foreground = PolyColor(textColor, textChroma, textChromaSpeed)
        PolyBox(modifier = background) {
            PolyCanvas(PolyModifier.size(blockWidth, blockHeight).let { if (static) it.align(alignment) else it }) { x, y, _, _ ->
                for ((index, line) in lines.withIndex()) {
                    var cursor = x + McFontQueue.lineOffset(blockWidth, widths[index], textAlign)
                    val top = y + index * height
                    for (run in line) {
                        val color = run.color?.let { PolyColor(it) } ?: foreground
                        if (native(run)) {
                            McFontQueue.renderer?.invoke(canvas, nativeText(run), cursor, top, color.argb, showShadow, textScale)
                        } else {
                            val baseline = top - skiaFont!!.metrics.ascent
                            if (showShadow) text(run.text, cursor + shadowOffsetX, baseline + shadowOffsetY,
                                PolyColor(shadowColor, shadowChroma, shadowChromaSpeed), skiaFont)
                            text(run.text, cursor, baseline, color, skiaFont)
                            if (textUnderline) line(cursor, baseline + textScale, cursor + width(run), baseline + textScale, color, textScale)
                        }
                        cursor += width(run)
                    }
                }
            }
        }
    }

    override fun clone(): Hud = (super.clone() as ColoredTextHud).also { it.runs = mutableStateOf(emptyList()) }
}
