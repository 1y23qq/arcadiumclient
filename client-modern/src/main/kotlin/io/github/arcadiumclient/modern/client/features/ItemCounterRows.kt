// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import net.minecraft.world.item.ItemStack
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.ui.v1.item.PolyItemIcon

object ItemCounterRows {
    class Entry(val stack: ItemStack, val text: String) {
        override fun equals(other: Any?): Boolean =
            other is Entry && text == other.text && ItemStack.matches(stack, other.stack)

        override fun hashCode(): Int = text.hashCode()
    }

    private class Access(type: Class<*>) {
        val item = type.getDeclaredField("item").apply { isAccessible = true }
        val recompute = type.getDeclaredMethod("recompute").apply { isAccessible = true }
        private val display = recompute.returnType
        val stack = display.getDeclaredField("stack").apply { isAccessible = true }
        val text = display.getDeclaredField("text").apply { isAccessible = true }
    }

    private val access = object : ClassValue<Access>() {
        override fun computeValue(type: Class<*>): Access = Access(type)
    }

    @JvmStatic
    fun newState(): MutableState<List<Entry>> = mutableStateOf(emptyList())

    @JvmStatic
    fun newMode(): MutableState<Boolean> = mutableStateOf(false)

    @JvmStatic
    fun recompute(hud: Hud, selected: Array<String>): List<Entry> {
        val fields = access.get(hud.javaClass)
        val result = ArrayList<Entry>(selected.size)
        try {
            for (query in selected.distinct()) {
                fields.item.set(hud, arrayOf(query))
                val display = fields.recompute.invoke(hud) ?: continue
                result.add(Entry(fields.stack.get(display) as ItemStack, fields.text.get(display) as String))
            }
        } finally {
            fields.item.set(hud, selected)
        }
        return result
    }

    @JvmStatic
    @Composable
    fun Content(hud: Hud, entries: List<Entry>, showIcon: Boolean, horizontal: Boolean) {
        if (entries.isEmpty()) return
        val scale = hud.textScale.coerceAtLeast(0.01f)
        val color = PolyColor(hud.textColor, hud.textChroma, hud.textChromaSpeed)
        PolyBox(modifier = hud.hudBackground().padding(
            hud.padLeft + scale,
            hud.padTop + scale,
            hud.padRight + (if (showIcon) 2f else 1f) * scale,
            hud.padBottom + scale,
        )) {
            if (horizontal) {
                PolyRow(gap = 5f * scale) { Items(hud, entries, showIcon, scale, color) }
            } else {
                PolyColumn(gap = 2f * scale) { Items(hud, entries, showIcon, scale, color) }
            }
        }
    }

    @Composable
    private fun Items(hud: Hud, entries: List<Entry>, showIcon: Boolean, scale: Float, color: PolyColor) {
        val overrideFont = McFontQueue.revision.let { McFontQueue.overrideModFont?.invoke() == true }
        for (entry in entries) {
            PolyRow(gap = if (showIcon) 2f * scale else 0f) {
                if (showIcon) {
                    PolyItemIcon(entry.stack, 16f * scale, decorations = false,
                        modifier = PolyModifier.align(PolyAlign.Center))
                }
                val text = when (hud.caseType) {
                    1 -> entry.text.uppercase()
                    2 -> entry.text.lowercase()
                    else -> entry.text
                }
                if (hud.font == Font.Minecraft || overrideFont) {
                    PolyMcText(text, color = color, shadow = hud.showShadow, scale = scale,
                        modifier = PolyModifier.align(PolyAlign.Center))
                } else {
                    PolyText(text, color = color, fontSize = 8f * scale, font = hud.getPoppinsFontName(),
                        shadow = hud.showShadow,
                        shadowColor = PolyColor(hud.shadowColor, hud.shadowChroma, hud.shadowChromaSpeed),
                        shadowOffset = hud.shadowOffsetX,
                        modifier = PolyModifier.align(PolyAlign.Center))
                }
            }
        }
    }
}
