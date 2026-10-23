// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object LevelHeadConfig : Config("arcadium-level-head.json", "LevelHead", Category.HYPIXEL) {
    @JvmStatic @Switch(title = "Enabled") var enabled = false
    @JvmStatic @Dropdown(title = "Value", options = ["Level", "Bedwars Level"]) var level = 0
    @JvmStatic @Switch(title = "Show Self") var showSelf = true
    @JvmStatic @Switch(title = "Background") var background = true
    @JvmStatic @Color(title = "Background Color") var backgroundColor = java.awt.Color(0, 0, 0, 128)
    @JvmStatic @Color(title = "Prefix Color", alpha = false) var prefixColor = java.awt.Color(85, 255, 255)
    @JvmStatic @Color(title = "Suffix Color", alpha = false) var suffixColor = java.awt.Color(255, 255, 85)
    @JvmStatic @Switch(title = "Text Shadow") var textShadow = true
    @JvmStatic @Slider(title = "Scale", min = 0.5f, max = 2f, step = 0.05f) var scale = 1f
    @JvmStatic @Slider(title = "Height Offset", description = "Extra space above the nametag. Text scale is accounted for automatically.", min = 0f, max = 1f, step = 0.01f) var heightOffset = 0.08f
    @JvmStatic @Slider(title = "Render Distance", min = 8f, max = 64f, step = 1f) var distance = 48
    @JvmStatic @Switch(title = "Use Hypixel API Key", description = "API key-less lookups are used by default.", subcategory = "Data") var useHypixelApiKey = false
    @JvmStatic @Text(title = "Hypixel API Key", subcategory = "Data") var hypixelApiKey = ""

    @JvmStatic fun initialize() {
        preload()
        eventHandler { _: TickEvent.End -> LevelHead.tick() }
    }
}
