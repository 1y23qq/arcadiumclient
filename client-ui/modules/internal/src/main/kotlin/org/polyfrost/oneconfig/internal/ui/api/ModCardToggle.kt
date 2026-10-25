// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui.api

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.ui.v1.ModToggles
import org.polyfrost.oneconfig.internal.ui.hud.HudModCardData

internal interface ModCardToggle {
    val enabled: Boolean
    val available: Boolean get() = true
    val property: Property<Boolean>? get() = null
    fun setEnabled(value: Boolean)
}

internal fun modCardToggle(mod: ConfigData): ModCardToggle? {
    ModToggles.toggleFor(mod.id)?.let { return RegisteredModCardToggle(mod.id) }
    return when (mod) {
        is TreeConfigData -> findModEnabledProperty(mod.uiTree)?.let { PropertyModCardToggle(it, mod.tree) }
        is HudModCardData -> HudModCardToggle(mod.hud)
        else -> null
    }
}

private class RegisteredModCardToggle(private val id: String) : ModCardToggle {
    override val enabled: Boolean
        get() {
            ModToggles.revision
            return ModToggles.isEnabled(id)
        }
    override val available get() = ModToggles.toggleFor(id)?.needsSetup() != true
    override fun setEnabled(value: Boolean) {
        if (!available) return
        ModToggles.setEnabled(id, value)
        (ConfigRegistry.findById(id) as? TreeConfigData)?.tree?.let { ConfigManager.active().save(it) }
    }
}

internal fun findModEnabledProperty(tree: Tree): Property<Boolean>? {
    if (tree.getMetadata<Boolean>("ui_only") == true) return null
    val explicit = tree.getMetadata<String>("mod_card_toggle")
    val names = explicit?.let(::listOf) ?: listOf("globalModToggle", "enabled", "isEnabled", "modEnabled", "enable")
    for (name in names) {
        val prop = tree.getProp(name) ?: continue
        if (prop.type != Boolean::class.javaPrimitiveType && prop.type != Boolean::class.javaObjectType) continue
        if (prop.display == Property.Display.HIDDEN) continue
        @Suppress("UNCHECKED_CAST")
        return prop as Property<Boolean>
    }
    return null
}

private class PropertyModCardToggle(
    override val property: Property<Boolean>,
    private val tree: Tree,
) : ModCardToggle {
    override val enabled get() = property.get() == true
    override val available get() = property.display == Property.Display.SHOWN

    override fun setEnabled(value: Boolean) {
        if (!available) return
        property.set(value)
        ConfigManager.active().save(tree)
    }
}

private class HudModCardToggle(private val provider: Hud) : ModCardToggle {
    private fun instances() = HudManager.getHudsOfType(provider::class.java)

    override val enabled: Boolean
        get() {
            HudManager.instancesRevision.intValue
            return instances().any { !it.hidden }
        }

    override fun setEnabled(value: Boolean) {
        var created: Hud? = null
        try {
            var targets = instances()
            if (value && targets.isEmpty()) {
                val instance = if (provider.isReal) provider else provider.make()
                created = instance
                val (x, y) = instance.defaultPosition()
                instance.setAbsolutePosition(x, y)
                HudManager.activeInstances.add(instance)
                HudManager.instancesRevision.intValue++
                HudManager.markProviderKnown(instance)
                instance.setup()
                instance.captureStaticSizeDefaults()
                instance.capturePositionDefaults()
                targets = arrayListOf(instance)
                created = null
            }
            for (hud in targets) {
                val hidden = hud.tree?.getProp("hidden")
                if (hidden != null) hidden.setAs(!value) else hud.hidden = !value
                hud.tree?.let { ConfigManager.active().save(it) }
            }
            HudManager.invalidate()
        } catch (failure: Exception) {
            created?.let { HudManager.removeHud(it) }
            LOGGER.error("Failed to change HUD visibility for {}", provider.title, failure)
        }
    }
}

private val LOGGER = LogManager.getLogger("Arcadium Options/ModToggle")
