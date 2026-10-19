// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.reflect.Field
import kotlin.math.roundToInt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import org.embeddedt.embeddium.impl.gui.framework.TextComponent
import io.github.arcadiumclient.runtime.client.features.FirstRunSetup
import io.github.arcadiumclient.runtime.mixin.compat.argentum.ArgentumVideoOptionsAccessor
import io.github.arcadiumclient.runtime.mixin.compat.argentum.CeleritasVideoOptionsAccessor
import org.taumc.celeritas.api.options.OptionIdentifier
import org.taumc.celeritas.api.options.control.ControlValueFormatter
import org.taumc.celeritas.api.options.control.CyclingControl
import org.taumc.celeritas.api.options.control.SliderControl
import org.taumc.celeritas.api.options.structure.Option
import org.taumc.celeritas.api.options.structure.OptionFlag
import org.taumc.celeritas.api.options.structure.StandardOptions

internal enum class VideoControl { TOGGLE, CHOICE, SLIDER }

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
    fun initialize(client: Minecraft, width: Int, height: Int) = delegate.init(client, width, height)
    fun apply()
    fun undo()
}

internal class RendererVideoSettingsModel(override val delegate: Screen) : VideoSettingsModel {
    private val access = delegate as ArgentumVideoOptionsAccessor
    private val controller = access.`arcadium$controller`() as CeleritasVideoOptionsAccessor
    override val parent: Screen? = access.`arcadium$parent`()
    private val font = controller.`arcadium$font`()
    private val extract = font.javaClass.getMethod("extractString", TextComponent::class.java).apply { isAccessible = true }
    private fun text(component: TextComponent): String = cleanVideoText(extract.invoke(font, component) as String)
    private val extras = mutableListOf<StagedExtra>()

    override val options: List<VideoSetting> by lazy {
      buildList {
        controller.`arcadium$pages`().forEach { page ->
            val category = VideoSettingsLayout.category(page.id.path)
            page.groups.forEach { group ->
                group.options.forEach { raw ->
                    @Suppress("UNCHECKED_CAST")
                    val option = raw as Option<Any>
                    val identifier = option.id
                    val key = STANDARD_KEYS.firstOrNull { identifier != null && it.first.matches(identifier) }?.second
                        ?: when (identifier?.path) {
                            "dynamic_fov", "dynamic_fov_strength" -> "dynamic_fov"
                            "dynamic_lights" -> "dynamic_lights"
                            else -> null
                        }
                        ?: identifier?.toString()
                        ?: "legacy:${System.identityHashCode(option)}"
                    val control: Any = option.control
                    val kind = when {
                        control is SliderControl -> VideoControl.SLIDER
                        control is CyclingControl<*> -> VideoControl.CHOICE
                        option.value is Boolean -> VideoControl.TOGGLE
                        else -> error("Unsupported video control: ${control.javaClass.name} (${option.id})")
                    }
                    val choices = if (control is CyclingControl<*>) {
                        val values = controlField(control, "allowedValues") as Array<*>
                        values.mapIndexed { index, value -> VideoChoice(requireNotNull(value), text(control.names[index])) }
                    } else emptyList()
                    val minimum = if (control is SliderControl) controlField(control, "min") as Int else 0
                    val maximum = if (control is SliderControl) controlField(control, "max") as Int else 1
                    val interval = if (control is SliderControl) controlField(control, "interval") as Int else 1
                    val formatter = if (control is SliderControl) controlField(control, "mode") as ControlValueFormatter else null
                    add(VideoSetting(
                        key, text(option.name), text(option.tooltip), category, group.id.path, kind,
                        choices, minimum, maximum, interval,
                        option.flags.contains(OptionFlag.REQUIRES_GAME_RESTART),
                        read = { option.value }, write = { option.value = it }, availability = { option.isAvailable },
                        format = { value ->
                            when {
                                formatter != null -> text(formatter.format((value as Number).toInt()))
                                choices.isNotEmpty() -> choices.firstOrNull { it.value == value }?.label ?: value.toString()
                                value is Boolean -> if (value) "On" else "Off"
                                else -> value.toString()
                            }
                        },
                    ))
                }
            }
        }
        if (none { it.key == "dynamic_lights" }) FirstRunSetup.currentDynamicLightsMode()?.let { initial ->
            val staged = StagedExtra(initial) { check(FirstRunSetup.applyDynamicLights(it as Int)) { "Could not save dynamic lights" } }
            extras += staged
            val choices = listOf("Off", "Fast", "Fancy").mapIndexed { index, label -> VideoChoice(index, label) }
            add(VideoSetting(
                "dynamic_lights", "Dynamic lights", "Light emitted by held items and nearby entities.",
                VideoPage.DETAILS, "lighting", VideoControl.CHOICE, choices,
                read = { staged.saved }, write = { staged.pending = it },
                format = { value -> choices.first { it.value == value }.label },
            ))
        }
      }.distinctBy { it.key }
    }

    override fun apply() {
        if (!changed) return
        controller.`arcadium$apply`()
        extras.forEach { it.commit() }
        options.forEach { it.refresh() }
    }

    override fun undo() {
        controller.`arcadium$undo`()
        extras.forEach { it.pending = it.saved }
        options.forEach { it.refresh() }
    }

    private class StagedExtra(initial: Any, private val save: (Any) -> Unit) {
        var saved = initial
        var pending = initial
        fun commit() {
            if (pending != saved) save(pending)
            saved = pending
        }
    }

    companion object {
        private val STANDARD_KEYS: List<Pair<OptionIdentifier<*>, String>> = listOf(
            StandardOptions.Option.GRAPHICS_MODE to "graphics",
            StandardOptions.Option.RENDER_DISTANCE to "render_distance",
            StandardOptions.Option.SMOOTH_LIGHT to "smooth_lighting",
            StandardOptions.Option.MAX_FRAMERATE to "max_framerate",
            StandardOptions.Option.GUI_SCALE to "gui_scale",
            StandardOptions.Option.ENTITY_SHADOWS to "entity_shadows",
            StandardOptions.Option.BRIGHTNESS to "brightness",
            StandardOptions.Option.VIEW_BOBBING to "view_bobbing",
            StandardOptions.Option.FULLSCREEN to "fullscreen",
            StandardOptions.Option.PARTICLES to "particles",
            StandardOptions.Option.CLOUDS to "clouds",
        )
        private fun controlField(control: Any, name: String): Any {
            val field: Field = control.javaClass.getDeclaredField(name)
            field.isAccessible = true
            return requireNotNull(field.get(control))
        }
    }
}

private fun cleanVideoText(value: String): String = value.replace(Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE), "").replace('\u2014', '-')
    .replace("Argentum Extras", "Arcadium").replace("Argentum", "Arcadium")
    .replace("Celeritas", "Arcadium").replace("OneConfig", "Arcadium Options")
