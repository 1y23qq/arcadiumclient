// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.launcher

import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AccountRepository {
    class StoredAccount(
        val id: String,
        val username: String,
        val accessToken: String,
        val expires: String,
        val kind: String,
        val refreshable: Boolean,
    ) {
        override fun toString() = "ArcadiumAccount($username, $kind)"
    }

    class CredentialsStore(val users: Map<String, StoredAccount>, val defaultUser: String?)

    fun load(): CredentialsStore = NativeAccountBridge.list()

    fun decode(input: JsonElement): StoredAccount {
        val fields = input.jsonObject
        fun text(key: String): String = fields[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val id = text("id")
        val username = text("username")
        require(id.isNotBlank() && username.isNotBlank()) { "The launcher returned an incomplete account." }
        return StoredAccount(id, username, text("access_token"), text("expires"),
            text("kind").ifBlank { "microsoft" }, fields["refreshable"]?.jsonPrimitive?.booleanOrNull ?: false)
    }

    fun isOnline(account: StoredAccount) = account.kind == "microsoft" || account.kind == "session"
    fun parseUuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
