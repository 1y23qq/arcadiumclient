// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import io.github.arcadiumclient.modern.client.RuntimeServices
import io.github.arcadiumclient.modern.client.skins.SkinChanger
import io.github.arcadiumclient.modern.client.skins.SkinLibrary
import io.github.arcadiumclient.modern.client.skins.SkinUploadClient
import io.github.arcadiumclient.modern.client.skins.CapeClient
import java.util.UUID

class SkinChangerScreen(private val parent: Screen?) : ArcadiumScreen() {
    private data class Saved(val entry: SkinLibrary.Entry, val image: ImageBitmap?)
    private data class SavedCape(val cape: CapeClient.Cape, val png: ByteArray?, val image: ImageBitmap?)
    private val targetId = Minecraft.getInstance().user.profileId
    private val targetName = Minecraft.getInstance().user.name
    private var entries by mutableStateOf<List<Saved>>(emptyList())
    private var selected by mutableStateOf<String?>(null)
    private var name by mutableStateOf("")
    private var variant by mutableStateOf("classic")
    private var busy by mutableStateOf<String?>("Loading skins...")
    private var error by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)
    private var capeTab by mutableStateOf(false)
    private var capes by mutableStateOf<List<SavedCape>>(emptyList())
    private var capesLoaded by mutableStateOf(false)
    private var selectedCape by mutableStateOf<String?>(null)
    private var activeCape by mutableStateOf<String?>(null)

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
                Minecraft.getInstance().execute { entries = skins; pick(skins.firstOrNull()); busy = null }
            }.onFailure { failure -> Minecraft.getInstance().execute { busy = null; error = failure.message ?: "Could not load saved skins." } }
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
                Minecraft.getInstance().execute {
                    busy = null
                    if (result != null) {
                        entries = result.second
                        pick(entries.firstOrNull { it.entry.id == result.first.id })
                    }
                }
            }.onFailure { failure -> Minecraft.getInstance().execute { busy = null; error = failure.message ?: "Could not import the skin." } }
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
            }.onSuccess { skins -> Minecraft.getInstance().execute { entries = skins; busy = null; message = "Saved."; error = null } }
                .onFailure { failure -> Minecraft.getInstance().execute { busy = null; error = failure.message ?: "Could not save the skin." } }
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
            }.onSuccess { skins -> Minecraft.getInstance().execute { entries = skins; pick(skins.firstOrNull()); busy = null; error = null } }
                .onFailure { failure -> Minecraft.getInstance().execute { busy = null; error = failure.message ?: "Could not delete the skin." } }
        }
    }

    private fun sameAccount(uuid: UUID, token: String): Boolean {
        val session = Minecraft.getInstance().user
        return session.profileId == uuid && session.accessToken == token
    }

    private fun applySkin() {
        val id = selected ?: return
        if (busy != null) return
        val mc = Minecraft.getInstance()
        val session = mc.user
        if (session.profileId != targetId) { error = "Your active account changed. Open Skins again."; return }
        val offline = SkinChanger.isOffline()
        val token = session.accessToken
        if (targetId == null) {
            error = "Your active account has no player ID. Sign in again."
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
                    if (offline) {
                        check(sameAccount(targetId, token)) { "Your active account changed. Open Skins again." }
                        SkinChanger.library.selectLocal(targetId, id, model)
                    } else SkinUploadClient().upload(token, targetId, bytes, model) { sameAccount(targetId, token) }
                    bytes
                }
                mc.execute {
                    busy = null
                    if (!sameAccount(targetId, token)) {
                        message = if (offline) "Local skin saved for $targetName. Your active account changed."
                            else "Skin updated for $targetName. Your active account changed, so its local preview was kept."
                    } else {
                        val refreshed = runCatching { SkinChanger.refresh(targetId, png, model) }.isSuccess
                        message = if (offline && refreshed) "Local skin applied to $targetName. Other players see the server's skin."
                            else if (offline) "Local skin saved for $targetName. Reopen Skins to refresh the preview."
                            else if (refreshed) "Skin updated for $targetName. Rejoin if other players still see your previous skin."
                            else "Skin updated for $targetName. Rejoin to refresh the preview."
                    }
                }
            }.onFailure { failure -> mc.execute { busy = null; error = failure.message ?: "Could not reach Minecraft. Try again." } }
            } finally { SkinChanger.finishUpload() }
        }
    }

    private fun resetLocalSkin() {
        val mc = Minecraft.getInstance()
        val session = mc.user
        val uuid = targetId ?: return
        if (busy != null) return
        if (session.profileId != uuid || !SkinChanger.isOffline()) {
            error = "Your active account changed. Open Skins again."
            return
        }
        busy = "Resetting local skin..."
        error = null
        message = null
        RuntimeServices.SCOPE.launch {
            runCatching { SkinChanger.library.selectLocal(uuid, null) }.onSuccess {
                mc.execute {
                    if (mc.user === session) SkinChanger.clearSkinPreview()
                    busy = null
                    message = "Local skin reset for $targetName."
                }
            }.onFailure { failure -> mc.execute { busy = null; error = failure.message ?: "Could not reset the local skin." } }
        }
    }

    private fun close() { client.execute { client.gui.setScreen(parent) } }

    override fun onClose() = close()

    private fun capeToken(): String? {
        val session = Minecraft.getInstance().user
        if (targetId == null || session.profileId != targetId) {
            error = "Your active account changed. Open Skins again."
            return null
        }
        val offline = UUID.nameUUIDFromBytes("OfflinePlayer:$targetName".toByteArray(Charsets.UTF_8))
        if (targetId == offline || session.accessToken.isBlank() || session.accessToken == "0" || session.accessToken == "null") {
            error = "Sign in to your Minecraft account to manage your capes."
            return null
        }
        return session.accessToken
    }

    private fun loadCapes() {
        if (busy != null) return
        val token = capeToken() ?: return
        val uuid = targetId ?: return
        busy = "Loading capes..."
        error = null
        message = null
        val mc = Minecraft.getInstance()
        RuntimeServices.SCOPE.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val service = CapeClient()
                    val profile = service.load(token, uuid) { sameAccount(uuid, token) }
                    var previewsAvailable = true
                    val loaded = profile.capes().map { cape ->
                        val png = if (previewsAvailable) runCatching { service.texture(cape) }.getOrNull() else null
                        if (png == null) previewsAvailable = false
                        SavedCape(cape, png, png?.let { runCatching { menuImage(it) }.getOrNull() })
                    }
                    profile to loaded
                }
            }.onSuccess { (profile, loaded) -> mc.execute {
                busy = null
                if (!sameAccount(uuid, token)) {
                    error = "Your active account changed. Open Skins again."
                    return@execute
                }
                capes = loaded
                activeCape = profile.activeId()
                selectedCape = activeCape
                capesLoaded = true
                val active = loaded.firstOrNull { it.cape.id() == activeCape }
                runCatching {
                    if (activeCape == null || active?.png != null) SkinChanger.refreshCape(uuid, active?.png)
                    else SkinChanger.clearCapePreview(uuid)
                }.onFailure { SkinChanger.clearCapePreview(uuid) }
            } }.onFailure { failure -> mc.execute { busy = null; error = failure.message ?: "Could not load your capes. Try again." } }
        }
    }

    private fun applyCape() {
        if (busy != null || !capesLoaded) return
        val token = capeToken() ?: return
        val uuid = targetId ?: return
        val chosen = selectedCape
        if (!SkinChanger.beginUpload()) { error = "An appearance change is already in progress."; return }
        busy = "Applying cape to $targetName..."
        error = null
        message = null
        val mc = Minecraft.getInstance()
        RuntimeServices.SCOPE.launch {
            try {
                runCatching {
                    withContext(Dispatchers.IO) { CapeClient().select(token, uuid, chosen) { sameAccount(uuid, token) } }
                }.onSuccess { profile -> mc.execute {
                    busy = null
                    if (!sameAccount(uuid, token)) {
                        message = "Cape updated for $targetName. Your active account changed, so its local preview was kept."
                        return@execute
                    }
                    activeCape = profile.activeId()
                    selectedCape = activeCape
                    capes = capes.map { saved ->
                        val updated = profile.capes().firstOrNull { it.id() == saved.cape.id() } ?: saved.cape
                        saved.copy(cape = updated)
                    }
                    val png = capes.firstOrNull { it.cape.id() == chosen }?.png
                    val refreshed = runCatching {
                        if (chosen == null || png != null) { SkinChanger.refreshCape(uuid, png); true }
                        else { SkinChanger.clearCapePreview(uuid); false }
                    }.getOrElse { SkinChanger.clearCapePreview(uuid); false }
                    message = if (refreshed) "Cape updated for $targetName. Rejoin if other players still see your previous cape."
                        else "Cape updated for $targetName. Rejoin to refresh the preview."
                } }.onFailure { failure -> mc.execute { busy = null; error = failure.message ?: "Could not change your cape. Try again." } }
            } finally { SkinChanger.finishUpload() }
        }
    }

    @Composable
    private fun ColumnScope.CapeBrowser() {
        val selected = capes.firstOrNull { it.cape.id() == selectedCape }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (capesLoaded) {
                    UiButton("No cape", Modifier.fillMaxWidth(), filled = selectedCape == null, enabled = busy == null) { selectedCape = null; message = null }
                    if (capes.isEmpty()) UiText("This Minecraft account does not own any capes.", 13.sp, color = UiTextSecondary, maxLines = 3)
                } else if (busy == null && error == null) {
                    UiText("Sign in to your Minecraft account, then refresh to load your capes.", 13.sp, color = UiTextSecondary, maxLines = 3)
                }
                capes.forEach { saved ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CapePreview(saved.image, Modifier.size(52.dp, 78.dp))
                        UiButton(saved.cape.name(), Modifier.weight(1f), filled = saved.cape.id() == selectedCape, enabled = busy == null) {
                            selectedCape = saved.cape.id(); message = null
                        }
                    }
                }
            }
            Column(Modifier.width(310.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CapePreview(selected?.image, Modifier.fillMaxSize())
                    if (selected?.image == null) UiText(if (!capesLoaded) "Choose a cape" else if (selectedCape == null) "No cape" else "Preview unavailable", 14.sp)
                }
                selected?.let { UiText(it.cape.name(), 14.sp) }
                UiButton("Apply cape", Modifier.fillMaxWidth(), filled = true,
                    enabled = capesLoaded && busy == null && selectedCape != activeCape, onClick = ::applyCape)
            }
        }
    }

    @Composable
    override fun compose() {
        Theme {
            ArcadiumMenuWindow("Skins", error, ::close, maxWidth = 880.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    UiText(targetName, 14.sp, Modifier.weight(1f))
                    if (capeTab) UiButton("Refresh capes", enabled = busy == null, onClick = ::loadCapes)
                    else UiButton("Import PNG", enabled = busy == null, onClick = ::importSkin)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Skins", Modifier.weight(1f), filled = !capeTab, enabled = busy == null) { capeTab = false; error = null; message = null }
                    UiButton("Capes", Modifier.weight(1f), filled = capeTab, enabled = busy == null) {
                        capeTab = true; error = null; message = null
                        if (!capesLoaded) loadCapes()
                    }
                }
                if (capeTab) CapeBrowser() else {
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
                        if (SkinChanger.isOffline()) {
                            UiButton("Reset local skin", enabled = busy == null, modifier = Modifier.fillMaxWidth(), onClick = ::resetLocalSkin)
                        }
                    }
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
private fun CapePreview(image: ImageBitmap?, modifier: Modifier) {
    Canvas(modifier.background(UiControlBackground).border(1.dp, UiBorderColor)) {
        if (image == null) return@Canvas
        val factor = image.width / 64
        val unit = minOf(size.width / 14f, size.height / 20f)
        drawImage(image, srcOffset = IntOffset(factor, factor), srcSize = IntSize(10 * factor, 16 * factor),
            dstOffset = IntOffset(((size.width - 10 * unit) / 2).toInt(), ((size.height - 16 * unit) / 2).toInt()),
            dstSize = IntSize((10 * unit).toInt().coerceAtLeast(1), (16 * unit).toInt().coerceAtLeast(1)), filterQuality = FilterQuality.None)
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
