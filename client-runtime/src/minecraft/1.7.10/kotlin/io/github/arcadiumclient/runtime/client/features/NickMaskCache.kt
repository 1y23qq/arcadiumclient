// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

internal class NickMaskCache(private val capacity: Int = 2048) {
    private class Result(val value: String?)
    private val unchanged = Result(null)
    private val entries = object : LinkedHashMap<String, Result>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result>): Boolean = size > capacity
    }
    private var username = ""
    private var nickname = ""
    private var alias = ""
    var hits = 0L
        private set
    var misses = 0L
        private set

    fun replace(text: String, name: String, custom: String): String {
        if (username != name || nickname != custom || alias.isEmpty()) {
            username = name; nickname = custom; alias = NickNameMask.alias(custom, name); entries.clear()
        }
        if (text.length > 4096) return NickNameMask.replaceAliased(text, name, alias)
        entries[text]?.let { hits++; return it.value ?: text }
        misses++
        val result = NickNameMask.replaceAliased(text, name, alias)
        entries[text] = if (result === text) unchanged else Result(result)
        return result
    }

    fun size(): Int = entries.size
}
