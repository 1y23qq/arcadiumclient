// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.capes

import io.github.arcadiumclient.runtime.client.features.CapeConfig
import io.github.arcadiumclient.runtime.client.gui.SkinChangerScreen
import io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline
import net.minecraft.client.Minecraft
import net.minecraft.resource.Identifier
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import java.util.UUID

object LocalCape {
    private var initialized = false
    private var cachedPreset: String? = null
    private var cachedTexture: Identifier? = null
    private var lastEnabled = false
    private var lastPreset: String? = null
    private var lastOwner: UUID? = null
    private var lastPreview = false

    @JvmStatic fun initialize() {
        if (initialized) return
        initialized = true
        eventHandler { _: TickEvent.End -> changed() }
    }

    @JvmStatic fun textureFor(uuid: UUID?): Identifier? {
        if (!CapeConfig.enabled) return null
        val entry = CapeCatalog.find(CapeConfig.preset) ?: return null
        val mc = Minecraft.getInstance()
        val self = mc.player?.uuid ?: mc.session.profile.id
        if (!LocalCapePolicy.applies(true, true, uuid, self, mc.screen is SkinChangerScreen)) return null
        if (cachedPreset != entry.id()) {
            cachedTexture = Identifier("arcadium", entry.texturePath())
            cachedPreset = entry.id()
        }
        return cachedTexture
    }

    @JvmStatic fun activeFor(uuid: UUID?): Boolean = textureFor(uuid) != null

    @JvmStatic fun changed() {
        val mc = Minecraft.getInstance()
        val owner = mc.player?.uuid ?: mc.session.profile.id
        val preview = mc.screen is SkinChangerScreen
        if (lastEnabled == CapeConfig.enabled && lastPreset == CapeConfig.preset && lastOwner == owner && lastPreview == preview) return
        lastEnabled = CapeConfig.enabled
        lastPreset = CapeConfig.preset
        lastOwner = owner
        lastPreview = preview
        GpuPlayerPipeline.reset()
    }

    @JvmStatic fun resourcesReloaded() {
        cachedPreset = null
        cachedTexture = null
        GpuPlayerPipeline.reset()
    }
}
