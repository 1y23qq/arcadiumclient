// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.OptionInstance
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.VideoSettingsScreen

internal enum class VideoControl { TOGGLE, CHOICE, SLIDER, ACTION }

internal data class VideoChoice(val value: Any, val label: String)

internal class VideoSetting(
    val key: String,
    val label: String,
    val description: String,
    val page: VideoPage,
    val group: String,
    val control: VideoControl,
    val choices: List<VideoChoice> = emptyList(),
    val minimum: Int = 0,
    val maximum: Int = 1,
    val interval: Int = 1,
    val requiresRestart: Boolean = false,
    val action: (() -> Unit)? = null,
    private val read: () -> Any,
    private val write: (Any) -> Unit,
    private val availability: () -> Boolean = { true },
    private val format: (Any) -> String = { it.toString() },
) {
    private var saved by mutableStateOf(read())
    var value by mutableStateOf(saved)
        private set
    val changed get() = value != saved
    val available get() = availability()
    val displayValue get() = format(value)

    fun change(next: Any) {
        if (!available) return
        if (control == VideoControl.CHOICE && choices.none { it.value == next }) return
        if (control == VideoControl.TOGGLE && next !is Boolean) return
        val normalized = if (control == VideoControl.SLIDER) {
            val number = (next as? Number)?.toDouble() ?: return
            if (!number.isFinite()) return
            val bounded = number.coerceIn(minimum.toDouble(), maximum.toDouble())
            (minimum + ((bounded - minimum) / interval).roundToInt() * interval)
                .coerceIn(minimum, maximum)
        } else next
        write(normalized)
        value = normalized
    }

    fun refresh() {
        saved = read()
        value = saved
    }
}

internal interface VideoSettingsModel {
    val delegate: Screen
    val parent: Screen?
    val options: List<VideoSetting>
    val changed get() = options.any { it.changed }
    val restartPending get() = options.any { it.changed && it.requiresRestart }
    fun apply()
    fun undo()
    fun refreshFromNative()
}

internal class ModernVideoSettingsModel(override val parent: Screen?) : VideoSettingsModel {
    private val minecraft = Minecraft.getInstance()
    private val native = minecraft.options
    override val delegate = VideoSettingsScreen(parent ?: ArcadiumMainMenu(), minecraft, native)
    override val restartPending get() = native.isRestartRequiredToApplyVideoSettings || options.any { it.changed && it.requiresRestart }
    private val bindings = mutableListOf<Binding>()

