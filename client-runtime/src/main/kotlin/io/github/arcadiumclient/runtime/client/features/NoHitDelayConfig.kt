// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

object NoHitDelayConfig : Config("arcadium-no-hit-delay.json", "NoHitDelay", Category.COMBAT) {
    @JvmStatic
    @Switch(title = "Enabled", description = "Remove the attack cooldown after a missed hit.")
    var enabled = true
}
