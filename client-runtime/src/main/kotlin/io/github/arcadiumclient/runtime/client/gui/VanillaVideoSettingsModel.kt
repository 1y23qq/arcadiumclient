// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.menu.options.VideoOptionsScreen
import net.minecraft.client.options.GameOptions
import io.github.arcadiumclient.runtime.client.ClientPreferences
import io.github.arcadiumclient.runtime.mixin.client.access.VideoOptionsScreenAccessor

internal class VanillaVideoSettingsModel(override val delegate: VideoOptionsScreen) : VideoSettingsModel {
    override val parent: Screen? = (delegate as VideoOptionsScreenAccessor).`arcadium$parent`()
    private val native = Minecraft.getInstance().options
    private val bindings = mutableListOf<Binding>()

    override val options: List<VideoSetting> = buildList {
        fun toggle(key: String, label: String, page: VideoPage, option: GameOptions.Option, read: () -> Boolean) {
            val binding = Binding(read) { wanted -> if (read() != wanted) native.set(option, 1) }
            bindings += binding
            add(VideoSetting(key, label, "", page, "", VideoControl.TOGGLE, read = { binding.saved }, write = { binding.pending = it },
                format = { if (it as Boolean) "On" else "Off" }))
        }
        fun choice(key: String, label: String, page: VideoPage, option: GameOptions.Option, names: List<String>, read: () -> Int) {
            val binding = Binding(read) { wanted ->
                var attempts = 0
                while (read() != wanted && attempts++ < names.size) native.set(option, 1)
                check(read() == wanted) { "Could not apply $label" }
            }
            bindings += binding
            val values = names.mapIndexed { index, value -> VideoChoice(index, value) }
            add(VideoSetting(key, label, "", page, "", VideoControl.CHOICE, values,
                read = { binding.saved }, write = { binding.pending = it }, format = { names[(it as Number).toInt()] }))
        }
        fun slider(key: String, label: String, page: VideoPage, option: GameOptions.Option, min: Int, max: Int, step: Int,
                   multiplier: Float = 1f, format: (Int) -> String) {
            val binding = Binding({ (native.getFloat(option) * multiplier).roundToInt() }) { native.set(option, (it as Number).toFloat() / multiplier) }
            bindings += binding
            add(VideoSetting(key, label, "", page, "", VideoControl.SLIDER, minimum = min, maximum = max, interval = step,
                read = { binding.saved }, write = { binding.pending = it }, format = { format((it as Number).toInt()) }))
        }

        val quality = VideoPage.QUALITY
        val display = VideoPage.DISPLAY
        val details = VideoPage.DETAILS
        choice("graphics", "Graphics", quality, GameOptions.Option.GRAPHICS, listOf("Fast", "Fancy")) { if (native.fancyGraphics) 1 else 0 }
        slider("render_distance", "Render distance", display, GameOptions.Option.RENDER_DISTANCE, 2, GameOptions.Option.RENDER_DISTANCE.max.toInt(), 1) { "$it chunks" }
        choice("smooth_lighting", "Smooth lighting", quality, GameOptions.Option.AMBIENT_OCCLUSION, listOf("Off", "Minimum", "Maximum")) { native.ambientOcclusion }
        slider("max_framerate", "Max framerate", display, GameOptions.Option.FRAMERATE_LIMIT, 10, 260, 10) { if (it >= 260) "Unlimited" else "$it FPS" }
        toggle("anaglyph", "3D anaglyph", VideoPage.OTHER, GameOptions.Option.ANAGLYPH) { native.anaglyph }
        toggle("view_bobbing", "View bobbing", VideoPage.ANIMATIONS, GameOptions.Option.VIEW_BOBBING) { native.viewBobbing }
        val guiScale = Binding({ native.guiScale }) { native.guiScale = (it as Number).toInt() }
        bindings += guiScale
        val scales = listOf("Auto", "Small", "Medium", "Large").mapIndexed { index, name -> VideoChoice(index, name) }.toMutableList()
        if (native.guiScale !in 0..3) scales += VideoChoice(native.guiScale, "${native.guiScale}x")
        add(VideoSetting("gui_scale", "GUI scale", "", display, "", VideoControl.CHOICE, scales,
            read = { guiScale.saved }, write = { guiScale.pending = it }, format = { value -> scales.first { it.value == value }.label }))
        slider("brightness", "Brightness", quality, GameOptions.Option.GAMMA, 0, 100, 1, 100f) { "$it%" }
        choice("clouds", "Clouds", details, GameOptions.Option.RENDER_CLOUDS, listOf("Off", "Fast", "Fancy")) { native.cloudRenderMode }
        choice("particles", "Particles", details, GameOptions.Option.PARTICLES, listOf("All", "Decreased", "Minimal")) { native.particles }
        toggle("fullscreen", "Fullscreen", display, GameOptions.Option.USE_FULLSCREEN) { native.fullscreen }
        toggle("vsync", "VSync", display, GameOptions.Option.ENABLE_VSYNC) { native.vsync }
        toggle("vbo", "Vertex buffer objects", VideoPage.PERFORMANCE, GameOptions.Option.USE_VBO) { native.useVbo }
        slider("mipmap", "Mipmap levels", quality, GameOptions.Option.MIPMAP_LEVELS, 0, 4, 1) { if (it == 0) "Off" else it.toString() }
        toggle("alternate_blocks", "Alternate block models", quality, GameOptions.Option.BLOCK_ALTERNATIVES) { native.allowBlockAlternatives }
        toggle("entity_shadows", "Entity shadows", details, GameOptions.Option.ENTITY_SHADOWS) { native.entityShadows }
        val fov = Binding({ ClientPreferences.dynamicFov }) { ClientPreferences.dynamicFov = it as Boolean; ClientPreferences.save() }
        bindings += fov
        add(VideoSetting("dynamic_fov", "Dynamic FOV", "Controls field-of-view changes while sprinting and using items.",
            VideoPage.ANIMATIONS, "", VideoControl.TOGGLE, read = { fov.saved }, write = { fov.pending = it },
            format = { if (it as Boolean) "On" else "Off" }))
    }

    override fun apply() {
        if (!changed) return
        for (binding in bindings) if (binding.saved != binding.pending) binding.write(binding.pending)
        native.save()
        bindings.forEach { it.refresh() }
        options.forEach { it.refresh() }
    }

    override fun undo() {
        bindings.forEach { it.pending = it.saved }
        options.forEach { it.refresh() }
    }

    private class Binding(val read: () -> Any, val write: (Any) -> Unit) {
        var saved = read()
        var pending = saved
        fun refresh() { saved = read(); pending = saved }
    }
}
