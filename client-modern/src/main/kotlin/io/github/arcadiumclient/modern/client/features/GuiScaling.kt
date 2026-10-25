// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.*

object GuiScaling {
    @JvmStatic fun isContainer(screen: Screen?) = screen is AbstractContainerScreen<*>
    private fun configuredScale(choice: Int, adjustment: Float): Double {
        val window = Minecraft.getInstance().window
        return GuiScaleMath.vanillaScale(window.width, window.height, choice, false) * GuiScaleMath.multiplier(adjustment)
    }
    private fun screenScale(screen: Screen?): Double = when (screen) {
        is InventoryScreen -> configuredScale(GuiScaleConfig.inventoryScale, GuiScaleConfig.inventoryAdjustment)
        is CreativeModeInventoryScreen, is HorseInventoryScreen -> configuredScale(GuiScaleConfig.miscInventoryScale, GuiScaleConfig.miscInventoryAdjustment)
        is ContainerScreen -> configuredScale(GuiScaleConfig.chestScale, GuiScaleConfig.chestAdjustment)
        is CraftingScreen -> configuredScale(GuiScaleConfig.craftingTableScale, GuiScaleConfig.craftingTableAdjustment)
        else -> configuredScale(GuiScaleConfig.miscContainerScale, GuiScaleConfig.miscContainerAdjustment)
    }
    @JvmStatic fun ratio(screen: Screen?): Float = if (GuiScaleConfig.enabled && isContainer(screen))
        (screenScale(screen) / Minecraft.getInstance().window.guiScale).toFloat() else 1f
    @JvmStatic fun width(screen: Screen?, native: Int) = if (GuiScaleConfig.enabled && isContainer(screen))
        GuiScaleMath.logicalSize(Minecraft.getInstance().window.width, screenScale(screen)) else native
    @JvmStatic fun height(screen: Screen?, native: Int) = if (GuiScaleConfig.enabled && isContainer(screen))
        GuiScaleMath.logicalSize(Minecraft.getInstance().window.height, screenScale(screen)) else native
    @JvmStatic fun hotbarRatio(): Float = if (GuiScaleConfig.enabled)
        (configuredScale(GuiScaleConfig.hotbarScale, GuiScaleConfig.hotbarAdjustment) / Minecraft.getInstance().window.guiScale).toFloat() else 1f
    @JvmStatic fun crosshairRatio(): Float = if (GuiScaleConfig.enabled)
        (configuredScale(GuiScaleConfig.crosshairScale, GuiScaleConfig.crosshairAdjustment) / Minecraft.getInstance().window.guiScale).toFloat() else 1f
}