    override val options: List<VideoSetting> = buildList {
        fun toggle(key: String, label: String, page: VideoPage, option: OptionInstance<Boolean>) {
            val binding = Binding({ option.get() }) { option.set(it as Boolean) }
            bindings += binding
            add(VideoSetting(key, label, "", page, "", VideoControl.TOGGLE,
                read = { binding.saved }, write = { binding.pending = it }, format = { if (it as Boolean) "On" else "Off" }))
        }
        fun <T : Any> choice(key: String, label: String, page: VideoPage, option: OptionInstance<T>, values: List<T>, requiresRestart: Boolean = false, format: (T) -> String) {
            val binding = Binding({ option.get() }) { @Suppress("UNCHECKED_CAST") option.set(it as T) }
            bindings += binding
            val choices = values.map { VideoChoice(it, format(it)) }
            add(VideoSetting(key, label, "", page, "", VideoControl.CHOICE, choices, requiresRestart = requiresRestart,
                read = { binding.saved }, write = { binding.pending = it }, format = { value -> choices.firstOrNull { it.value == value }?.label ?: value.toString() }))
        }
        fun integer(key: String, label: String, page: VideoPage, option: OptionInstance<Int>, min: Int, max: Int, step: Int = 1, format: (Int) -> String = { it.toString() }) {
            val binding = Binding({ option.get() }) { option.set((it as Number).toInt()) }
            bindings += binding
            add(VideoSetting(key, label, "", page, "", VideoControl.SLIDER, minimum = min, maximum = max, interval = step,
                read = { binding.saved }, write = { binding.pending = it }, format = { format((it as Number).toInt()) }))
        }
        fun percent(key: String, label: String, page: VideoPage, option: OptionInstance<Double>, max: Int = 100) {
            val binding = Binding({ (option.get() * 100).roundToInt() }) { option.set((it as Number).toDouble() / 100) }
            bindings += binding
            add(VideoSetting(key, label, "", page, "", VideoControl.SLIDER, minimum = 0, maximum = max,
                read = { binding.saved }, write = { binding.pending = it }, format = { "${(it as Number).toInt()}%" }))
        }
        val quality = VideoPage.QUALITY
        val display = VideoPage.DISPLAY
        val details = VideoPage.DETAILS
        val animation = VideoPage.ANIMATIONS
        choice("graphics", "Graphics", quality, native.graphicsPreset(), net.minecraft.client.GraphicsPreset.entries) { it.toString().lowercase().replaceFirstChar(Char::uppercase) }
        choice("graphics_backend", "Graphics API", display, native.preferredGraphicsBackend(), net.minecraft.client.PreferredGraphicsApi.entries, requiresRestart = true) { it.caption().string }
        integer("render_distance", "Render distance", display, native.renderDistance(), 2, 32) { "$it chunks" }
        integer("simulation_distance", "Simulation distance", display, native.simulationDistance(), 5, 32) { "$it chunks" }
        toggle("smooth_lighting", "Smooth lighting", quality, native.ambientOcclusion())
        integer("max_framerate", "Max framerate", display, native.framerateLimit(), 10, 260, 10) { if (it >= 260) "Unlimited" else "$it FPS" }
        val maximumGuiScale = minecraft.window.calculateScale(0, minecraft.isEnforceUnicode).coerceAtLeast(1)
        choice("gui_scale", "GUI scale", display, native.guiScale(), (0..maximumGuiScale).toList()) { if (it == 0) "Auto" else "${it}x" }
        percent("brightness", "Brightness", quality, native.gamma())
        toggle("entity_shadows", "Entity shadows", details, native.entityShadows())
        toggle("view_bobbing", "View bobbing", animation, native.bobView())
        percent("dynamic_fov", "Dynamic FOV", animation, native.fovEffectScale())
        percent("entity_distance", "Entity distance", details, native.entityDistanceScaling(), 500)
        choice("clouds", "Clouds", details, native.cloudStatus(), net.minecraft.client.CloudStatus.entries) { it.toString().lowercase().replaceFirstChar(Char::uppercase) }
        choice("particles", "Particles", details, native.particles(), net.minecraft.server.level.ParticleStatus.entries) { it.toString().lowercase().replaceFirstChar(Char::uppercase) }
        toggle("fullscreen", "Fullscreen", display, native.fullscreen())
        toggle("vsync", "VSync", display, native.enableVsync())
        integer("mipmap", "Mipmap levels", quality, native.mipmapLevels(), 0, 4)
        toggle("leaves", "Cutout leaves", quality, native.cutoutLeaves())
        toggle("vignette", "Vignette", quality, native.vignette())
        toggle("improved_transparency", "Improved transparency", quality, native.improvedTransparency())
        choice("texture_filtering", "Texture filtering", quality, native.textureFiltering(), net.minecraft.client.TextureFilteringMethod.entries) { it.caption().string }
        integer("anisotropy", "Anisotropic filtering", quality, native.maxAnisotropyBit(), 0, 4) { if (it == 0) "Off" else "${1 shl it}x" }
        integer("biome_blend", "Biome blend", quality, native.biomeBlendRadius(), 0, 7)
        percent("screen_effects", "Screen effects", animation, native.screenEffectScale())
        percent("darkness_effect", "Darkness effect", animation, native.darknessEffectScale())
        percent("damage_tilt", "Damage tilt", animation, native.damageTiltStrength())
        percent("glint_speed", "Glint speed", animation, native.glintSpeed())
        percent("glint_strength", "Glint strength", animation, native.glintStrength())
        toggle("lightning", "Hide lightning flashes", VideoPage.OTHER, native.hideLightningFlash())
        toggle("autosave", "Autosave indicator", VideoPage.OTHER, native.showAutosaveIndicator())
        toggle("raw_mouse", "Raw mouse input", VideoPage.OTHER, native.rawMouseInput())
        toggle("splash", "Hide splash texts", VideoPage.OTHER, native.hideSplashTexts())
    }

    override fun apply() {
        bindings.filter { it.saved != it.pending }.forEach { it.write(it.pending) }
        native.save()
        bindings.forEach { it.refresh() }
        options.forEach { it.refresh() }
    }

    override fun undo() {
        bindings.forEach { it.pending = it.saved }
        options.forEach { it.refresh() }
    }

    override fun refreshFromNative() {
        bindings.forEach { it.refresh() }
        options.forEach { it.refresh() }
    }

    private class Binding(val read: () -> Any, val write: (Any) -> Unit) {
        var saved = read()
        var pending = saved
        fun refresh() { saved = read(); pending = saved }
    }
}
