// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text

object PasswordHider : Config("arcadium-password-hider.json", "Password Hider", Category.QOL) {
    @JvmStatic @Switch(title = "Enabled", description = "Mask password command arguments in the chat input. The original command is sent to the server.")
    var enabled = true
    @JvmStatic @Text(title = "Commands", description = "Command names separated by commas or spaces.")
    var commands = "login, l, register, reg, changepassword, changepass, cp"

    @JvmStatic fun display(text: String): String = if (enabled) PasswordMask.display(text, commands) else text
}
