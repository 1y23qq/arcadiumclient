// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import io.github.arcadiumclient.modern.client.features.CrosshairDesign
import io.github.arcadiumclient.modern.client.features.CustomCrosshair
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

class CustomCrosshairScreen(private val parent: Screen?) : ArcadiumScreen() {
    private var draft by mutableStateOf(CustomCrosshair.design().copy())
    private var tab by mutableIntStateOf(if (draft.mode == 1) 2 else 0)
    private var enabled by mutableStateOf(CustomCrosshair.enabled())
    private var profileName by mutableStateOf(draft.name)
    private var profiles by mutableStateOf(CustomCrosshair.profiles())
    private var selected by mutableIntStateOf(-1)
    private var mirrorX by mutableStateOf(false)
    private var mirrorY by mutableStateOf(false)
    private var eraser by mutableStateOf(false)
    private var importing by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private val undo = ArrayDeque<CrosshairDesign>()
    private var historyRevision by mutableIntStateOf(0)

    private fun change(action: CrosshairDesign.() -> Unit) {
        draft = draft.copy().apply(action).apply { normalize() }
        CustomCrosshair.update(draft)
    }

    private fun load(design: CrosshairDesign) {
        draft = design.copy()
        profileName = draft.name
        tab = if (draft.mode == 1) 2 else 0
        undo.clear()
        historyRevision++
        CustomCrosshair.update(draft)
    }

    private fun checkpoint() {
        undo.addLast(draft.copy())
        while (undo.size > 20) undo.removeFirst()
        historyRevision++
    }

    private fun close() {
        if (CustomCrosshair.save()) client.execute { client.gui.setScreen(parent) }
        else error = "Could not save crosshairs. Try again."
    }

    override fun removed() {
        CustomCrosshair.save()
        super.removed()
    }

