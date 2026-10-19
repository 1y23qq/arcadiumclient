// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text

object NickHiderConfig : Config(
    "arcadium-nick-hider.json",
    "assets/arcadium/mainmenu/users-01.svg",
    "Nick Hider",
    Category.VISUALS,
) {
    @JvmStatic
    @Switch(title = "Enabled", description = "Hide your username on this screen and in game.")
    var enabled = false

    @JvmStatic
    @Text(title = "Nickname", description = "The name shown in place of your username. Names containing your username use a safe alias.")
    var nickname = "You"
}
