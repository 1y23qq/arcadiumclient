package org.polyfrost.evergreenhud.client.utils.shaders

import org.slf4j.LoggerFactory
import java.lang.reflect.Method

data object IrisShaderMod : ShaderMod {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Shaders")

    private val CLASS_NAMES = listOf("net.irisshaders.iris.Iris", "net.coderbot.iris.Iris")

    private const val INTERNAL_PACK = "(internal)"

    override val modIds = listOf("iris", "oculus")

    override val displayName = "Iris"

    private val irisClass: Class<*>? by lazy {
        CLASS_NAMES.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name) }.getOrNull()
        }.also {
            if (it == null) LOGGER.warn("Iris is installed but its entrypoint class could not be found")
        }
    }

    private val currentPackName: Method? by lazy { staticMethod("getCurrentPackName") }

    private val packInUse: Method? by lazy { staticMethod("isPackInUseQuick") }

    private val fallback: Method? by lazy { staticMethod("isFallback") }

    private fun staticMethod(name: String): Method? {
        val owner = irisClass ?: return null
        return runCatching { owner.getMethod(name) }.getOrElse {
            LOGGER.warn("Iris is installed but {}#{} could not be found", owner.name, name)
            null
        }
    }

    override fun shadersEnabled(): Boolean = invoke<Boolean>(packInUse) == true

    override fun packName(): String? {
        if (!shadersEnabled()) return null
        if (invoke<Boolean>(fallback) == true) return null

        val name = invoke<String>(currentPackName)?.takeIf { it.isNotBlank() } ?: return null
        return if (name == INTERNAL_PACK) null else name
    }

    private inline fun <reified T> invoke(method: Method?): T? {
        if (method == null) return null
        return runCatching { method.invoke(null) as? T }.getOrElse {
            LOGGER.warn("Failed to call Iris' {}", method.name, it)
            null
        }
    }
}
