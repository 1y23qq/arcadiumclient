// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import io.github.arcadiumclient.shared.debug.DebugLayout
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.*
import io.github.arcadiumclient.runtime.client.gui.ArcadiumCustomF3Screen

object CustomF3Config : Config("arcadium-custom-f3.json", "Custom F3", Category.QOL) {
    @JvmStatic @Switch(title = "Enabled") var enabled = false
    @JvmStatic @Switch(title = "Auto Start", category = "General") var autoStart = false
    @JvmStatic @Switch(title = "Optimize Debug Text", category = "General") var optimizeDebugText = true
    @JvmStatic @Switch(title = "Space Modules", category = "General") var spaceModules = false
    @JvmStatic @Switch(title = "Shadow Text", category = "General") var shadowText = true
    @JvmStatic @Switch(title = "Enable Animations", category = "General") var animations = true
    @JvmStatic @Slider(title = "Animation Speed", min = 0.1f, max = 5f, category = "General") var animationSpeed = 1f
    @JvmStatic @Slider(title = "Font Scale", min = 0.25f, max = 4f, category = "General") var fontScale = 1f
    @JvmStatic @Color(title = "Background Color", alpha = true, category = "General") var backgroundColor = java.awt.Color(0x6F505050, true)
    @JvmStatic @Switch(title = "Hide Debug Crosshair", category = "General") var hideDebugCrosshair = false
    @JvmStatic @Switch(title = "Hide Sidebar", category = "General") var hideSidebar = true
    @JvmStatic @Switch(title = "Hide Bossbar", category = "General") var hideBossbar = true
    @JvmStatic @Switch(title = "Always Enable the Profiler", category = "Graphs") var alwaysProfiler = false
    @JvmStatic @Switch(title = "Always Enable the FPS / TPS Graph", category = "Graphs") var alwaysTps = false
    @JvmStatic @Switch(title = "Always Enable the Ping Graph", category = "Graphs") var alwaysPing = false
    @JvmStatic @Switch(title = "Override Specification Text", category = "Spec Spoof") var spoofSpecs = false
    @JvmStatic @Text(title = "CPU Name", description = "Leave empty to display the actual value.", category = "Spec Spoof") var cpu = ""
    @JvmStatic @Text(title = "GPU Name", category = "Spec Spoof") var gpu = ""
    @JvmStatic @Text(title = "GPU Vendor", category = "Spec Spoof") var gpuVendor = ""
    @JvmStatic @Text(title = "GPU Driver", category = "Spec Spoof") var gpuDriver = ""
    @JvmStatic @Text(title = "Graphics API", category = "Spec Spoof") var graphicsApi = ""
    @JvmStatic @Text(title = "Java Version", category = "Spec Spoof") var javaVersion = ""
    @JvmStatic @Text(title = "Operating System", category = "Spec Spoof") var operatingSystem = ""
    @JvmStatic @Include var layoutJson = ""
    private var cachedText: String? = null
    private var cachedLayout: DebugLayout? = null
    var layoutError: String? = null
        private set
    @JvmStatic var preview = false

    fun initialize() {
        preload()
        org.polyfrost.oneconfig.api.event.v1.eventHandler { _: org.polyfrost.oneconfig.api.event.v1.events.TickEvent.End ->
            io.github.arcadiumclient.runtime.client.debug.CustomF3Renderer.cleanup()
        }
    }
    @JvmStatic fun layout(): DebugLayout {
        if (cachedText != layoutJson || cachedLayout == null) {
            cachedLayout = runCatching { DebugLayout.decode(layoutJson) }.getOrElse {
                layoutError = "Could not read the debug layout. The saved layout has been kept unchanged."
                DebugLayout.defaults()
            }
            cachedText = layoutJson
        }
        return cachedLayout!!
    }
    fun storeLayout(layout: DebugLayout) {
        layoutJson = layout.encode()
        cachedText = layoutJson
        cachedLayout = layout
        layoutError = null
        save()
    }
    @JvmStatic fun overrideText(key: String, actual: String): String {
        if (!spoofSpecs) return actual
        val replacement = when (key) {
            "cpu" -> cpu; "gpu" -> gpu; "vendor" -> gpuVendor; "gpu_driver" -> gpuDriver
            "opengl_version" -> graphicsApi; "java_version" -> javaVersion; "os" -> operatingSystem
            else -> ""
        }
        return replacement.take(256).replace("\n", " ").replace("\r", " ").ifBlank { actual }
    }
    @Button(title = "Debug Modules", text = "Edit modules")
    fun edit() {
        val mc = Minecraft.getInstance()
        mc.tell { mc.openScreen(ArcadiumCustomF3Screen(mc.screen)) }
    }
}
