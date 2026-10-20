// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime

import net.fabricmc.api.ClientModInitializer
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import io.github.arcadiumclient.runtime.client.ClientPreferences
import io.github.arcadiumclient.runtime.client.MenuPreferences
import io.github.arcadiumclient.runtime.client.features.*
import io.github.arcadiumclient.runtime.client.presence.ArcadiumPlayersConfig
import io.github.arcadiumclient.runtime.client.presence.ArcadiumPresence
import io.github.arcadiumclient.runtime.client.presence.OnlineUsage

class ArcadiumRuntime : ClientModInitializer {
    override fun onInitializeClient() {
        val log = LogManager.getLogger("Arcadium")
        val components = linkedMapOf<String, () -> Unit>(
            "Client preferences" to { ClientPreferences.preload(); MenuPreferences.preload() },
            "Fullbright" to { io.github.arcadiumclient.runtime.legacy.LegacyFullbrightConfig.preload() },
            "FOV Changer" to { io.github.arcadiumclient.runtime.legacy.LegacyFovConfig.preload() },
            "Height Overlay" to { io.github.arcadiumclient.runtime.legacy.LegacyHeightConfig.preload() },
            "Nick Hider" to { NickHider.initialize() },
            "GUI Scale" to { GuiScaling.initialize() },
            "Tab Background" to { TabBackgroundConfig.preload() },
            "Light Level" to { LightLevelOverlay.initialize() },
            "NoRender" to { NoRender.initialize() },
            "3D Skin" to { Skin3DConfig.initialize() },
            "Kill Effects" to { KillEffectsConfig.initialize() },
            "Cute Visuals" to { CuteVisualsConfig.initialize() },
            "Custom Crosshair" to { io.github.arcadiumclient.runtime.legacy.LegacyCrosshairConfig.preload(); CustomCrosshair.initialize() },
            "AutoText" to { AutoText.initialize() },
            "Toggle Sprint" to {
                ToggleSprintConfig.initialize()
                HudManager.register(ToggleSprintHud(), "polysprint.json", "/assets/arcadium/logo.svg")
            },
            "Chat Tweaks" to { ChatTweaksConfig.initialize() },
            "Chat Filters" to { ChatFiltersConfig.initialize() },
            "Height Limit HUD" to { HudManager.register(HeightLimitHud(), "hybedwars.json", "/assets/arcadium/logo.svg") },
            "Animations" to { AnimationsConfig.initialize() },
            "Arcadium Badge" to { ArcadiumPlayersConfig.preload(); ArcadiumPresence.initialize() },
            "Online Count" to { OnlineUsage.initialize() },
        )
        components.forEach { (name, start) ->
            try { start() } catch (error: Exception) { log.error("Could not start {}", name, error) }
        }
    }
}
