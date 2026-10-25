// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.lang.reflect.Modifier
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.config.v1.ConfigManager

object ModernIdle {
    private class Access {
        val type = Class.forName("dynamic_fps.impl.config.DynamicFPSConfig")
        val owner: Any = type.getField("INSTANCE").get(null)
        private val defaults: Any = type.getField("DEFAULTS").get(null)
        private val util = Class.forName("dynamic_fps.impl.util.JsonUtil")
        private val encode = util.getMethod("toJsonTree", Any::class.java)
        private val decode = util.getMethod("fromJson", JsonElement::class.java, Class::class.java)
        private val changed = Class.forName("dynamic_fps.impl.DynamicFPSMod").getMethod("onConfigChanged")
        private val enabled = type.getMethod("enabled")
        private val setEnabled = type.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
        private val fields = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.onEach { it.isAccessible = true }
        private val state = Class.forName("dynamic_fps.impl.config.Config").getDeclaredField("state").apply { isAccessible = true }
        fun snapshot(): JsonObject = (encode.invoke(null, owner) as JsonElement).asJsonObject
        fun defaults(): JsonObject = (encode.invoke(null, defaults) as JsonElement).asJsonObject
        fun load(value: JsonObject) {
            val next = decode.invoke(null, value, type)
            for (field in fields) if (field.name != "enabled") field.set(owner, field.get(next))
            setEnabled.invoke(owner, enabled.invoke(next))
            @Suppress("UNCHECKED_CAST")
            val states = fields.first { it.name == "configs" }.get(owner) as Map<Any, Any>
            for ((key, setting) in states) state.set(setting, key)
            changed.invoke(null)
        }
    }
    private val logger = org.slf4j.LoggerFactory.getLogger("Arcadium/Idle")
    private var access: Access? = null
    private var ready = false
    private var loading = false
    private var currentProfile: String? = null
    private var blockedProfile: String? = null

    fun initialize() {
        if (!FabricLoader.getInstance().isModLoaded("dynamic_fps")) return
        ClientLifecycleEvents.CLIENT_STARTED.register {
            runCatching { access = access ?: Access(); ready = true; loadProfile(ConfigManager.activeProfile()) }
                .onFailure { logger.warn("Could not initialize the selected idle controls", it) }
        }
        ConfigManager.addProfileChangeListener(object : ConfigManager.ProfileChangeListener {
            override fun onProfileSaving(profile: String) {
                val codec = access ?: return
                if (!ready || loading || profile != currentProfile || profile != ConfigManager.activeProfile() || profile == blockedProfile) return
                runCatching { IdleSettings.save(ConfigManager.active().folder.resolve("dynamic_fps.json"), codec.snapshot()) }
                    .onFailure { logger.warn("Could not save idle profile {}", profile, it) }
            }
            override fun onProfileChanged(newProfile: String) { if (ready) loadProfile(newProfile) }
        })
    }
    private fun loadProfile(profile: String) {
        val codec = access ?: return
        val before = codec.snapshot()
        loading = true
        try {
            codec.load(IdleSettings.load(ConfigManager.active().folder.resolve("dynamic_fps.json"), codec.defaults()))
            blockedProfile = null
        } catch (failure: Exception) {
            runCatching { codec.load(before) }
            blockedProfile = profile
            logger.warn("Kept unreadable idle profile {} unchanged", profile, failure)
        } finally { currentProfile = profile; loading = false }
    }
    @JvmStatic fun interceptSave(): Boolean {
        if (loading) return true
        val codec = access ?: runCatching { Access().also { access = it } }.getOrElse {
            logger.warn("Could not preserve idle settings", it); return true
        }
        if (ready && (ConfigManager.activeProfile() != currentProfile || currentProfile == blockedProfile)) return true
        val file = if (ready) ConfigManager.active().folder.resolve("dynamic_fps.json") else FabricLoader.getInstance().configDir.resolve("dynamic_fps.json")
        runCatching { IdleSettings.save(file, codec.snapshot()) }
            .onFailure { logger.warn("Could not preserve idle settings", it) }
        return true
    }
}