    private fun importPng() {
        importing = true
        Multithreading.submit {
            val result = runCatching {
                val file = TinyFdApi.getInstance().openFileSelector("Import crosshair PNG", null, arrayOf("*.png"), "PNG image")
                    ?: return@runCatching null
                require(Files.size(file) <= 4 * 1024 * 1024) { "The PNG must be smaller than 4 MB." }
                ImageIO.createImageInputStream(file.toFile()).use { input ->
                    val readers = ImageIO.getImageReaders(input)
                    require(readers.hasNext()) { "The selected file is not a PNG image." }
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val width = reader.getWidth(0)
                        val height = reader.getHeight(0)
                        require(width in 1..64 && height in 1..64) { "Use a PNG no larger than 64 by 64 pixels." }
                        val image = reader.read(0)
                        val dimension = if (maxOf(width, height) <= 16) 16 else if (maxOf(width, height) <= 32) 32 else 64
                        CrosshairDesign().apply {
                            mode = 1
                            outline = false
                            canvasSize = dimension
                            pixels = IntArray(dimension * dimension)
                            name = file.fileName.toString().substringBeforeLast('.').take(32)
                            val left = (dimension - width) / 2
                            val top = (dimension - height) / 2
                            for (y in 0 until height) for (x in 0 until width) pixels[(top + y) * dimension + left + x] = image.getRGB(x, y)
                        }
                    } finally { reader.dispose() }
                }
            }
            client.execute {
                importing = false
                result.onSuccess { design -> design?.let(::load) }.onFailure { error = it.message ?: "Could not import the PNG." }
            }
        }
    }

    @Composable
    override fun compose() {
        Theme {
            ArcadiumMenuWindow("Crosshair", error, ::close, maxWidth = 960.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Custom", "Presets", "Canvas").forEachIndexed { index, name ->
                        UiButton(name, filled = tab == index, modifier = Modifier.width(100.dp)) {
                            tab = index
                            if (index != 1) change { mode = if (index == 2) 1 else 0 }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    UiToggle("Enabled", enabled, Modifier.width(190.dp)) {
                        enabled = !enabled
                        CustomCrosshair.setEnabled(enabled)
                    }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (tab) {
                            0 -> customControls()
                            1 -> CrosshairDesign.presets().forEach { preset ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CrosshairPreview(preset, Modifier.size(64.dp))
                                    UiText(preset.name, 14.sp, Modifier.weight(1f))
                                    UiButton("Use") { selected = -1; load(preset) }
                                }
                            }
                            else -> canvasControls()
                        }
                    }
                    Column(Modifier.width(260.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UiText("Preview (4x)", 12.sp, color = UiTextSecondary)
                        CrosshairPreview(draft, Modifier.fillMaxWidth().height(180.dp))
                        UiText("Saved crosshairs", 14.sp)
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (profiles.isEmpty()) UiText("Save the current design to add a crosshair.", 12.sp, color = UiTextSecondary, maxLines = 3)
                            profiles.forEachIndexed { index, profile ->
                                UiButton(profile.name, filled = selected == index, modifier = Modifier.fillMaxWidth()) {
                                    selected = index
                                    load(profile)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            UiButton("New", modifier = Modifier.weight(1f)) { selected = -1; load(CrosshairDesign()) }
                            UiButton("Delete", enabled = selected in profiles.indices, danger = true, modifier = Modifier.weight(1f)) {
                                if (CustomCrosshair.deleteProfile(selected)) {
                                    profiles = CustomCrosshair.profiles()
                                    selected = -1
                                } else error = "Could not delete the saved crosshair."
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiTextField(profileName, { profileName = it }, Modifier.weight(1f), "Crosshair name", maxLength = 32)
                    UiButton("Save profile", enabled = profileName.isNotBlank()) {
                        if (CustomCrosshair.saveProfile(profileName)) {
                            profiles = CustomCrosshair.profiles()
                            selected = profiles.indexOfFirst { it.name.equals(profileName.trim(), true) }
                            error = null
                        } else error = "Could not save the profile. Keep fewer than 64 profiles and try again."
                    }
                    UiButton("Done", filled = true, onClick = ::close)
                }
            }
        }
    }

    @Composable
    private fun customControls() {
        UiText("Shape", 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Cross", "Plus", "Ring", "Dot", "T").forEachIndexed { index, label ->
                UiButton(label, filled = draft.shape == index) { change { shape = index } }
            }
        }
        CrosshairNumber("Size", draft.length, 1, 20) { change { length = it } }
        CrosshairNumber("Thickness", draft.thickness, 1, 8) { change { thickness = it } }
        CrosshairNumber("Gap", draft.gap, 0, 20) { change { gap = it } }
        CrosshairColor("Color", draft.color) { change { color = it } }
        UiToggle("Center dot", draft.dot) { change { dot = !dot } }
        if (draft.dot || draft.shape == 3) CrosshairNumber("Dot size", draft.dotSize, 1, 9) { change { dotSize = it } }
        commonControls()
    }

    @Composable
    private fun commonControls() {
        UiToggle("Outline", draft.outline) { change { outline = !outline } }
        if (draft.outline) {
            CrosshairNumber("Outline width", draft.outlineWidth, 1, 4) { change { outlineWidth = it } }
            CrosshairColor("Outline color", draft.outlineColor) { change { outlineColor = it } }
        }
        CrosshairNumber("Opacity", (draft.opacity * 100).roundToInt(), 0, 100, 5, "%") { change { opacity = it / 100f } }
        CrosshairNumber("Scale", (draft.scale * 100).roundToInt(), 25, 400, 25, "%") { change { scale = it / 100f } }
    }

    @Composable
    private fun canvasControls() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            UiText("Canvas size", 13.sp, Modifier.weight(1f))
            listOf(16, 32, 64).forEach { dimension ->
                UiButton("${dimension}px", filled = draft.canvasSize == dimension) {
                    checkpoint()
                    change { resizeCanvas(dimension) }
                }
            }
        }
        val latest by rememberUpdatedState(draft)
        Canvas(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "Pixel crosshair canvas. Drag to draw, right click to erase." }
            .pointerInput(draft.canvasSize, mirrorX, mirrorY, eraser) {
                awaitPointerEventScope {
                    var previous: Pair<Int, Int>? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull() ?: continue
                        if (!pointer.pressed) { previous = null; continue }
                        val dimension = latest.canvasSize
                        val x = floor(pointer.position.x / size.width * dimension).toInt()
                        val y = floor(pointer.position.y / size.height * dimension).toInt()
                        if (x !in 0 until dimension || y !in 0 until dimension) { previous = null; continue }
                        if (previous == null) checkpoint()
                        val start = previous ?: (x to y)
                        val ink = if (eraser || event.buttons.isSecondaryPressed) 0 else latest.color
                        change {
                            mode = 1
                            val steps = maxOf(abs(x - start.first), abs(y - start.second)).coerceAtLeast(1)
                            for (step in 0..steps) {
                                val px = (start.first + (x - start.first) * step.toFloat() / steps).roundToInt()
                                val py = (start.second + (y - start.second) * step.toFloat() / steps).roundToInt()
                                paint(px, py, ink, mirrorX, mirrorY)
                            }
                        }
                        previous = x to y
                        pointer.consume()
                    }
                }
            }) {
            val dimension = draft.canvasSize
            val cell = size.width / dimension
            for (y in 0 until dimension) for (x in 0 until dimension) {
                drawRect(if ((x + y) % 2 == 0) Color(0xFF293137) else Color(0xFF22292E), Offset(x * cell, y * cell), Size(cell, cell))
                val pixel = draft.pixels[y * dimension + x]
                if (pixel ushr 24 != 0) drawRect(Color(pixel), Offset(x * cell, y * cell), Size(cell, cell))
            }
            for (i in 0..dimension) {
                val color = if (i == dimension / 2) Color(0xFF71848D) else Color(0xFF12191D)
                drawLine(color, Offset(i * cell, 0f), Offset(i * cell, size.height), 1f)
                drawLine(color, Offset(0f, i * cell), Offset(size.width, i * cell), 1f)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiToggle("Mirror X", mirrorX, Modifier.weight(1f)) { mirrorX = !mirrorX }
            UiToggle("Mirror Y", mirrorY, Modifier.weight(1f)) { mirrorY = !mirrorY }
        }
        CrosshairColor("Pen color", draft.color) { change { color = it } }
        UiToggle("Eraser", eraser) { eraser = !eraser }
        @Suppress("UNUSED_VARIABLE") val history = historyRevision
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UiButton("Undo", enabled = undo.isNotEmpty()) {
                val restored = undo.removeLast()
                historyRevision++
                draft = restored
                CustomCrosshair.update(draft)
            }
            UiButton("Clear") { checkpoint(); change { pixels = IntArray(canvasSize * canvasSize) } }
            UiButton(if (importing) "Opening..." else "Import PNG", enabled = !importing, onClick = ::importPng)
        }
        commonControls()
    }
}

