// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object Skin3DConfig : Config("arcadium-3d-skin.json", "3D Skin", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled", description = "Give the outer skin pixels depth. Requires an outer layer on the player's skin.")
    var enabled = false

    @JvmStatic @Switch(title = "Head", category = "Body Parts")
    var head = true

    @JvmStatic @Switch(title = "Body", category = "Body Parts")
    var body = true

    @JvmStatic @Switch(title = "Left Arm", category = "Body Parts")
    var leftArm = true

    @JvmStatic @Switch(title = "Right Arm", category = "Body Parts")
    var rightArm = true

    @JvmStatic @Switch(title = "Left Leg", category = "Body Parts")
    var leftLeg = true

    @JvmStatic @Switch(title = "Right Leg", category = "Body Parts")
    var rightLeg = true

    @JvmStatic @Slider(title = "Render Distance", description = "Use normal skin layers beyond this distance.", min = 4f, max = 64f, step = 1f)
    var distance = 24

    @JvmStatic @Switch(title = "Adaptive Detail", description = "Prioritize nearby players in crowded areas.", category = "Performance")
    var adaptiveDetail = true

    @JvmStatic @Slider(title = "Detailed Players", description = "Maximum nearby players with voxel skin layers when Adaptive Detail is enabled.", min = 4f, max = 64f, step = 1f, category = "Performance")
    var detailedPlayers = 12

    private var lastApplied: List<Any>? = null
    private var retryTicks = 0
    private var warned = false

    fun initialize() {
        if (tree == null) preload()
        applyIfChanged()
        eventHandler { _: TickEvent.End ->
            if (retryTicks > 0) retryTicks-- else applyIfChanged()
        }
    }

    fun chooseEnabled(value: Boolean): Boolean {
        if (tree == null) preload()
        enabled = value
        save()
        return applyIfChanged(force = true)
    }

    private fun applyIfChanged(force: Boolean = false): Boolean {
        val state = listOf(enabled, head, body, leftArm, rightArm, leftLeg, rightLeg, distance)
        if (!force && state == lastApplied) return true
        return runCatching {
            SkinLayersBridge.apply(enabled && head, enabled && body, enabled && leftArm, enabled && rightArm,
                enabled && leftLeg, enabled && rightLeg, distance)
            lastApplied = state
            true
        }.getOrElse {
            retryTicks = 100
            if (!warned) LogManager.getLogger("Arcadium/3DSkin").warn("Could not configure the 3D skin renderer", it)
            warned = true
            false
        }
    }
}
