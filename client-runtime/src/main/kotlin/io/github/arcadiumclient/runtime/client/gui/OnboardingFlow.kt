// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background as flatBackground
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBackground as background
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBorder as border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.function.Predicate
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.resource.Identifier
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.settings.SliderControl
import org.polyfrost.oneconfig.internal.ui.themes.*
import io.github.arcadiumclient.runtime.client.ClientPreferences
import io.github.arcadiumclient.runtime.client.features.FirstRunSetup
import io.github.arcadiumclient.runtime.client.features.ToggleSprintConfig

@Composable
internal fun OnboardingFlow(page: Int, onPage: (Int) -> Unit, onComplete: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    fun finish() {
        try {
            ConfigManager.active().saveAll()
            ClientPreferences.setupComplete = true
            ClientPreferences.save()
            onComplete()
        } catch (_: Exception) {
            ClientPreferences.setupComplete = false
            error = "Could not save setup. Try again."
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 520.dp
        Column(Modifier.width(minOf(820.dp, maxWidth * .86f)).heightIn(max = 600.dp).fillMaxHeight()
            .clip(uiShape(10.dp)).background(UiWindowBackground).border(1.dp, UiBorderColor, uiShape(10.dp))
            .padding(if (compact) 18.dp else 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(if (compact) 34.dp else 52.dp), contentAlignment = Alignment.Center) {
                if (page != 3) UiText(when (page) { 0 -> "Select a theme"; 1 -> "Toggle Sprint"; else -> "Block Overlay" },
                    24.sp, fontWeight = FontWeight.Medium)
            }
            AnimatedContent(page, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) }, label = "Setup page") { current ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (current) {
                        0 -> SetupThemes(compact)
                        1 -> SetupSprint()
                        2 -> SetupBlockOverlay(compact)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(MENU_ICONS + "check-circle.svg", UiTextPrimary, Modifier.size(66.dp))
                            UiText("All Done!", 25.sp, fontWeight = FontWeight.Medium)
                            UiText("You can change these settings in Arcadium Options.", 12.sp, color = UiTextPrimary.copy(alpha = .75f))
                        }
                    }
                }
            }
            error?.let { UiText(it, 12.sp, color = UiDangerColor) }
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                UiButton(if (page == 0) "Skip" else "Back", Modifier.width(96.dp).fillMaxHeight(),
                    icon = MENU_ICONS + (if (page == 0) "close.svg" else "left-arrow.svg")) {
                    if (page == 0) finish() else onPage(page - 1)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(Modifier.semantics {
                        contentDescription = "Setup progress"
                        progressBarRangeInfo = ProgressBarRangeInfo(page + 1f, 1f..4f, 2)
                    }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { step -> Box(Modifier.size(6.dp).clip(uiShape(3.dp))
                            .background(if (step == page) Accent else UiTextSecondary.copy(alpha = .45f))) }
                    }
                }
                UiButton(if (page == 3) "Finish" else "Next", Modifier.width(112.dp).fillMaxHeight(), filled = true) {
                    if (page == 3) finish() else onPage(page + 1)
                }
            }
        }
    }
}

