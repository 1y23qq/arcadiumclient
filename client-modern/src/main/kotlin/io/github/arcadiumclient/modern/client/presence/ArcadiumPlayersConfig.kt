// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.presence

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object ArcadiumPlayersConfig : Config(
    "arcadium-players.json",
    "Arcadium Badge",
    Category.VISUALS,
) {
    @JvmStatic
    @Switch(title = "Show Nametag Badges", description = "Show an Arcadium badge beside connected players' names.")
    var showNametag = true

    @JvmStatic
    @Switch(title = "Show Tab Badges", description = "Show an Arcadium badge in the player list.")
    var showTab = true
}
