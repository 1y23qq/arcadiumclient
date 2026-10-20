// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import java.lang.reflect.Method
import kotlin.math.floor
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.TextHud

class HeightLimitHud : TextHud("arcadium-height-limit-hud.json", "Height Limit HUD", Category.INFO, "") {
    @Dropdown(title = "Style", options = ["Classic", "Compact"])
    var style = 0

    @Switch(title = "Show Y Limit", description = "Show the map's build limit in Compact style.")
    var showYLimit = true

    @Switch(title = "Show Current Y", description = "Show your current height in Compact style.")
    var showCurrentY = false

    @Switch(title = "Show Distance to Limit", description = "Show the remaining blocks in parentheses in Compact style.")
    var showDistanceToLimit = true

    @Switch(title = "Show Unknown Limit", description = "Display ? while this map's height limit is unavailable.")
    var showUnknown = false

    override fun addCallbacks(tree: Tree) {
        super.addCallbacks(tree)
        val compact = arrayOf("showYLimit", "showCurrentY", "showDistanceToLimit")
        compact.forEach { tree.getProp(it).addDisplayCondition { if (style == 1) Property.Display.SHOWN else Property.Display.HIDDEN } }
        tree.getProp("style").addCallback {
            compact.forEach { tree.getProp(it).revaluateDisplay() }
            false
        }
    }

    override fun showByDefault(): Boolean = false

    override fun defaultPosition(): Pair<Float, Float> = 10f to 85f

    override fun updateFrequency(): Long = 100_000_000L

    override fun shouldShow(): Boolean {
        if (HudManager.isEditing) return true
        if (style == 1 && !showYLimit && !showCurrentY && !showDistanceToLimit) return false
        if (Minecraft.getInstance().world == null) return false
        val value = HeightLimitSource.snapshot()
        return value.supported && (value.maximum != null || showUnknown)
    }

    override fun getText(): String {
        val mc = Minecraft.getInstance()
        val value = HeightLimitSource.snapshot()
        if (HudManager.isEditing && (!value.supported || mc.player == null)) {
            return HeightLimitText.format(style, if (style == 1) 67 else 64, if (style == 1) 256 else 113,
                showYLimit, showCurrentY, showDistanceToLimit)
        }
        val height = mc.player?.let { floor(it.shape.minY).toInt() } ?: 0
        return HeightLimitText.format(style, height, value.maximum, showYLimit, showCurrentY, showDistanceToLimit)
    }
}

object HeightLimitSource {
    data class Snapshot(val inBedwars: Boolean = false, val map: String? = null, val maximum: Int? = null,
                        val supported: Boolean = inBedwars)
    private var cachedAddress: String? = null
    private var cachedMinemen = false
    @JvmStatic fun minemenLimitActive(): Boolean {
        val client = Minecraft.getInstance()
        if (client.world == null || client.isSingleplayer || !io.github.arcadiumclient.runtime.legacy.LegacyHeightConfig.minemenHeightLimit) return false
        val address = client.currentServerEntry?.ip
        if (address != cachedAddress) {
            cachedAddress = address
            cachedMinemen = MinemenHeightLimit.matches(address)
        }
        return cachedMinemen
    }
    @JvmStatic fun minemenOverlayActive() = minemenLimitActive() && io.github.arcadiumclient.runtime.legacy.LegacyHeightConfig.heightOverlay
    @JvmStatic fun snapshot(): Snapshot {
        if (Minecraft.getInstance().world == null) return Snapshot()
        if (minemenLimitActive()) return Snapshot(false, "Minemen Club", 100, true)
        return Snapshot(false, null, currentLimit(), true)
    }
    @JvmStatic fun currentLimit(): Int = if (minemenLimitActive()) 100
        else if (io.github.arcadiumclient.runtime.legacy.LegacyHeightConfig.debugModeEnabled)
            io.github.arcadiumclient.runtime.legacy.LegacyHeightConfig.debugModeHeight.coerceIn(1, 256) else 256
}
