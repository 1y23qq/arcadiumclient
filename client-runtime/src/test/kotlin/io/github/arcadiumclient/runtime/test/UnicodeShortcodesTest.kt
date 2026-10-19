// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.emoji.UnicodeShortcodes

class UnicodeShortcodesTest {
    private val replacements = UnicodeShortcodes(mapOf(
        "❤" to ":heart:", "❤️" to ":red_heart:",
        "👩" to ":woman:", "👩‍💻" to ":woman_technologist:",
        "👍" to ":thumbsup:", "👍🏽" to ":thumbsup_medium_skin_tone:",
    ))

    @Test
    fun `uses the longest complete sequence and preserves neighboring text`() {
        assertEquals("a :red_heart: :woman_technologist: :thumbsup_medium_skin_tone: z",
            replacements.replace("a ❤️ 👩‍💻 👍🏽 z"))
        assertEquals(":woman:‍x", replacements.replace("👩‍x"))
    }

    @Test
    fun `leaves commands shortcodes and unrelated Unicode unchanged`() {
        val command = "/msg Player テスト :heart:"
        assertSame(command, replacements.replace(command))
        assertEquals("/msg Player :thumbsup: :heart:", replacements.replace("/msg Player 👍 :heart:"))
    }
}
