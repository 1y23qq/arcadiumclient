// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import net.caffeinemc.mods.sodium.client.config.ConfigManager
import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption
import net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption
import net.caffeinemc.mods.sodium.client.config.structure.ExternalPage
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption
import net.caffeinemc.mods.sodium.client.config.structure.Option
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.Identifier

internal class SodiumVideoSettingsModel(private val vanilla: VideoSettingsModel) : VideoSettingsModel {
    private val native = checkNotNull(ConfigManager.CONFIG) { "Renderer settings are not initialized" }
    override val delegate: Screen get() = vanilla.delegate
    override val parent: Screen? get() = vanilla.parent
    override val restartPending get() = vanilla.restartPending || options.any { it.changed && it.requiresRestart }

    override val options: List<VideoSetting> = buildList {
        addAll(vanilla.options)
        val existing = vanilla.options.map { it.key }.toSet()
        for (mod in native.modOptions) for ((pageIndex, page) in mod.pages().withIndex()) {
            val category = VideoSettingsLayout.category(page.name().string)
            if (page is ExternalPage) {
                add(VideoSetting("renderer:${mod.configId()}:$pageIndex", page.name().string, "", VideoPage.OTHER, mod.name(), VideoControl.ACTION,
                    action = { page.currentScreenConsumer().accept(Minecraft.getInstance().gui.screen()) }, read = { false }, write = {}))
                continue
            }
            for (group in page.groups()) for (option in group.options()) {
                val id = optionId(option)
                val path = id.path.substringAfterLast('.')
                val key = when (path) {
                    "gamma" -> "brightness"
                    "framerate_limit" -> "max_framerate"
                    "ambient_occlusion" -> "smooth_lighting"
                    "bob_view" -> "view_bobbing"
                    "fov_effect_scale" -> "dynamic_fov"
                    "cloud_status" -> "clouds"
                    "preferred_graphics_backend" -> "graphics_backend"
                    "max_anisotropy_bit" -> "anisotropy"
                    else -> path
                }
                if (key in existing && id.namespace == "sodium") continue
                val title = option.name.string
                val description = option.tooltip?.string.orEmpty()
                val groupName = listOf(mod.name(), page.name().string, group.name()?.string.orEmpty()).filter(String::isNotBlank).joinToString(" / ")
                val actualPage = when {
                    id.path.startsWith("performance.") -> VideoPage.PERFORMANCE
                    id.path.startsWith("quality.") -> VideoPage.QUALITY
                    id.path.startsWith("advanced.") -> VideoPage.PERFORMANCE
                    else -> category
                }
                val nativeKey = "renderer:$id"
                when (option) {
                    is BooleanOption -> add(VideoSetting(nativeKey, title, description, actualPage, groupName, VideoControl.TOGGLE,
                        read = { option.validatedValue }, write = { option.modifyValue(it as Boolean) }, availability = { option.isEnabled }, format = { if (it as Boolean) "On" else "Off" }))
                    is IntegerOption -> {
                        val validator = option.steppedValidator
                        add(VideoSetting(nativeKey, title, description, actualPage, groupName, VideoControl.SLIDER,
                            minimum = validator.min(), maximum = validator.max(), interval = validator.step().coerceAtLeast(1),
                            read = { option.validatedValue }, write = { option.modifyValue((it as Number).toInt()) }, availability = { option.isEnabled },
                            format = { option.formatValue((it as Number).toInt()).string }))
                    }
                    is EnumOption<*> -> {
                        @Suppress("UNCHECKED_CAST") val stateful = option as StatefulOption<Any>
                        val allowed = option.javaClass.getMethod("isValueAllowed", Enum::class.java)
                        val values = option.enumClass.enumConstants.filter { allowed.invoke(option, it) == true }.map { value ->
                            val method = option.javaClass.getMethod("getElementName", Enum::class.java)
                            VideoChoice(value, (method.invoke(option, value) as net.minecraft.network.chat.Component).string)
                        }
                        add(VideoSetting(nativeKey, title, description, actualPage, groupName, VideoControl.CHOICE, values,
                            read = { stateful.validatedValue }, write = { stateful.modifyValue(it) }, availability = { option.isEnabled },
                            format = { value -> values.firstOrNull { it.value == value }?.label ?: value.toString() }))
                    }
                    is ExternalButtonOption -> add(VideoSetting(nativeKey, title, description, actualPage, groupName, VideoControl.ACTION,
                        action = { option.currentScreenConsumer.accept(Minecraft.getInstance().gui.screen()) },
                        read = { false }, write = {}, availability = { option.isEnabled }))
                }
            }
        }
    }

    override fun apply() {
        vanilla.apply()
        native.applyAllOptions()
        native.resetAllOptionsFromBindings()
        vanilla.apply()
        options.forEach(VideoSetting::refresh)
    }

    override fun undo() {
        native.resetAllOptionsFromBindings()
        vanilla.undo()
        options.forEach(VideoSetting::refresh)
    }

    override fun refreshFromNative() {
        native.resetAllOptionsFromBindings()
        vanilla.refreshFromNative()
        options.forEach(VideoSetting::refresh)
    }

    private fun optionId(option: Option): Identifier {
        val field = Option::class.java.getDeclaredField("id").apply { isAccessible = true }
        return field.get(option) as Identifier
    }
}
