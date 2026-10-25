// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.skins

import com.mojang.blaze3d.platform.NativeImage
import io.github.arcadiumclient.modern.client.RuntimeServices
import io.github.arcadiumclient.modern.mixin.SkinDecoderAccess
import io.github.arcadiumclient.modern.client.features.AnimationsConfig
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO

object SkinChanger {
    val library = SkinLibrary(FabricLoader.getInstance().configDir.resolve("arcadium/skins"))
    private var texture: Identifier? = null
    private var owner: UUID? = null
    private var slim = false
    private var cape: Identifier? = null
    private var capeOwner: UUID? = null
    private var capeOverride = false
    private var lastUser: Any? = null
    private var revision = 0L
    private val uploading = AtomicBoolean(false)

    @JvmStatic fun isOffline(): Boolean {
        val user = Minecraft.getInstance().user
        return user.accessToken.isBlank() || user.accessToken == "0" ||
            user.profileId == UUID.nameUUIDFromBytes("OfflinePlayer:${user.name}".toByteArray(Charsets.UTF_8))
    }

    fun initialize() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (lastUser !== mc.user) {
                lastUser = mc.user
                clearSkinPreview()
                capeOwner?.let(::clearCapePreview)
                val user = mc.user
                val expected = revision
                if (isOffline()) RuntimeServices.SCOPE.launch {
                    val selected = runCatching { library.localSelection(user.profileId)?.let { it to library.png(it.id) } }.getOrNull()
                    mc.execute {
                        if (mc.user === user && revision == expected && selected != null)
                            runCatching { refresh(user.profileId, selected.second, selected.first.variant) }
                    }
                }
            }
        }
    }

    fun beginUpload() = uploading.compareAndSet(false, true)
    fun finishUpload() { uploading.set(false) }

    fun clearSkinPreview() {
        revision++
        texture?.let { Minecraft.getInstance().textureManager.release(it) }
        texture = null
        owner = null
    }

    @JvmStatic fun textureFor(uuid: UUID?): Identifier? = texture?.takeIf {
        uuid == owner && Minecraft.getInstance().user.profileId == owner
    }
    @JvmStatic fun modelFor(uuid: UUID?): String? = textureFor(uuid)?.let { if (slim) "slim" else "default" }
    @JvmStatic fun hasCapeOverride(uuid: UUID?) = capeOverride && uuid == capeOwner && Minecraft.getInstance().user.profileId == capeOwner
    @JvmStatic fun capeTextureFor(uuid: UUID?): Identifier? = if (hasCapeOverride(uuid)) cape else null

    @JvmStatic fun apply(uuid: UUID?, original: PlayerSkin): PlayerSkin {
        val skin = textureFor(uuid)
        val wide = AnimationsConfig.badlion()?.noSmallArms() == true
        if (skin == null && !hasCapeOverride(uuid) && !wide) return original
        return PlayerSkin(skin?.let(::asset) ?: original.body(),
            if (hasCapeOverride(uuid)) cape?.let(::asset) else original.cape(), original.elytra(),
            if (wide) PlayerModelType.WIDE else if (skin != null) { if (slim) PlayerModelType.SLIM else PlayerModelType.WIDE } else original.model(), original.secure())
    }

    private fun asset(id: Identifier) = object : ClientAsset.Texture {
        override fun id() = id
        override fun texturePath() = id
    }

    fun refresh(uuid: UUID, png: ByteArray, variant: String) {
        val mc = Minecraft.getInstance()
        if (mc.user.profileId != uuid) return
        val canonical = SkinLibrary.canonicalize(png)
        val next = Identifier.fromNamespaceAndPath("arcadium", "skins/${UUID.randomUUID()}")
        val native = SkinDecoderAccess.`arcadium$decode`(NativeImage.read(ByteArrayInputStream(canonical.png())), "Arcadium skin")
        mc.textureManager.register(next, DynamicTexture({ "Arcadium skin" }, native))
        val previous = texture
        texture = next
        owner = uuid
        slim = variant == "slim"
        revision++
        previous?.let(mc.textureManager::release)
    }

    fun refreshCape(uuid: UUID, png: ByteArray?) {
        val mc = Minecraft.getInstance()
        if (mc.user.profileId != uuid) return
        val next = png?.let {
            val output = ByteArrayOutputStream()
            ImageIO.write(CapeClient.decodeTexture(it), "png", output)
            val id = Identifier.fromNamespaceAndPath("arcadium", "capes/${UUID.randomUUID()}")
            mc.textureManager.register(id, DynamicTexture({ "Arcadium cape" }, NativeImage.read(output.toByteArray())))
            id
        }
        val previous = cape
        cape = next
        capeOwner = uuid
        capeOverride = true
        previous?.let(mc.textureManager::release)
    }

    fun clearCapePreview(uuid: UUID) {
        if (capeOwner != uuid) return
        cape?.let { Minecraft.getInstance().textureManager.release(it) }
        cape = null
        capeOwner = null
        capeOverride = false
    }
}
