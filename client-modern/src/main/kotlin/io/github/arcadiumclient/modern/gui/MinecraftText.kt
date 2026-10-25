// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

private val gameFont = FontFamily(
    Font("assets/oneconfig/fonts/minecraft/Minecraft-Regular.otf", FontWeight.Normal),
    Font("assets/oneconfig/fonts/minecraft/Minecraft-Bold.otf", FontWeight.Bold),
)

@Composable
internal fun MinecraftText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = UiTextPrimary,
    fontSize: TextUnit = 13.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = remember(text, color) { minecraftFormatting(text, color) },
        style = TextStyle(fontFamily = gameFont, fontSize = fontSize, color = color),
        modifier = modifier, maxLines = maxLines, overflow = overflow,
    )
}

private val gameColors = intArrayOf(0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
    0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF)

internal fun minecraftFormatting(text: String, base: Color) = buildAnnotatedString {
    var color = base
    var bold = false
    var italic = false
    var underline = false
    var strike = false
    var start = 0
    var cursor = 0

    fun appendRun(end: Int) {
        if (end <= start) return
        withStyle(SpanStyle(
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            textDecoration = when {
                underline && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                underline -> TextDecoration.Underline
                strike -> TextDecoration.LineThrough
                else -> TextDecoration.None
            },
        )) { append(text.substring(start, end)) }
    }

    while (cursor + 1 < text.length) {
        if (text[cursor] != '§') { cursor++; continue }
        val code = text[cursor + 1].lowercaseChar()
        if (code == 'x' && cursor + 13 < text.length) {
            val hex = (0..5).map { offset ->
                val marker = cursor + 2 + offset * 2
                if (text[marker] != '§') null else text[marker + 1].digitToIntOrNull(16)
            }
            if (hex.all { it != null }) {
                appendRun(cursor)
                color = Color(0xFF000000.toInt() or hex.fold(0) { value, digit -> value * 16 + digit!! })
                bold = false; italic = false; underline = false; strike = false
                cursor += 14
                start = cursor
                continue
            }
        }
        val index = "0123456789abcdef".indexOf(code)
        if (index < 0 && code !in "klmnor") { cursor += 2; continue }
        appendRun(cursor)
        if (index >= 0 || code == 'r') {
            color = if (index >= 0) Color(0xFF000000.toInt() or gameColors[index]) else base
            bold = false; italic = false; underline = false; strike = false
        } else when (code) {
            'l' -> bold = true
            'm' -> strike = true
            'n' -> underline = true
            'o' -> italic = true
        }
        cursor += 2
        start = cursor
    }
    appendRun(text.length)
}

internal fun componentFormatting(component: Component): String = buildString {
    component.visit(FormattedText.StyledContentConsumer<Unit> { style, content ->
        append("§r")
        style.color?.let { color ->
            append("§x")
            color.value.toString(16).padStart(6, '0').forEach { append('§').append(it) }
        }
        if (style.isBold) append("§l")
        if (style.isItalic) append("§o")
        if (style.isUnderlined) append("§n")
        if (style.isStrikethrough) append("§m")
        append(content)
        Optional.empty()
    }, Style.EMPTY)
}
