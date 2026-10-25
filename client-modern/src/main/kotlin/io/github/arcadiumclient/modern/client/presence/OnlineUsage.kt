// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.presence

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareDelete
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.utils.io.readAvailable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import io.github.arcadiumclient.modern.ClientBuild

internal class UsageIdentity(val installationId: String, val secret: String)

internal suspend fun acceptedUsageResponse(response: HttpResponse): Boolean {
    val channel = response.bodyAsChannel()
    try {
        if (response.status.value !in 200..299
            || (response.headers["Content-Length"]?.toLongOrNull() ?: 0L) > 4096L) return false
        val buffer = ByteArray(4097)
        var received = 0
        while (received < buffer.size) {
            val count = channel.readAvailable(buffer, received, buffer.size - received)
            if (count < 0) return true
            received += count
        }
        return false
    } finally { channel.cancel(null) }
}

internal object OnlineUsageFiles {
    fun identity(file: File): UsageIdentity? = runCatching {
        val data = Json.parseToJsonElement(read(file, 2048)).jsonObject
        if (data["version"]?.jsonPrimitive?.intOrNull != 1) return null
        val id = data["installationId"]?.jsonPrimitive?.content ?: return null
        val uuid = UUID.fromString(id)
        if (uuid.version() != 4 || uuid.variant() != 2 || uuid.toString() != id) return null
        val secret = data["secret"]?.jsonPrimitive?.content ?: return null
        if (secret.length != 64 || secret.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        UsageIdentity(id, secret)
    }.getOrNull()

    fun enabled(file: File): Boolean = runCatching {
        val data = Json.parseToJsonElement(read(file, 1024 * 1024)).jsonObject
        val accepted = data["accepted_tos_version"]?.jsonPrimitive?.intOrNull ?: 0
        val completed = data["seen_onboarding"]?.jsonPrimitive?.booleanOrNull ?: false
        val declined = data["declined_tos"]?.jsonPrimitive?.booleanOrNull ?: false
        val enabled = data["online_count_enabled"]?.jsonPrimitive?.booleanOrNull ?: true
        (completed || accepted > 0) && !declined && enabled
    }.getOrDefault(false)

    private fun read(file: File, maximum: Int): String {
        val bytes = file.inputStream().use { it.readNBytes(maximum + 1) }
        require(bytes.size <= maximum)
        return bytes.toString(Charsets.UTF_8)
    }
}

object OnlineUsage {
    private val initialized = AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = lazy {
        HttpClient(CIO) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = 3_000
                requestTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
            }
        }
    }

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        val directory = System.getProperty("arcadium.launcherDataDir")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        val endpoint = PresenceEndpoint.parse(
            System.getProperty("arcadium.usageEndpoint") ?: DEFAULT_PRESENCE_ENDPOINT,
            DEFAULT_PRESENCE_ENDPOINT,
        ) ?: return
        val worker = scope.launch {
            val identity = OnlineUsageFiles.identity(File(directory, "usage-identity.json")) ?: return@launch
            val session = UUID.randomUUID().toString()
            val body = buildJsonObject {
                put("installationId", identity.installationId)
                put("sessionId", session)
                put("client", "game")
            }.toString()
            var leased = false
            suspend fun request(revoke: Boolean): Boolean = try {
                val url = "${endpoint.origin}/v1/usage/heartbeat"
                val statement = if (revoke) client.value.prepareDelete(url) {
                    bearerAuth(identity.secret)
                    userAgent("Arcadium/${ClientBuild.VERSION}")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                } else client.value.preparePost(url) {
                    bearerAuth(identity.secret)
                    userAgent("Arcadium/${ClientBuild.VERSION}")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                statement.execute { response -> acceptedUsageResponse(response) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            try {
                while (isActive) {
                    if (OnlineUsageFiles.enabled(File(directory, "settings.json"))) {
                        leased = request(false) || leased
                    } else if (leased && request(true)) {
                        leased = false
                    }
                    delay(30_000)
                }
            } finally {
                if (leased) withContext(NonCancellable) { withTimeoutOrNull(2_000) { request(true) } }
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread({
            worker.cancel()
            runBlocking { withTimeoutOrNull(2_500) { worker.join() } }
            scope.cancel()
            if (client.isInitialized()) client.value.close()
        }, "arcadium-online-count-shutdown"))
    }
}
