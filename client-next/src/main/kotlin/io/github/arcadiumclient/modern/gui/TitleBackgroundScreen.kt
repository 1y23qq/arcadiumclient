// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arcadiumclient.modern.client.MenuPreferences
import io.github.arcadiumclient.modern.client.background.PanoramaCatalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.resources.Identifier
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.components.settings.SliderControl
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import kotlin.math.roundToInt

class TitleBackgroundScreen(private val parent: Screen?) : ArcadiumScreen() {
    private var selected by mutableStateOf(PanoramaCatalog.find(MenuPreferences.titlePanorama))
    private var blur by mutableIntStateOf(MenuPreferences.titleBlurPercent.coerceIn(0, 100))
    private var gradient by mutableStateOf(MenuPreferences.titleGradient)
    private var useResourcePack by mutableStateOf(MenuPreferences.titleUseResourcePackPanorama)
    private var error by mutableStateOf<String?>(null)

    override val panoramaBlurRadius: Float get() = PanoramaCatalog.blurRadius(blur)
    override val shadesPanorama: Boolean get() = gradient

    private fun updateBlur(value: Int) {
        blur = value.coerceIn(0, 100)
        MenuPreferences.titleBlurPercent = blur
    }

    private fun choose(entry: PanoramaCatalog.Entry) {
        selected = entry
        MenuPreferences.titlePanorama = entry.id()
    }

    override fun onClose() = close()

    private fun close() {
        try {
            MenuPreferences.save()
            client.gui.setScreen(parent)
        } catch (failure: Exception) {
            error = "Could not save background settings."
            org.apache.logging.log4j.LogManager.getLogger("Arcadium").warn(error, failure)
        }
    }

    override fun removed() {
        runCatching { MenuPreferences.save() }.onFailure {
            org.apache.logging.log4j.LogManager.getLogger("Arcadium").warn("Could not save background settings", it)
        }
        super.removed()
    }

    @Composable
    override fun compose() {
        Theme {
            ArcadiumMenuWindow("Background", error, ::close, maxWidth = 1020.dp, maxHeight = 760.dp, dimBackground = false) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val compact = maxHeight < 470.dp
                    val narrow = maxWidth < 640.dp
                    val columns = if (maxWidth >= 800.dp) 4 else if (maxWidth >= 540.dp) 3 else 2
                    val previewHeight = if (compact) 180.dp else 236.dp
                    val scroll = rememberScrollState()
                    Column(Modifier.fillMaxSize().then(if (narrow) Modifier.verticalScroll(scroll) else Modifier), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (narrow) {
                            Preview(Modifier.fillMaxWidth().height(if (compact) 105.dp else 180.dp))
                            Controls(Modifier.fillMaxWidth(), compact = true)
                        } else {
                            Row(Modifier.fillMaxWidth().height(previewHeight), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Preview(Modifier.weight(1f).fillMaxHeight())
                                Controls(Modifier.width(238.dp).fillMaxHeight(), compact)
                            }
                        }
                        UiText("Panorama", 14.sp, fontWeight = FontWeight.Medium)
                        val grid = rememberLazyGridState()
                        Box(Modifier.then(if (narrow) Modifier.height(260.dp) else Modifier.weight(1f)).fillMaxWidth()) {
                          LazyVerticalGrid(GridCells.Fixed(columns),
                            Modifier.fillMaxSize().padding(end = 12.dp), state = grid, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(PanoramaCatalog.ENTRIES, key = { it.id() }) { entry ->
                                val active = selected == entry
                                Column(Modifier.fillMaxWidth().clip(UiFieldShape).background(UiControlBackground)
                                    .border(if (active) 2.dp else 1.dp, if (active) Accent else UiBorderColor, UiFieldShape)
                                    .semantics { contentDescription = "Panorama ${entry.name()}"; this.selected = active }
                                    .clickable(role = Role.RadioButton) { choose(entry) }) {
                                    PanoramaImage(entry, Modifier.fillMaxWidth().then(if (compact) Modifier.height(64.dp) else Modifier.aspectRatio(16f / 9f)))
                                    UiText(entry.name(), 12.sp, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), maxLines = 1)
                                }
                            }
                          }
                          VerticalScrollbar(rememberScrollbarAdapter(grid), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    }
                }
                UiButton("Done", Modifier.fillMaxWidth(), filled = true, onClick = ::close)
            }
        }
    }

    @Composable
    private fun Controls(modifier: Modifier, compact: Boolean) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UiText("Blur strength", modifier = Modifier.weight(1f))
                UiText("$blur%", color = UiTextSecondary)
            }
            SliderControl(blur.toFloat(), { updateBlur(it.roundToInt()) }, 0f, 100f, 1f,
                Modifier.fillMaxWidth().semantics {
                    contentDescription = "Blur strength"
                    progressBarRangeInfo = ProgressBarRangeInfo(blur.toFloat(), 0f..100f, 99)
                    setProgress { updateBlur(it.roundToInt()); true }
                }, thumbSize = 14.dp, trackHeight = 4.dp)
            UiToggle("Gradient overlay", gradient, Modifier.fillMaxWidth()) {
                gradient = !gradient
                MenuPreferences.titleGradient = gradient
            }
            UiToggle("Use resourcepack's panorama image", useResourcePack, Modifier.fillMaxWidth()) {
                useResourcePack = !useResourcePack
                MenuPreferences.titleUseResourcePackPanorama = useResourcePack
            }
            if (!compact) Spacer(Modifier.weight(1f))
            UiButton("Reset to default", Modifier.fillMaxWidth()) {
                choose(PanoramaCatalog.find(PanoramaCatalog.DEFAULT))
                updateBlur(40)
                gradient = true
                MenuPreferences.titleGradient = true
                useResourcePack = false
                MenuPreferences.titleUseResourcePackPanorama = false
            }
        }
    }

    @Composable
    private fun Preview(modifier: Modifier) {
        val radius = with(LocalDensity.current) { PanoramaCatalog.blurRadius(blur).toDp() }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth().clip(UiFieldShape).border(1.dp, UiBorderColor, UiFieldShape)
                .semantics { contentDescription = "Background preview ${this@TitleBackgroundScreen.selected.name()}" }) {
                Box(Modifier.fillMaxSize().blur(radius)) {
                    PanoramaImage(selected, Modifier.fillMaxSize(), useResourcePack)
                    if (gradient) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .5f), Color.White.copy(alpha = 0f)))))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .5f)))))
                    }
                }
            }
            UiText(if (useResourcePack) "Resource pack / ${selected.name()}" else selected.name(), 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PanoramaImage(entry: PanoramaCatalog.Entry, modifier: Modifier, useResourcePack: Boolean = false) {
    val image by produceState<ImageBitmap?>(null, entry.id(), useResourcePack) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = if (useResourcePack && MenuPanoramaRenderer.hasResourcePackPanorama()) {
                    val resource = Minecraft.getInstance().resourceManager.getResourceOrThrow(Identifier.parse(PanoramaCatalog.resourcePackFace(0)))
                    resource.open().use { it.readBytes() }
                } else {
                    PanoramaCatalog::class.java.getResourceAsStream(entry.preview())!!.use { it.readBytes() }
                }
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.background(UiControlBackground), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
    }
}
