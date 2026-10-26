// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper

object ToggleSprintConfig : Config("polysprint.json", "Toggle Sprint", Category.COMBAT) {
    @JvmStatic @Switch(title = "Enabled") var isEnabled = true
    @JvmStatic @Switch(title = "Enable Toggle Sprint", subcategory = "Sprint") var toggleSprint = true
    @JvmStatic @Switch(title = "Toggle Sneak", description = "Press the sneak key once to keep sneaking, then press it again to release.", subcategory = "Sneak") var toggleSneak = false
    @JvmStatic @Switch(title = "Disable W-Tap Sprint") var disableWTapSprint = false
    @JvmField @Include var toggleSprintState = false
    @JvmField @Include var toggleSneakState = false

    @JvmStatic @Switch(title = "Separate Keybind for Toggle Sprint", subcategory = "Sprint")
    var keybindToggleSprint = false

    @Keybind(title = "Toggle Sprint Keybind", subcategory = "Sprint")
    var keybindToggleSprintKey = KeybindHelper.builder().action { down: Boolean ->
        ToggleSprintController.separateSprint(down)
        true
    }.build()

    @JvmStatic @Switch(title = "Separate Keybind for Toggle Sneak", subcategory = "Sneak")
    var keybindToggleSneak = false

    @Keybind(title = "Toggle Sneak Keybind", subcategory = "Sneak")
    var keybindToggleSneakKey = KeybindHelper.builder().action { down: Boolean ->
        ToggleSprintController.separateSneak(down)
        true
    }.build()

    @JvmStatic @Switch(title = "Show Sprint Text while Flying", subcategory = "Flying")
    var showSprintTextWhileFlying = true

    @JvmStatic @Switch(title = "Unsneak on Flight Start", subcategory = "Flying")
    var unsneakOnFlightStart = true

    @JvmStatic @Switch(title = "Fly Boost", description = "Applies only in singleplayer while flying with sprint toggled.", subcategory = "Flying")
    var toggleFlyBoost = false

    @JvmStatic @Slider(title = "Fly Boost Amount", min = 1f, max = 10f, step = 1f, subcategory = "Flying")
    var flyBoostAmount = 4f

    @JvmStatic fun initialize() { preload() }
}

class ToggleSprintHud : TextHud("arcadium-toggle-sprint-hud.json", "Toggle Sprint HUD", Category.COMBAT, "") {
    override fun showByDefault() = true
    override fun defaultPosition() = 10f to 110f
    override fun updateFrequency() = 50_000_000L
    override fun shouldShow(): Boolean {
        if (HudManager.isEditing) return true
        val player = Minecraft.getInstance().player ?: return false
        if (!ToggleSprintConfig.isEnabled) return false
        if (player.abilities.flying && !ToggleSprintConfig.showSprintTextWhileFlying) return false
        return player.isSneaking || player.isSprinting || ToggleSprintConfig.toggleSprint && ToggleSprintConfig.toggleSprintState ||
            ToggleSprintConfig.toggleSneak && ToggleSprintConfig.toggleSneakState
    }

    override fun getText(): String {
        val player = Minecraft.getInstance().player
        if (ToggleSprintConfig.toggleSneak && ToggleSprintConfig.toggleSneakState) return "Sneaking (Toggled)"
        if (player?.isSneaking == true) return "Sneaking (Held)"
        if (ToggleSprintConfig.toggleSprint && ToggleSprintConfig.toggleSprintState || HudManager.isEditing) return "Sprinting (Toggled)"
        return "Sprinting (Held)"
    }
}
