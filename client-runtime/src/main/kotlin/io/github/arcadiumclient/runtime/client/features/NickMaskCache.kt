// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features

internal class NickMaskCache(private val capacity: Int = 2048) {
    private class Result(val value: String?)
    private val unchanged = Result(null)
    private val entries = object : LinkedHashMap<String, Result>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result>): Boolean = size > capacity
    }
    private val rendered = java.util.IdentityHashMap<String, Boolean>()
    private val order = java.util.ArrayDeque<String>()
    private var renderedChars = 0
    private var revision = Long.MIN_VALUE
    private var depth = 0
    private var legacyName: String? = null
    private var legacyNickname: String? = null
    private var legacyRevision = 0L
    private var legacyPolicy: NickRenderPolicy? = null
    var hits = 0L
        private set
    var misses = 0L
        private set

    fun synchronize(policy: NickRenderPolicy) {
        if (revision != policy.revision) { revision = policy.revision; entries.clear(); rendered.clear(); order.clear(); renderedChars = 0 }
    }
    fun disable() {
        if (revision != Long.MIN_VALUE) { revision = Long.MIN_VALUE; entries.clear(); rendered.clear(); order.clear(); renderedChars = 0 }
    }
    fun replace(text: String, policy: NickRenderPolicy, useCache: Boolean): String {
        synchronize(policy)
        if (!policy.enabled || rendered.containsKey(text)) return text
        if (useCache && text.length <= 4096) entries[text]?.let { hits++; return it.value?.also(::rememberRendered) ?: text }
        misses++
        val value = policy.replace(text)
        if (useCache && text.length <= 4096) entries[text] = if (value === text) unchanged else Result(value)
        if (value !== text) rememberRendered(value)
        return value
    }
    fun replace(text: String, name: String, custom: String): String {
        if (legacyPolicy == null || legacyName != name || legacyNickname != custom) {
            legacyName = name; legacyNickname = custom
            legacyPolicy = NickRenderPolicy(--legacyRevision, true, name, custom, emptyList())
        }
        return replace(text, legacyPolicy!!, true)
    }
    fun rememberRendered(value: String) {
        if (value.length > 16384 || rendered.put(value, true) != null) return
        order.addLast(value); renderedChars += value.length
        while (order.size > capacity || renderedChars > 262144) {
            val removed = order.removeFirst(); rendered.remove(removed); renderedChars -= removed.length
        }
    }
    fun enter(): Boolean { val outer = depth == 0; depth++; return outer }
    fun exit() { check(depth > 0); depth-- }
    fun size(): Int = entries.size
    fun renderedSize(): Int = rendered.size
    fun renderedCharacters(): Int = renderedChars
}
