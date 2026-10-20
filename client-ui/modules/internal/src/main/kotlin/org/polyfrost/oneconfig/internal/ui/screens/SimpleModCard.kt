// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.function.Consumer
import java.util.function.Predicate
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.internal.ui.api.ConfigData
import org.polyfrost.oneconfig.internal.ui.api.ModCardToggle
import org.polyfrost.oneconfig.internal.ui.api.modCardToggle
import org.polyfrost.oneconfig.internal.ui.components.GridReorderState
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.asRenderText
import org.polyfrost.oneconfig.internal.ui.components.blockInteraction
import org.polyfrost.oneconfig.internal.ui.components.canRenderIcon
import org.polyfrost.oneconfig.internal.ui.components.excludeFromReorder
import org.polyfrost.oneconfig.internal.ui.components.onClick
import org.polyfrost.oneconfig.internal.ui.components.rememberInteractionSource
import org.polyfrost.oneconfig.internal.ui.sound.UiSoundEvent
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBackground as background
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBorder as border
import org.polyfrost.oneconfig.internal.ui.themes.accentBackground

@Composable
internal fun SimpleModCard(
    mod: ConfigData,
    modifier: Modifier,
    favorite: Boolean,
    hoverHint: Boolean,
    interactive: Boolean,
    reorderState: GridReorderState?,
) {
    val theme = LocalTheme.current
    val interaction = rememberInteractionSource()
    val hovered by interaction.collectIsHoveredAsState()
    val toggle = remember(mod) { modCardToggle(mod) }
    val title = remember(mod.title) { mod.title.asRenderText() }
    val icon = remember(mod.icon) { mod.icon?.takeIf(::canRenderIcon) ?: "settings" }
    val edge by animateColorAsState(
        if (hovered) theme.textColor.copy(alpha = .25f) else theme.borderColor,
    )

    Box(
        modifier.fillMaxWidth().height(112.dp)
            .background(theme.modCardBackground, theme.modCardShape)
            .border(1.dp, edge, theme.modCardShape)
            .clip(theme.modCardShape)
            .then(if (interactive) Modifier
                .focusProperties { canFocus = false }
                .onClick(interaction) { openModCard(mod) }
                .pointerHoverIcon(PointerIcon.Hand)
                .semantics { contentDescription = "Open $title settings" }
            else Modifier),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(end = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(icon, color = theme.textColorSecondary, modifier = Modifier.size(26.dp))
                Text(
                    title, color = theme.textColor, fontSize = 15.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Settings", color = theme.textColorSecondary, fontSize = 12.sp)
                if (toggle != null) {
                    SimpleModuleSwitch(title, toggle, interactive, Modifier.excludeFromReorder(reorderState))
                } else {
                    Icon("up", color = theme.textColorSecondary, modifier = Modifier.size(12.dp).rotate(90f))
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(3.dp).excludeFromReorder(reorderState)) {
            if (interactive) HideToggle(mod, interaction)
            FavoriteStar(mod, favorite, interaction, hoverHint, interactive)
        }
    }
}

@Composable
private fun SimpleModuleSwitch(title: String, toggle: ModCardToggle, interactive: Boolean, modifier: Modifier) {
    val theme = LocalTheme.current
    val prop = toggle.property
    var propertyChecked by remember(toggle) { mutableStateOf(toggle.enabled) }
    var available by remember(toggle) { mutableStateOf(toggle.available) }
    DisposableEffect(toggle, prop) {
        val valueListener = Predicate<Boolean> { value ->
            Snapshot.withMutableSnapshot { propertyChecked = value == true }
            false
        }
        val displayListener = Consumer<Property.Display> { display ->
            Snapshot.withMutableSnapshot { available = display == Property.Display.SHOWN }
        }
        prop?.addCallback(valueListener)
        prop?.addDisplayListener(displayListener)
        onDispose {
            prop?.removeCallback(valueListener)
            prop?.removeDisplayListener(displayListener)
        }
    }
    val checked = if (prop == null) toggle.enabled else propertyChecked
    val usable = interactive && available
    val interaction = rememberInteractionSource()
    val thumb by animateDpAsState(if (checked) 24.dp else 3.dp, animationSpec = spring())
    Row(
        modifier.height(28.dp).alpha(if (available) 1f else .45f).blockInteraction(!usable)
            .focusProperties { canFocus = false }
            .toggleable(
                value = checked, enabled = usable, role = Role.Switch,
                interactionSource = interaction, indication = null,
            ) {
                UiSounds.play(UiSoundEvent.CLICK)
                toggle.setEnabled(it)
                propertyChecked = toggle.enabled
            }
            .semantics { contentDescription = "Toggle $title" }
            .pointerHoverIcon(if (usable) PointerIcon.Hand else PointerIcon.Default),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (checked) "On" else "Off", color = theme.textColorSecondary, fontSize = 12.sp)
        Box(
            Modifier.size(42.dp, 21.dp).clip(theme.circleShape)
                .background(theme.controlTrackColor)
                .accentBackground(theme.circleShape, enabled = checked),
        ) {
            Box(
                Modifier.align(Alignment.CenterStart).offset(x = thumb).size(15.dp)
                    .background(theme.controlThumbColor, theme.circleShape),
            )
        }
    }
}
