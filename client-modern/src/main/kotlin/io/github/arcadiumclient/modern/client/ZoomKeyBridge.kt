// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client

import com.google.gson.Gson
import io.github.arcadiumclient.modern.mixin.ZoomClickAccess
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.platform.InputConstants
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.serialize.adapter.impl.OneConfigKeybindAdapter
import org.polyfrost.oneconfig.api.ui.v1.keybind.BindNotInScreen
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import java.nio.file.Files

object ZoomKeyBridge {
    private class Binding(val source: String, val target: KeyMapping, val key: OneConfigKeybind) { var down = false }
    private val bindings = linkedMapOf<String, Binding>()
    private val nativeNames = mapOf("zoomKey" to "zoomKey", "secondaryZoomKey" to "secondaryZoomKey", "zoomInKey" to "scrollZoomIn", "zoomOutKey" to "scrollZoomOut")
    private val adapter = OneConfigKeybindAdapter()
    private val gson = Gson()
    private var targets = emptyMap<String, KeyMapping>()
    private var ownerProfile: String? = null
    private var started = false
    private var pendingReload = false

    private fun keyFile() = if (ConfigManager.profileSpecificControls()) ConfigManager.active().folder.resolve("polyzoom-keybinds.json") else FabricLoader.getInstance().configDir.resolve("polyzoom-keybinds.json")
    private fun document(): com.google.gson.JsonObject {
        val file = keyFile()
        val root = FabricLoader.getInstance().configDir.resolve("polyzoom-keybinds.json")
        return when {
            Files.isRegularFile(file) -> ModernSettingsMigration.read(file)
            Files.isRegularFile(root) -> ModernSettingsMigration.read(root)
            else -> com.google.gson.JsonObject()
        }
    }

