// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NickReplacementRule(
    val id: String = UUID.randomUUID().toString(),
    val source: String = "",
    val replacement: String = "",
    val enabled: Boolean = false,
    val caseSensitive: Boolean = true,
)

internal object NickReplacementRules {
    const val MAX_RULES = 128
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    fun decode(value: String): List<NickReplacementRule> {
        require(value.length <= 262144)
        return checked(json.decodeFromString(value))
    }
    fun encode(rules: List<NickReplacementRule>): String = json.encodeToString(checked(rules))
    fun checked(rules: List<NickReplacementRule>): List<NickReplacementRule> {
        require(rules.size <= MAX_RULES && rules.map { it.id }.distinct().size == rules.size)
        rules.forEach { require(runCatching { UUID.fromString(it.id) }.isSuccess && it.source.length <= 128 && it.replacement.length <= 256 && it.source.none(Char::isISOControl) && it.replacement.none { char -> char.isISOControl() || char == '\u00a7' }) }
        return rules.toList()
    }
    fun visible(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            if (value[i] == '\u00a7' && i + 1 < value.length) i += 2 else append(value[i++])
        }
    }
}

internal class NickRenderPolicy(val revision: Long, val enabled: Boolean, username: String, nickname: String, rules: List<NickReplacementRule>) {
    private val ownUsername = username
    private val ownNickname = nickname
    private data class Rule(val source: String, val target: String, val ignoreCase: Boolean, val order: Int)
    private data class Match(val start: Int, val end: Int, val replacement: String)
    private val active: List<Rule> = if (!enabled) emptyList() else buildList {
        if (username.isNotEmpty()) add(Rule(username, NickNameMask.alias(nickname, username), true, Int.MAX_VALUE))
        rules.forEachIndexed { index, rule ->
            val source = NickReplacementRules.visible(rule.source)
            if (rule.enabled && source.isNotEmpty()) add(Rule(source, rule.replacement, !rule.caseSensitive, index))
        }
    }.sortedWith(compareByDescending<Rule> { it.source.length }.thenBy { it.order })
    private val candidates = active.groupBy { fold(it.source[0]) }

    private fun matches(text: String, limited: Boolean = true): List<Match>? {
        if (active.isEmpty()) return emptyList()
        var result: ArrayList<Match>? = null
        var position = 0
        var growth = 0L
        while (position < text.length) {
            if (text[position] == '\u00a7' && position + 1 < text.length) { position += 2; continue }
            var found: Match? = null
            for (rule in candidates[fold(text[position])].orEmpty()) {
                var raw = position
                var source = 0
                while (raw < text.length && source < rule.source.length) {
                    if (text[raw] == '\u00a7' && raw + 1 < text.length) { raw += 2; continue }
                    if (!text[raw].equals(rule.source[source], ignoreCase = rule.ignoreCase)) break
                    raw++; source++
                }
                if (source != rule.source.length) continue
                if (!NickTextProjection.codePointBoundary(text, position) || !NickTextProjection.codePointBoundary(text, raw)) continue
                val restore = buildString {
                    var i = position
                    while (i + 1 < raw) { if (text[i] == '\u00a7') { append(text[i++]); append(text[i++]) } else i++ }
                }
                found = Match(position, raw, rule.target + restore)
                break
            }
            if (found == null) position++ else {
                growth += found.replacement.length.toLong() - (found.end - found.start)
                if (limited && text.length.toLong() + growth > maxOf(65536L, text.length.toLong() * 8)) return null
                if (result == null) result = ArrayList()
                result += found
                position = found.end
            }
        }
        return result ?: emptyList()
    }
    fun replace(text: String): String {
        val matches = matches(text) ?: return NickNameMask.replace(text, ownUsername, ownNickname)
        if (matches.isEmpty()) return text
        return buildString(text.length) {
            var position = 0
            matches.forEach { append(text, position, it.start); append(it.replacement); position = it.end }
            append(text, position, text.length)
        }
    }
    fun project(text: String): NickTextProjection {
        val matches = matches(text) ?: NickRenderPolicy(revision, enabled, ownUsername, ownNickname, emptyList()).matches(text, false).orEmpty()
        return NickTextProjection.replace(text, matches.map { NickTextProjection.Edit(it.start, it.end, it.replacement) })
    }
    fun replaceRuns(runs: List<String>): List<String> {
        if (active.isEmpty()) return runs
        val text = runs.joinToString("")
        val matches = matches(text) ?: return NickNameMask.replaceRuns(runs, ownUsername, ownNickname)
        if (matches.isEmpty()) return runs
        var offset = 0
        var index = 0
        return runs.map { run ->
            val start = offset; val end = start + run.length; offset = end
            buildString(run.length) {
                var position = start
                while (index < matches.size && matches[index].start < end) {
                    val match = matches[index]
                    if (match.end <= start) { index++; continue }
                    val before = maxOf(position, minOf(match.start, end))
                    if (before > position) append(text, position, before)
                    if (match.start >= start) append(match.replacement)
                    position = minOf(match.end, end)
                    if (match.end <= end) index++ else break
                }
                if (position < end) append(text, position, end)
            }
        }
    }
    private fun fold(value: Char): Char = value.uppercaseChar().lowercaseChar()
}
