// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.resolver.ServerAddress
import org.polyfrost.oneconfig.api.config.v1.Config

internal object LastServerJoined {
    private val config by lazy {
        runCatching { Class.forName("org.polyfrost.redaction.client.RedactionConfig").getField("INSTANCE").get(null) as Config }.getOrNull()
    }

    fun initialize() {
        ClientPlayConnectionEvents.JOIN.register { _, _, client ->
            client.currentServer?.ip?.let { address ->
                config?.let { value ->
                    value.javaClass.getMethod("setLastServerIP", String::class.java).invoke(value, address)
                    value.save()
                }
            }
        }
    }

    fun address(): String? {
        val value = config ?: return null
        if (value.javaClass.getMethod("getModEnabled").invoke(value) != true ||
            value.javaClass.getMethod("getLastServerJoined").invoke(value) != true) return null
        return (value.javaClass.getMethod("getLastServerIP").invoke(value) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun join(parent: Screen) {
        val address = address() ?: return
        val client = Minecraft.getInstance()
        val servers = ServerList(client).apply { load() }
        val entry = (0 until servers.size()).map { servers.get(it) }.firstOrNull { it.ip.equals(address, true) }
            ?: ServerData("Last server", address, ServerData.Type.OTHER)
        ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(address), entry, false, null)
    }
}
