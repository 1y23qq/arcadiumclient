// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import net.minecraft.text.ClickEvent
import net.minecraft.text.LiteralText
import net.minecraft.text.Style
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.features.LegacyNickHiderText

class LegacyNickHiderTextTest {
    @Test
    fun `masking copies styled chat and leaves the original identity intact`() {
        val action = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg BassBoost ")
        val nameStyle = Style().setBold(true).setClickEvent(action)
        val original = LiteralText("<").append(LiteralText("Bass").setStyle(nameStyle))
            .append(LiteralText("Boost"))
            .append(LiteralText("> hi"))
        val masked = LegacyNickHiderText.display(original, "BassBoost", "Guest")
        assertEquals("<Guest> hi", masked.string)
        assertEquals("<BassBoost> hi", original.string)
        val name = masked.first { it.content == "Guest" }
        assertEquals(true, name.style.isBold)
        assertEquals(action, name.style.clickEvent)
        assertNotSame(nameStyle, name.style)
        assertEquals("/msg BassBoost ", name.style.clickEvent.value)
    }
}
