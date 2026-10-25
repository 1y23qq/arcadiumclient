// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.presence

internal enum class PresencePolicyAction { RECONNECT, RENEW_LEASE, REJECT_PROTOCOL }

internal fun presencePolicyAction(code: Int?, reason: String?): PresencePolicyAction = when {
    code in setOf(1002, 1003, 1007, 1009) -> PresencePolicyAction.REJECT_PROTOCOL
    code != 1008 -> PresencePolicyAction.RECONNECT
    reason == "Session expired" || reason == "Too many messages" -> PresencePolicyAction.RENEW_LEASE
    else -> PresencePolicyAction.REJECT_PROTOCOL
}

internal fun presenceHandshakeNeedsNewLease(status: Int): Boolean = status == 401 || status == 403

private val CIO_HANDSHAKE_STATUS = Regex("^Handshake exception, expected status code 101 but was (\\d{3})$")

internal fun presenceHandshakeStatus(message: String?): Int? =
    message?.let { CIO_HANDSHAKE_STATUS.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }

internal class PresenceLeaseRecovery {
    private var nextCreationAt: Long? = null
    private var failures = 0

    fun creationDelay(now: Long): Long = nextCreationAt?.let { (it - now).coerceAtLeast(0) } ?: 0

    fun creationStarted(now: Long) {
        nextCreationAt = now + MIN_CREATION_INTERVAL
    }

    fun leaseRejected(now: Long, connectedFor: Long) {
        if (connectedFor >= STABLE_CONNECTION_INTERVAL) failures = 0
        failures = (failures + 1).coerceAtMost(4)
        val interval = (MIN_CREATION_INTERVAL shl (failures - 1)).coerceAtMost(MAX_CREATION_INTERVAL)
        nextCreationAt = maxOf(nextCreationAt ?: now, now + interval)
    }

    private companion object {
        const val MIN_CREATION_INTERVAL = 60_000L
        const val MAX_CREATION_INTERVAL = 5 * 60_000L
        const val STABLE_CONNECTION_INTERVAL = 2 * 60_000L
    }
}
