// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import org.polyfrost.oneconfig.internal.ui.components.settings.LocalOptionWidth

@get:JvmName("getCurrentOptionWidth")
val currentOptionWidth: Dp
    @Composable get() = LocalOptionWidth.current
