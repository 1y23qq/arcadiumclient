// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import io.github.arcadiumclient.runtime.client.utils.GameThread

object NickHider {
    private var lastUsername = ""
    private var lastNickname = ""
    private var lastEnabled = false

    fun initialize() {
        NickHiderConfig.preload()
        eventHandler { _: TickEvent.End ->
            val name = sourceName()
            val nickname = NickHiderConfig.nickname
            val enabled = NickHiderConfig.enabled
            val displayName = display(name)
            if (org.polyfrost.oneconfig.internal.ui.shell.ShellState.playerName != displayName) {
                org.polyfrost.oneconfig.internal.ui.shell.ShellState.playerName = displayName
            }
            if (name != lastUsername || nickname != lastNickname || enabled != lastEnabled) {
                lastUsername = name
                lastNickname = nickname
                lastEnabled = enabled
                Minecraft.getInstance().gui?.chat?.reset()
            }
        }
    }

    @JvmStatic
    fun sourceName(): String =
        Minecraft.getInstance().session.username

    @JvmStatic
    fun display(text: String): String {
        if (!NickHiderConfig.enabled) return text
        return NickNameMask.replace(text, sourceName(), NickHiderConfig.nickname)
    }

    @JvmStatic
    fun displayRuns(runs: List<String>): List<String> {
        if (!NickHiderConfig.enabled) return runs
        return NickNameMask.replaceRuns(runs, sourceName(), NickHiderConfig.nickname)
    }
}
