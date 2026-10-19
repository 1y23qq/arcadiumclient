// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import io.github.arcadiumclient.runtime.client.RuntimeServices
import io.github.arcadiumclient.runtime.client.skins.SkinChanger
import io.github.arcadiumclient.runtime.client.skins.SkinLibrary
import io.github.arcadiumclient.runtime.client.skins.SkinUploadClient
import java.util.UUID

class SkinChangerScreen(private val parent: Screen?) : ComposeScreen(RenderMode.CONTINUOUS) {
    private data class Saved(val entry: SkinLibrary.Entry, val image: ImageBitmap?)
    private val targetId = Minecraft.getInstance().session.profile.id
    private val targetName = Minecraft.getInstance().session.profile.name
    private var entries by mutableStateOf<List<Saved>>(emptyList())
    private var selected by mutableStateOf<String?>(null)
    private var name by mutableStateOf("")
    private var variant by mutableStateOf("classic")
    private var busy by mutableStateOf<String?>("Loading skins...")
    private var error by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)

    init { reload() }

    private suspend fun readLibrary(): List<Saved> = withContext(Dispatchers.IO) {
        SkinChanger.library.list().map { entry ->
            Saved(entry, runCatching { menuImage(SkinChanger.library.png(entry.id)) }.getOrNull())
        }
    }

    private fun pick(value: Saved?) {
        selected = value?.entry?.id
        name = value?.entry?.name.orEmpty()
        variant = value?.entry?.variant ?: "classic"
        message = null
    }

    private fun reload() {
        RuntimeServices.SCOPE.launch {
            runCatching { readLibrary() }.onSuccess { skins ->
                Minecraft.getInstance().tell { entries = skins; pick(skins.firstOrNull()); busy = null }
            }.onFailure { failure -> Minecraft.getInstance().tell { busy = null; error = failure.message ?: "Could not load saved skins." } }
        }
    }

    private fun importSkin() {
        if (busy != null) return
        busy = "Choose a skin PNG..."
        error = null
        message = null
        RuntimeServices.SCOPE.launch {
            runCatching {
                val source = withContext(Dispatchers.IO) {
                    TinyFdApi.getInstance().openFileSelector("Choose skin", null, arrayOf("*.png"), "Minecraft skin PNG")
                } ?: return@runCatching null
                val entry = withContext(Dispatchers.IO) {
                    SkinChanger.library.add(source, source.fileName.toString().substringBeforeLast('.'), "classic")
                }
                entry to readLibrary()
            }.onSuccess { result ->
                Minecraft.getInstance().tell {
                    busy = null
                    if (result != null) {
                        entries = result.second
                        pick(entries.firstOrNull { it.entry.id == result.first.id })
                    }
                }
            }.onFailure { failure -> Minecraft.getInstance().tell { busy = null; error = failure.message ?: "Could not import the skin." } }
        }
    }

    private fun saveDetails() {
        val id = selected ?: return
        if (busy != null) return
        busy = "Saving skin..."
        val label = name
        val model = variant
        RuntimeServices.SCOPE.launch {
            runCatching {
                withContext(Dispatchers.IO) { SkinChanger.library.edit(id, label, model) }
                readLibrary()
            }.onSuccess { skins -> Minecraft.getInstance().tell { entries = skins; busy = null; message = "Saved."; error = null } }
                .onFailure { failure -> Minecraft.getInstance().tell { busy = null; error = failure.message ?: "Could not save the skin." } }
        }
    }

    private fun deleteSkin() {
        val id = selected ?: return
        if (busy != null) return
        busy = "Deleting skin..."
        RuntimeServices.SCOPE.launch {
            runCatching {
                withContext(Dispatchers.IO) { SkinChanger.library.delete(id) }
                readLibrary()
            }.onSuccess { skins -> Minecraft.getInstance().tell { entries = skins; pick(skins.firstOrNull()); busy = null; error = null } }
                .onFailure { failure -> Minecraft.getInstance().tell { busy = null; error = failure.message ?: "Could not delete the skin." } }
        }
    }

    private fun sameAccount(uuid: UUID, token: String): Boolean {
        val session = Minecraft.getInstance().session
        return session.profile.id == uuid && session.accessToken == token
    }

    private fun applySkin() {
        val id = selected ?: return
        if (busy != null) return
        val mc = Minecraft.getInstance()
        val session = mc.session
        if (session.profile.id != targetId) { error = "Your active account changed. Open Skins again."; return }
        val offline = UUID.nameUUIDFromBytes("OfflinePlayer:$targetName".toByteArray(Charsets.UTF_8))
        val token = session.accessToken
        if (targetId == null || targetId == offline || token.isBlank() || token == "0" || token == "null") {
            error = "Sign in to your Minecraft account before changing skins."
            return
        }
        if (!SkinChanger.beginUpload()) { error = "A skin change is already in progress."; return }
        val model = variant
        busy = "Applying skin to $targetName..."
        error = null
        message = null
        RuntimeServices.SCOPE.launch {
            try { runCatching {
                val png = withContext(Dispatchers.IO) {
                    val bytes = SkinChanger.library.png(id)
                    SkinUploadClient().upload(token, targetId, bytes, model) { sameAccount(targetId, token) }
                    bytes
                }
                mc.tell {
                    busy = null
                    if (!sameAccount(targetId, token)) {
                        message = "Skin updated for $targetName. Your active account changed, so its local preview was kept."
                    } else {
                        val refreshed = runCatching { SkinChanger.refresh(targetId, png, model) }.isSuccess
                        message = if (refreshed) "Skin updated for $targetName. Rejoin if other players still see your previous skin."
                            else "Skin updated for $targetName. Rejoin to refresh the preview."
                    }
                }
            }.onFailure { failure -> mc.tell { busy = null; error = failure.message ?: "Could not reach Minecraft. Try again." } }
            } finally { SkinChanger.finishUpload() }
        }
    }

    private fun close() { client.tell { client.openScreen(parent) } }

    @Composable
    override fun compose() {
        Theme {
            ArcadiumMenuWindow("Skins", error, ::close, maxWidth = 880.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    UiText(targetName, 14.sp, Modifier.weight(1f))
                    UiButton("Import PNG", enabled = busy == null, onClick = ::importSkin)
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (entries.isEmpty() && busy == null) UiText("Import a Minecraft skin PNG to add it to your saved skins.", 13.sp,
                            color = UiTextSecondary, maxLines = 3)
                        entries.forEach { saved ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SkinFrontPreview(saved.image, saved.entry.variant, Modifier.size(52.dp, 84.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    UiButton(saved.entry.name, filled = saved.entry.id == selected, enabled = busy == null,
                                        modifier = Modifier.fillMaxWidth()) { pick(saved) }
                                    UiText(if (saved.entry.variant == "slim") "Slim" else "Classic", 11.sp, color = UiTextSecondary)
                                }
                            }
                        }
                    }
                    val saved = entries.firstOrNull { it.entry.id == selected }
                    Column(Modifier.width(310.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkinFrontPreview(saved?.image, variant, Modifier.fillMaxWidth().weight(1f))
                        UiTextField(name, { name = it }, Modifier.fillMaxWidth(), "Skin name", maxLength = 40)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UiButton("Classic", filled = variant == "classic", enabled = saved != null && busy == null, modifier = Modifier.weight(1f)) { variant = "classic" }
                            UiButton("Slim", filled = variant == "slim", enabled = saved != null && saved.entry.height == 64 && busy == null,
                                modifier = Modifier.weight(1f)) { variant = "slim" }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UiButton("Save", enabled = saved != null && busy == null && name.isNotBlank(), modifier = Modifier.weight(1f), onClick = ::saveDetails)
                            UiButton("Delete", enabled = saved != null && busy == null, danger = true, modifier = Modifier.weight(1f), onClick = ::deleteSkin)
                        }
                        UiButton("Apply to $targetName", filled = true, enabled = saved != null && busy == null,
                            modifier = Modifier.fillMaxWidth(), onClick = ::applySkin)
                    }
                }
                busy?.let { UiText(it, 12.sp, color = UiTextSecondary) }
                message?.let { UiText(it, 12.sp, color = UiTextSecondary, maxLines = 3) }
                UiButton("Done", modifier = Modifier.fillMaxWidth(), onClick = ::close)
            }
        }
    }
}

