// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.presence.MAX_PRESENCE_PLAYERS
import io.github.arcadiumclient.runtime.client.presence.PresenceEndpoint
import io.github.arcadiumclient.runtime.client.presence.PresenceSnapshot
import io.github.arcadiumclient.runtime.client.presence.presenceId
import java.util.UUID

class PresenceSnapshotTest {
    @Test
    fun `players leaving the watch list immediately lose their badge`() {
        val player = UUID.randomUUID()
        val other = UUID.randomUUID()
        val cache = PresenceSnapshot()
        cache.begin(1)
        cache.watch(1, listOf(player))
        assertTrue(cache.accept(1, listOf(player.presenceId(), other.presenceId())))
        assertTrue(cache.contains(player))
        assertFalse(cache.contains(other))
        cache.watch(1, emptyList())
        assertFalse(cache.contains(player))
        cache.accept(1, listOf(player.presenceId()))
        assertFalse(cache.contains(player))
    }

    @Test
    fun `late packets and cleanup from the previous account cannot overwrite a new connection`() {
        val previous = UUID.randomUUID()
        val current = UUID.randomUUID()
        val cache = PresenceSnapshot()
        cache.begin(1)
        cache.watch(1, listOf(previous))
        cache.accept(1, listOf(previous.presenceId()))
        cache.begin(2)
        cache.watch(2, listOf(current))
        cache.accept(2, listOf(current.presenceId()))
        assertFalse(cache.accept(1, listOf(previous.presenceId())))
        cache.clear(1)
        assertTrue(cache.contains(current))
        assertFalse(cache.contains(previous))
        cache.clear(2)
        assertFalse(cache.contains(current))
    }

    @Test
    fun `tab list mutation cannot change an already published snapshot`() {
        val player = UUID.randomUUID()
        val source = mutableListOf(player)
        val cache = PresenceSnapshot()
        cache.begin(1)
        cache.watch(1, source)
        source.clear()
        assertEquals(setOf(player), cache.watches(1))
        assertThrows(UnsupportedOperationException::class.java) {
            (cache.watches(1) as MutableSet<UUID>).add(UUID.randomUUID())
        }
    }

    @Test
    fun `offline identities and excessive watch lists cannot enter the protocol`() {
        val players = List(MAX_PRESENCE_PLAYERS + 20) { UUID.randomUUID() }
        val offline = UUID.nameUUIDFromBytes("OfflinePlayer:Fixture".toByteArray())
        val cache = PresenceSnapshot()
        cache.begin(1)
        cache.watch(1, listOf(offline) + players)
        assertEquals(MAX_PRESENCE_PLAYERS, cache.watches(1).size)
        assertFalse(offline in cache.watches(1))
        assertFalse(cache.accept(1, List(MAX_PRESENCE_PLAYERS + 1) { players[0].presenceId() }))
        assertFalse(cache.accept(1, listOf("invalid")))
        assertFalse(cache.accept(1, listOf(offline.presenceId())))
    }

    @Test
    fun `production connections preserve TLS and keep credentials out of endpoint URLs`() {
        val origin = "https://presence.arcadium.test"
        val endpoint = PresenceEndpoint.parse(origin, origin)
        assertNotNull(endpoint)
        assertEquals("$origin/v1/session", endpoint!!.sessionUrl)
        assertEquals("wss://presence.arcadium.test/v1/socket", endpoint.socketUrl)
        for (value in listOf(
            "http://presence.arcadium.test",
            "https://another.arcadium.test",
            "$origin?token=fixture",
            "$origin#fixture",
            "https://fixture@presence.arcadium.test",
            "$origin/v1/session",
            "http://127.0.0.1.example.test",
        )) assertNull(PresenceEndpoint.parse(value, origin))
        assertEquals("ws://127.0.0.1:8765/v1/socket", PresenceEndpoint.parse("http://127.0.0.1:8765", origin)?.socketUrl)
    }
}
