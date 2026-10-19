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

object SkinChanger {
    val library = SkinLibrary(FabricLoader.getInstance().configDir.resolve("arcadium").resolve("skins"))
    private var texture: Identifier? = null
    private var account: UUID? = null
    private var slim = false
    private val uploading = AtomicBoolean(false)

    fun beginUpload(): Boolean = uploading.compareAndSet(false, true)
    fun finishUpload() { uploading.set(false) }

    @JvmStatic fun textureFor(uuid: UUID?): Identifier? {
        val selected = texture ?: return null
        if (uuid == null || uuid != account) return null
        val active = Minecraft.getInstance().session.profile.id
        return selected.takeIf { active == account }
    }

    @JvmStatic fun modelFor(uuid: UUID?): String? = if (textureFor(uuid) == null) null else if (slim) "slim" else "default"

    fun refresh(uuid: UUID, png: ByteArray, variant: String) {
        val mc = Minecraft.getInstance()
        if (mc.session.profile.id != uuid) return
        val image = SkinImageProcessor().process(ImageIO.read(ByteArrayInputStream(png)))
        val next = Identifier("arcadium", "skins/" + UUID.randomUUID().toString())
        val uploaded = UploadedSkinTexture(image)
        check(mc.textureManager.register(next, uploaded)) { "Could not refresh the local skin texture." }
        val previous = texture
        texture = next
        account = uuid
        slim = variant == "slim"
        if (previous != null) {
            SkinLayersBridge.discardTexture(previous)
            mc.textureManager.close(previous)
        }
    }
}
