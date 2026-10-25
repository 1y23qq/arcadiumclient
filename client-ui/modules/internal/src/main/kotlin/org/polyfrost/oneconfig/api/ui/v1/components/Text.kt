// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.polyfrost.oneconfig.internal.ui.components.Text as ArcadiumText

@Composable
@JvmName("Text-5ts07b0")
fun Text(text: Any, modifier: Modifier, color: Color, fontSize: TextUnit) =
    ArcadiumText(text, modifier, color, fontSize)

@Composable
@JvmName("Text-mhOCef0")
fun Text(text: Any, modifier: Modifier, color: Color, fontSize: TextUnit, fontWeight: FontWeight) =
    ArcadiumText(text, modifier, color, fontSize, fontWeight = fontWeight)

@Composable
@JvmName("Text-Vukvz_4")
fun Text(text: Any, modifier: Modifier, color: Color, fontSize: TextUnit, lineHeight: TextUnit?,
         shift: BaselineShift, fontWeight: FontWeight, textAlign: TextAlign, maxLines: Int, overflow: TextOverflow) =
    ArcadiumText(text, modifier, color, fontSize, lineHeight, shift, fontWeight, textAlign, maxLines, overflow)
