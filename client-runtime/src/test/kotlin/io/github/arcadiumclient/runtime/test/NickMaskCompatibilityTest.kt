// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.features.NickMaskCache
import io.github.arcadiumclient.runtime.client.features.NickNameMask
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class NickMaskCompatibilityTest {
    @Test fun `streaming matching preserves the previous formatted replacement semantics`() {
        val random = Random(70031)
        val names = listOf("BassBoost", "AaA", "You", "Q", "iI", "Long_User_17", "K", "s", "\u0130", "\u017f")
        val codes = listOf("§a", "§L", "§r", "§x", "§", "§§", "", "§b§o")
        repeat(12000) {
            val name = names.random(random)
            val nickname = listOf("Guest", "", "${name}_alias", "\n\u0000Guest§c", "Player", "測定").random(random)
            val value = buildString {
                repeat(random.nextInt(1, 9)) {
                    when (random.nextInt(5)) {
                        0 -> for (c in name) { append(if (random.nextBoolean()) c.uppercaseChar() else c.lowercaseChar()); append(codes.random(random)) }
                        1 -> append(name)
                        2 -> append(codes.random(random))
                        3 -> append("Lobby ABC aaA iiI 123_ / \u00a0測定 ")
                        else -> append("🙂 §b[VIP] Guest ")
                    }
                }
            }
            val expected = legacy(value, name, nickname)
            assertEquals(expected, NickNameMask.replace(value, name, nickname), "$name / $value")
            assertEquals(expected, NickNameMask.replaceAliased(value, name, NickNameMask.alias(nickname, name)))
            val runs = mutableListOf<String>(); var from = 0
            while (from < value.length) { val end = minOf(value.length, from + random.nextInt(1, 8)); runs += value.substring(from, end); from = end; if (random.nextInt(4) == 0) runs += "" }
            assertEquals(expected, NickNameMask.replaceRuns(runs, name, nickname).joinToString(""))
        }
    }

    @Test fun `cache changes identity and aliases immediately and preserves unmatched input identity`() {
        val cache = NickMaskCache(2)
        assertEquals("§aGuest", cache.replace("§aBassBoost", "BassBoost", "Guest"))
        assertEquals("§aNew", cache.replace("§aBassBoost", "BassBoost", "New"))
        assertEquals("§aBassBoost", cache.replace("§aBassBoost", "Other", "New"))
        assertEquals("§aYou", cache.replace("§aBassBoost", "BassBoost", "BassBoost_hidden"))
        val first = charArrayOf('a','b','c').concatToString()
        val second = charArrayOf('a','b','c').concatToString()
        assertSame(first, cache.replace(first, "BassBoost", "Guest"))
        assertSame(second, cache.replace(second, "BassBoost", "Guest"))
        assertTrue(cache.hits > 0)
        repeat(20) { cache.replace("unmatched-$it", "BassBoost", "Guest") }
        assertEquals(2, cache.size())
        val huge = "x".repeat(8192)
        assertSame(huge, cache.replace(huge, "BassBoost", "Guest"))
        assertEquals(2, cache.size())
    }

    private data class Match(val start: Int, val end: Int, val value: String)
    private fun legacy(text: String, username: String, nickname: String): String {
        if (username.isEmpty() || text.isEmpty()) return text
        val formatted = '§' in text
        val visible = if (formatted) StringBuilder(text.length) else null
        val positions = if (formatted) IntArray(text.length) else null
        if (formatted) {
            var index = 0
            while (index < text.length) {
                if (text[index] == '§' && index + 1 < text.length) index += 2
                else { positions!![visible!!.length] = index; visible.append(text[index++]) }
            }
        }
        val plain = visible?.toString() ?: text
        val alias = NickNameMask.alias(nickname, username)
        val matches = ArrayList<Match>()
        var index = plain.indexOf(username, ignoreCase = true)
        while (index >= 0) {
            val start = positions?.get(index) ?: index
            val end = (positions?.get(index + username.length - 1) ?: (index + username.length - 1)) + 1
            val restore = if (formatted) buildString {
                var cursor = start
                while (cursor < end) {
                    if (text[cursor] == '§' && cursor + 1 < end) { append(text[cursor++]); append(text[cursor++]) }
                    else cursor++
                }
            } else ""
            matches += Match(start, end, alias + restore)
            index = plain.indexOf(username, index + username.length, ignoreCase = true)
        }
        if (matches.isEmpty()) return text
        return buildString(text.length) { var at = 0; for (match in matches) { append(text, at, match.start); append(match.value); at = match.end }; append(text, at, text.length) }
    }
}
