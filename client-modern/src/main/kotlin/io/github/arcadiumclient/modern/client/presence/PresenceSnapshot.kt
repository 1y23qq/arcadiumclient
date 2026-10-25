// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.presence

import java.util.Collections
import java.util.UUID

internal const val MAX_PRESENCE_PLAYERS = 256

internal fun presencePlayerId(value: String): UUID? {
    if (value.length != 32 || value.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return runCatching {
        UUID.fromString("${value.substring(0, 8)}-${value.substring(8, 12)}-${value.substring(12, 16)}-${value.substring(16, 20)}-${value.substring(20)}")
    }.getOrNull()?.takeIf { it.version() == 4 }
}

internal fun UUID.presenceId(): String = toString().replace("-", "")

internal class PresenceSnapshot {
    private class State(val generation: Long, val watched: Set<UUID>, val online: Set<UUID>)
    private val lock = Any()
    @Volatile private var state = State(0, emptySet(), emptySet())

    fun begin(generation: Long) = synchronized(lock) {
        state = State(generation, emptySet(), emptySet())
    }

    fun watch(generation: Long, players: Collection<UUID>): Boolean = synchronized(lock) {
        val current = state
        if (current.generation != generation) return@synchronized false
        val watched = immutable(players.asSequence().filter { it.version() == 4 }.distinct().take(MAX_PRESENCE_PLAYERS).toList())
        if (watched == current.watched) return@synchronized false
        state = State(generation, watched, immutable(current.online.filter(watched::contains)))
        true
    }

    fun accept(generation: Long, players: List<String>): Boolean = synchronized(lock) {
        val current = state
        if (current.generation != generation || players.size > MAX_PRESENCE_PLAYERS) return@synchronized false
        val decoded = players.map { presencePlayerId(it) ?: return@synchronized false }
        state = State(generation, current.watched, immutable(decoded.filter(current.watched::contains)))
        true
    }

    fun clear(generation: Long) = synchronized(lock) {
        val current = state
        if (current.generation == generation) state = State(generation, current.watched, emptySet())
    }

    fun clear() = synchronized(lock) {
        val current = state
        state = State(current.generation, current.watched, emptySet())
    }

    fun watches(generation: Long): Set<UUID> = state.let { if (it.generation == generation) it.watched else emptySet() }

    fun current(generation: Long): Boolean = state.generation == generation

    fun contains(player: UUID): Boolean = player in state.online

    private fun immutable(players: Collection<UUID>): Set<UUID> =
        Collections.unmodifiableSet(LinkedHashSet(players))
}
