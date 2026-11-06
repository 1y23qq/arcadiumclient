// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig
import io.github.arcadiumclient.runtime.client.features.NickReplacementRule
import io.github.arcadiumclient.runtime.client.features.NickReplacementRules

@Composable
fun NickReplacementSettings() {
    var rules by remember { mutableStateOf(NickHiderConfig.rules()) }
    fun update(value: List<NickReplacementRule>) { rules = value; NickHiderConfig.storeRules(value) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiText("Replacement rules", 18.sp, Modifier.weight(1f))
            UiButton("Add rule", enabled = rules.size < 128) { update(rules + NickReplacementRule()) }
        }
        UiText("Display only; existing text styles are preserved. Longest matches win, then the first rule. Replacement text is not processed again.", 13.sp, color = UiTextSecondary)
        NickHiderConfig.rulesError?.let { UiText(it, 13.sp, color = UiTextSecondary) }
        if (rules.isEmpty()) UiText("No replacement rules.", 14.sp, color = UiTextSecondary)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                fun change(value: NickReplacementRule) { update(rules.toMutableList().also { it[index] = value }) }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiToggle("Enabled", rule.enabled, Modifier.weight(1f)) { change(rule.copy(enabled = !rule.enabled)) }
                        UiButton("Up", enabled = index > 0) { update(rules.toMutableList().also { java.util.Collections.swap(it, index, index - 1) }) }
                        UiButton("Down", enabled = index < rules.lastIndex) { update(rules.toMutableList().also { java.util.Collections.swap(it, index, index + 1) }) }
                        UiButton("Remove", danger = true) { update(rules.filterNot { it.id == rule.id }) }
                    }
                    UiTextField(rule.source, { change(rule.copy(source = it.filterNot(Char::isISOControl))) }, Modifier.fillMaxWidth(), "Find visible text", maxLength = 128)
                    UiTextField(rule.replacement, { change(rule.copy(replacement = NickReplacementRules.visible(it).filterNot(Char::isISOControl))) }, Modifier.fillMaxWidth(), "Replace with (empty removes text)", maxLength = 256)
                    UiToggle("Case sensitive", rule.caseSensitive) { change(rule.copy(caseSensitive = !rule.caseSensitive)) }
                    if (rule.enabled && NickReplacementRules.visible(rule.source).isEmpty()) UiText("Enter text to match. Empty rules are ignored.", 12.sp, color = UiTextSecondary)
                }
            }
        }
    }
}
