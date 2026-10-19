// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.presence.PresenceLeaseRecovery
import io.github.arcadiumclient.runtime.client.presence.PresencePolicyAction
import io.github.arcadiumclient.runtime.client.presence.presenceHandshakeNeedsNewLease
import io.github.arcadiumclient.runtime.client.presence.presenceHandshakeStatus
import io.github.arcadiumclient.runtime.client.presence.presencePolicyAction

class PresenceRecoveryTest {
    @Test
    fun `only known temporary policy closures renew the lease`() {
        assertEquals(PresencePolicyAction.RENEW_LEASE, presencePolicyAction(1008, "Session expired"))
        assertEquals(PresencePolicyAction.RENEW_LEASE, presencePolicyAction(1008, "Too many messages"))
        for (reason in listOf("Invalid message", "Unknown policy", "", null)) {
            assertEquals(PresencePolicyAction.REJECT_PROTOCOL, presencePolicyAction(1008, reason))
        }
        assertEquals(PresencePolicyAction.RECONNECT, presencePolicyAction(1011, "Connection unavailable"))
        for (code in listOf(1002, 1003, 1007, 1009)) {
            assertEquals(PresencePolicyAction.REJECT_PROTOCOL, presencePolicyAction(code, "Invalid message"))
        }
        assertTrue(presenceHandshakeNeedsNewLease(401))
        assertTrue(presenceHandshakeNeedsNewLease(403))
        assertFalse(presenceHandshakeNeedsNewLease(429))
        assertFalse(presenceHandshakeNeedsNewLease(503))
    }

    @Test
    fun `CIO fallback accepts only its exact numeric handshake failure format`() {
        assertEquals(401, presenceHandshakeStatus("Handshake exception, expected status code 101 but was 401"))
        assertEquals(403, presenceHandshakeStatus("Handshake exception, expected status code 101 but was 403"))
        for (message in listOf(null, "401", "expected status code 101 but was 401", "Handshake exception, expected status code 101 but was 4010", "Handshake exception, expected status code 101 but was 401 private")) {
            assertNull(presenceHandshakeStatus(message))
        }
    }

    @Test
    fun `failed proof requests cannot create more than one lease per minute`() {
        val recovery = PresenceLeaseRecovery()
        assertEquals(0L, recovery.creationDelay(-100_000))
        recovery.creationStarted(-100_000)
        assertEquals(60_000L, recovery.creationDelay(-100_000))
        assertEquals(1L, recovery.creationDelay(-40_001))
        assertEquals(0L, recovery.creationDelay(-40_000))
        recovery.creationStarted(-40_000)
        assertEquals(60_000L, recovery.creationDelay(-40_000))
    }

    @Test
    fun `immediate socket rejection backs off even after a successful handshake`() {
        val recovery = PresenceLeaseRecovery()
        var time = 0L
        recovery.creationStarted(time)
        for (expectedDelay in listOf(60_000L, 120_000L, 240_000L, 300_000L, 300_000L)) {
            time += 500
            recovery.leaseRejected(time, connectedFor = 500)
            assertEquals(expectedDelay, recovery.creationDelay(time))
            assertEquals(0L, recovery.creationDelay(time + expectedDelay))
            time += expectedDelay
            recovery.creationStarted(time)
        }
    }

    @Test
    fun `a stable connection resets failure backoff while keeping the creation cooldown`() {
        val recovery = PresenceLeaseRecovery()
        recovery.leaseRejected(0, 0)
        recovery.leaseRejected(60_000, 0)
        assertEquals(120_000L, recovery.creationDelay(60_000))
        recovery.creationStarted(180_000)
        recovery.leaseRejected(300_000, connectedFor = 120_000)
        assertEquals(60_000L, recovery.creationDelay(300_000))
        assertEquals(1L, recovery.creationDelay(359_999))
    }
}
