// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.utils

import net.minecraft.client.Minecraft

object GameThread {
    fun localPlayerName(): String = Minecraft.getInstance().session.username
}
