// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

internal object NickNameMask {
    private data class Replacement(val start: Int, val end: Int, val value: String)

    fun alias(value: String, username: String): String {
        val filtered = value.filter { !it.isISOControl() && it != '\u00a7' }.trim()
        var end = minOf(64, filtered.length)
        if (end > 0 && end < filtered.length && filtered[end - 1].isHighSurrogate() && filtered[end].isLowSurrogate()) end--
        val clean = filtered.substring(0, end)
        return clean.takeIf { it.isNotEmpty() && !it.contains(username, ignoreCase = true) }
            ?: if ("You".contains(username, ignoreCase = true)) "Hidden" else "You"
    }

    fun replace(text: String, username: String, nickname: String): String {
        val first = find(text, username, 0)
        if (first < 0) return text
        return replaceMatched(text, username, alias(nickname, username), first)
    }

    fun replaceAliased(text: String, username: String, alias: String): String {
        val first = find(text, username, 0)
        return if (first < 0) text else replaceMatched(text, username, alias, first)
    }

    private fun replaceMatched(text: String, username: String, alias: String, first: Long): String = buildString(text.length) {
        var position = 0
        var match = first
        while (match >= 0) {
            val start = (match ushr 32).toInt()
            val end = match.toInt()
            append(text, position, start)
            append(alias)
            var cursor = start
            while (cursor + 1 < end) {
                if (text[cursor] == '\u00a7') { append(text[cursor++]); append(text[cursor++]) }
                else cursor++
            }
            position = end
            match = find(text, username, end)
        }
        append(text, position, text.length)
    }

    fun replaceRuns(runs: List<String>, username: String, nickname: String): List<String> {
        if (username.isEmpty() || runs.none { run -> run.any { same(it, username[0]) } }) return runs
        val text = runs.joinToString("")
        val matches = replacements(text, username, nickname)
        if (matches.isEmpty()) return runs
        var offset = 0
        var matchIndex = 0
        return runs.map { run ->
            val start = offset
            val end = start + run.length
            offset = end
            buildString(run.length) {
                var position = start
                while (matchIndex < matches.size && matches[matchIndex].start < end) {
                    val match = matches[matchIndex]
                    if (match.end <= start) {
                        matchIndex++
                        continue
                    }
                    val before = maxOf(position, minOf(match.start, end))
                    if (before > position) append(text, position, before)
                    if (match.start >= start) append(match.value)
                    position = minOf(match.end, end)
                    if (match.end <= end) matchIndex++ else break
                }
                if (position < end) append(text, position, end)
            }
        }
    }

    private fun replacements(text: String, username: String, nickname: String): List<Replacement> {
        var match = find(text, username, 0)
        if (match < 0) return emptyList()
        val alias = alias(nickname, username)
        val out = ArrayList<Replacement>()
        while (match >= 0) {
            val rawStart = (match ushr 32).toInt()
            val rawEnd = match.toInt()
            val restore = buildString {
                var cursor = rawStart
                while (cursor + 1 < rawEnd) {
                    if (text[cursor] == '\u00a7') {
                        append(text[cursor++])
                        append(text[cursor++])
                    } else cursor++
                }
            }
            out += Replacement(rawStart, rawEnd, alias + restore)
            match = find(text, username, rawEnd)
        }
        return out
    }

    private fun find(text: String, username: String, from: Int): Long {
        if (username.isEmpty()) return -1
        var start = from
        while (start < text.length) {
            if (text[start] == '\u00a7' && start + 1 < text.length) { start += 2; continue }
            if (same(text[start], username[0])) {
                var raw = start
                var matched = 0
                while (raw < text.length && matched < username.length) {
                    if (text[raw] == '\u00a7' && raw + 1 < text.length) { raw += 2; continue }
                    if (!same(text[raw], username[matched])) break
                    raw++; matched++
                }
                if (matched == username.length) return (start.toLong() shl 32) or (raw.toLong() and 0xffffffffL)
            }
            start++
        }
        return -1
    }

    private fun same(first: Char, second: Char): Boolean {
        if (first == second) return true
        if (first.code < 128 && second.code < 128) {
            val lower = first.code or 32
            return lower in 97..122 && lower == (second.code or 32)
        }
        return first.equals(second, ignoreCase = true)
    }
}
