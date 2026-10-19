// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.network

import kotlin.random.Random

fun reconnectDelay(attempt: Int): Long {
    val ceiling = (1_000L shl attempt.coerceIn(0, 6)).coerceAtMost(60_000L)
    return Random.nextLong(ceiling / 2, ceiling + 1)
}
