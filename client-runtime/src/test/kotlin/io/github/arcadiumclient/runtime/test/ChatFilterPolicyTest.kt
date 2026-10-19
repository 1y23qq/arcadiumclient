// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.features.ChatFilterPolicy

class ChatFilterPolicyTest {
    @Test fun matchesAcrossFormattingAndIgnoresCase() {
        val policy = ChatFilterPolicy(listOf("bad word"), false, false)
        assertTrue(policy.hides("prefix §cBAD §rWORD suffix"))
        assertFalse(policy.hides("another message"))
    }

    @Test fun wholeWordChecksUnicodeAndUnderscores() {
        val policy = ChatFilterPolicy(listOf("bad"), false, true)
        assertTrue(policy.hides("That is bad!"))
        assertFalse(policy.hides("badge"))
        assertFalse(policy.hides("bad_name"))
        assertFalse(policy.hides("bad猫"))
        assertTrue(policy.hides("bad..."))
    }

    @Test fun phrasesAreLiteralAndCaseCanBePreserved() {
        val policy = ChatFilterPolicy(listOf("[Test]"), true, false)
        assertTrue(policy.hides("[Test] text"))
        assertFalse(policy.hides("[test] text"))
        assertFalse(policy.hides("Test text"))
        assertFalse(ChatFilterPolicy(emptyList(), false, false).hides("anything"))
    }

    @Test fun matchesWordsSplitByColorCodesAndClientMarkers() {
        val policy = ChatFilterPolicy(listOf("bad"), false, false)
        assertTrue(policy.hides("b§ca§rd"))
        assertTrue(policy.hides("b\u2063a\u2064d"))
        assertFalse(ChatFilterPolicy(listOf("§a"), false, false).hides("plain message"))
        assertEquals("word§", ChatFilterPolicy.plain("word§"))
    }

    @Test fun combiningMarksRemainPartOfUnicodeWords() {
        val policy = ChatFilterPolicy(listOf("bad"), false, true)
        assertFalse(policy.hides("bad\u0301"))
        assertFalse(policy.hides("\uD801\uDC00bad"))
        assertTrue(policy.hides("(bad)"))
        assertTrue(policy.hides("bad/word"))
    }

    @Test fun settingsListIsSnapshottedAndTermsStayLiteral() {
        val words = mutableListOf("[test]", "a.b")
        val policy = ChatFilterPolicy(words, false, false)
        words.clear()
        assertTrue(policy.hides("[TEST]"))
        assertTrue(policy.hides("a.b"))
        assertFalse(policy.hides("axb"))
        assertFalse(policy.hides("test"))
        assertFalse(ChatFilterPolicy(listOf("   ", "§r"), false, false).hides("anything"))
    }

    @Test fun retainedHistoryUsesConfiguredHostLimit() {
        assertEquals(1000, ChatFilterPolicy.historyLimit(154, 1000))
        assertEquals(2000, ChatFilterPolicy.historyLimit(1000, 2000))
        assertEquals(100, ChatFilterPolicy.historyLimit(100, 100))
    }

    @Test fun unknownHostLimitPreservesExistingExtendedHistory() {
        assertEquals(154, ChatFilterPolicy.historyLimit(154, -1))
        assertEquals(100, ChatFilterPolicy.historyLimit(10, -1))
        assertEquals(154, ChatFilterPolicy.historyLimit(154, 0))
    }
}
