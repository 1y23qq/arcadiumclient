// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.render.texture.SkinImageProcessor
import net.minecraft.resource.Identifier
import io.github.arcadiumclient.runtime.client.features.SkinLayersBridge
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO
import io.github.arcadiumclient.runtime.client.RuntimeServices
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object SkinChanger {
    val library = SkinLibrary(FabricLoader.getInstance().configDir.resolve("arcadium").resolve("skins"))
    private var texture: Identifier? = null
    private var account: UUID? = null
    private var slim = false
    private var localOffline = false
    private var cape: Identifier? = null
    private var capeOwner: UUID? = null
    private var capeOverride = false
    private val uploading = AtomicBoolean(false)
    private var lastSession: Any? = null
    private var initialized = false
    private var skinRevision = 0L

    @JvmStatic fun isOffline(): Boolean {
        val session = Minecraft.getInstance().session
        val id = UUID.nameUUIDFromBytes("OfflinePlayer:${session.profile.name}".toByteArray(Charsets.UTF_8))
        return session.profile.id == id
    }

    @JvmStatic fun initialize() {
        if (initialized) return
        initialized = true
        eventHandler { _: TickEvent.End ->
            val mc = Minecraft.getInstance()
            val session = mc.session
            if (lastSession !== session) {
                lastSession = session
                clearSkinPreview()
                capeOwner?.let(::clearCapePreview)
                val revision = skinRevision
                if (isOffline()) session.profile.id?.let { uuid ->
                    RuntimeServices.SCOPE.launch {
                        runCatching { library.localSelection(uuid)?.let { it to library.png(it.id) } }.onSuccess { selected ->
                            mc.tell {
                                if (mc.session === session && revision == skinRevision && selected != null) runCatching {
                                    refresh(uuid, selected.second, selected.first.variant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun beginUpload(): Boolean = uploading.compareAndSet(false, true)
    fun finishUpload() { uploading.set(false) }

    fun clearSkinPreview() {
        skinRevision++
        texture?.let {
            SkinLayersBridge.discardTexture(it)
            Minecraft.getInstance().textureManager.close(it)
        }
        texture = null
        account = null
        slim = false
        localOffline = false
    }

    @JvmStatic fun textureFor(uuid: UUID?): Identifier? {
        val selected = texture ?: return null
        if (uuid == null) return null
        val mc = Minecraft.getInstance()
        if (mc.session.profile.id != account) return null
        return selected.takeIf { uuid == account || (localOffline && uuid == mc.player?.uuid) }
    }

    @JvmStatic fun modelFor(uuid: UUID?): String? = if (textureFor(uuid) == null) null else if (slim) "slim" else "default"

    @JvmStatic fun hasCapeOverride(uuid: UUID?): Boolean = capeOverride && uuid != null && uuid == capeOwner &&
        Minecraft.getInstance().session.profile.id == capeOwner

    @JvmStatic fun capeTextureFor(uuid: UUID?): Identifier? = if (hasCapeOverride(uuid)) cape else null

    fun refreshCape(uuid: UUID, png: ByteArray?) {
        val mc = Minecraft.getInstance()
        if (mc.session.profile.id != uuid) return
        val next = png?.let {
            val id = Identifier("arcadium", "capes/" + UUID.randomUUID())
            check(mc.textureManager.register(id, UploadedSkinTexture(CapeClient.decodeTexture(it)))) { "Could not refresh the local cape texture." }
            id
        }
        val previous = cape
        cape = next
        capeOwner = uuid
        capeOverride = true
        if (previous != null) mc.textureManager.close(previous)
    }

    fun clearCapePreview(uuid: UUID) {
        if (capeOwner != uuid) return
        cape?.let { Minecraft.getInstance().textureManager.close(it) }
        cape = null
        capeOwner = null
        capeOverride = false
    }

    fun refresh(uuid: UUID, png: ByteArray, variant: String) {
        val mc = Minecraft.getInstance()
        if (mc.session.profile.id != uuid) return
        val image = SkinImageProcessor().process(ImageIO.read(ByteArrayInputStream(png)))
        val next = Identifier("arcadium", "skins/" + UUID.randomUUID().toString())
        val uploaded = UploadedSkinTexture(image)
        check(mc.textureManager.register(next, uploaded)) { "Could not refresh the local skin texture." }
        skinRevision++
        val previous = texture
        texture = next
        account = uuid
        slim = variant == "slim"
        localOffline = isOffline()
        if (previous != null) {
            SkinLayersBridge.discardTexture(previous)
            mc.textureManager.close(previous)
        }
    }
}
