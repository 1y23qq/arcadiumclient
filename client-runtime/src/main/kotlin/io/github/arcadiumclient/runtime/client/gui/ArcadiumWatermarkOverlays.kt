// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import io.github.arcadiumclient.runtime.client.gui.WatermarkPlacement.Rect
import java.lang.reflect.Field

internal object ArcadiumWatermarkOverlays {
    private val loader get() = ArcadiumWatermarkOverlays::class.java.classLoader
    private val jei by lazy { optional { Jei() } }
    private val nei by lazy { optional { Nei() } }
    private var jeiFailed = false
    private var neiFailed = false

    fun regions(screen: Screen): MutableList<Rect> {
        val regions = ArrayList<Rect>()
        if (!jeiFailed) try { jei?.append(screen, regions) } catch (_: ReflectiveOperationException) { jeiFailed = true }
        if (!neiFailed) try { nei?.append(regions) } catch (_: ReflectiveOperationException) { neiFailed = true }
        return regions
    }

    private fun type(name: String) = Class.forName(name, false, loader)
    private fun field(owner: Class<*>, name: String): Field = owner.getDeclaredField(name).apply { isAccessible = true }
    private inline fun <T> optional(read: () -> T): T? = try { read() } catch (_: ReflectiveOperationException) { null } catch (_: LinkageError) { null }

    private class Jei {
        private val runtime = type("mezz.jei.Internal").getMethod("getRuntime")
        private val overlay = type("mezz.jei.JeiRuntime").getMethod("getItemListOverlay")
        private val overlayType = type("mezz.jei.gui.ItemListOverlay")
        private val open = overlayType.getMethod("isOpen")
        private val properties = field(overlayType, "guiProperties")
        private val search = field(overlayType, "searchField")
        private val propertyType = type("mezz.jei.gui.GuiProperties")
        private val guiClass = propertyType.getMethod("getGuiClass")
        private val guiLeft = propertyType.getMethod("getGuiLeft")
        private val guiWidth = propertyType.getMethod("getGuiXSize")
        private val screenWidth = propertyType.getMethod("getScreenWidth")
        private val screenHeight = propertyType.getMethod("getScreenHeight")

        fun append(screen: Screen, result: MutableList<Rect>) {
            val runtime = runtime.invoke(null) ?: return
            val overlay = overlay.invoke(runtime) ?: return
            if (!overlayType.isInstance(overlay) || open.invoke(overlay) != true) return
            val properties = properties.get(overlay) ?: return
            if (!propertyType.isInstance(properties) || (guiClass.invoke(properties) as? Class<*>)?.isInstance(screen) != true) return
            if (screenWidth.invoke(properties) != screen.width || screenHeight.invoke(properties) != screen.height) return
            val right = (guiLeft.invoke(properties) as Int) + (guiWidth.invoke(properties) as Int)
            if (right in 0 until screen.width) result.add(Rect(right, 0, screen.width - right, screen.height))
            val search = search.get(overlay) as? TextFieldWidget ?: return
            if (search.isVisible && search.y < screen.height) {
                val width = search.innerWidth + if (search.hasBorder()) 8 else 0
                result.add(Rect(search.x, search.y, width, screen.height - search.y))
            }
        }
    }

    private class Nei {
        private val config = type("codechicken.nei.NEIClientConfig")
        private val hidden = config.getMethod("isHidden")
        private val enabled = config.getMethod("isEnabled")
        private val layout = type("codechicken.nei.LayoutManager")
        private val widgets = field(layout, "drawWidgets")
        private val options = layout.getField("options")
        private val widget = type("codechicken.nei.Widget")
        private val x = widget.getField("x")
        private val y = widget.getField("y")
        private val width = widget.getField("w")
        private val height = widget.getField("h")

        fun append(result: MutableList<Rect>) {
            if (hidden.invoke(null) == true) return
            val visible = if (enabled.invoke(null) == true) (widgets.get(null) as? Iterable<*>) ?: return else listOf(options.get(null))
            for (entry in visible) {
                if (entry == null || !widget.isInstance(entry)) continue
                val w = width.getInt(entry)
                val h = height.getInt(entry)
                if (w > 0 && h > 0) result.add(Rect(x.getInt(entry), y.getInt(entry), w, h))
            }
        }
    }
}
