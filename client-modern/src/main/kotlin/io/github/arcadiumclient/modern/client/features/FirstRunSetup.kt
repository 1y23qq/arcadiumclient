// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Property
import net.minecraft.client.Minecraft

object FirstRunSetup {
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
        return when (val value = lights()?.get()) {
            is Number -> value.toInt()
            is Enum<*> -> value.ordinal
            is Boolean -> if (value) 1 else 0
            else -> null
        }
    }

    fun applyDynamicLights(mode: Int): Boolean {
        if (mode !in 0..2) return false
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
