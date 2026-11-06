// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.config.v1.annotations.Include

object NickHiderConfig : Config(
    "arcadium-nick-hider.json",
    "assets/arcadium/mainmenu/users-01.svg",
    "Nick Hider",
    Category.VISUALS,
) {
    @JvmStatic
    @Switch(title = "Enabled", description = "Hide your username and apply display replacements.")
    var enabled = false
        set(value) { field = value; NickHider.settingsChanged() }

    @JvmStatic
    @Text(title = "Nickname", description = "The name shown in place of your username. Names containing your username use a safe alias.")
    var nickname = "You"
        set(value) { field = value; NickHider.settingsChanged() }

    @JvmStatic @Include var replacementRulesJson = "[]"
        set(value) { field = value; NickHider.settingsChanged() }
    private var cachedJson: String? = null
    private var cachedRules = emptyList<NickReplacementRule>()
    var rulesError: String? = null
        private set

    fun rules(): List<NickReplacementRule> {
        if (cachedJson != replacementRulesJson) {
            cachedRules = runCatching { NickReplacementRules.decode(replacementRulesJson) }.onSuccess { rulesError = null }.getOrElse {
                rulesError = "Could not read the replacement rules. The saved data has been kept."
                emptyList()
            }
            cachedJson = replacementRulesJson
        }
        return cachedRules
    }
    fun storeRules(rules: List<NickReplacementRule>) {
        replacementRulesJson = NickReplacementRules.encode(rules)
        cachedJson = replacementRulesJson; cachedRules = rules.toList(); rulesError = null
        save()
    }
}