@Composable
private fun CrosshairNumber(label: String, value: Int, min: Int, max: Int, step: Int = 1, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        UiText(label, 13.sp, Modifier.weight(1f))
        UiButton("-", enabled = value > min) { onChange((value - step).coerceAtLeast(min)) }
        UiText("$value$suffix", 13.sp, Modifier.width(56.dp))
        UiButton("+", enabled = value < max) { onChange((value + step).coerceAtMost(max)) }
    }
}

@Composable
private fun CrosshairColor(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf("%08X".format(value)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        UiText(label, 13.sp, Modifier.weight(1f))
        Spacer(Modifier.size(24.dp).background(Color(value)).border(1.dp, UiBorderColor))
        UiTextField(text, {
            text = it.filter { char -> char.isDigit() || char.uppercaseChar() in 'A'..'F' }.take(8).uppercase()
            if (text.length == 8) {
                text.toLongOrNull(16)?.let { parsed -> onChange(parsed.toInt()) }
            }
        }, Modifier.width(132.dp), "AARRGGBB", maxLength = 8)
    }
}

@Composable
private fun CrosshairPreview(design: CrosshairDesign, modifier: Modifier) {
    val geometry = remember(design) { design.copy().mesh().quads().map { quad ->
        Path().apply {
            moveTo(quad.x1(), quad.y1()); lineTo(quad.x2(), quad.y2()); lineTo(quad.x3(), quad.y3()); lineTo(quad.x4(), quad.y4()); close()
        } to Color(quad.color())
    } }
    Canvas(modifier.background(Color(0xFF1D252A)).border(1.dp, UiBorderColor)) {
        val zoom = if (size.width < 100) 2f else 4f
        drawContext.canvas.save()
        drawContext.canvas.translate(size.width / 2, size.height / 2)
        drawContext.canvas.scale(zoom * design.scale, zoom * design.scale)
        geometry.forEach { (path, color) -> drawPath(path, color, alpha = design.opacity) }
        drawContext.canvas.restore()
    }
}
