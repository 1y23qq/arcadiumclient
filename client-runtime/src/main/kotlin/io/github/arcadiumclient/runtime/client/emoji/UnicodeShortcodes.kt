// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.emoji

internal class UnicodeShortcodes(replacements: Map<String, String>) {
    private class Node {
        val children = HashMap<Char, Node>()
        var replacement: String? = null
    }

    private val root = Node()

    init {
        for ((sequence, replacement) in replacements) {
            if (sequence.isEmpty()) continue
            var node = root
            for (character in sequence) node = node.children.getOrPut(character) { Node() }
            node.replacement = replacement
        }
    }

    fun replace(text: String): String {
        var output: StringBuilder? = null
        var index = 0
        var copied = 0
        while (index < text.length) {
            var node = root
            var cursor = index
            var end = index
            var replacement: String? = null
            while (cursor < text.length) {
                node = node.children[text[cursor]] ?: break
                cursor++
                node.replacement?.let { replacement = it; end = cursor }
            }
            if (replacement == null) {
                index++
                continue
            }
            val buffer = output ?: StringBuilder(text.length).also { output = it }
            buffer.append(text, copied, index).append(replacement)
            index = end
            copied = end
        }
        return output?.append(text, copied, text.length)?.toString() ?: text
    }
}