@Composable
private fun SetupThemes(compact: Boolean) {
    val active = LocalTheme.current
    val dark = active.name == PolyGlassDark.name || active.name == MinecraftDark.name
    val glass = active.name == PolyGlassDark.name || active.name == PolyGlassLight.name
    fun select(isDark: Boolean = dark, isGlass: Boolean = glass) {
        ThemeRegistry.activate(if (isGlass) { if (isDark) PolyGlassDark else PolyGlassLight }
            else { if (isDark) MinecraftDark else MinecraftLight })
    }
    Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        UiText("UI Colors", 12.sp, color = UiTextPrimary.copy(alpha = .75f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupChoice("Dark", dark, Modifier.weight(1f), "moon.svg") { select(isDark = true) }
            SetupChoice("Light", !dark, Modifier.weight(1f), "sun.svg") { select(isDark = false) }
        }
        Spacer(Modifier.height(4.dp))
        UiText("UI Style", 12.sp, color = UiTextPrimary.copy(alpha = .75f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((name, isGlass) in listOf("Glass" to true, "Minecraft" to false)) {
                val preview = if (isGlass) { if (dark) PolyGlassDark else PolyGlassLight }
                    else { if (dark) MinecraftDark else MinecraftLight }
                val border by animateColorAsState(if (glass == isGlass) Accent else UiBorderColor, tween(150))
                Column(Modifier.weight(1f).clip(UiFieldShape).background(UiControlBackground.copy(alpha = .65f))
                    .border(if (glass == isGlass) 2.dp else 1.dp, border, UiFieldShape)
                    .semantics { selected = glass == isGlass }.clickable(role = Role.RadioButton) { select(isGlass = isGlass) }
                    .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource("/assets/oneconfig/images/${preview.previewImage}.svg"), null,
                        Modifier.fillMaxWidth().height(if (compact) 98.dp else 140.dp))
                    UiText(name, 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupSprint() {
    var enabled by remember { mutableStateOf(ToggleSprintConfig.toggleSprint) }
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        UiText("Keep sprinting after you release the sprint key.", 13.sp, color = UiTextPrimary.copy(alpha = .75f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupChoice("Enabled", enabled, Modifier.weight(1f), "lightning-01.svg") {
                if (FirstRunSetup.toggleSprint(true)) { ToggleSprintConfig.isEnabled = true; enabled = true }
            }
            SetupChoice("Disabled", !enabled, Modifier.weight(1f)) {
                if (FirstRunSetup.toggleSprint(false)) enabled = false
            }
        }
    }
}

@Composable
private fun SetupChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, icon: String? = null, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Accent else UiBorderColor, tween(140))
    Row(modifier.height(36.dp).clip(UiFieldShape)
        .background(if (selected) Accent.copy(alpha = .16f) else UiControlBackground.copy(alpha = .6f))
        .border(1.dp, border, UiFieldShape).semantics { this.selected = selected }
        .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(MENU_ICONS + icon, UiTextPrimary, Modifier.size(15.dp))
        UiText(label, 13.sp)
    }
}

@Composable
private fun SetupBlockOverlay(compact: Boolean) {
    val revision = ConfigRegistry.revision
    val tree = ConfigRegistry.findTree("custom-block-highlight.json")
    if (tree == null) {
        UiText("Block Overlay is unavailable.", color = UiTextSecondary)
        return
    }
    val settings = key(revision) { rememberBlockSettings(tree) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically) {
        BlockPreview(settings, Modifier.weight(1f).fillMaxHeight())
        Column(Modifier.weight(1.15f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
            BlockToggle(settings, "Enabled", "globalModToggle", compact)
            BlockPalette(settings, "Outline", "lineCol", compact)
            BlockSlider(settings, "Opacity", "lineAlpha", 0f, 255f, 1f, compact) { "${(it / 2.55f).roundToInt()}%" }
            BlockSlider(settings, "Width", "lineWidth", .5f, 10f, .5f, compact) { "${(it * 10).roundToInt() / 10f}" }
            BlockToggle(settings, "Rainbow", "lineRainbow", compact)
            BlockSettingRow("See-through", compact) {
                val value = (settings.value("lineDepthTest") as? Enum<*>)
                BlockOnOff("See-through", value?.name == "ALWAYS_PASS") { on ->
                    value?.javaClass?.enumConstants?.firstOrNull { (it as Enum<*>).name == if (on) "ALWAYS_PASS" else "NORMAL" }
                        ?.let { settings.set("lineDepthTest", it) }
                }
            }
            BlockToggle(settings, "Fill", "fillEnabled", compact)
            BlockPalette(settings, "Fill colour", "fillCol", compact)
            BlockSlider(settings, "Fill opacity", "fillAlpha", 0f, 255f, 1f, compact) { "${(it / 2.55f).roundToInt()}%" }
            BlockToggle(settings, "Fill rainbow", "fillRainbow", compact)
            BlockToggle(settings, "Animations", "doEasing", compact)
        }
    }
}

private class SetupBlockSettings(val props: Map<String, Property<*>>) {
    var revision by mutableIntStateOf(0)
    fun value(id: String): Any? { revision; return props[id]?.get() }
    fun enabled(id: String) = value(id) == true
    fun number(id: String, fallback: Float) = (value(id) as? Number)?.toFloat() ?: fallback
    fun color(id: String): Color = (value(id) as? java.awt.Color)?.let { Color(it.rgb) } ?: Color.Black
    @Suppress("UNCHECKED_CAST")
    fun set(id: String, value: Any) { (props[id] as? Property<Any>)?.set(value) }
}

@Composable
private fun rememberBlockSettings(tree: Tree): SetupBlockSettings {
    val settings = remember(tree) {
        fun collect(node: Tree): List<Property<*>> = node.map.values.flatMap {
            when (it) { is Property<*> -> listOf(it); is Tree -> collect(it); else -> emptyList() }
        }
        SetupBlockSettings(collect(tree).associateBy { it.getID() })
    }
    DisposableEffect(settings) {
        val callback = Predicate<Any?> { settings.revision++; false }
        @Suppress("UNCHECKED_CAST")
        val observed = settings.props.values.map { it as Property<Any?> }
        observed.forEach { it.addCallback(callback) }
        onDispose { observed.forEach { it.removeCallback(callback) } }
    }
    return settings
}

@Composable
private fun BlockSettingRow(label: String, compact: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(if (compact) 23.dp else 28.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UiText(label, 12.sp, Modifier.weight(.8f), maxLines = 1)
        Row(Modifier.weight(1.7f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun BlockToggle(settings: SetupBlockSettings, label: String, id: String, compact: Boolean) {
    BlockSettingRow(label, compact) { BlockOnOff(label, settings.enabled(id)) { settings.set(id, it) } }
}

@Composable
private fun RowScope.BlockOnOff(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    for (state in listOf(true, false)) {
        val selected = state == enabled
        Box(Modifier.weight(1f).fillMaxHeight().clip(UiFieldShape)
            .background(if (selected) Accent.copy(alpha = .2f) else UiControlBackground.copy(alpha = .5f))
            .border(1.dp, if (selected) Accent else UiBorderColor, UiFieldShape)
            .semantics { contentDescription = "$label: ${if (state) "On" else "Off"}"; this.selected = selected }
            .clickable(role = Role.RadioButton) { onChange(state) }, contentAlignment = Alignment.Center) {
            UiText(if (state) "On" else "Off", 12.sp)
        }
    }
}

@Composable
private fun BlockPalette(settings: SetupBlockSettings, label: String, id: String, compact: Boolean) {
    val colors = listOf("Black" to 0x000000, "Grey" to 0x565B65, "White" to 0xFFFFFF, "Red" to 0xEF7474,
        "Orange" to 0xFF9C49, "Yellow" to 0xF8D36A, "Lime" to 0xB5EA70, "Green" to 0x54D18A,
        "Cyan" to 0x58D5ED, "Blue" to 0x5B8DEF, "Purple" to 0xAC82EB)
    BlockSettingRow(label, compact) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            for ((name, rgb) in colors) {
                val color = Color(0xFF000000.toInt() or rgb)
                val selected = settings.color(id) == color
                Box(Modifier.size(if (compact) 15.dp else 17.dp).clip(uiShape(3.dp))
                    .flatBackground(color).border(if (selected) 2.dp else 1.dp, if (selected) Accent else UiBorderColor, uiShape(3.dp))
                    .semantics { contentDescription = "$label: $name"; this.selected = selected }
                    .clickable(role = Role.RadioButton) {
                        settings.set(id, java.awt.Color(rgb))
                        settings.set(id + "2", java.awt.Color(rgb))
                    })
            }
        }
    }
}

@Composable
private fun BlockSlider(settings: SetupBlockSettings, label: String, id: String, min: Float, max: Float, step: Float,
    compact: Boolean, display: (Float) -> String) {
    val value = settings.number(id, min)
    fun update(value: Float) { settings.set(id, if (id.endsWith("Alpha")) value.roundToInt() else value) }
    BlockSettingRow(label, compact) {
        SliderControl(value, ::update, min, max, step, Modifier.weight(1f).semantics {
            contentDescription = "Block Overlay $label"
            progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, ((max-min)/step).toInt()-1)
            setProgress { update(it.coerceIn(min,max)); true }
        }, thumbSize = 12.dp, trackHeight = 3.dp)
        UiText(display(value), 11.sp, Modifier.width(42.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun BlockPreview(settings: SetupBlockSettings, modifier: Modifier) {
    val bitmap = remember { loadSetupBlock() }
    val animated = listOf("lineRainbow", "slineRainbow", "tlineRainbow", "fillRainbow").any(settings::enabled)
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (animated) while (true) withFrameNanos { seconds = (it % 3600000000000L) / 1e9f }
    }
    fun brush(first: String, second: String, alpha: String, rainbow: String, speed: String): Brush {
        val opacity = (settings.number(alpha,255f)/255f).coerceIn(0f,1f)
        val color = if (settings.enabled(rainbow)) Color(java.awt.Color.HSBtoRGB((seconds*settings.number(speed,5f)*.025f)%1f,1f,1f))
            else settings.color(first)
        return Brush.linearGradient(listOf(color.copy(alpha = opacity),
            (if (settings.enabled(rainbow)) color else settings.color(second)).copy(alpha = opacity)))
    }
    val enabled = settings.enabled("globalModToggle")
    val fill = brush("fillCol", "fillCol2", "fillAlpha", "fillRainbow", "fillRainbowSpeed")
    val outlines = listOf("t", "s", "").filter { settings.enabled(it + "outlineEnabled") }.map { prefix ->
        brush(prefix+"lineCol", prefix+"lineCol2", prefix+"lineAlpha", prefix+"lineRainbow", prefix+"lineRainbowSpeed") to
            settings.number(prefix+"lineWidth",2.5f).coerceIn(.5f,12f)
    }
    Box(modifier.clip(UiFieldShape).background(UiControlBackground.copy(alpha = .65f))
        .border(1.dp, UiBorderColor, UiFieldShape).semantics {
            contentDescription = "Block Overlay preview"
            stateDescription = if (bitmap != null) "Texture loaded" else "Texture unavailable"
        },
        contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 192f; val y = size.height / 192f
                val silhouette = Path().apply {
                    moveTo(96*x,24*y); lineTo(160*x,56*y); lineTo(160*x,136*y)
                    lineTo(96*x,168*y); lineTo(32*x,136*y); lineTo(32*x,56*y); close()
                }
                val path = Path().apply {
                    addPath(silhouette)
                    moveTo(32*x,56*y); lineTo(96*x,88*y); lineTo(160*x,56*y)
                    moveTo(96*x,88*y); lineTo(96*x,168*y)
                }
                if (enabled) {
                    if (settings.enabled("fillEnabled")) drawPath(silhouette, fill)
                    outlines.forEach { (color,width) -> drawPath(path, color, style = Stroke(width)) }
                }
                if (settings.enabled("vanillaOutline")) drawPath(path, Color.Black.copy(alpha=.4f), style = Stroke(2f))
            }
        }
    }
}

private fun loadSetupBlock() = runCatching {
    fun texture(name: String): BufferedImage {
        val resource = Minecraft.getInstance().resourceManager.getResource(Identifier("minecraft", "textures/blocks/$name.png"))
        return resource.asStream().use { ImageIO.read(it) }
    }
    val top = texture("grass_top")
    val side = texture("grass_side")
    val image = BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB)
    fun face(texture: BufferedImage, ox: Float, oy: Float, ax: Float, ay: Float, bx: Float, by: Float, shade: Float, tint: Boolean) {
        val determinant = ax*by - ay*bx
        for (y in 0 until 192) for (x in 0 until 192) {
            val dx = x+.5f-ox; val dy = y+.5f-oy
            val u = (dx*by-dy*bx)/determinant; val v = (ax*dy-ay*dx)/determinant
            if (u < 0 || u >= 1 || v < 0 || v >= 1) continue
            val pixel = texture.getRGB((u*texture.width).toInt(), (v*minOf(texture.width,texture.height)).toInt())
            fun channel(shift: Int, multiplier: Float) = (((pixel ushr shift) and 255)*shade*multiplier).toInt().coerceIn(0,255)
            image.setRGB(x,y,(0xFF shl 24) or (channel(16,if(tint).49f else 1f) shl 16)
                or (channel(8,if(tint).67f else 1f) shl 8) or channel(0,if(tint).28f else 1f))
        }
    }
    face(side,32f,56f,64f,32f,0f,80f,.85f,false)
    face(side,96f,88f,64f,-32f,0f,80f,.68f,false)
    face(top,96f,24f,64f,32f,-64f,32f,1f,true)
    val bytes = ByteArrayOutputStream()
    ImageIO.write(image,"png",bytes)
    menuImage(bytes.toByteArray())
}.getOrNull()
