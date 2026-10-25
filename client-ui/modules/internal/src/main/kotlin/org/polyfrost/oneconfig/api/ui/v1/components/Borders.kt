// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.polyfrost.oneconfig.internal.ui.components.fadingEdges as arcadiumFadingEdges

@Composable
@JvmName("fadingEdges-XO-JAsU")
fun Modifier.fadingEdges(scrollState: ScrollState, color: Color): Modifier = arcadiumFadingEdges(scrollState, color)
