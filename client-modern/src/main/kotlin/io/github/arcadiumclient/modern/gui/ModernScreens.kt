// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import io.github.arcadiumclient.modern.client.MenuPreferences
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.VideoSettingsScreen
import net.minecraft.client.gui.screens.packs.PackSelectionScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.GuiGraphics
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen

object ModernScreens {
    private var initialized = false
    private var nativeVideo = false

    @JvmStatic fun initialize() {
        if (initialized) return
        initialized = true
        LastServerJoined.initialize()
    }

    @JvmStatic fun replace(screen: Screen?): Screen? {
        if (!initialized || screen == null || screen is ArcadiumScreen || screen is ArcadiumPauseScreen) return screen
        return when (screen) {
            is TitleScreen -> ArcadiumMainMenu()
            is SelectWorldScreen -> ArcadiumWorldsScreen(parent(screen))
            is CreateWorldScreen -> ArcadiumCreateWorldScreen(screen)
            is JoinMultiplayerScreen -> ArcadiumServersScreen(parent(screen))
            is VideoSettingsScreen -> if (nativeVideo) screen else ArcadiumVideoSettingsScreen.create(parent(screen))
            is PackSelectionScreen -> if (isClientPacks(screen)) ArcadiumResourcePacksScreen(Minecraft.getInstance().screen) else screen
            is PauseScreen -> if (screen.showsPauseMenu()) ArcadiumPauseScreen() else screen
            else -> if (!nativeVideo && screen.javaClass.name == "net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
                ArcadiumVideoSettingsScreen.create(parent(screen)) else screen
        }
    }

    private fun isClientPacks(screen: PackSelectionScreen): Boolean {
        val directory = screen.javaClass.declaredFields.firstOrNull { it.type == java.nio.file.Path::class.java }
            ?.apply { isAccessible = true }?.get(screen) as? java.nio.file.Path ?: return false
        return directory.toAbsolutePath().normalize() == Minecraft.getInstance().resourcePackDirectory.toAbsolutePath().normalize()
    }

    private fun parent(screen: Screen): Screen? {
        var type: Class<*>? = screen.javaClass
        while (type != null && type != Screen::class.java) {
            type.declaredFields.firstOrNull { it.type == Screen::class.java }?.let {
                return runCatching { it.isAccessible = true; it.get(screen) as? Screen }.getOrNull()
            }
            type = type.superclass
        }
        return Minecraft.getInstance().screen
    }

    fun openNativeVideo(parent: Screen) {
        nativeVideo = true
        try {
            val mc = Minecraft.getInstance()
            val screen = runCatching {
                Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
                    .getMethod("createScreen", Screen::class.java).invoke(null, parent) as Screen
            }.getOrElse { VideoSettingsScreen(parent, mc, mc.options) }
            mc.setScreen(screen)
        } finally { nativeVideo = false }
    }

    @JvmStatic fun drawPanorama(graphics: GuiGraphics, width: Int, height: Int) = MenuPanoramaRenderer.draw(graphics, width, height)

    @JvmStatic fun backgroundBlur(): Int? = if (Minecraft.getInstance().screen is ComposeScreen && Minecraft.getInstance().level == null)
        (MenuPreferences.titleBlurPercent.coerceIn(0, 100) * net.minecraft.client.renderer.GameRenderer.MAX_BLUR_RADIUS / 100f).toInt() else null
}
