// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui.api

import org.polyfrost.oneconfig.api.config.v1.Config
import java.util.Locale

internal object ChatModuleGroup : ConfigData {
    override val id = "arcadium-chat"
    override val title = "Chat"
    override val icon = "chat"
    override val description = "Chatting, Chat Tweaks, Chat Filters and AutoText."
    override val source = ConfigSource.OC
    override val category = Config.Category.QOL

    val ids = listOf("chatting.json", "chattweaks.json", "arcadium-chat-filters.json", "arcadium-auto-text.json")
    fun contains(id: String): Boolean {
        val normalized = id.lowercase(Locale.ROOT).removeSuffix(".json")
        return ids.any { it.removeSuffix(".json") == normalized }
    }
    fun children(): List<ConfigData> = ids.mapNotNull { id ->
        ConfigRegistry.configs.firstOrNull { it.id == id || it.id == id.removeSuffix(".json") }
    }
}
