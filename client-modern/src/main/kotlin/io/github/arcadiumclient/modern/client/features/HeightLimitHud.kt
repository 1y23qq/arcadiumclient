// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

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

open class HeightLimitHud : ColoredTextHud("arcadium-height-limit-hud.json", "Height Limit HUD", Category.INFO) {
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
        if (Minecraft.getInstance().level == null) return false
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
    data class Snapshot(val maximum: Int? = null, val supported: Boolean = false)
    @JvmStatic fun snapshot(): Snapshot {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return Snapshot()
        val server = mc.currentServer?.ip
        if (MinemenHeightLimit.matches(server)) return if (HeightLimitOptions.minemen) Snapshot(MinemenHeightLimit.LIMIT, true) else Snapshot()
        if (io.github.arcadiumclient.modern.client.hypixel.HypixelChatPolicy.isHypixel(server))
            return Snapshot(HypixelHeightLimit.maximum(), HypixelHeightLimit.inBedwars())
        return Snapshot(level.maxY + 1, true)
    }
}

object HeightLimitOptions : org.polyfrost.oneconfig.api.config.v1.Config("arcadium-height-limit.json", "Height Limit", org.polyfrost.oneconfig.api.config.v1.Config.Category.VISUALS) {
    @JvmStatic @org.polyfrost.oneconfig.api.config.v1.annotations.Switch(title = "Minemen Height Limit", description = "Use a build limit of 100 on Minemen Club.")
    var minemen = true
    private var readingLegacy = false
    private fun source() = org.polyfrost.oneconfig.api.config.v1.ConfigManager.active().folder.resolve("hybedwars.json")
    override fun initialize(byConfigManager: Boolean) {
        super.initialize(byConfigManager)
        syncLegacy()
        tree?.getProp("minemen")?.addCallback { value ->
            if (!readingLegacy) runCatching { LegacyHeightLimitSettings.write(source(), value == true) }
                .onFailure { org.apache.logging.log4j.LogManager.getLogger("Arcadium/Height Limit").warn("Could not share the height-limit setting", it) }
            false
        }
    }
    fun initialize() {
        preload()
        HypixelHeightLimit.initialize()
        org.polyfrost.oneconfig.api.config.v1.ConfigManager.addProfileChangeListener(object : org.polyfrost.oneconfig.api.config.v1.ConfigManager.ProfileChangeListener {
            override fun onProfileChanged(newProfile: String) { syncLegacy() }
        })
    }
    private fun syncLegacy() {
        runCatching { LegacyHeightLimitSettings.read(source()) }.getOrNull()?.let { value ->
            readingLegacy = true
            try { minemen = value; tree?.getProp("minemen")?.setAs(value) } finally { readingLegacy = false }
        }
    }
}
