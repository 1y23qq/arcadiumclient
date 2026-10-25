// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern

import io.github.arcadiumclient.modern.client.ClientPreferences
import io.github.arcadiumclient.modern.client.MenuPreferences
import io.github.arcadiumclient.modern.client.ModernBrand
import io.github.arcadiumclient.modern.client.features.*
import io.github.arcadiumclient.modern.client.presence.ArcadiumPlayersConfig
import io.github.arcadiumclient.modern.client.presence.ArcadiumPresence
import io.github.arcadiumclient.modern.client.presence.OnlineUsage
import io.github.arcadiumclient.modern.client.skins.SkinChanger
import io.github.arcadiumclient.modern.gui.ModernScreens
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.hud.v1.HudManager

class ModernRuntime : ClientModInitializer {
    override fun onInitializeClient() {
        val log = LogManager.getLogger("Arcadium")
        val components = linkedMapOf<String, () -> Unit>(
            "Preferences" to { ClientPreferences.preload(); MenuPreferences.preload() },
            "Selected Features" to { io.github.arcadiumclient.modern.client.ModernScopePolicy.initialize() },
            "Shared Zoom Shortcuts" to { io.github.arcadiumclient.modern.client.ZoomKeyBridge.initialize() },
            "Shared Zoom Profiles" to { io.github.arcadiumclient.modern.client.ZoomProfiles.initialize() },
            "Idle Controls" to { io.github.arcadiumclient.modern.client.ModernIdle.initialize() },
            "Shared Native Profiles" to { io.github.arcadiumclient.modern.client.profile.NativeYaclProfiles.initialize() },
            "Legacy Snapshot Aliases" to { io.github.arcadiumclient.modern.client.profile.LegacyOslProfileAliases.initialize() },
            "Shared Legacy Native Profiles" to { io.github.arcadiumclient.modern.client.migration.ModernLegacyNativeProfiles.initialize() },
            "Tab Background" to { TabBackgroundConfig.preload() },
            "Nick Hider" to { NickHider.initialize() },
            "GUI Scale" to { GuiScaleConfig.preload() },
            "NoRender" to { NoRender.initialize() },
            "Light Level" to { LightLevelConfig.preload() },
            "Vignette" to { VignetteConfig.INSTANCE.preload() },
            "Hurt Camera" to { BetterHurtCamConfig.initialize() },
            "Glint" to { io.github.arcadiumclient.modern.client.glint.ModernGlint.initialize() },
            "3D Skin" to { Skin3DConfig.initialize() },
            "AutoText" to { AutoText.initialize() },
            "Chat" to { ChatTweaksConfig.initialize(); ChatFiltersConfig.initialize() },
            "Crosshair" to { LegacyCrosshairTweaks.initialize(); CustomCrosshairConfig.preload(); CustomCrosshair.initialize() },
            "Animations" to { AnimationsConfig.initialize() },
            "Toggle Sprint" to {
                ToggleSprintConfig.initialize()
                HudManager.register(io.github.arcadiumclient.runtime.client.features.ToggleSprintHud(), "polysprint.json", "/assets/arcadium/logo.svg")
            },
            "Health" to { HudManager.register(io.github.arcadiumclient.runtime.client.features.HealthHud(), "arcadium-health.json", "/assets/arcadium/logo.svg") },
            "Height Limit" to { HeightLimitOptions.initialize(); HudManager.register(io.github.arcadiumclient.runtime.client.features.HeightLimitHud(), "arcadium-height-limit.json", "/assets/arcadium/logo.svg") },
            "Kill Effects" to { KillEffectsConfig.initialize(); KillEffects.initialize() },
            "Skins" to { SkinChanger.initialize() },
            "Badge" to { ArcadiumPlayersConfig.preload(); ArcadiumPresence.initialize() },
            "Online Count" to { OnlineUsage.initialize() },
            "Hypixel Helpers" to { io.github.arcadiumclient.modern.client.hypixel.ModernHypixel.initialize() },
            "Menus" to { ModernScreens.initialize() },
        )
        components.forEach { (name, start) ->
            try { start() } catch (failure: Exception) { log.error("Could not start {}", name, failure) }
        }
        ClientTickEvents.START_CLIENT_TICK.register { ToggleSprintController.beforeTick() }
        ClientTickEvents.END_CLIENT_TICK.register {
            ToggleSprintController.afterTick()
            NoRender.tick()
            LightLevelOverlay.tick()
        }
        ClientLifecycleEvents.CLIENT_STARTED.register { ModernBrand.installIcon(it) }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            io.github.arcadiumclient.modern.client.chat.ChatImagePreviews.close()
            io.github.arcadiumclient.modern.client.chat.ChatHistory.close()
        }
    }
}
