// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.gui.VideoChoice
import io.github.arcadiumclient.runtime.client.gui.VideoControl
import io.github.arcadiumclient.runtime.client.gui.VideoPage
import io.github.arcadiumclient.runtime.client.gui.VideoSetting

class VideoSettingTest {
    @Test
    fun `slider respects its native origin interval and limits`() {
        var pending: Any = 10
        val setting = VideoSetting("slider", "Slider", "", VideoPage.QUALITY, "test", VideoControl.SLIDER,
            minimum = 10, maximum = 40, interval = 5, read = { pending }, write = { pending = it })
        setting.change(37)
        assertEquals(35, pending)
        assertTrue(setting.changed)
        setting.change(Int.MAX_VALUE)
        assertEquals(40, pending)
        setting.change(Int.MIN_VALUE)
        assertEquals(10, pending)
        assertFalse(setting.changed)
        setting.change(Double.NaN)
        assertEquals(10, pending)
    }

    @Test
    fun `unavailable controls preserve their native state`() {
        var writes = 0
        val setting = VideoSetting("toggle", "Toggle", "", VideoPage.OTHER, "test", VideoControl.TOGGLE,
            read = { false }, write = { writes++ }, availability = { false })
        setting.change(true)
        assertEquals(0, writes)
        assertEquals(false, setting.value)
        assertFalse(setting.changed)
    }

    @Test
    fun `choices preserve the native allowed values`() {
        var pending: Any = "fast"
        val setting = VideoSetting("quality", "Quality", "", VideoPage.QUALITY, "test", VideoControl.CHOICE,
            choices = listOf(VideoChoice("fast", "Fast"), VideoChoice("fancy", "Fancy")),
            read = { pending }, write = { pending = it })
        setting.change("invalid")
        assertEquals("fast", pending)
        setting.change("fancy")
        assertEquals("fancy", pending)
        assertTrue(setting.changed)
        setting.refresh()
        assertFalse(setting.changed)
        assertEquals("fancy", setting.value)
    }

    @Test
    fun `undo refreshes from the original native option`() {
        var pending: Any = true
        val setting = VideoSetting("toggle", "Toggle", "", VideoPage.OTHER, "test", VideoControl.TOGGLE,
            read = { pending }, write = { pending = it })
        setting.change(false)
        assertTrue(setting.changed)
        pending = true
        setting.refresh()
        assertEquals(true, setting.value)
        assertFalse(setting.changed)
    }
}
