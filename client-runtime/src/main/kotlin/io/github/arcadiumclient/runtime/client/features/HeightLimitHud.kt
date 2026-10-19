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
        return value.inBedwars && (value.maximum != null || showUnknown)
    }

    override fun getText(): String {
        val mc = Minecraft.getInstance()
        val value = HeightLimitSource.snapshot()
        if (HudManager.isEditing && (!value.inBedwars || mc.player == null)) {
            return HeightLimitText.format(style, if (style == 1) 67 else 64, if (style == 1) 256 else 113,
                showYLimit, showCurrentY, showDistanceToLimit)
        }
        val height = mc.player?.let { floor(it.y).toInt() } ?: 0
        return HeightLimitText.format(style, height, value.maximum, showYLimit, showCurrentY, showDistanceToLimit)
    }
}

object HeightLimitSource {
    data class Snapshot(val inBedwars: Boolean = false, val map: String? = null, val maximum: Int? = null)
    private class Access {
        val packetType = Class.forName("tomeko.hybedwars.location.HypixelPackets")
        val packets = packetType.getField("INSTANCE").get(null)
        val inBedwars = packetType.getMethod("getInBedwars")
        val map = packetType.getMethod("getCurrentMapName")
        val dataType = Class.forName("tomeko.hybedwars.heightlimit.HeightLimitData")
        val data = dataType.getField("INSTANCE").get(null)
        val limits = dataType.getMethod("getBedwarsLimits", String::class.java)
        val maximum: Method = limits.returnType.getMethod("getMaxBuild")
    }
    private val access by lazy { runCatching { Access() }.getOrNull() }
    private var sampledAt = Long.MIN_VALUE / 2
    private var limitAt = Long.MIN_VALUE / 2
    private var value = Snapshot()
    private var lastWorld: Any? = null

    @JvmStatic
    fun snapshot(): Snapshot {
        val world = Minecraft.getInstance().world
        val now = System.nanoTime()
        if (world === lastWorld && now - sampledAt < 100_000_000L) return value
        if (world !== lastWorld) {
            value = Snapshot()
            limitAt = Long.MIN_VALUE / 2
            lastWorld = world
        }
        sampledAt = now
        if (world == null) return Snapshot().also { value = it }
        val api = access ?: return Snapshot().also { value = it }
        value = runCatching {
            if (api.inBedwars.invoke(api.packets) != true) return@runCatching Snapshot()
            val map = api.map.invoke(api.packets) as? String
            var max = if (map == value.map) value.maximum else null
            if (map != null && (map != value.map || now - limitAt >= 1_000_000_000L)) {
                val limits = api.limits.invoke(api.data, map)
                max = limits?.let { (api.maximum.invoke(it) as Number).toInt().takeIf { limit -> limit in 1..256 } }
                limitAt = now
            }
            Snapshot(true, map, max)
        }.getOrDefault(Snapshot())
        return value
    }
}
