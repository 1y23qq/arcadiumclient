// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBackground as background
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBorder as border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.accentBackground
import org.polyfrost.oneconfig.internal.ui.themes.accentBrush
import org.polyfrost.oneconfig.internal.ui.components.Icon

@OptIn(ExperimentalComposeUiApi::class)
class ArcadiumVideoSettingsScreen private constructor(private val model: VideoSettingsModel) : ArcadiumScreen() {
    override val shadesPanorama: Boolean get() = false
    private var page by mutableStateOf(VideoPage.MAIN)
    private var query by mutableStateOf("")
    private var description by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)
    private var returningFromRenderer = false

    override fun init() {
        if (returningFromRenderer) { model.refreshFromNative(); returningFromRenderer = false }
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

    override fun onClose() = close()

    private fun close() {
        if (page != VideoPage.MAIN) {
            navigate(VideoPage.MAIN)
            return
        }
        client.execute {
            try {
                model.apply()
                if (client.gui.screen() === this) client.gui.setScreen(model.parent)
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
                maxWidth = 920.dp, maxHeight = if (page == VideoPage.MAIN) 600.dp else 780.dp, dimBackground = false) {
                UiTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    if (page == VideoPage.MAIN) "Search all video settings..." else "Search settings...")
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val compact = maxHeight < 360.dp
                    val dense = maxHeight < 432.dp
                    val rowHeight = if (compact) 32.dp else if (dense) 42.dp else 48.dp
                    val navigationHeight = if (compact) 32.dp else if (dense) 36.dp else 40.dp
                    val gap = if (compact) 2.dp else if (dense) 6.dp else 8.dp
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
                            if (current == VideoPage.MAIN && query.isBlank()) {
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
                                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                                VideoSettingsLayout.navigation.forEach { (left, right) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        for (target in listOf(left, right)) {
                                            UiButton(
                                                target.title + "...", modifier = Modifier.weight(1f).height(navigationHeight),
                                                enabled = model.options.any { it.page == target },
                                            ) { navigate(target) }
                                        }
                                    }
                                }
                            } else {
                                val visible = model.options.filter {
                                    (current == VideoPage.MAIN || it.page == current) &&
                                        (query.isBlank() || it.label.contains(query, true) || it.description.contains(query, true) || it.key.contains(query, true))
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
                Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
                    if (description.isNotBlank()) UiText(description, 11.sp, color = UiTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else if (model.restartPending) UiText("Restart Minecraft after applying these changes.", 11.sp, color = UiWarnColor)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UiButton("Renderer settings", modifier = Modifier.weight(1f).height(40.dp)) {
                        runCatching {
                            model.apply()
                            returningFromRenderer = true
                            ModernScreens.openNativeVideo(this@ArcadiumVideoSettingsScreen)
                        }.onFailure { error = "Could not open renderer settings."; LOGGER.error(error, it) }
                    }
                    if (model.changed) UiButton("Undo changes", modifier = Modifier.weight(1f).height(40.dp)) { model.undo(); error = null }
                    UiButton("Done", filled = true, modifier = Modifier.weight(2f).height(40.dp)) { close() }
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
                UiText(option.label, 13.sp, Modifier.weight(1f), UiTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiText(option.displayValue, 13.sp, color = UiTextSecondary, maxLines = 1)
            }
            return
        }
        when (option.control) {
            VideoControl.TOGGLE -> VideoToggle(option, container)
            VideoControl.CHOICE -> VideoChoiceControl(option, container)
            VideoControl.SLIDER -> VideoSlider(option, container, height <= 34.dp) { option.change(it) }
            VideoControl.ACTION -> UiButton(option.label, container) {
                runCatching {
                    model.apply()
                    returningFromRenderer = true
                    option.action?.invoke()
                }.onFailure { error = "Could not open these settings."; LOGGER.error(error, it) }
            }
        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger("ArcadiumVideoSettings")
        @JvmStatic fun create(parent: Screen?): ArcadiumVideoSettingsScreen {
            val vanilla = ModernVideoSettingsModel(parent)
            val model = if (FabricLoader.getInstance().isModLoaded("sodium")) runCatching {
                SodiumVideoSettingsModel(vanilla)
            }.getOrElse { LOGGER.error("Could not read renderer options", it); vanilla } else vanilla
            return ArcadiumVideoSettingsScreen(model)
        }
    }
}

@Composable
private fun VideoChoiceControl(option: VideoSetting, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }
    val (interaction, hovered) = rememberUiHover()
    val border by animateColorAsState(if (hovered || expanded) Accent else UiBorderColor, tween(130))
    val density = LocalDensity.current
    Box(modifier.onSizeChanged { width = it.width; height = it.height }) {
        Row(Modifier.fillMaxSize().clip(UiFieldShape).background(UiControlBackground)
            .border(1.dp, border, UiFieldShape).hoverable(interaction)
            .semantics { contentDescription = option.label; stateDescription = option.displayValue }
            .clickable(interactionSource = interaction, indication = null, role = Role.DropdownList) { expanded = !expanded }
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UiText(option.label, 13.sp, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiText(option.displayValue, 13.sp, color = UiTextPrimary.copy(alpha = .74f), maxLines = 1)
            Icon(MENU_ICONS + (if (expanded) "up.svg" else "down.svg"), UiTextPrimary.copy(alpha = .74f), Modifier.size(13.dp))
        }
        if (expanded) Popup(alignment = Alignment.TopStart, offset = IntOffset(0, height + with(density) { 4.dp.roundToPx() }),
            onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(with(density) { width.toDp() }).heightIn(max = 280.dp)
                .clip(UiFieldShape).background(UiWindowBackground)
                .border(1.dp, UiBorderColor, UiFieldShape).verticalScroll(rememberScrollState()).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                option.choices.forEach { choice ->
                    UiButton(choice.label, Modifier.fillMaxWidth().height(34.dp), filled = choice.value == option.value) {
                        option.change(choice.value)
                        expanded = false
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoToggle(option: VideoSetting, modifier: Modifier) {
    val checked = option.value as Boolean
    val (interaction, hovered) = rememberUiHover()
    val border by animateColorAsState(if (hovered) Accent else UiBorderColor, tween(130))
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(140))
    val foreground = if (checked) LocalTheme.current.accentTextColor else UiTextSecondary
    Row(modifier.fillMaxWidth().clip(UiFieldShape).background(UiControlBackground)
        .border(1.dp, border, UiFieldShape).hoverable(interaction)
        .toggleable(checked, interactionSource = interaction, indication = null, role = Role.Switch) { option.change(it) }
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UiText(option.label, 13.sp, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.size(34.dp, 20.dp).clip(uiShape(10.dp))
            .then(if (checked) Modifier.accentBackground(uiShape(10.dp)) else Modifier.background(UiBorderColor))) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = 7.dp.toPx()
                drawCircle(foreground, radius, Offset(10.dp.toPx() + 14.dp.toPx() * position, size.height / 2))
            }
        }
    }
}

@Composable
private fun VideoSlider(option: VideoSetting, modifier: Modifier, compact: Boolean, onChange: (Int) -> Unit) {
    val (interaction, hovered) = rememberUiHover()
    val focused by interaction.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val border by animateColorAsState(if (focused) Accent else if (hovered) UiHoverBorder else UiBorderColor)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentValue = (option.value as Number).toInt()
    val fraction = ((currentValue - option.minimum).toFloat() / (option.maximum - option.minimum).coerceAtLeast(1)).coerceIn(0f, 1f)
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
            .pointerInput(option.key, compact) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    focus.requestFocus()
                    fun update(x: Float) {
                        val inset = (if (compact) 15.dp else 16.dp).toPx()
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
            }.padding(horizontal = 12.dp, vertical = if (compact) 2.dp else 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            UiText(option.label, 13.sp, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiText(option.displayValue, 13.sp, color = UiTextPrimary.copy(alpha = .74f), maxLines = 1)
        }
        Canvas(Modifier.fillMaxWidth().height(if (compact) 8.dp else 10.dp)) {
            val y = size.height / 2
            val radius = (if (compact) 3.dp else 4.dp).toPx()
            val left = radius
            val right = (size.width - radius).coerceAtLeast(left)
            val x = left + (right - left) * fraction
            drawLine(track, Offset(left, y), Offset(right, y), strokeWidth = 3.dp.toPx())
            drawLine(accentBrush(), Offset(left, y), Offset(x, y), strokeWidth = 3.dp.toPx())
            drawCircle(thumb, radius, Offset(x, y))
        }
    }
}
