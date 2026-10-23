// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.launcher

import kotlinx.coroutines.future.await
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import io.github.arcadiumclient.runtime.client.RuntimeServices
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object NativeAccountBridge {
    private class Connection(val process: Process, val writer: BufferedWriter) {
        val pending = ConcurrentHashMap<Long, CompletableFuture<JsonElement>>()
        val progress = ConcurrentHashMap<Long, (String) -> Unit>()
    }

    private val nextId = AtomicLong()
    private val lock = Any()
    @Volatile private var connection: Connection? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ close() }, "arcadium-account-shutdown"))
    }

    fun list(): AccountRepository.CredentialsStore {
        val value = request("list").get(30, TimeUnit.SECONDS).jsonObject
        val accounts = value["users"]?.jsonArray.orEmpty().map(AccountRepository::decode)
        return AccountRepository.CredentialsStore(
            users = accounts.associateBy { it.id },
            defaultUser = value["default_user"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun switchAccount(id: String): AccountRepository.StoredAccount =
        AccountRepository.decode(request("switch", mapOf("account" to id)).get(120, TimeUnit.SECONDS))

    suspend fun refreshAccount(id: String): AccountRepository.StoredAccount =
        AccountRepository.decode(request("refresh", mapOf("account" to id)).await())

    fun removeAccount(id: String) {
        request("remove", mapOf("account" to id)).get(30, TimeUnit.SECONDS)
    }

    suspend fun login(): AccountRepository.StoredAccount =
        addedAccount(request("login").await())

    suspend fun importCookies(onProgress: ((String) -> Unit)? = null): AccountRepository.StoredAccount? =
        request("cookie", onProgress = onProgress).await().takeUnless { it == JsonNull }?.let { addedAccount(it) }

    suspend fun importSession(accessToken: String, refresh: Boolean = false, clientId: String = ""): AccountRepository.StoredAccount =
        addedAccount(request("session", buildMap {
            put("access_token", accessToken)
            put("token_type", if (refresh) "refresh" else "access")
            if (clientId.isNotBlank()) put("client_id", clientId.trim())
        }).await())

    private suspend fun addedAccount(metadata: JsonElement): AccountRepository.StoredAccount {
        val id = AccountRepository.decode(metadata).id
        return AccountRepository.decode(request("switch", mapOf("account" to id)).await())
    }

    fun cancelLogin() {
        if (connection != null) request("cancel")
    }

    fun addOffline(username: String): AccountRepository.StoredAccount =
        AccountRepository.decode(request("offline", mapOf("username" to username)).get(30, TimeUnit.SECONDS))
            .let { switchAccount(it.id) }

    fun close() {
        val owned = synchronized(lock) {
            connection.also { connection = null }
        } ?: return
        runCatching { owned.writer.close() }
        owned.process.destroy()
        failPending(owned)
    }

    private fun request(operation: String, arguments: Map<String, String> = emptyMap(), onProgress: ((String) -> Unit)? = null): CompletableFuture<JsonElement> {
        val result = CompletableFuture<JsonElement>()
        try {
            synchronized(lock) {
                val current = connect()
                val id = nextId.incrementAndGet()
                current.pending[id] = result
                if (onProgress != null) current.progress[id] = onProgress
                result.orTimeout(if (operation == "login" || operation == "cookie") 900 else 120, TimeUnit.SECONDS)
                    .whenComplete { _, _ -> current.pending.remove(id, result); current.progress.remove(id) }
                val request = buildJsonObject {
                    put("id", id)
                    put("op", operation)
                    arguments.forEach { (key, value) -> put(key, value) }
                }
                current.writer.write(request.toString())
                current.writer.newLine()
                current.writer.flush()
            }
        } catch (_: Exception) {
            result.completeExceptionally(IllegalStateException("Could not connect to Arcadium Launcher. Reopen the launcher and try again"))
        }
        return result
    }

    private fun connect(): Connection {
        connection?.takeIf { it.process.isAlive }?.let { return it }
        val executable = System.getProperty("arcadium.launcherExecutable")?.let(::File)
            ?.takeIf { it.isAbsolute && it.isFile }
            ?: error("Arcadium launcher path is unavailable")
        val builder = ProcessBuilder(executable.absolutePath, "--account-service")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        System.getProperty("arcadium.launcherDataDir")?.let { builder.environment()["ARCADIUM_DATA_DIR"] = it }
        val process = builder.start()
        val current = Connection(process, process.outputStream.bufferedWriter(Charsets.UTF_8))
        connection = current
        Thread({
            try {
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        val value = runCatching { RuntimeServices.JSON.parseToJsonElement(line).jsonObject }.getOrNull()
                            ?: return@forEach
                        val id = value["id"]?.jsonPrimitive?.longOrNull ?: return@forEach
                        if (value["event"]?.jsonPrimitive?.contentOrNull == "progress") {
                            val message = when (value["stage"]?.jsonPrimitive?.contentOrNull) {
                                "cookie_browser_confirmation" -> "Confirm access in the Microsoft window..."
                                "cookie_verifying_identity" -> "Verifying the imported account..."
                                else -> null
                            }
                            if (message != null) runCatching { current.progress[id]?.invoke(message) }
                            return@forEach
                        }
                        val pending = current.pending.remove(id) ?: return@forEach
                        if (value["ok"]?.jsonPrimitive?.booleanOrNull == true) {
                            pending.complete(value["result"] ?: JsonNull)
                        } else {
                            pending.completeExceptionally(IllegalStateException(value["error"]?.jsonPrimitive?.contentOrNull ?: "Account operation failed"))
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                synchronized(lock) {
                    if (connection === current) connection = null
                }
                failPending(current)
                process.destroy()
            }
        }, "arcadium-account-reader").apply { isDaemon = true; start() }
        return current
    }

    private fun failPending(current: Connection) {
        val error = IllegalStateException("Arcadium account service closed. Please try again")
        current.pending.values.forEach { it.completeExceptionally(error) }
        current.pending.clear()
        current.progress.clear()
    }
}
