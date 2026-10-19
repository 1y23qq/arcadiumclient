package org.polyfrost.oneconfig.internal.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.onClick
import org.polyfrost.oneconfig.internal.ui.components.rememberInteractionSource
import org.polyfrost.oneconfig.internal.ui.components.dropdown.DropdownPositionProvider
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.GradientPresets
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

@Composable
fun GradientPresetOption(prop: Property<*>) {
    val theme = LocalTheme.current
    var selected by remember(prop) { mutableIntStateOf(prop.get() as? Int ?: 0) }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val preset = GradientPresets.get(selected)
    val trigger = rememberInteractionSource()
    Box {
        Row(
            Modifier.width(LocalOptionWidth.current).height(34.dp)
                .background(theme.componentBackground, theme.buttonShape)
                .border(1.dp, if (expanded) Accent else theme.borderColor, theme.buttonShape)
                .onClick(trigger) { expanded = !expanded }
                .pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PresetSwatch(preset, Modifier.size(40.dp, 18.dp))
            Text(preset.name(), color = theme.textColor, modifier = Modifier.weight(1f),
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (expanded) "up" else "down", color = theme.textColorSecondary)
        }
        if (expanded) Popup(
            popupPositionProvider = DropdownPositionProvider(DpOffset(0.dp, 8.dp), LocalDensity.current),
            onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true),
        ) {
            Column(
                Modifier.width(440.dp).heightIn(max = 380.dp).clip(theme.popupShape)
                    .background(theme.popupBackground).border(1.dp, theme.borderColor, theme.popupShape).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = theme.typography.family, color = theme.textColor, fontSize = 13.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.fillMaxWidth().trackTextInputFocus()
                        .background(theme.componentBackground, theme.buttonShape).padding(10.dp),
                    decorationBox = { field ->
                        Box {
                            if (query.isEmpty()) Text("Search presets", color = theme.textColorSecondary, fontSize = 13.sp)
                            field()
                        }
                    },
                )
                val choices = remember(query) { GradientPresets.all().filter { it.name().contains(query.trim(), ignoreCase = true) } }
                if (choices.isEmpty()) Text("No matching presets", color = theme.textColorSecondary, fontSize = 13.sp)
                LazyVerticalGrid(
                    GridCells.Fixed(2), modifier = Modifier.heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(choices, key = { it.name() }) { choice ->
                        val interaction = rememberInteractionSource()
                        Row(
                            Modifier.fillMaxWidth().height(42.dp)
                                .background(theme.componentBackground, theme.buttonShape)
                                .border(1.dp, if (choice == preset) Accent else theme.borderColor, theme.buttonShape)
                                .onClick(interaction) {
                                    selected = GradientPresets.all().indexOf(choice)
                                    prop.setAs(selected)
                                    expanded = false
                                }.pointerHoverIcon(PointerIcon.Hand).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PresetSwatch(choice, Modifier.size(36.dp, 23.dp))
                            Text(choice.name(), color = theme.textColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSwatch(preset: GradientPresets.Preset, modifier: Modifier) {
    Box(modifier.clip(LocalTheme.current.buttonShape)
        .background(Brush.horizontalGradient(listOf(Color(preset.start()), Color(preset.end())))))
}
