// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

internal enum class VideoPage(val title: String) {
    MAIN("Video settings"),
    DETAILS("Details"),
    QUALITY("Quality"),
    ANIMATIONS("Animations"),
    PERFORMANCE("Performance"),
    DISPLAY("Display"),
    OTHER("Other"),
}

internal object VideoSettingsLayout {
    val mainRows = listOf(
        "graphics" to "render_distance",
        "smooth_lighting" to "max_framerate",
        "gui_scale" to "entity_shadows",
        "brightness" to "view_bobbing",
        "dynamic_lights" to "dynamic_fov",
    )

    val navigation = listOf(
        VideoPage.DETAILS to VideoPage.QUALITY,
        VideoPage.ANIMATIONS to VideoPage.PERFORMANCE,
        VideoPage.DISPLAY to VideoPage.OTHER,
    )

    fun category(path: String): VideoPage = when (path.substringAfterLast('.').substringAfterLast('/').lowercase()) {
        "general" -> VideoPage.DISPLAY
        "quality" -> VideoPage.QUALITY
        "performance" -> VideoPage.PERFORMANCE
        "environment", "entities" -> VideoPage.DETAILS
        "camera_effects", "particles" -> VideoPage.ANIMATIONS
        else -> VideoPage.OTHER
    }
}
