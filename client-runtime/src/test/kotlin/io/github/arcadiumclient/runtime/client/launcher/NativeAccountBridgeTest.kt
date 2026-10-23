// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.launcher

import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NativeAccountBridgeTest {
    @Test fun newlyAddedOnlineAccountsResolveUsableSessionsBeforeActivation() = runBlocking {
        val additions = listOf<Pair<String, suspend () -> AccountRepository.StoredAccount>>(
            "login" to { NativeAccountBridge.login() },
            "cookie" to { requireNotNull(NativeAccountBridge.importCookies()) },
            "session" to { NativeAccountBridge.importSession("fixture-input-access") },
            "session" to { NativeAccountBridge.importSession("fixture-input-refresh", true, "00000000402b5328") },
        )
        for ((operation, add) in additions) {
            Fixture().use { fixture ->
                val account = add()
                assertEquals("fixture-session-access", account.accessToken)
                assertEquals(listOf(operation, "switch"), fixture.requests.map { it["op"]!!.jsonPrimitive.content })
                assertEquals(ID, fixture.requests.last()["account"]!!.jsonPrimitive.content)
                assertTrue(AccountRepository.isOnline(account))
            }
        }
    }

    @Test fun cancelledCookieSelectionDoesNotSwitchAccounts() = runBlocking {
        Fixture(cancelCookie = true).use { fixture ->
            assertNull(NativeAccountBridge.importCookies())
            assertEquals(listOf("cookie"), fixture.requests.map { it["op"]!!.jsonPrimitive.content })
        }
    }

    @Test fun offlineAdditionAlsoSelectsTheSavedAccount() {
        Fixture(offline = true).use { fixture ->
            val account = NativeAccountBridge.addOffline("FixtureUser")
            assertEquals("0", account.accessToken)
            assertFalse(AccountRepository.isOnline(account))
            assertEquals(listOf("offline", "switch"), fixture.requests.map { it["op"]!!.jsonPrimitive.content })
            assertEquals(ID, fixture.requests.last()["account"]!!.jsonPrimitive.content)
        }
    }

    private class Fixture(private val offline: Boolean = false, private val cancelCookie: Boolean = false) : AutoCloseable {
        val requests = mutableListOf<JsonObject>()
        private val state = NativeAccountBridge::class.java.getDeclaredField("connection").apply { isAccessible = true }
        private val previous = state.get(NativeAccountBridge)
        private val process = IdleProcess()
        private val written = StringWriter()
        private var consumed = 0
        private lateinit var pending: ConcurrentHashMap<Long, CompletableFuture<JsonElement>>
        private val writer = object : BufferedWriter(written) {
            override fun flush() {
                super.flush()
                val text = written.toString()
                val messages = text.substring(consumed).lineSequence().filter { it.isNotBlank() }.toList()
                consumed = text.length
                for (line in messages) {
                    val request = Json.parseToJsonElement(line).jsonObject
                    requests += request
                    val operation = request["op"]!!.jsonPrimitive.content
                    val response = if (operation == "cookie" && cancelCookie) JsonNull else buildJsonObject {
                        put("id", ID)
                        put("username", "FixtureUser")
                        put("kind", if (offline) "offline" else "microsoft")
                        put("refreshable", !offline)
                        put("expires", "2099-01-01T00:00:00Z")
                        if (operation == "switch") put("access_token", if (offline) "0" else "fixture-session-access")
                    }
                    requireNotNull(pending[request["id"]!!.jsonPrimitive.long]).complete(response)
                }
            }
        }

        init {
            val connectionType = NativeAccountBridge::class.java.declaredClasses.single { it.simpleName == "Connection" }
            val constructor = connectionType.getDeclaredConstructor(Process::class.java, BufferedWriter::class.java)
                .apply { isAccessible = true }
            val connection = constructor.newInstance(process, writer)
            val pendingField = connectionType.getDeclaredField("pending").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val responses = pendingField.get(connection) as ConcurrentHashMap<Long, CompletableFuture<JsonElement>>
            pending = responses
            state.set(NativeAccountBridge, connection)
        }

        override fun close() {
            state.set(NativeAccountBridge, previous)
            writer.close()
            process.destroy()
        }
    }

    private class IdleProcess : Process() {
        private var alive = true
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor() = 0
        override fun exitValue() = 0
        override fun isAlive() = alive
        override fun destroy() { alive = false }
    }

    companion object {
        private const val ID = "12345678-1234-1234-1234-123456789abc"
    }
}
