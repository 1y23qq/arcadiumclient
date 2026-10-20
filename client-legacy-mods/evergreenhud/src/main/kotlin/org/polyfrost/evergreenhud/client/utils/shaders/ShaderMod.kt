package org.polyfrost.evergreenhud.client.utils.shaders

import net.fabricmc.loader.api.FabricLoader

sealed interface ShaderMod {
    val modIds: List<String>

    val displayName: String

    val isPresent: Boolean
        get() = modIds.any { FabricLoader.getInstance().isModLoaded(it) }

    fun shadersEnabled(): Boolean

    fun packName(): String?

    companion object {
        private val ALL: List<ShaderMod> = listOf(IrisShaderMod)

        val active: ShaderMod? by lazy { ALL.firstOrNull(ShaderMod::isPresent) }

        fun isSupported(): Boolean = active != null
    }
}
