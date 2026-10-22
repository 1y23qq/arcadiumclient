// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import java.lang.reflect.Method
import kotlin.math.floor
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.hud.v1.HudManager

class HeightLimitHud : ColoredTextHud("arcadium-height-limit-hud.json", "Height Limit HUD", Category.INFO) {
    @Dropdown(title = "Style", options = ["Classic", "Compact", "Custom"])
    var style = 0

    @Text(title = "Format", description = "Use #current, #limit and #distance.")
    var format = "#limit(#distance)"

    @Color(title = "Height Limit Color", subcategory = "Colors", alpha = false)
    var limitColor = 0xFFFFFFFF.toInt()

    @Color(title = "Parentheses Color", subcategory = "Colors", alpha = false)
    var parenthesesColor = 0xFFFFFFFF.toInt()

    @Color(title = "Distance: 16+ Blocks", subcategory = "Distance Colors", alpha = false)
    var distanceFarColor = 0xFFFFFFFF.toInt()

    @Color(title = "Distance: 6 to 15 Blocks", subcategory = "Distance Colors", alpha = false)
    var distanceWarningColor = 0xFFFFAA00.toInt()

    @Color(title = "Distance: 0 to 5 Blocks", subcategory = "Distance Colors", alpha = false)
    var distanceNearColor = 0xFFFF5555.toInt()

    @Slider(title = "Warning Distance", description = "Use the warning color at this distance or closer.", min = 0f, max = 256f, step = 1f, subcategory = "Distance Colors")
    var warningDistance = 15

    @Slider(title = "Danger Distance", description = "Use the danger color at this distance or closer.", min = 0f, max = 256f, step = 1f, subcategory = "Distance Colors")
    var dangerDistance = 5

    @Switch(title = "Show Y Limit", description = "Show the map's build limit in Compact style.")
    var showYLimit = true

    @Switch(title = "Show Current Y", description = "Show your current height in Compact style.")
    var showCurrentY = false

    @Switch(title = "Show Distance to Limit", description = "Show the remaining blocks in parentheses in Compact style.")
    var showDistanceToLimit = true

    @Switch(title = "Space Before Distance", description = "Add a space before the distance in Compact style.")
    var spaceBeforeDistance = false

    @Switch(title = "Show Unknown Limit", description = "Display ? while this map's height limit is unavailable.")
    var showUnknown = false

    override fun addCallbacks(tree: Tree) {
        super.addCallbacks(tree)
        val compact = arrayOf("showYLimit", "showCurrentY", "showDistanceToLimit", "spaceBeforeDistance")
        compact.forEach { tree.getProp(it).addDisplayCondition { if (style == 1) Property.Display.SHOWN else Property.Display.HIDDEN } }
        tree.getProp("format").addDisplayCondition { if (style == 2) Property.Display.SHOWN else Property.Display.HIDDEN }
        tree.getProp("style").addCallback {
            compact.forEach { tree.getProp(it).revaluateDisplay() }
            tree.getProp("format").revaluateDisplay()
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

    override fun valueRuns(): List<Run> {
        val mc = Minecraft.getInstance()
        val value = HeightLimitSource.snapshot()
        val preview = HudManager.isEditing && (!value.supported || mc.player == null)
        val height = if (preview) 67 else mc.player?.let { floor(it.y).toInt() } ?: 0
        val limit = if (preview) 256 else value.maximum
        val template = if (style == 2) format else HeightLimitText.template(style, showYLimit, showCurrentY, showDistanceToLimit, spaceBeforeDistance)
        val distanceColor = when (HeightLimitText.distanceRange(limit?.let { HeightLimitText.distance(height, it) } ?: Long.MAX_VALUE, warningDistance, dangerDistance)) {
            2 -> distanceNearColor
            1 -> distanceWarningColor
            else -> distanceFarColor
        }
        return HeightLimitText.parts(template, height, limit).map { part ->
            Run(part.text(), when (part.kind()) {
                HeightLimitText.Kind.LIMIT -> limitColor
                HeightLimitText.Kind.PARENTHESES -> parenthesesColor
                HeightLimitText.Kind.DISTANCE -> distanceColor
                else -> null
            })
        }
    }
}

object HeightLimitSource {
    data class Snapshot(val inBedwars: Boolean = false, val map: String? = null, val maximum: Int? = null,
                        val supported: Boolean = inBedwars)
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
    private val overlaySettings by lazy {
        runCatching {
            Class.forName("tomeko.hybedwars.config.HyBedWarsConfig").getField("INSTANCE").get(null) as? HeightOverlaySettings
        }.getOrNull()
    }
    private var sampledAt = Long.MIN_VALUE / 2
    private var limitAt = Long.MIN_VALUE / 2
    private var value = Snapshot()
    private var lastWorld: Any? = null
    private var lastServer: String? = null
    private var lastMinemenEnabled = true
    private var matchedAddress: String? = null
    private var addressIsMinemen = false

    private fun matchesMinemen(server: String?): Boolean {
        if (server != matchedAddress) {
            matchedAddress = server
            addressIsMinemen = MinemenHeightLimit.matches(server)
        }
        return addressIsMinemen
    }

    @JvmStatic
    fun minemenLimitActive(): Boolean {
        val mc = Minecraft.getInstance()
        return mc.world != null && !mc.isSingleplayer &&
            overlaySettings?.`arcadium$minemenHeightLimitEnabled`() != false &&
            matchesMinemen(mc.currentServerEntry?.ip)
    }

    @JvmStatic
    fun minemenOverlayActive(): Boolean = minemenLimitActive() &&
        overlaySettings?.`arcadium$heightOverlayEnabled`() == true

    @JvmStatic
    fun snapshot(): Snapshot {
        val mc = Minecraft.getInstance()
        val world = mc.world
        val server = if (mc.isSingleplayer) null else mc.currentServerEntry?.ip
        val minemenEnabled = overlaySettings?.`arcadium$minemenHeightLimitEnabled`() != false
        val now = System.nanoTime()
        val sameContext = world === lastWorld && server == lastServer && minemenEnabled == lastMinemenEnabled
        if (sameContext && now - sampledAt < 100_000_000L) return value
        if (!sameContext) {
            value = Snapshot()
            limitAt = Long.MIN_VALUE / 2
            lastWorld = world
            lastServer = server
            lastMinemenEnabled = minemenEnabled
        }
        sampledAt = now
        if (world == null) return Snapshot().also { value = it }
        if (matchesMinemen(server)) {
            value = if (minemenEnabled) Snapshot(false, "Minemen Club", MinemenHeightLimit.LIMIT, true) else Snapshot()
            return value
        }
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
