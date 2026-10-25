// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import java.util.function.Consumer
import java.util.function.Supplier

object LegacyCrosshairTweaks {
    fun initialize() {
        LegacyCrosshairTweaksConfig.INSTANCE.preload()
        hideCard()
        ConfigManager.addTreeRegistrationListener { tree ->
            if (tree.id == "polycrosshair.json") attach(tree)
        }
        if (ConfigManager.active().exists("polycrosshair.json")) attach(ConfigManager.active().get("polycrosshair.json"))
        ClientLifecycleEvents.CLIENT_STARTED.register {
            hideCard()
            if (ConfigManager.active().exists("polycrosshair.json")) attach(ConfigManager.active().get("polycrosshair.json"))
        }
    }
    private fun hideCard() {
        LegacyCrosshairTweaksConfig.INSTANCE.tree?.addMetadata("hidden", true)
        ConfigRegistry.unregister("crosshairtweaks.json")
    }
    private fun attach(target: Tree) {
        val source = LegacyCrosshairTweaksConfig.INSTANCE.tree ?: return
        source.map.forEach { (key, node) ->
            if (node !is Property<*> || key == "hideCrosshairInPerspective") return@forEach
            val mirror = Properties.functional(Supplier { node.get() }, Consumer<Any?> { value ->
                if (ConfigManager.isRebindingProfiles()) return@Consumer
                node.setAsReferential(value)
                LegacyCrosshairTweaksConfig.INSTANCE.save()
            }, "arcadiumLegacy_$key", node.title ?: key, node.description)
            mirror.addMetadata("visualizer", if (node.get() is Boolean) Visualizer.SwitchVisualizer::class.java else Visualizer.SliderVisualizer::class.java)
            mirror.addMetadata("category", "Legacy Crosshair Tweaks")
            mirror.addMetadata("subcategory", "Shared Settings")
            mirror.addMetadata("default", node.getMetadata<Any?>("default") ?: node.get())
            if (node.get() is Number) {
                mirror.addMetadata("min", 0f); mirror.addMetadata("max", 100f); mirror.addMetadata("step", 1f)
            }
            target.put(mirror)
        }
        ConfigRegistry.invalidate()
    }
    @JvmStatic fun active() = LegacyCrosshairTweaksConfig.legacySettings
    @JvmStatic fun hidden(): Boolean {
        if (!active()) return false
        val mc = Minecraft.getInstance()
        return LegacyCrosshairPolicy.hidden(LegacyCrosshairTweaksConfig.disableCrosshair,
            LegacyCrosshairTweaksConfig.hideCrosshairInContainers, mc.gui.screen() is AbstractContainerScreen<*>,
            LegacyCrosshairTweaksConfig.hideCrosshairInPerspective, mc.options.cameraType.isFirstPerson)
    }
    @JvmStatic fun thirdPerson(fallback: Boolean) = if (active()) !LegacyCrosshairTweaksConfig.hideCrosshairInPerspective else fallback
    fun setThirdPerson(value: Boolean): Boolean {
        if (ConfigManager.isRebindingProfiles()) return true
        if (!active()) return false
        LegacyCrosshairTweaksConfig.INSTANCE.tree?.getProp("hideCrosshairInPerspective")?.setAsReferential(!value)
        LegacyCrosshairTweaksConfig.hideCrosshairInPerspective = !value
        LegacyCrosshairTweaksConfig.INSTANCE.save()
        return true
    }
    @JvmStatic fun alpha(): Int {
        val first = Minecraft.getInstance().options.cameraType.isFirstPerson
        return LegacyCrosshairPolicy.alpha(if (first) LegacyCrosshairTweaksConfig.crosshairOpacityFirstPerson else LegacyCrosshairTweaksConfig.crosshairOpacityThirdPerson)
    }
    @JvmStatic fun removeBlending(): Boolean = if (Minecraft.getInstance().options.cameraType.isFirstPerson)
        LegacyCrosshairTweaksConfig.removeCrosshairBlendingFirstPerson else LegacyCrosshairTweaksConfig.removeCrosshairBlendingThirdPerson
}
