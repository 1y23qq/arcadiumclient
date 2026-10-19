// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Property
import net.minecraft.client.Minecraft

object FirstRunSetup {
    private class CeraLights {
        private val owner = Class.forName("dev.rdh.cera.Cera", false, FirstRunSetup::class.java.classLoader)
        private val configuration = owner.getField("CONFIG")
        private val storage = owner.getField("CONFIG_STORAGE")
        private val mode = configuration.type.getField("dynamicLights")
        private val save = storage.type.getMethod("save")

        fun current(): Int? = (configuration.get(null)?.let(mode::get) as? Enum<*>)?.ordinal

        fun apply(index: Int): Boolean {
            val config = configuration.get(null) ?: return false
            val target = mode.type.enumConstants?.getOrNull(index) ?: return false
            val previous = mode.get(config)
            if (previous == target) return true
            mode.set(config, target)
            try {
                val destination = storage.get(null) ?: error("Dynamic lighting storage is not initialized")
                save.invoke(destination)
            } catch (failure: Exception) {
                mode.set(config, previous)
                throw failure
            }
            val client = Minecraft.getInstance()
            if (client.world != null) client.worldRenderer?.reload()
            return true
        }
    }

    private val cera by lazy { runCatching { CeraLights() }.getOrNull() }

    private fun lights(): Property<*>? {
        for (id in listOf("lambdynlights.json", "dynamiclights.json", "argentum-extras.json")) {
            val tree = runCatching { ConfigManager.active().get(id) }.getOrNull() ?: continue
            for (key in listOf("mode", "dynamicLightsMode", "dynamicLights")) {
                tree.getProp(key)?.let { return it }
            }
        }
        return null
    }

    fun currentDynamicLightsMode(): Int? {
        runCatching { cera?.current() }.getOrNull()?.let { return it }
        return when (val value = lights()?.get()) {
            is Number -> value.toInt()
            is Enum<*> -> value.ordinal
            is Boolean -> if (value) 1 else 0
            else -> null
        }
    }

    fun applyDynamicLights(mode: Int): Boolean {
        if (mode !in 0..2) return false
        if (cera != null) return runCatching { cera?.apply(mode) == true }.getOrDefault(false)
        @Suppress("UNCHECKED_CAST")
        val property = lights() as? Property<Any> ?: return false
        val previous = property.get()
        val value: Any = when (previous) {
            is Number -> mode
            is Enum<*> -> previous.javaClass.enumConstants?.getOrNull(mode) ?: return false
            is Boolean -> mode != 0
            else -> return false
        }
        property.set(value)
        ConfigManager.active().saveAll()
        return true
    }

    fun toggleSprint(enabled: Boolean): Boolean = set("polysprint.json", "toggleSprint", enabled)
    fun blockOverlay(enabled: Boolean): Boolean =
        set("custom-block-highlight.json", "globalModToggle", enabled) || set("blockhighlight.json", "enabled", enabled)

    private fun set(id: String, key: String, value: Boolean): Boolean {
        val tree = runCatching { ConfigManager.active().get(id) }.getOrNull() ?: return false
        @Suppress("UNCHECKED_CAST")
        val property = tree.getProp(key) as? Property<Boolean> ?: return false
        property.set(value)
        ConfigManager.active().save(tree)
        return true
    }
}
