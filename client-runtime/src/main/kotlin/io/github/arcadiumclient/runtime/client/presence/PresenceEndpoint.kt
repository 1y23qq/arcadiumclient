// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.presence

import java.net.URI

internal const val DEFAULT_PRESENCE_ENDPOINT = "https://presence-test.arcadiumclient.com"

internal class PresenceEndpoint private constructor(val origin: String, val socketOrigin: String) {
    val sessionUrl: String get() = "$origin/v1/session"
    val socketUrl: String get() = "$socketOrigin/v1/socket"

    companion object {
        fun configured(): PresenceEndpoint? = parse(
            System.getProperty("arcadium.presenceEndpoint") ?: DEFAULT_PRESENCE_ENDPOINT,
            DEFAULT_PRESENCE_ENDPOINT,
        )

        fun parse(value: String, production: String): PresenceEndpoint? {
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            if (uri.rawPath !in listOf("", "/") || uri.host == null || uri.port == 0 || uri.port < -1) return null
            val local = uri.host.lowercase() in setOf("localhost", "127.0.0.1", "[::1]", "::1")
            val scheme = uri.scheme?.lowercase() ?: return null
            val origin = URI(scheme, null, uri.host.lowercase(), uri.port, null, null, null).toASCIIString()
            val approved = runCatching { URI(production.removeSuffix("/")) }.getOrNull()?.toASCIIString()
            if (scheme != "https" && !(scheme == "http" && local)) return null
            if (!local && origin != approved) return null
            val socket = URI(if (scheme == "https") "wss" else "ws", null, uri.host.lowercase(), uri.port, null, null, null).toASCIIString()
            return PresenceEndpoint(origin, socket)
        }
    }
}
