// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.polyfrost.oneconfig.api.toggle.v1.ModToggle

object ModToggles {
    interface Store {
        fun load(): Set<String>
        fun save(disabled: Set<String>)
    }

    fun interface Listener {
        fun onToggle(modId: String, enabled: Boolean)
    }

    private val toggles = ConcurrentHashMap<String, ModToggle>()
    private val managed = ConcurrentHashMap.newKeySet<String>()
    private val disabled = ConcurrentHashMap.newKeySet<String>()
    private val listeners = CopyOnWriteArrayList<Listener>()
    @Volatile private var store: Store? = null

    @get:JvmStatic
    var revision by mutableIntStateOf(0)
        private set

    private fun key(id: String): String = id.lowercase(Locale.ROOT).removeSuffix(".json")

    @JvmStatic
    fun register(modId: String, toggle: ModToggle) {
        val id = key(modId)
        toggles[id] = toggle
        if (id in managed) toggle.setEnabled(id !in disabled)
        revision++
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun register(modId: String, toggle: org.polyfrost.oneconfig.api.ui.v1.ModToggle) =
        register(modId, toggle as ModToggle)

    @JvmStatic
    fun manage(vararg modIds: String) {
        modIds.forEach {
            val id = key(it)
            managed.add(id)
            toggles[id]?.setEnabled(id !in disabled)
        }
        revision++
    }

    @JvmStatic
    fun toggleFor(modId: String): ModToggle? = toggles[key(modId)]

    @JvmStatic
    fun isEnabled(modId: String): Boolean = toggleFor(modId)?.isEnabled ?: (key(modId) !in disabled)

    @JvmStatic
    fun setEnabled(modId: String, enabled: Boolean) {
        val id = key(modId)
        toggles[id]?.setEnabled(enabled)
        if (enabled) disabled.remove(id) else disabled.add(id)
        if (id in managed) store?.save(disabled.toSet())
        revision++
        listeners.forEach { it.onToggle(id, enabled) }
    }

    @JvmStatic
    fun addListener(listener: Listener) { listeners.addIfAbsent(listener) }

    @JvmStatic
    fun removeListener(listener: Listener) { listeners.remove(listener) }

    @JvmStatic
    fun setStore(store: Store) {
        this.store = store
        disabled.clear()
        disabled.addAll(store.load().map(::key))
        managed.forEach { id -> toggles[id]?.setEnabled(id !in disabled) }
        revision++
    }
}
