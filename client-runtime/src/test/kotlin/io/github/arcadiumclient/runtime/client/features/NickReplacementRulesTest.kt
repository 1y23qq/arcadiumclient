// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import net.minecraft.text.ClickEvent
import net.minecraft.text.LiteralText
import net.minecraft.text.Style

class NickReplacementRulesTest {
    private fun rule(source: String, target: String, enabled: Boolean = true, case: Boolean = true) = NickReplacementRule(source = source, replacement = target, enabled = enabled, caseSensitive = case)
    private fun policy(vararg rules: NickReplacementRule, revision: Long = 1) = NickRenderPolicy(revision, true, "ActualUser", "You", rules.toList())
    @Test fun `replacements are literal longest first and never cascade`() {
        val p = policy(rule("A", "B"), rule("B", "C"), rule("Player", "Short"), rule("PlayerName", "Player2"), rule("a.*", "literal"))
        assertEquals("B C Player2 literal", p.replace("A B PlayerName a.*"))
        assertEquals("AA", policy(rule("A", "AA")).replace("A"))
        assertEquals("first", policy(rule("X", "first"), rule("X", "second")).replace("X"))
    }
    @Test fun `empty disabled and format-only sources cannot match zero characters`() {
        val p = policy(rule("", "spam"), rule("§c", "spam"), rule("X", "changed", enabled = false))
        val input = "X untouched"
        assertSame(input, p.replace(input))
        assertEquals("A", policy(rule("a", "changed")).replace("a".uppercase()))
    }
    @Test fun `case mode Unicode and deletion remain literal`() {
        assertEquals("abc", policy(rule("ABC", "x")).replace("abc"))
        assertEquals("x", policy(rule("ABC", "x", case = false)).replace("abc"))
        assertEquals("猫", policy(rule("🙂", "猫")).replace("🙂"))
        assertEquals("ac", policy(rule("b", "")).replace("abc"))
    }
    @Test fun `an explicit own-name rule wins an equal-length fallback`() {
        assertEquals("Player2", policy(rule("ActualUser", "Player2")).replace("ActualUser"))
        assertEquals("You", policy().replace("ActualUser"))
    }
    @Test fun `formatting and styled split matches preserve following codes`() {
        val p = policy(rule("PlayerName", "Player2"))
        assertEquals("§aPlayer2§b!", p.replace("§aPlayer§bName!"))
        val runs = p.replaceRuns(listOf("<Pla", "yer", "Name", ">"))
        assertEquals(listOf("<Player2", "", "", ">"), runs)
        assertEquals("<You>", policy().replaceRuns(listOf("<Actual", "User>")).joinToString(""))
    }
    @Test fun `identity provenance avoids nested render cascading without suppressing raw input`() {
        val p = policy(rule("A", "B"), rule("B", "C"))
        val cache = NickMaskCache(8)
        val rendered = cache.replace("A", p, true)
        assertEquals("B", rendered)
        assertSame(rendered, cache.replace(rendered, p, true))
        val rawB = String(charArrayOf('B'))
        assertEquals("C", cache.replace(rawB, p, true))
        assertTrue(cache.enter())
        try { assertFalse(cache.enter()); cache.exit() } finally { cache.exit() }
        assertTrue(cache.enter()); cache.exit()
    }
    @Test fun `rule and username revisions invalidate results and identity provenance`() {
        val cache = NickMaskCache(4)
        val old = cache.replace("A", policy(rule("A", "B")), true)
        assertEquals("new", cache.replace("A", policy(rule("A", "new"), revision = 2), true))
        assertEquals("C", cache.replace(old, policy(rule("B", "C"), revision = 3), true))
        cache.replace("Raw", NickRenderPolicy(4, true, "Raw", "Alias", emptyList()), true)
        assertEquals("Raw", cache.replace("Raw", NickRenderPolicy(5, true, "Other", "Alias", emptyList()), true))
        cache.disable(); assertEquals(0, cache.renderedSize()); assertEquals(0, cache.size())
    }
    @Test fun `render provenance and stored results stay bounded`() {
        val cache = NickMaskCache(4)
        val p = policy(rule("A", "B"))
        repeat(100) { cache.replace("A $it", p, true) }
        assertTrue(cache.size() <= 4); assertTrue(cache.renderedSize() <= 4); assertTrue(cache.renderedCharacters() <= 262144)
    }
    @Test fun `rules round trip and expansion fallback keeps own name hidden`() {
        val rules = listOf(rule("PlayerName", "Player2"))
        assertEquals(rules, NickReplacementRules.decode(NickReplacementRules.encode(rules)))
        val input = "X".repeat(500) + "ActualUser"
        assertEquals("X".repeat(500) + "You", policy(rule("X", "y".repeat(256))).replace(input))
        assertThrows(IllegalArgumentException::class.java) { NickReplacementRules.checked(List(129) { rule("x", "y") }) }
    }
    @Test fun `literal rules span styles without editing click payloads or cascading`() {
        val action = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg PlayerName ")
        val style = Style().setBold(true).setClickEvent(action)
        val original = LiteralText("<").append(LiteralText("Player").setStyle(style)).append(LiteralText("Name> B"))
        val rules = listOf(rule("PlayerName", "B"), rule("B", "C"))
        val masked = LegacyNickHiderText.display(original, "Self", "You", rules)
        assertEquals("<B> C", masked.string)
        assertEquals("<PlayerName> B", original.string)
        val replaced = masked.first { it.content == "B" }
        assertEquals(true, replaced.style.isBold)
        assertEquals("/msg PlayerName ", replaced.style.clickEvent.value)
    }
}
