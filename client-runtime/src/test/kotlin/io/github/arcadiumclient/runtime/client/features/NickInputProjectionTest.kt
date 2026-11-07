// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.polyfrost.oneconfig.internal.emoji.EmojiText

class NickInputProjectionTest {
    private fun rule(source: String, target: String, case: Boolean = true) = NickReplacementRule(source = source, replacement = target, enabled = true, caseSensitive = case)
    private fun policy(vararg rules: NickReplacementRule, revision: Long = 1) = NickRenderPolicy(revision, true, "ActualUser", "You", rules.toList())
    private class Metrics(var advance: Int = 5) : NickInputLayout.Metrics {
        override fun width(text: String): Int {
            var i = 0; var width = 0
            while (i < text.length) {
                if (text[i] == '§' && i + 1 < text.length) i += 2
                else { width += if (EmojiText.isGlyph(text.codePointAt(i))) 9 else advance; i += Character.charCount(text.codePointAt(i)) }
            }
            return width
        }
        override fun trim(text: String, width: Int, reverse: Boolean): String {
            if (!reverse) {
                var at = 0; var result = 0
                while (at < text.length) {
                    val end = if (text[at] == '§' && at + 1 < text.length) at + 2 else at + Character.charCount(text.codePointAt(at))
                    if (this.width(text.substring(0, end)) > width) break
                    result = end; at = end
                }
                return text.substring(0, result)
            }
            var start = text.length
            while (start > 0) {
                val previous = text.offsetByCodePoints(start, -1)
                if (this.width(text.substring(previous)) > width) break
                start = previous
            }
            return text.substring(start)
        }
        override fun formatting(prefix: String): String {
            val at = prefix.lastIndexOf('§')
            return if (at >= 0 && at + 1 < prefix.length) prefix.substring(at, at + 2) else ""
        }
    }
    @Test fun `screenshot command uses one non cascading projection with raw source indices`() {
        val raw = "/msg Lyhap Euphoria12 hi"
        val p = policy(rule("Lyhap", "Euphoria12"), rule("Euphoria12", "User"))
        val projection = p.project(raw)
        assertEquals("/msg Euphoria12 User hi", projection.display)
        assertEquals(raw, projection.source)
        assertEquals(projection.display.length, projection.displayIndex(raw.length))
        assertEquals(raw.length, projection.sourceIndex(projection.display.length, true))
        var previous = 0
        for (i in 0..raw.length) { val next = projection.displayIndex(i); assertTrue(next >= previous); previous = next }
        val layout = NickInputLayout(); val font = Metrics()
        layout.update(projection, 400, raw.length, raw.length, 0, font)
        assertEquals(projection.display, layout.window())
        assertEquals(projection.display.length, layout.windowIndex(raw.length))
        assertEquals(raw.length, layout.sourceIndex(projection.display.length))
        assertEquals(raw, projection.source)
    }
    @Test fun `long aliases scroll visually even when only one raw character exists`() {
        val target = "LongAlias_".repeat(10)
        val projection = policy(rule("X", target)).project("X")
        val layout = NickInputLayout(); val font = Metrics()
        layout.update(projection, 30, 1, 1, 0, font)
        assertTrue(layout.displayFirst() > 1)
        assertTrue(font.width(layout.window()) <= 30)
        assertEquals(layout.window().length, layout.windowIndex(1))
        assertEquals(1, layout.sourceIndex(layout.window().length))
        layout.update(projection, 30, 0, 0, layout.sourceFirst(), font)
        assertEquals(0, layout.displayFirst()); assertTrue(layout.window().startsWith("LongAlias_"))
        font.advance = 12
        layout.update(projection, 30, 1, 1, layout.sourceFirst(), font)
        assertTrue(font.width(layout.window()) <= 30)
    }
    @Test fun `short and deleted replacements retain deterministic mouse and selection boundaries`() {
        for (target in listOf("Q", "", "several_more_characters")) {
            val raw = "pre PlayerName suffix"
            val projection = policy(rule("PlayerName", target)).project(raw)
            val font = Metrics(); val layout = NickInputLayout()
            for (cursor in 0..raw.length) {
                layout.update(projection, 45, cursor, cursor, 0, font)
                val caret = layout.windowIndex(cursor)
                assertTrue(caret in 0..layout.window().length)
                assertTrue(NickTextProjection.boundary(layout.window(), caret))
                val hit = layout.sourceIndex(caret)
                assertTrue(hit in 0..raw.length)
                assertTrue(NickTextProjection.boundary(raw, hit))
            }
        }
    }
    @Test fun `font trimming returns source slices and never partial replacement source text`() {
        val font = Metrics()
        for (target in listOf("A_very_long_alias", "Q", "")) {
            val raw = "pre PlayerName suffix"; val p = policy(rule("PlayerName", target)); val projection = p.project(raw)
            for (width in 0..font.width(projection.display) + 10) for (reverse in listOf(false, true)) {
                val visible = font.trim(projection.display, width, reverse)
                val trimmed = projection.sourceTrim(visible, reverse)
                assertTrue(if (reverse) raw.endsWith(trimmed) else raw.startsWith(trimmed))
                assertTrue(trimmed.length <= raw.length)
                assertTrue(font.width(p.replace(trimmed)) <= width, "$target/$width/$reverse: $trimmed")
                assertFalse(trimmed.contains("Play") && !trimmed.contains("PlayerName"))
            }
        }
    }
    @Test fun `literal overlap order case and expansion fallback keep projection consistent`() {
        val p = policy(rule("A", "B"), rule("B", "C"), rule("Player", "short"), rule("PlayerName", "winner"), rule("X", "first"), rule("X", "second"), rule("CASE", "case", false))
        val raw = "A B PlayerName X case"
        assertEquals("B C winner first case", p.project(raw).display)
        assertEquals(p.replace(raw), p.project(raw).display)
        val enormous = "X".repeat(500) + "ActualUser"; val fallback = policy(rule("X", "y".repeat(256)))
        assertEquals(fallback.replace(enormous), fallback.project(enormous).display)
        assertTrue(fallback.project(enormous).display.endsWith("You"))
    }
    @Test fun `format surrogate combining and emoji boundaries are not split`() {
        val raw = "§aA🙂e\u0301👩‍💻Z"
        val projection = policy(rule("A", "Long🙂"), rule("🙂", "猫"), rule("\uD83D", "broken")).project(raw)
        assertFalse(projection.display.contains("broken"))
        for (i in 0..raw.length) assertTrue(NickTextProjection.boundary(projection.display, projection.displayIndex(i)))
        for (i in 0..projection.display.length) assertTrue(NickTextProjection.boundary(raw, projection.sourceIndex(i, false)))
        assertEquals("You\u0301", policy().project("ActualUser\u0301").display)
        assertFalse(NickNameMask.alias("a".repeat(63) + "🙂", "ActualUser").last().isHighSurrogate())
        val old = EmojiText.enabled(); EmojiText.setEnabled(true)
        try {
            val text = ":smile: ActualUser 🙂"
            val nick = policy().project(text)
            val combined = nick.then(NickTextFieldProjection.emojiProjection(nick.display))
            assertEquals(EmojiText.display("\uD83D\uDE04 You 🙂"), combined.display)
            for (i in 0..text.length) assertTrue(NickTextProjection.boundary(combined.display, combined.displayIndex(i)))
        } finally { EmojiText.setEnabled(old) }
    }
    @Test fun `password masking composes before nick projection while raw command remains unchanged`() {
        val raw = "/login ActualUser🙂 password"
        val masked = PasswordMask.display(raw, "login")
        val p = policy(rule("login", "authenticate"))
        val projection = p.project(masked).withSource(raw)
        assertEquals(raw, projection.source)
        assertTrue(projection.display.startsWith("/authenticate "))
        assertFalse(projection.display.contains("ActualUser")); assertFalse(projection.display.contains("password"))
        assertEquals(projection.display.length, projection.displayIndex(raw.length))
    }
    @Test fun `a clipped suffix inherits formatting without rebuilding unchanged styling`() {
        val raw = "§aHello PlayerName suffix"
        val projection = policy(rule("PlayerName", "LongAlias")).project(raw)
        val layout = NickInputLayout(); val font = Metrics()
        layout.update(projection, 30, 4, 4, 0, font)
        val visible = font.trim(layout.window(), 30, false)
        val suffix = visible.substring(layout.windowIndex(4))
        val styled = layout.styledSuffix(suffix)
        assertTrue(styled.startsWith("§a")); assertTrue(styled.endsWith(suffix))
        assertSame(styled, layout.styledSuffix(String(suffix.toCharArray())))
    }
    @Test fun `projection reuse is bounded revision aware and distinguishes raw input provenance`() {
        val cache = NickMaskCache(4); val p = policy(rule("A", "B"), rule("B", "C"))
        val first = cache.project("A", p, true)
        repeat(100) { assertSame(first, cache.project(String(charArrayOf('A')), p, true)) }
        val displayed = cache.replace("A", p, true)
        assertEquals("C", cache.project(displayed, p, true).display)
        assertEquals("B", cache.project(displayed, p, false).display)
        assertEquals("new", cache.project("A", policy(rule("A", "new"), revision = 2), true).display)
        repeat(100) { cache.project("A $it", p, true) }
        assertTrue(cache.projectionSize() <= 4); assertTrue(cache.projectionCharacters() <= 262144)
        cache.disable(); assertEquals(0, cache.projectionSize()); assertEquals(0, cache.projectionCharacters())
    }
}
