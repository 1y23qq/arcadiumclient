// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import io.github.arcadiumclient.runtime.mixin.client.access.VideoOptionsScreenAccessor

@OptIn(ExperimentalComposeUiApi::class)
class ArcadiumVideoSettingsScreen private constructor(private val model: VideoSettingsModel) : ComposeScreen(RenderMode.CONTINUOUS) {
    private var page by mutableStateOf(VideoPage.MAIN)
    private var query by mutableStateOf("")
    private var description by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)

    override fun init() {
        model.initialize(client, width, height)
        super.init()
    }

    override fun removed() {
        model.delegate.removed()
        super.removed()
    }

    private fun navigate(next: VideoPage) {
        page = next
        query = ""
        description = ""
    }

    private fun close() {
        if (page != VideoPage.MAIN) {
            navigate(VideoPage.MAIN)
            return
        }
        client.tell {
            try {
                model.apply()
                if (client.screen === this) client.openScreen(model.parent)
            } catch (failure: Exception) {
                LOGGER.error("Could not save video settings", failure)
                error = "Could not save video settings. Check the game log for details."
            }
        }
    }

    @Composable
    override fun compose() {
        Theme {
            ArcadiumMenuWindow(if (page == VideoPage.MAIN) "Video settings" else "Video settings / ${page.title}", error, ::close,
                maxWidth = 920.dp, maxHeight = if (page == VideoPage.MAIN) 600.dp else 780.dp) {
                if (page != VideoPage.MAIN) {
                    UiTextField(query, { query = it }, Modifier.fillMaxWidth(), "Search settings...")
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val compact = maxHeight < 400.dp
                    val rowHeight = if (compact) 32.dp else 42.dp
                    val gap = if (compact) 3.dp else 8.dp
                    AnimatedContent(
                        page,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = { fadeIn(tween(130)) togetherWith fadeOut(tween(90)) },
                        label = "Video settings page",
                    ) { current ->
                      val scroll = rememberScrollState()
                      Box(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(scroll).padding(end = if (scroll.maxValue > 0) 12.dp else 2.dp),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            if (current == VideoPage.MAIN) {
                                val byKey = model.options.associateBy { it.key }
                                VideoSettingsLayout.mainRows.forEach { (left, right) ->
                                    val first = byKey[left] ?: if (left == "dynamic_lights") byKey["clouds"] else null
                                    val second = byKey[right]
                                    if (first != null || second != null) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (first != null) Setting(first, rowHeight, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                                            if (second != null) Setting(second, rowHeight, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
                                VideoSettingsLayout.navigation.forEach { (left, right) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        for (target in listOf(left, right)) {
                                            UiButton(
                                                target.title + "...", modifier = Modifier.weight(1f).height(if (compact) 28.dp else 38.dp),
                                                enabled = model.options.any { it.page == target },
                                            ) { navigate(target) }
                                        }
                                    }
                                }
                            } else {
                                val visible = model.options.filter {
                                    it.page == current && (query.isBlank() || it.label.contains(query, true) || it.description.contains(query, true))
                                }
                                if (visible.isEmpty()) UiText("No matching settings.", 13.sp, Modifier.padding(12.dp), color = UiTextSecondary)
                                visible.groupBy { it.group }.forEach { (group, options) ->
                                    val title = group.substringAfter(':').replace('_', ' ').trim()
                                    if (title.isNotEmpty() && title != "default") {
                                        UiText(title.replaceFirstChar { it.titlecase() }, 12.sp, Modifier.padding(top = 6.dp, bottom = 2.dp),
                                            color = UiTextSecondary, fontWeight = FontWeight.Medium)
                                    }
                                    options.chunked(2).forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            row.forEach { Setting(it, rowHeight, Modifier.weight(1f)) }
                                            if (row.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        if (scroll.maxValue > 0) VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                      }
                    }
                }
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
                    if (description.isNotBlank()) UiText(description, 11.sp, color = UiTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else if (model.restartPending) UiText("Restart Minecraft after applying these changes.", 11.sp, color = UiWarnColor)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (model.changed) UiButton("Undo changes", modifier = Modifier.weight(1f)) { model.undo(); error = null }
                    UiButton("Done", filled = true, modifier = Modifier.weight(2f)) { close() }
                }
            }
        }
    }

    @Composable
    private fun Setting(option: VideoSetting, height: Dp, modifier: Modifier) {
        val container = modifier.height(height)
            .onPointerEvent(PointerEventType.Enter) { description = option.description }
            .onPointerEvent(PointerEventType.Exit) { if (description == option.description) description = "" }
        if (!option.available) {
            Row(container.fillMaxWidth().clip(UiFieldShape).background(UiControlBackground)
                .border(UiBorderWidth, UiBorderColor, UiFieldShape).padding(horizontal = 12.dp)
                .semantics { disabled() }, verticalAlignment = Alignment.CenterVertically) {
                UiText(option.label, 12.sp, Modifier.weight(1f), UiTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiText(option.displayValue, 12.sp, color = UiTextSecondary, maxLines = 1)
            }
            return
        }
        when (option.control) {
            VideoControl.TOGGLE -> UiToggle(option.label, option.value as Boolean, container) { option.change(!(option.value as Boolean)) }
            VideoControl.CHOICE -> UiDropdown(
                option.label, option.choices, option.choices.first { it.value == option.value }, { it.label }, container,
            ) { option.change(it.value) }
            VideoControl.SLIDER -> VideoSlider(option, container) { option.change(it) }
        }
    }

    companion object {
        private const val NATIVE_VIDEO_SCREEN = "dev.rdh.argentum.impl.gui.VideoOptionsScreen"
        private val LOGGER = LogManager.getLogger("ArcadiumVideoSettings")

        @JvmStatic
        fun replace(screen: Screen?): Screen? {
            if (screen == null || screen is ArcadiumVideoSettingsScreen) return screen
            if (screen !is VideoOptionsScreen && screen.javaClass.name != NATIVE_VIDEO_SCREEN) return screen
            return runCatching {
                if (!FabricLoader.getInstance().isModLoaded("argentum")) {
                    return@runCatching ArcadiumVideoSettingsScreen(VanillaVideoSettingsModel(screen as VideoOptionsScreen))
                }
                val native = if (screen is VideoOptionsScreen) {
                    val parent = (screen as VideoOptionsScreenAccessor).`arcadium$parent`()
                    Class.forName(NATIVE_VIDEO_SCREEN).getConstructor(Screen::class.java).newInstance(parent) as Screen
                } else screen
                ArcadiumVideoSettingsScreen(RendererVideoSettingsModel(native))
            }.getOrElse {
                LOGGER.error("Could not open Arcadium video settings; using the native screen", it)
                screen
            }
        }
    }
}

@Composable
private fun VideoSlider(option: VideoSetting, modifier: Modifier, onChange: (Int) -> Unit) {
    val (interaction, hovered) = rememberUiHover()
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val border by animateColorAsState(if (focused) Accent else if (hovered) UiHoverBorder else UiBorderColor)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentValue = (option.value as Number).toInt()
    val fraction = ((currentValue - option.minimum).toFloat() / (option.maximum - option.minimum).coerceAtLeast(1)).coerceIn(0f, 1f)
    val accent = Accent
    val track = UiBorderColor
    val thumb = UiTextPrimary
    Column(
        modifier.fillMaxWidth().clip(UiFieldShape).background(UiControlBackground)
            .border(UiBorderWidth, border, UiFieldShape).hoverable(interaction)
            .semantics {
                contentDescription = option.label
                progressBarRangeInfo = ProgressBarRangeInfo(currentValue.toFloat(), option.minimum.toFloat()..option.maximum.toFloat(),
                    ((option.maximum - option.minimum) / option.interval - 1).coerceAtLeast(0))
                setProgress { currentOnChange(it.roundToInt()); true }
            }
            .focusRequester(focus).onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyDown) false
                else when (it.key) {
                    Key.DirectionLeft -> { currentOnChange(currentValue - option.interval); true }
                    Key.DirectionRight -> { currentOnChange(currentValue + option.interval); true }
                    else -> false
                }
            }.focusable(interactionSource = interaction)
            .pointerInput(option.key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    focus.requestFocus()
                    fun update(x: Float) {
                        val inset = 15.dp.toPx()
                        val progress = ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        currentOnChange((option.minimum + progress * (option.maximum - option.minimum)).roundToInt())
                    }
                    update(down.position.x)
                    down.consume()
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        update(change.position.x)
                        change.consume()
                    } while (change.pressed)
                }
            }.padding(horizontal = 12.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UiText(option.label, 12.sp, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiText(option.displayValue, 12.sp, color = UiTextSecondary, maxLines = 1)
        }
        Canvas(Modifier.fillMaxWidth().height(9.dp)) {
            val y = size.height / 2
            val radius = 3.dp.toPx()
            val left = radius
            val right = (size.width - radius).coerceAtLeast(left)
            val x = left + (right - left) * fraction
            drawLine(track, Offset(left, y), Offset(right, y), strokeWidth = 3.dp.toPx())
            drawLine(accent, Offset(left, y), Offset(x, y), strokeWidth = 3.dp.toPx())
            drawCircle(thumb, radius, Offset(x, y))
        }
    }
}
