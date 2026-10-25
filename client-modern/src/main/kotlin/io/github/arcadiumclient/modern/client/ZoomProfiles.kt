// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client

import com.google.gson.JsonObject
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import java.nio.file.Path
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.config.v1.ConfigManager

object ZoomProfiles {
    private class Access {
        private val type = Class.forName("dev.isxander.zoomify.config.ZoomifySettings")
        val owner: Any = type.getField("Companion").get(null)
        private val encode = type.getMethod("encodeStart", DynamicOps::class.java)
        private val decode = type.getMethod("decode", Any::class.java, DynamicOps::class.java)
        fun snapshot(): JsonObject = ((encode.invoke(owner, JsonOps.INSTANCE) as DataResult<*>).getOrThrow() as com.google.gson.JsonElement).asJsonObject
        fun load(value: JsonObject): Boolean = decode.invoke(owner, value, JsonOps.INSTANCE) == true
    }
    private val logger = org.slf4j.LoggerFactory.getLogger("Arcadium/Zoom Profiles")
    private var access: Access? = null
    private var defaults: JsonObject? = null
    private var currentProfile: String? = null
    private var blockedProfile: String? = null
    private var loading = false

    fun initialize() {
        if (!FabricLoader.getInstance().isModLoaded("zoomify")) return
        ClientLifecycleEvents.CLIENT_STARTED.register {
            runCatching {
                val codec = Access()
                defaults = codec.snapshot()
                access = codec
                loadProfile(ConfigManager.activeProfile())
            }.onFailure { logger.warn("Could not activate shared Zoom profiles", it) }
        }
        ConfigManager.addProfileChangeListener(object : ConfigManager.ProfileChangeListener {
            override fun onProfileSaving(profile: String) {
                val codec = access ?: return
                if (profile != currentProfile || profile != ConfigManager.activeProfile() || profile == blockedProfile || loading) return
                runCatching { save(codec, ConfigManager.active().folder.resolve("zoomify.json")) }
                    .onFailure { logger.warn("Could not save shared Zoom profile {}", profile, it) }
            }
            override fun onProfileChanged(newProfile: String) { if (access != null) loadProfile(newProfile) }
        })
    }

    private fun loadProfile(profile: String) {
        val codec = access ?: return
        val baseline = defaults ?: return
        val before = codec.snapshot()
        loading = true
        try {
            val value = ZoomProfileStore.load(ConfigManager.active().folder.resolve("zoomify.json"), baseline)
            check(codec.load(value)) { "Zoom profile contains invalid settings." }
            blockedProfile = null
        } catch (failure: Exception) {
            codec.load(before)
            blockedProfile = profile
            logger.warn("Kept unreadable Zoom profile {} unchanged", profile, failure)
        } finally {
            currentProfile = profile
            loading = false
        }
    }

    @JvmStatic fun interceptSave(owner: Any, nativeFile: Path): Boolean {
        val codec = access ?: return false
        if (codec.owner !== owner || nativeFile.toAbsolutePath().normalize() != FabricLoader.getInstance().configDir.resolve("zoomify.json").toAbsolutePath().normalize()) return false
        val profile = ConfigManager.activeProfile()
        if (loading || profile != currentProfile || profile == blockedProfile) return true
        if (profile.isEmpty()) return false
        runCatching { save(codec, ConfigManager.active().folder.resolve("zoomify.json")) }
            .onFailure { logger.warn("Could not save shared Zoom profile {}", profile, it) }
        return true
    }

    private fun save(codec: Access, file: Path) {
        ZoomProfileStore.save(file, codec.snapshot())
        if (file.toAbsolutePath().normalize() == FabricLoader.getInstance().configDir.resolve("zoomify.json").toAbsolutePath().normalize())
            ModernSettingsMigration.afterZoomSave(file, FabricLoader.getInstance().configDir)
    }
}
