// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object GuiScaleConfig : Config("arcadium-gui-scale.json", "GUI Scale", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled", description = "Use separate sizes for the vanilla HUD and inventories.")
    var enabled = false

    @JvmStatic @Dropdown(title = "Hotbar Scale", options = ["Small", "Medium", "Large", "Auto"])
    var hotbarScale = 1

    @JvmStatic @Slider(title = "Hotbar Adjustment", min = -0.5f, max = 0.5f, step = 0.01f)
    var hotbarAdjustment = 0f

    @JvmStatic @Dropdown(title = "Vanilla Crosshair Scale", options = ["Small", "Medium", "Large", "Auto"])
    var crosshairScale = 1

    @JvmStatic @Slider(title = "Vanilla Crosshair Adjustment", min = -0.5f, max = 0.5f, step = 0.01f)
    var crosshairAdjustment = 0f

    @JvmStatic @Dropdown(title = "Inventory Scale", options = ["Small", "Medium", "Large", "Auto"])
    var inventoryScale = 1

    @JvmStatic @Slider(title = "Inventory Adjustment", min = -0.5f, max = 0.5f, step = 0.01f)
    var inventoryAdjustment = 0f

    @JvmStatic @Dropdown(title = "Misc. Inventory Scale", options = ["Small", "Medium", "Large", "Auto"])
    var miscInventoryScale = 1

    @JvmStatic @Slider(title = "Misc. Inventory Adjustment", min = -0.5f, max = 0.5f, step = 0.01f)
    var miscInventoryAdjustment = 0f

    @JvmStatic @Dropdown(title = "Chest Scale", options = ["Small", "Medium", "Large", "Auto"])
    var chestScale = 1

    @JvmStatic @Slider(title = "Chest Adjustment", min = -0.5f, max = 0.5f, step = 0.01f)
    var chestAdjustment = 0f

    @JvmStatic @Dropdown(title = "Crafting Table Scale", options = ["Small", "Medium", "Large", "Auto"])
    var craftingTableScale = 1

    @JvmStatic @Slider(title = "Crafting Table Adjustment", min = -0.5f, max = 0.5f, step = 0.01f)
    var craftingTableAdjustment = 0f

    @JvmStatic @Dropdown(title = "Misc. Container Scale", options = ["Small", "Medium", "Large", "Auto"])
    var miscContainerScale = 1

    @JvmStatic @Slider(title = "Misc. Container Adjustment", min = -0.5f, max = 0.5f, step = 0.01f)
    var miscContainerAdjustment = 0f
}
