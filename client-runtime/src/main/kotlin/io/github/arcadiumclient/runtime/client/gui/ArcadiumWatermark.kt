// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiElement
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen
import net.minecraft.client.gui.screen.menu.GameMenuScreen
import net.minecraft.client.render.Window
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.texture.DynamicTexture
import net.minecraft.resource.Identifier
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import io.github.arcadiumclient.runtime.client.features.GuiScaling
import io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess
import io.github.arcadiumclient.runtime.mixin.client.access.InventoryMenuScreenAccessor
import io.github.arcadiumclient.runtime.client.gui.WatermarkPlacement.Rect
import javax.imageio.ImageIO
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

object ArcadiumWatermark {
    private const val WIDTH = 107
    private const val HEIGHT = 22
    private const val PADDING = 10
    private val identifier = Identifier("arcadium", "ui/wordmark")
    private val color = BufferUtils.createFloatBuffer(4)
    private var initialized = false
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var cacheLoads = 0
    private var renderedScreen = WeakReference<Screen>(null)
    private val lastBounds = IntArray(4)
    private val layoutViewport = IntArray(5)
    private var layoutObstacles: List<Rect> = emptyList()
    private var layoutBounds = Rect(0, 0, 0, 0)

    @JvmStatic
    fun pauseTop(screen: Screen): Int {
        val firstButton = (screen as ScreenAccess).`arcadium$buttons`().minOfOrNull { it.y }
            ?: (screen.height / 4 + 8)
        return (firstButton - HEIGHT - 10).coerceAtLeast(4)
    }

    @JvmStatic
    fun pauseTitleY(screen: Screen): Int = minOf(40, (pauseTop(screen) - 14).coerceAtLeast(4))

    @JvmStatic
    fun render(screen: Screen) {
        val pause = screen is GameMenuScreen
        if (!pause && !GuiScaling.isContainer(screen)) return
        val minecraft = Minecraft.getInstance()
        if (minecraft.width <= 0 || minecraft.height <= 0) return
        val window = Window(minecraft)
        val scale = window.scale
        val bounds = if (pause) Rect((minecraft.width - WIDTH * scale) / 2,
            (pauseTop(screen).toDouble() * minecraft.height / screen.height).roundToInt(), WIDTH * scale, HEIGHT * scale)
            else containerBounds(screen, minecraft.width, minecraft.height, WIDTH * scale, HEIGHT * scale, PADDING * scale,
                window)
        val x = bounds.x()
        val y = bounds.y()
        val width = bounds.width()
        val height = bounds.height()
        val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        GlStateManager.activeTexture(GL13.GL_TEXTURE0)
        val binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val lighting = GL11.glIsEnabled(GL11.GL_LIGHTING)
        val sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        val matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE)
        color.clear()
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color)
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.ortho(0.0, minecraft.width.toDouble(), minecraft.height.toDouble(), 0.0, 1000.0, 3000.0)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.translatef(0f, 0f, -2000f)
        try {
            prepareTexture(minecraft)
            GlStateManager.enableTexture()
            GlStateManager.enableBlend()
            GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GlStateManager.disableAlphaTest()
            GlStateManager.disableDepthTest()
            GlStateManager.disableLighting()
            GlStateManager.color4f(1f, 1f, 1f, 0.94f)
            minecraft.textureManager.bind(identifier)
            GuiElement.drawTexture(x, y, 0f, 0f, sourceWidth, sourceHeight, width, height, sourceWidth.toFloat(), sourceHeight.toFloat())
            if (renderedScreen.get() !== screen) renderedScreen = WeakReference(screen)
            lastBounds[0] = x
            lastBounds[1] = y
            lastBounds[2] = width
            lastBounds[3] = height
        } finally {
            GlStateManager.bindTexture(binding)
            if (!texture) GlStateManager.disableTexture()
            GlStateManager.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
            if (!blend) GlStateManager.disableBlend()
            if (alpha) GlStateManager.enableAlphaTest()
            if (depth) GlStateManager.enableDepthTest()
            if (lighting) GlStateManager.enableLighting()
            GlStateManager.color4f(color[0], color[1], color[2], color[3])
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.popMatrix()
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.popMatrix()
            GlStateManager.matrixMode(matrix)
            GlStateManager.activeTexture(activeTexture)
        }
    }

    private fun containerBounds(screen: Screen, width: Int, height: Int, imageWidth: Int, imageHeight: Int, padding: Int, window: Window): Rect {
        val logical = ArcadiumWatermarkOverlays.regions(screen)
        if (logical.isNotEmpty()) {
            val menu = screen as? InventoryMenuScreenAccessor
            if (menu != null) {
                val tabs = if (screen is CreativeInventoryScreen) 28 else 0
                logical.add(Rect(menu.`arcadium$left`(), menu.`arcadium$top`() - tabs,
                    menu.`arcadium$width`(), menu.`arcadium$height`() + tabs * 2))
            }
        }
        val obstacles = if (screen.width > 0 && screen.height > 0) logical.mapTo(ArrayList<Rect>()) {
            WatermarkPlacement.pixels(it, screen.width, screen.height, width, height)
        } else ArrayList<Rect>()
        if (logical.isNotEmpty()) {
            val hotbarScale = GuiScaling.hotbarRatio(window) * window.scale
            val barWidth = (182 * hotbarScale).roundToInt()
            val barHeight = (22 * hotbarScale).roundToInt()
            obstacles.add(Rect((width - barWidth) / 2, height - barHeight, barWidth, barHeight))
        }
        if (layoutViewport[0] != width || layoutViewport[1] != height || layoutViewport[2] != imageWidth ||
            layoutViewport[3] != imageHeight || layoutViewport[4] != padding || layoutObstacles != obstacles) {
            layoutBounds = WatermarkPlacement.choose(width, height, imageWidth, imageHeight, padding, obstacles)
            layoutViewport[0] = width
            layoutViewport[1] = height
            layoutViewport[2] = imageWidth
            layoutViewport[3] = imageHeight
            layoutViewport[4] = padding
            layoutObstacles = obstacles
        }
        return layoutBounds
    }

    private fun prepareTexture(minecraft: Minecraft) {
        if (initialized) return
        val image = ArcadiumWatermark::class.java.getResourceAsStream("/assets/arcadium/brand/arcadium-watermark.png")
            ?.use(ImageIO::read) ?: error("Arcadium watermark is missing")
        sourceWidth = image.width
        sourceHeight = image.height
        minecraft.textureManager.register(identifier, DynamicTexture(image))
        minecraft.textureManager.bind(identifier)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        initialized = true
        cacheLoads++
    }

    @JvmStatic
    fun visibleOn(screen: Screen): Boolean = renderedScreen.get() === screen && initialized

    @JvmStatic
    fun bounds(): IntArray = lastBounds.clone()

    @JvmStatic
    fun textureLoads(): Int = cacheLoads
}
