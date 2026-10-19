// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager

object RuntimeServices {
    private val log = LogManager.getLogger("Arcadium")
    @JvmField val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    @JvmField val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO +
        CoroutineExceptionHandler { _, error -> log.warn("Arcadium background task failed", error) })
}
