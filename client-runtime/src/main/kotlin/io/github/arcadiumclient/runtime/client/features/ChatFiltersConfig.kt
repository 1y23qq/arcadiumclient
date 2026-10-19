// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.TextList
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import net.minecraft.client.Minecraft

object ChatFiltersConfig : Config("arcadium-chat-filters.json", "Chat Filters", Category.QOL) {
    @JvmStatic @Switch(title = "Enabled")
    var enabled = false

    @JvmStatic @TextList(title = "Blocked Words", description = "Hide messages containing any of these words or phrases.", maxEntries = 256, addText = "Add word", regex = ".{1,128}")
    var words: List<String> = emptyList()

    @JvmStatic @Switch(title = "Case Sensitive")
    var caseSensitive = false

    @JvmStatic @Switch(title = "Whole Words Only")
    var wholeWords = false

    private var lastWords = emptyList<String>()
    private var lastCase = false
    private var lastWhole = false
    private var lastEnabled = false
    private var policy = ChatFilterPolicy(emptyList(), false, false)
    private var initialized = false
    private var refreshNeeded = false

    @JvmStatic
    fun initialize() {
        if (initialized) return
        initialized = true
        preload()
        eventHandler { _: TickEvent.End ->
            refreshPolicy()
            if (refreshNeeded) {
                refreshNeeded = false
                Minecraft.getInstance().gui?.chat?.reset()
            }
        }
    }

    @JvmStatic
    fun hide(text: String?): Boolean {
        refreshPolicy()
        return enabled && text != null && policy.hides(text)
    }

    private fun refreshPolicy() {
        if (words != lastWords || caseSensitive != lastCase || wholeWords != lastWhole) {
            lastWords = words.toList()
            lastCase = caseSensitive
            lastWhole = wholeWords
            policy = ChatFilterPolicy(lastWords, lastCase, lastWhole)
            refreshNeeded = refreshNeeded || enabled
        }
        if (enabled != lastEnabled) {
            lastEnabled = enabled
            refreshNeeded = true
        }
    }
}
