// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern

import net.fabricmc.loader.api.FabricLoader

object ClientBuild {
    val VERSION: String by lazy {
        FabricLoader.getInstance().getModContainer("arcadium_runtime")
            .orElseThrow { IllegalStateException("Arcadium Runtime metadata is unavailable.") }
            .metadata.version.friendlyString
    }
    const val MINECRAFT_VERSION = "26.2"
    const val SUPPORT_URL = "https://discord.gg/QbNfaCHjUz"
}
