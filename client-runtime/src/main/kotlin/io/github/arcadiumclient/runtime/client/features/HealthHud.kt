// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.hud.v1.HudManager

class HealthHud : ColoredTextHud("arcadium-health-hud.json", "Health", Category.INFO) {
    @Dropdown(title = "Health Units", options = ["Hearts (10)", "HP (20)"])
    var units = 0

    override fun showByDefault() = false
    override fun defaultPosition() = 10f to 135f
    override fun updateFrequency() = 50_000_000L
    override fun shouldShow() = HudManager.isEditing || Minecraft.getInstance().player != null
    override fun valueRuns(): List<Run> {
        val health = Minecraft.getInstance().player?.health ?: 17f
        return listOf(Run("❤", 0xFFFF5555.toInt(), minecraftFont = true), Run(HealthText.format(health, units == 0)))
    }
}
