// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.polyfrost.oneconfig.internal.ui.components.onClick as arcadiumOnClick
import org.polyfrost.oneconfig.internal.ui.components.rememberInteractionSource as arcadiumInteractionSource

@Composable
fun rememberInteractionSource(): MutableInteractionSource = arcadiumInteractionSource()

fun Modifier.onClick(interactionSource: MutableInteractionSource, onClick: () -> Unit): Modifier =
    arcadiumOnClick(interactionSource, onClick)
