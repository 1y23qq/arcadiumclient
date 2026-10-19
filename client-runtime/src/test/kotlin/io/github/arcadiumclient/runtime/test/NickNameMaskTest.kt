// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.features.NickNameMask

class NickNameMaskTest {
    @Test
    fun `replaces every case variant without changing unrelated names`() {
        assertEquals("<Guest> Guest defeated Other", NickNameMask.replace("<BassBoost> BASSBOOST defeated Other", "BassBoost", "Guest"))
        val unrelated = "claudemaxxing joined the game"
        assertSame(unrelated, NickNameMask.replace(unrelated, "BassBoost", "Guest"))
    }

    @Test
    fun `formatting inside a username cannot reveal it and is restored afterward`() {
        assertEquals("§bGuest§c§l won", NickNameMask.replace("§bBa§css§lBoost won", "BassBoost", "Guest"))
        assertEquals("Guest§c Guest", NickNameMask.replace("Bass§cBoost bassboost", "BassBoost", "Guest"))
    }

    @Test
    fun `names split across styled chat components are hidden before wrapping`() {
        val original = listOf("§a<Ba", "ss", "Boost>", " Bass", "Boost won")
        val masked = NickNameMask.replaceRuns(original, "BassBoost", "Guest")
        assertEquals(listOf("§a<Guest", "", ">", " Guest", " won"), masked)
        assertEquals("§a<Guest> Guest won", masked.joinToString(""))
        assertEquals("§a<BassBoost> BassBoost won", original.joinToString(""))
    }

    @Test
    fun `unmatched component runs retain their original objects`() {
        val runs = listOf("hello ", "world")
        assertSame(runs, NickNameMask.replaceRuns(runs, "BassBoost", "Guest"))
    }

    @Test
    fun `a custom nickname cannot retain the hidden username`() {
        assertEquals("You", NickNameMask.alias("hello BassBoost", "BassBoost"))
        assertEquals("Hidden", NickNameMask.alias("", "You"))
        assertFalse(NickNameMask.alias("BassBoost", "BassBoost").contains("BassBoost", ignoreCase = true))
        assertEquals("Guest", NickNameMask.alias("\nGuest\u0000", "BassBoost"))
    }

    @Test
    fun `formatted matches can cross several runs without losing surrounding text`() {
        val masked = NickNameMask.replaceRuns(listOf("pre Ba§", "cssB", "oost suffix"), "BassBoost", "Test")
        assertEquals("pre Test§c suffix", masked.joinToString(""))
    }

    @Test
    fun `adjacent repeated names and empty styled runs remain private`() {
        assertEquals("XX", NickNameMask.replace("BassBoostbassboost", "BassBoost", "X"))
        assertEquals("Guest!", NickNameMask.replaceRuns(listOf("Ba", "", "ssB", "oost!"), "BassBoost", "Guest").joinToString(""))
    }

    @Test
    fun `nested font entry points do not grow an alias containing the real name`() {
        val once = NickNameMask.replace("<ArcadiumTest>", "ArcadiumTest", "ArcadiumTest_hidden")
        assertEquals("<You>", once)
        assertEquals(once, NickNameMask.replace(once, "ArcadiumTest", "ArcadiumTest_hidden"))
    }

    @Test
    fun `safe aliases remain unchanged when render and measurement repeat replacement`() {
        val once = NickNameMask.replace("§bBassBoost: BassBoost", "BassBoost", "Guest")
        assertEquals("§bGuest: Guest", once)
        assertEquals(once, NickNameMask.replace(once, "BassBoost", "Guest"))
        assertEquals(listOf("§bGuest", ": Guest"), NickNameMask.replaceRuns(listOf("§bGuest", ": Guest"), "BassBoost", "Guest"))
    }
}
