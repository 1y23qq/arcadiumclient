// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry

object ModernScopePolicy {
    fun initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register { hideExcludedControls() }
        ConfigManager.addProfileChangeListener(object : ConfigManager.ProfileChangeListener {
            override fun onProfileChanged(newProfile: String) { hideExcludedControls() }
        })
    }

    private fun hideExcludedControls() {
        if (!FabricLoader.getInstance().isModLoaded("chatting")) return
        runCatching {
            val config = Class.forName("org.polyfrost.chatting.config.ChattingConfig").getField("INSTANCE").get(null) as Config
            if (config.tree == null) config.preload()
            for (id in listOf("chatTabs", "hypixelOnlyChatTabs")) config.tree?.getProp(id)?.addDisplayCondition { Property.Display.HIDDEN }
            ConfigRegistry.invalidate()
        }.onFailure { org.slf4j.LoggerFactory.getLogger("Arcadium/Scope").warn("Could not hide excluded chat controls", it) }
    }
}
