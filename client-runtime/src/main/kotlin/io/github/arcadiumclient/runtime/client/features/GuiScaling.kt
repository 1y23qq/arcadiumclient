// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.game.inventory.ChestScreen
import net.minecraft.client.gui.screen.game.inventory.CraftingTableScreen
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen
import net.minecraft.client.gui.screen.game.inventory.HorseScreen
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen
import net.minecraft.client.render.Window
import org.lwjgl.input.Mouse
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object GuiScaling {
    fun initialize() {
        GuiScaleConfig.preload()
        eventHandler { _: TickEvent.End ->
            val minecraft = Minecraft.getInstance()
            val screen = minecraft.screen
            if (screen is InventoryMenuScreen) {
                val width = screenWidth(screen)
                val height = screenHeight(screen)
                if (screen.width != width || screen.height != height) screen.init(minecraft, width, height)
            }
        }
    }

    @JvmStatic
    fun isContainer(screen: Screen): Boolean = screen is InventoryMenuScreen

    @JvmStatic
    fun enabled(): Boolean = GuiScaleConfig.enabled

    private fun scale(choice: Int, adjustment: Float): Double {
        val minecraft = Minecraft.getInstance()
        return GuiScaleMath.vanillaScale(minecraft.width, minecraft.height, choice, minecraft.isUnicode) *
            GuiScaleMath.multiplier(adjustment)
    }

    private fun screenScale(screen: Screen): Double {
        if (!enabled()) return Window(Minecraft.getInstance()).scale.toDouble()
        return when (screen) {
            is SurvivalInventoryScreen -> scale(GuiScaleConfig.inventoryScale, GuiScaleConfig.inventoryAdjustment)
            is CreativeInventoryScreen, is HorseScreen -> scale(GuiScaleConfig.miscInventoryScale, GuiScaleConfig.miscInventoryAdjustment)
            is ChestScreen -> scale(GuiScaleConfig.chestScale, GuiScaleConfig.chestAdjustment)
            is CraftingTableScreen -> scale(GuiScaleConfig.craftingTableScale, GuiScaleConfig.craftingTableAdjustment)
            else -> scale(GuiScaleConfig.miscContainerScale, GuiScaleConfig.miscContainerAdjustment)
        }
    }

    @JvmStatic
    fun screenWidth(screen: Screen): Int = GuiScaleMath.logicalSize(Minecraft.getInstance().width, screenScale(screen))

    @JvmStatic
    fun screenHeight(screen: Screen): Int = GuiScaleMath.logicalSize(Minecraft.getInstance().height, screenScale(screen))

    @JvmStatic
    fun mouseX(screen: Screen): Int = GuiScaleMath.mouseX(Mouse.getX(), Minecraft.getInstance().width, screen.width)

    @JvmStatic
    fun mouseY(screen: Screen): Int = GuiScaleMath.mouseY(Mouse.getY(), Minecraft.getInstance().height, screen.height)

    @JvmStatic
    fun hotbarRatio(window: Window): Float = if (enabled())
        (scale(GuiScaleConfig.hotbarScale, GuiScaleConfig.hotbarAdjustment) / window.scale).toFloat() else 1f

    @JvmStatic
    fun crosshairRatio(window: Window): Float = if (enabled())
        (scale(GuiScaleConfig.crosshairScale, GuiScaleConfig.crosshairAdjustment) / window.scale).toFloat() else 1f
}