    fun initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register { started = true; pendingReload = false; reload() }
        ConfigManager.addProfileChangeListener(object : ConfigManager.ProfileChangeListener {
            override fun onProfileChanged(newProfile: String) { pendingReload = true }
            override fun onProfileSaving(profile: String) {
                if (profile != ownerProfile || profile != ConfigManager.activeProfile()) return
                runCatching {
                    val saved = document()
                    val original = saved.deepCopy()
                    for ((source, target) in targets) {
                        if (!saved.has(source) || bindings.containsKey(source) && target.isUnbound) continue
                        ModernSettingsMigration.applyNativeKey(saved.getAsJsonObject(source), target.saveString())
                    }
                    if (saved != original) ModernSettingsMigration.write(keyFile(), saved)
                }.onFailure { org.slf4j.LoggerFactory.getLogger("Arcadium/Zoom").warn("Could not save shared zoom shortcuts", it) }
            }
        })
        KeybindManager.addMenuEditListener { changed ->
            val binding = bindings.values.firstOrNull { it.key === changed } ?: return@addMenuEditListener
            runCatching {
                val file = keyFile()
                val document = document()
                val value = document.getAsJsonObject(binding.source)?.deepCopy() ?: com.google.gson.JsonObject()
                listOf("keyCodes", "mouseBtns", "mods", "durationNanos", "class").forEach(value::remove)
                for ((name, field) in adapter.serialize(changed)) value.add(name, gson.toJsonTree(field))
                value.addProperty("class", BindNotInScreen::class.java.name)
                document.add(binding.source, value)
                ModernSettingsMigration.write(file, document)
            }.onFailure { org.slf4j.LoggerFactory.getLogger("Arcadium/Zoom").warn("Could not save the shared zoom shortcut", it) }
        }
        ClientTickEvents.START_CLIENT_TICK.register { mc ->
            if (started && pendingReload) { pendingReload = false; reload() }
            if (mc.level == null || mc.screen != null) for (binding in bindings.values) {
                binding.down = false
                if (binding.target.isUnbound) {
                    binding.target.isDown = false
                    (binding.target as ZoomClickAccess).`arcadium$clicks`(0)
                }
            }
        }
    }
    private fun reload() {
        for (binding in bindings.values) { KeybindManager.unregister(binding.key); if (binding.target.isUnbound) binding.target.isDown = false }
        bindings.clear()
        ownerProfile = null
        runCatching {
            val marker = ModernSettingsMigration.marker(FabricLoader.getInstance().configDir)
            val rootState = if (Files.isRegularFile(marker)) ModernSettingsMigration.read(marker) else com.google.gson.JsonObject()
            var saved = rootState.getAsJsonObject("zoomBridgedKeys")?.deepCopy() ?: com.google.gson.JsonObject()
            val document = document()
            val owner = Class.forName("dev.isxander.zoomify.Zoomify")
            targets = nativeNames.mapValues { (_, name) -> owner.getDeclaredField(name).apply { isAccessible = true }.get(null) as KeyMapping }
            if (ConfigManager.activeProfile().isEmpty()) rootState.getAsJsonObject("zoomDeferredRootKeys")?.let { deferred ->
                for ((source, value) in deferred.entrySet()) targets[source]?.setKey(InputConstants.getKey(value.asString))
                KeyMapping.resetMapping()
                Minecraft.getInstance().options.save()
                rootState.remove("zoomDeferredRootKeys")
                ModernSettingsMigration.write(marker, rootState)
            }
            if (ConfigManager.activeProfile().isNotEmpty() && ConfigManager.profileSpecificControls() && Files.isRegularFile(keyFile())) {
                val stateFile = ConfigManager.active().folder.resolve("arcadium-zoom-keys.json")
                val state = if (Files.isRegularFile(stateFile)) ModernSettingsMigration.read(stateFile) else com.google.gson.JsonObject()
                val previous = state.deepCopy()
                val flags = state.getAsJsonObject("bridged") ?: com.google.gson.JsonObject()
                val tracking = state.getAsJsonObject("state") ?: com.google.gson.JsonObject()
                val controlsFile = ConfigManager.active().folder.resolve("minecraft-keybinds.json")
                val controls = if (Files.isRegularFile(controlsFile)) ModernSettingsMigration.read(controlsFile).getAsJsonObject("controls") ?: com.google.gson.JsonObject() else com.google.gson.JsonObject()
                val imports = ZoomShortcutProfile.imports(document, controls, flags, tracking)
                saved = com.google.gson.JsonObject()
                for ((source, value) in imports.entrySet()) {
                    val target = targets[source] ?: continue
                    target.setKey(InputConstants.getKey(if (value.isJsonNull) "key.keyboard.unknown" else value.asString))
                    if (value.isJsonNull) saved.addProperty(source, ModernSettingsMigration.ZOOM_KEYS[source])
                }
                state.add("bridged", saved.deepCopy())
                val next = com.google.gson.JsonObject()
                for ((source, target) in targets) if (document.has(source)) next.add(source, com.google.gson.JsonObject().apply {
                    addProperty("legacy", ModernSettingsMigration.fingerprint(document.getAsJsonObject(source)))
                    addProperty("native", target.saveString())
                })
                state.add("state", next)
                if (state != previous) ModernSettingsMigration.write(stateFile, state)
                KeyMapping.resetMapping()
                if (!imports.isEmpty) Minecraft.getInstance().options.save()
            }
            for ((source, _) in saved.entrySet()) {
                val target = targets[source] ?: continue
                if (!target.isUnbound) continue
                if (!document.has(source)) {
                    val value = com.google.gson.JsonObject()
                    value.addProperty("class", BindNotInScreen::class.java.name)
                    val default = when (source) { "zoomKey" -> "key.keyboard.c"; "secondaryZoomKey" -> "key.keyboard.f6"; else -> null }
                    if (default != null) value.add("keyCodes", com.google.gson.JsonArray().apply { add(default) })
                    document.add(source, value)
                }
                @Suppress("UNCHECKED_CAST")
                val raw = gson.fromJson(document.getAsJsonObject(source), Map::class.java) as Map<String, Any>
                val stored = adapter.deserialize(raw)
                lateinit var binding: Binding
                val key = BindNotInScreen(stored.keyCodes, stored.mouseBtns, stored.mods, stored.durationNanos) { down ->
                    val mc = Minecraft.getInstance()
                    val available = target.isUnbound && mc.level != null && mc.screen == null
                    if (available) {
                        target.isDown = down
                        if (down && !binding.down) {
                            val click = target as ZoomClickAccess
                            click.`arcadium$clicks`(click.`arcadium$clicks`() + 1)
                        }
                    }
                    binding.down = down
                    available
                }.apply {
                    mergeOverwrite(stored)
                    name = when (source) { "zoomKey" -> "Zoom"; "secondaryZoomKey" -> "Secondary Zoom"; "zoomInKey" -> "Zoom In"; else -> "Zoom Out" }
                    category = "Arcadium Zoom"
                }
                binding = Binding(source, target, key)
                bindings[source] = binding
                KeybindManager.register(key)
            }
            ownerProfile = ConfigManager.activeProfile()
        }.onFailure { org.slf4j.LoggerFactory.getLogger("Arcadium/Zoom").warn("Could not activate shared zoom shortcuts", it) }
    }
}
