// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features

import net.minecraft.client.Minecraft
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

object NickHider {
    private class Access {
        val type = Class.forName("dev.microcontrollers.simplenickhider.config.SimpleNickHiderConfig")
        val handler = type.getField("CONFIG").get(null)
        val instance = Class.forName("dev.isxander.yacl3.config.v2.api.ConfigClassHandler").getMethod("instance")
        val enabled = type.getField("enable")
        val replacement = type.getField("replacement")
    }
    private val upstream by lazy { runCatching { Access() }.getOrNull() }
    fun initialize() {
        NickHiderConfig.preload()
        ClientTickEvents.END_CLIENT_TICK.register {
            upstream?.let { access ->
                val config = access.instance.invoke(access.handler)
                access.enabled.setBoolean(config, NickHiderConfig.enabled)
                access.replacement.set(config, NickNameMask.alias(NickHiderConfig.nickname, sourceName()))
            }
            org.polyfrost.oneconfig.internal.ui.shell.ShellState.playerName = display(sourceName())
        }
    }
    @JvmStatic fun sourceName() = Minecraft.getInstance().user.name
    @JvmStatic fun display(text: String) = if (NickHiderConfig.enabled) NickNameMask.replace(text, sourceName(), NickHiderConfig.nickname) else text
    @JvmStatic fun displayRuns(runs: List<String>) = if (NickHiderConfig.enabled) NickNameMask.replaceRuns(runs, sourceName(), NickHiderConfig.nickname) else runs
}