@Composable
private fun SkinFrontPreview(image: ImageBitmap?, variant: String, modifier: Modifier) {
    Canvas(modifier.background(UiControlBackground).border(1.dp, UiBorderColor)) {
        if (image == null) return@Canvas
        val unit = minOf(size.width / 24f, size.height / 36f)
        val left = size.width / 2 - unit * 4
        val top = size.height / 2 - unit * 16
        fun part(sx: Int, sy: Int, width: Int, height: Int, dx: Float, dy: Float) {
            drawImage(image, srcOffset = IntOffset(sx, sy), srcSize = IntSize(width, height),
                dstOffset = IntOffset((left + dx * unit).toInt(), (top + dy * unit).toInt()),
                dstSize = IntSize((width * unit).toInt().coerceAtLeast(1), (height * unit).toInt().coerceAtLeast(1)), filterQuality = FilterQuality.None)
        }
        val arm = if (variant == "slim") 3 else 4
        part(8, 8, 8, 8, 0f, 0f)
        part(20, 20, 8, 12, 0f, 8f)
        part(44, 20, arm, 12, -arm.toFloat(), 8f)
        part(if (image.height == 64) 36 else 44, if (image.height == 64) 52 else 20, arm, 12, 8f, 8f)
        part(4, 20, 4, 12, 0f, 20f)
        part(if (image.height == 64) 20 else 4, if (image.height == 64) 52 else 20, 4, 12, 4f, 20f)
        part(40, 8, 8, 8, 0f, 0f)
        if (image.height == 64) {
            part(20, 36, 8, 12, 0f, 8f)
            part(44, 36, arm, 12, -arm.toFloat(), 8f)
            part(52, 52, arm, 12, 8f, 8f)
            part(4, 36, 4, 12, 0f, 20f)
            part(4, 52, 4, 12, 4f, 20f)
        }
    }
}
