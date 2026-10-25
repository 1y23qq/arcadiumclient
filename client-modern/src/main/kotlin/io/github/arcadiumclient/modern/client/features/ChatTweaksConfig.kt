// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import io.github.arcadiumclient.modern.client.chat.ChatHistory
import io.github.arcadiumclient.modern.client.chat.ChatTweaks
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.internal.emoji.EmojiText

object ChatTweaksConfig : Config("chattweaks.json", "Chat Tweaks", Category.QOL) {
    @JvmStatic @Switch(title = "Enable Emoji", description = "Render emoji shortcodes and supported Unicode emoji. Plain chat sends ASCII shortcodes for legacy servers.", subcategory = "Emoji")
    var emoji = true
    @JvmStatic @Switch(title = "Show Emoji Picker", description = "Open the picker in chat with the Emoji button or Ctrl+Space.", subcategory = "Emoji")
    var showEmojiPicker = true
    @JvmStatic @Switch(title = "Remove Blank Messages", subcategory = "Cleanup") var removeBlankMessages = false
    @JvmStatic @Slider(title = "Chat History Limit", min = 100f, max = 16384f, step = 1f, subcategory = "History")
    var increaseChatHistoryLimit = 1000
    @JvmStatic @Switch(title = "Keep Chat on Disconnect", subcategory = "History") var dontClearChatHistory = true
    @JvmStatic @Switch(title = "Save Chat History", subcategory = "History") var saveChatHistory = true
    @JvmStatic @Slider(title = "Save Interval", description = "Seconds between background history saves.", min = 5f, max = 60f, step = 1f, subcategory = "History")
    var saveChatHistoryInterval = 30
    @JvmStatic @Switch(title = "Session Markers", subcategory = "History") var sessionMarkers = true

    @JvmStatic @Switch(title = "Compact Chat", subcategory = "Compact Chat") var compactChat = true
    @JvmStatic @Switch(title = "Consecutive Messages Only", subcategory = "Compact Chat") var consecutiveCompactChat = false
    @JvmStatic @Switch(title = "Keep Screenshot Messages Separate", subcategory = "Compact Chat") var dontCompactScreenshots = true
    @JvmStatic @Dropdown(title = "Merge Limit", options = ["Time", "Lines", "Time and Lines"], subcategory = "Compact Chat")
    var compactChatLimit = 0
    @JvmStatic @Slider(title = "Merge Line Range", description = "Only merge repeats within this many visible chat lines.", min = 1f, max = 1000f, step = 1f, subcategory = "Compact Chat")
    var compactChatLines = 10
    @JvmStatic @Slider(title = "Merge Window", description = "Only merge repeats received within this many seconds.", min = 1f, max = 120f, step = 1f, subcategory = "Compact Chat")
    var compactChatTime = 60
    @JvmStatic @Dropdown(title = "Counter Format", options = ["(2)", "[2]", "{2}", "<2>", "(x2)", "[x2]", "{x2}", "<x2>", "x2", "2x"], subcategory = "Compact Chat")
    var compactChatFormat = 0
    @JvmStatic @Color(title = "Counter Color", alpha = false, subcategory = "Compact Chat")
    var compactChatColor = java.awt.Color(0xAAAAAA)
    @JvmStatic @Switch(title = "Hide Duplicate Count", description = "Merge repeated messages while leaving their counter hidden.", subcategory = "Compact Chat")
    var hideDuplicateCount = false

    @JvmStatic @Switch(title = "Chat Timestamps", subcategory = "Timestamps") var timestamps = false
    @JvmStatic @Dropdown(title = "Clock", options = ["12 Hour", "24 Hour"], subcategory = "Timestamps") var timestampsFormat = 0
    @JvmStatic @Dropdown(title = "Timestamp Display", options = ["Always", "On Hover"], subcategory = "Timestamps") var timestampsStyle = 0
    @JvmStatic @Dropdown(title = "Timestamp Brackets", options = ["[12:00]", "(12:00)", "{12:00}", "<12:00>", "12:00", "Custom"], subcategory = "Timestamps")
    var timestampsBrackets = 0
    @JvmStatic @Text(title = "Left Bracket", subcategory = "Timestamps") var timestampsLeftBracket = "["
    @JvmStatic @Text(title = "Right Bracket", subcategory = "Timestamps") var timestampsRightBracket = "]"
    @JvmStatic @Color(title = "Timestamp Color", alpha = false, subcategory = "Timestamps") var timestampsColor = java.awt.Color(0xAAAAAA)
    @JvmStatic @Switch(title = "Only Show New Times", subcategory = "Timestamps") var onlyNewTimestamps = false
    @JvmStatic @Switch(title = "Include Seconds", subcategory = "Timestamps") var secondsOnTimestamps = false

    @JvmStatic @Switch(title = "Shift-Enter Keeps Chat Open", subcategory = "Input") var shiftChat = false
    @JvmStatic @Switch(title = "Long Local Commands", description = "Allow longer client commands. Server messages keep the protocol limit.", subcategory = "Input")
    var bypassCommandLimit = true
    @JvmStatic @Switch(title = "Preview Chat Clicks", description = "Show the destination of links and commands on hover.", subcategory = "Clicks") var safeChatClicks = false
    @JvmStatic @Switch(title = "Save Clicked Commands in History", subcategory = "Clicks") var safeChatClicksHistory = false
    @JvmStatic @Switch(title = "Image Previews", description = "Preview public image links on hover. Hold Shift for a larger view.", subcategory = "Images") var imagePreview = true
    @JvmStatic @Slider(title = "Preview Width", description = "Percent of the window width.", min = 5f, max = 50f, step = 1f, subcategory = "Images") var imagePreviewWidth = 25f

    override fun initialize(byConfigManager: Boolean) {
        super.initialize(byConfigManager)
        val tree = tree ?: return
        EmojiText.setEnabled(emoji)
        tree.getProp("emoji")?.addCallback { value ->
            EmojiText.setEnabled(value == true)
            ChatTweaks.requestRefresh()
            false
        }
        listOf("removeBlankMessages", "compactChat", "consecutiveCompactChat", "dontCompactScreenshots", "compactChatTime", "compactChatLimit", "compactChatLines",
            "compactChatFormat", "hideDuplicateCount", "timestamps", "timestampsFormat", "timestampsStyle", "timestampsBrackets",
            "timestampsLeftBracket", "timestampsRightBracket", "onlyNewTimestamps", "secondsOnTimestamps").forEach { key ->
            tree.getProp(key)?.addCallback { ChatTweaks.requestRefresh(); false }
        }
        tree.getProp("increaseChatHistoryLimit")?.addCallback { value ->
            ChatFilterPolicy.setHistoryLimit((value as? Number)?.toInt() ?: increaseChatHistoryLimit)
            ChatTweaks.requestRefresh()
            false
        }
    }

    @JvmStatic fun initialize() {
        preload()
        EmojiText.setEnabled(emoji)
        ChatFilterPolicy.setHistoryLimit(increaseChatHistoryLimit)
        eventHandler { _: TickEvent.End -> ChatTweaks.tick(); ChatHistory.tick() }
    }
}
