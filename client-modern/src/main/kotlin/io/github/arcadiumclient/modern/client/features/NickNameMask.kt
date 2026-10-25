// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

internal object NickNameMask {
    private data class Replacement(val start: Int, val end: Int, val value: String)

    fun alias(value: String, username: String): String {
        val clean = value.filter { !it.isISOControl() && it != '\u00a7' }.trim().take(64)
        return clean.takeIf { it.isNotEmpty() && !it.contains(username, ignoreCase = true) }
            ?: if ("You".contains(username, ignoreCase = true)) "Hidden" else "You"
    }

    fun replace(text: String, username: String, nickname: String): String {
        val matches = replacements(text, username, nickname)
        if (matches.isEmpty()) return text
        return buildString(text.length) {
            var position = 0
            for (match in matches) {
                append(text, position, match.start)
                append(match.value)
                position = match.end
            }
            append(text, position, text.length)
        }
    }

    fun replaceRuns(runs: List<String>, username: String, nickname: String): List<String> {
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
        if (username.isEmpty() || text.isEmpty()) return emptyList()
        val first = username[0]
        if (text.none { it.equals(first, ignoreCase = true) }) return emptyList()
        val formatted = '\u00a7' in text
        if (!formatted && !text.contains(username, ignoreCase = true)) return emptyList()
        val visible = if (formatted) StringBuilder(text.length) else null
        val positions = if (formatted) IntArray(text.length) else null
        if (formatted) {
            var index = 0
            while (index < text.length) {
                if (text[index] == '\u00a7' && index + 1 < text.length) {
                    index += 2
                } else {
                    positions!![visible!!.length] = index
                    visible.append(text[index++])
                }
            }
        }
        val plain = visible?.toString() ?: text
        val alias = alias(nickname, username)
        val out = ArrayList<Replacement>()
        var index = plain.indexOf(username, ignoreCase = true)
        while (index >= 0) {
            val rawStart = positions?.get(index) ?: index
            val rawEnd = (positions?.get(index + username.length - 1) ?: (index + username.length - 1)) + 1
            val restore = if (formatted) buildString {
                var cursor = rawStart
                while (cursor < rawEnd) {
                    if (text[cursor] == '\u00a7' && cursor + 1 < rawEnd) {
                        append(text[cursor++])
                        append(text[cursor++])
                    } else cursor++
                }
            } else ""
            out += Replacement(rawStart, rawEnd, alias + restore)
            index = plain.indexOf(username, index + username.length, ignoreCase = true)
        }
        return out
    }
}
