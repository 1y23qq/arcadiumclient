// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import io.github.arcadiumclient.modern.client.MenuPreferences
import io.github.arcadiumclient.modern.client.background.PanoramaCatalog
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.CubeMap
import net.minecraft.client.renderer.state.gui.PanoramaRenderState
import net.minecraft.client.renderer.texture.CubeMapTexture
import net.minecraft.client.renderer.texture.MipmapStrategy
import net.minecraft.client.renderer.texture.TextureContents
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import com.mojang.blaze3d.platform.NativeImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import io.github.arcadiumclient.modern.mixin.gui.ScreenBackgroundAccess

abstract class ArcadiumScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        if (client.level == null) MenuPanoramaRenderer.draw(graphics, width, height)
    }

    override fun isPauseScreen(): Boolean = false
}

internal object MenuPanoramaRenderer {
    private var current: String? = null
    private var cubemap: CubeMap? = null
    private var spin = 0f
    private val texture = Identifier.fromNamespaceAndPath("arcadium", "title-panorama")

    fun hasResourcePackPanorama(): Boolean = (0..5).any {
        Minecraft.getInstance().resourceManager.getResourceStack(Identifier.parse(PanoramaCatalog.resourcePackFace(it))).size > 1
    }

    fun draw(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
        val mc = Minecraft.getInstance()
        spin = (spin + mc.deltaTracker.realtimeDeltaTicks * .07f * mc.options.panoramaSpeed().get().toFloat()) % 360f
        mc.gameRenderer.gameRenderState().guiRenderState.panoramaRenderState = PanoramaRenderState(-spin)
        if (MenuPreferences.titleBlurPercent > 0) (mc.gui.screen() as? ScreenBackgroundAccess)?.`arcadium$blurBackground`(graphics)
        if (MenuPreferences.titleGradient) {
            graphics.fillGradient(0, 0, width, height, 0x70FFFFFF, 0x00FFFFFF)
            graphics.fillGradient(0, 0, width, height, 0, 0x70000000)
        }
    }

    fun render(fallback: CubeMap, pitch: Float, yaw: Float) {
        val mc = Minecraft.getInstance()
        if (mc.level != null || mc.gui.screen() !is ComposeScreen) {
            fallback.render(pitch, yaw)
            return
        }
        val entry = PanoramaCatalog.find(MenuPreferences.titlePanorama)
        val resourcePack = MenuPreferences.titleUseResourcePackPanorama && hasResourcePackPanorama()
        val key = if (resourcePack) "resourcepack" else entry.id()
        if (current != key) {
            cubemap?.close()
            mc.textureManager.registerAndLoad(texture, object : CubeMapTexture(texture) {
                override fun loadContents(manager: ResourceManager): TextureContents {
                    var result: NativeImage? = null
                    try {
                        intArrayOf(1, 3, 5, 4, 0, 2).forEachIndexed { layer, face ->
                            val bytes = if (resourcePack) {
                                manager.getResourceOrThrow(Identifier.parse(PanoramaCatalog.resourcePackFace(face))).open().use { it.readBytes() }
                            } else {
                                PanoramaCatalog::class.java.getResourceAsStream("/assets/arcadium/panoramas/${entry.id()}/$face.jpg")!!.use { it.readBytes() }
                            }
                            val png = if (resourcePack) bytes else ByteArrayOutputStream().use { output ->
                                ImageIO.write(ImageIO.read(ByteArrayInputStream(bytes)), "png", output)
                                output.toByteArray()
                            }
                            NativeImage.read(png).use { image ->
                                if (result == null) result = NativeImage(image.width, image.height * 6, false)
                                check(result!!.width == image.width && result!!.height == image.height * 6) { "Panorama faces have different sizes" }
                                image.copyRect(result!!, 0, 0, 0, layer * image.height, image.width, image.height, false, true)
                            }
                        }
                        return TextureContents(result!!, TextureMetadataSection(true, false, MipmapStrategy.MEAN, 0f))
                    } catch (failure: Throwable) {
                        result?.close()
                        throw failure
                    }
                }
            })
            cubemap = CubeMap(texture)
            current = key
        }
        cubemap?.render(pitch, yaw)
    }

    fun invalidate() { current = null }
    fun close() { cubemap?.close(); cubemap = null; current = null }
}
