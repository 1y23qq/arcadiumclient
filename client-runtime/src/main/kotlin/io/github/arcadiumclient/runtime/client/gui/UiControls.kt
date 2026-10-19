// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.accentBackground
import org.polyfrost.oneconfig.internal.ui.sound.UiSoundEvent
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds

internal const val UI_ASSETS = "assets/arcadium/"
internal val UiScrim = Color(0x99000000)
internal val UiDangerColor = Color(0xFFEF7474)
internal val UiWarnColor = Color(0xFFE3C16F)
internal val UiBorderWidth = 1.dp
internal val UiTextPrimary: Color @Composable get() = LocalTheme.current.textColor
internal val UiTextSecondary: Color @Composable get() = LocalTheme.current.textColorSecondary
internal val UiWindowBackground: Color @Composable get() = LocalTheme.current.pageBackground
internal val UiSidebarBackground: Color @Composable get() = LocalTheme.current.sidebarBackground
internal val UiCardBackground: Color @Composable get() = LocalTheme.current.modCardBackground
internal val UiControlBackground: Color @Composable get() = LocalTheme.current.componentBackground
internal val UiBorderColor: Color @Composable get() = LocalTheme.current.borderColor
internal val UiHoverBorder: Color @Composable get() = Accent.copy(alpha = 0.75f)
internal val UiFieldShape: Shape get() = RoundedCornerShape(5.dp)
internal fun uiShape(radius: Dp): Shape = RoundedCornerShape(radius)

@Composable
internal fun rememberUiHover(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    return source to hovered
}

internal fun Modifier.arcadiumClickable(action: () -> Unit): Modifier = clickable {
    UiSounds.play(UiSoundEvent.CLICK)
    action()
}

@Composable
internal fun UiText(
    text: String,
    size: TextUnit = 13.sp,
    modifier: Modifier = Modifier,
    color: Color = UiTextPrimary,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign = TextAlign.Start,
) {
    BasicText(text, modifier, TextStyle(color = color, fontSize = size,
        fontFamily = LocalTheme.current.typography.family, fontWeight = fontWeight,
        textAlign = textAlign), overflow = overflow, maxLines = maxLines)
}

@Composable
internal fun UiButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: String? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val theme = LocalTheme.current
    val shape = theme.buttonShape
    val background by animateColorAsState(if (hovered && enabled)
        theme.componentBackground.copy(alpha = 0.96f) else theme.componentBackground)
    val foreground = when { danger -> UiDangerColor; filled -> theme.accentTextColor; else -> theme.textColor }
    Row(modifier.heightIn(min = 34.dp).alpha(if (enabled) 1f else 0.4f)
        .clip(shape).then(if (filled) Modifier.accentBackground(shape) else Modifier.background(background))
        .border(1.dp, if (hovered && enabled) Accent else theme.borderColor, shape)
        .hoverable(interaction).clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, foreground, Modifier.size(15.dp))
        UiText(label, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun UiIconButton(icon: String, modifier: Modifier = Modifier, tooltip: String = "", onClick: () -> Unit) {
    val shape = LocalTheme.current.buttonShape
    Box(modifier.size(32.dp).clip(shape).background(UiControlBackground)
        .border(1.dp, UiBorderColor, shape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, UiTextPrimary, Modifier.size(17.dp))
    }
}

@Composable
internal fun UiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    icon: String? = null,
    maxLength: Int = 1024,
    onSubmit: (() -> Unit)? = null,
    password: Boolean = false,
) {
    val theme = LocalTheme.current
    BasicTextField(value, { if (it.length <= maxLength) onValueChange(it) },
        modifier.heightIn(min = 34.dp).trackTextInputFocus(), singleLine = true,
        textStyle = TextStyle(color = theme.textColor, fontFamily = theme.typography.family, fontSize = 13.sp),
        cursorBrush = SolidColor(Accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit?.invoke() }),
        decorationBox = { field ->
            Row(Modifier.clip(UiFieldShape).background(theme.componentBackground)
                .border(1.dp, theme.borderColor, UiFieldShape).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, theme.textColorSecondary, Modifier.size(15.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) UiText(placeholder, color = theme.textColorSecondary, maxLines = 1)
                    field()
                }
            }
        })
}

@Composable
internal fun UiToggle(label: String, checked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(modifier.heightIn(min = 32.dp).clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val shape = RoundedCornerShape(11.dp)
        Box(Modifier.size(34.dp, 20.dp).clip(shape)
            .then(if (checked) Modifier.accentBackground(shape) else Modifier.background(UiControlBackground))
            .border(1.dp, UiBorderColor, shape).padding(3.dp)) {
            Box(Modifier.size(14.dp).align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(if (checked) LocalTheme.current.accentTextColor else UiTextSecondary, RoundedCornerShape(7.dp)))
        }
        UiText(label, maxLines = 2)
    }
}

@Composable
internal fun <T> UiDropdown(label: String, values: List<T>, value: T, display: (T) -> String,
    modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        UiButton((if (label.isEmpty()) "" else "$label: ") + display(value), Modifier.fillMaxWidth()) { expanded = !expanded }
        if (expanded) Popup(alignment = Alignment.TopStart, onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true)) {
            Column(Modifier.widthIn(min = 140.dp, max = 320.dp).background(UiWindowBackground, UiFieldShape)
                .border(1.dp, UiBorderColor, UiFieldShape).padding(4.dp)) {
                values.forEach { option ->
                    UiButton(display(option), Modifier.fillMaxWidth(), filled = option == value) {
                        expanded = false; onSelect(option)
                    }
                }
            }
        }
    }
}

@Composable
internal fun UiModalScrim(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(UiScrim).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center, content = content)
}

@Composable
internal fun ModalPanel(width: Dp = 400.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = LocalTheme.current.popupShape
    Column(Modifier.width(width).clip(shape).background(UiWindowBackground)
        .border(1.dp, UiBorderColor, shape).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
