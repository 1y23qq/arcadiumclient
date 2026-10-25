// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.presence

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.userAgent
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import io.github.arcadiumclient.modern.ClientBuild
import io.github.arcadiumclient.modern.client.network.reconnectDelay
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object ArcadiumPresence {
    private class Identity(val uuid: UUID, val accessToken: String) {
        fun same(other: Identity?): Boolean = other != null && uuid == other.uuid && accessToken == other.accessToken
    }

    @Serializable
    private class Session(val token: String, val uuid: String, val expiresAt: Long)

    @Serializable
    private class Players(val type: String, val players: List<String>)

    private class AccountRejected : Exception()
    private class ProtocolRejected : Exception()
    private class LeaseRejected : Exception()
    private class Unavailable : Exception()

    private val initialized = AtomicBoolean()
    private val lock = Any()
    private val snapshot = PresenceSnapshot()
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private val json = Json { ignoreUnknownKeys = true }
    private val endpoint by lazy { PresenceEndpoint.configured() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> snapshot.clear() })
    private val http by lazy {
        HttpClient(CIO) {
            followRedirects = false
            expectSuccess = false
            defaultRequest { userAgent("Arcadium/${ClientBuild.VERSION}") }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 35_000
                requestTimeoutMillis = 15_000
            }
            install(WebSockets) { maxFrameSize = 12_288 }
        }
    }

    private var worker: Job? = null
    private var identity: Identity? = null
    private var generation = 0L
    private var scanTicks = 0
    @Volatile private var rejectedIdentity: Identity? = null
    @Volatile private var shuttingDown = false

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        eventHandler { _: TickEvent.End -> tick() }
        Runtime.getRuntime().addShutdownHook(Thread({ shutdown() }, "arcadium-presence-shutdown"))
    }

    @JvmStatic
    fun isOnline(uuid: UUID): Boolean = snapshot.contains(uuid)

    fun clearBadges() = snapshot.clear()

    private fun tick() {
        if (shuttingDown) return
        val endpoint = this.endpoint
        val client = Minecraft.getInstance()
        val current = if (endpoint != null && (ArcadiumPlayersConfig.showNametag || ArcadiumPlayersConfig.showTab)) currentIdentity(client) else null
        synchronized(lock) {
            val changed = if (current == null) identity != null else !current.same(identity)
            if (changed || (current != null && worker?.isCompleted != false && !current.same(rejectedIdentity))) {
                worker?.cancel()
                worker = null
                identity = current
                generation++
                snapshot.begin(generation)
                scanTicks = 0
                if (current != null && endpoint != null && !current.same(rejectedIdentity)) {
                    val ownedGeneration = generation
                    worker = scope.launch { connect(current, endpoint, ownedGeneration) }
                }
            }
            if (current != null && (changed || ++scanTicks >= 20)) {
                scanTicks = 0
                if (snapshot.watch(generation, visiblePlayers(client, current.uuid))) updates.trySend(Unit)
            }
        }
    }

    private fun currentIdentity(client: Minecraft): Identity? = runCatching {
        if (client.level == null || client.player == null) return null
        val uuid = client.user.profileId ?: return null
        val token = client.user.accessToken
        if (uuid.version() != 4 || token.length !in 32..8192 || token.any(Char::isWhitespace)) return null
        Identity(uuid, token)
    }.getOrNull()

    private fun visiblePlayers(client: Minecraft, self: UUID): Set<UUID> {
        val players = LinkedHashSet<UUID>()
        players += self
        val loaded = client.level?.players()?.asSequence()?.map { it.uuid }.orEmpty()
        val listed = client.connection?.onlinePlayers?.asSequence()?.mapNotNull { it.profile.id }.orEmpty()
        for (uuid in loaded + listed) {
            if (uuid.version() == 4) players += uuid
            if (players.size >= MAX_PRESENCE_PLAYERS) break
        }
        return players
    }

    private suspend fun createSession(identity: Identity, endpoint: PresenceEndpoint): Session {
        val response = http.post(endpoint.sessionUrl) { bearerAuth(identity.accessToken) }
        if (response.status.value == 401 || response.status.value == 403) throw AccountRejected()
        if (response.status.value != 200) throw Unavailable()
        val body = response.bodyAsText()
        if (body.length > 12_288) throw ProtocolRejected()
        val session = try { json.decodeFromString<Session>(body) } catch (_: Exception) { throw ProtocolRejected() }
        val now = System.currentTimeMillis()
        if (session.token.length != 64 || session.token.any { it !in '0'..'9' && it !in 'a'..'f' }) throw ProtocolRejected()
        if (presencePlayerId(session.uuid) != identity.uuid || session.expiresAt <= now + 30_000 || session.expiresAt > now + 24 * 60 * 60 * 1000L) throw ProtocolRejected()
        return session
    }

    private suspend fun connect(identity: Identity, endpoint: PresenceEndpoint, generation: Long) {
        var owned: Session? = null
        var attempt = 0
        val recovery = PresenceLeaseRecovery()
        try {
            while (currentCoroutineContext().isActive && snapshot.current(generation)) {
                var connectedAt: Long? = null
                try {
                    if (owned == null || owned.expiresAt <= System.currentTimeMillis() + 10_000) {
                        owned?.let { revoke(endpoint, it) }
                        owned = null
                        val creationDelay = recovery.creationDelay(monotonicMillis())
                        if (creationDelay > 0) delay(creationDelay)
                        recovery.creationStarted(monotonicMillis())
                        owned = createSession(identity, endpoint)
                    }
                    val session = owned ?: throw Unavailable()
                    var connected = false
                    var close: CloseReason? = null
                    val lifetime = (session.expiresAt - System.currentTimeMillis() - 10_000).coerceAtLeast(1_000)
                    withTimeoutOrNull(lifetime) {
                        http.webSocket(endpoint.socketUrl, request = {
                            expectSuccess = true
                            bearerAuth(session.token)
                        }) {
                            connected = true
                            connectedAt = monotonicMillis()
                            val alive = AtomicLong(System.nanoTime())
                            coroutineScope {
                                val sender = launch {
                                    var sent: Set<UUID>? = null
                                    suspend fun sendWatch() {
                                        val watched = snapshot.watches(generation)
                                        if (watched == sent) return
                                        send(Frame.Text(buildJsonObject {
                                            put("type", "watch")
                                            putJsonArray("players") { watched.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.presenceId())) } }
                                        }.toString()))
                                        sent = watched
                                    }
                                    sendWatch()
                                    for (update in updates) sendWatch()
                                }
                                val heartbeat = launch {
                                    while (isActive) {
                                        delay(25_000)
                                        if (System.nanoTime() - alive.get() > 70_000_000_000L) throw Unavailable()
                                        send(Frame.Text("ping"))
                                    }
                                }
                                try {
                                    for (frame in incoming) {
                                        if (frame !is Frame.Text || frame.data.size > 12_288) throw ProtocolRejected()
                                        val text = frame.readText()
                                        if (text != "pong") {
                                            val packet = try { json.decodeFromString<Players>(text) } catch (_: Exception) { throw ProtocolRejected() }
                                            if (packet.type != "players" || !snapshot.accept(generation, packet.players)) {
                                                if (!snapshot.current(generation)) return@coroutineScope
                                                throw ProtocolRejected()
                                            }
                                        }
                                        alive.set(System.nanoTime())
                                    }
                                    close = closeReason.await()
                                } finally {
                                    sender.cancel()
                                    heartbeat.cancel()
                                }
                            }
                        }
                    }
                    snapshot.clear(generation)
                    if (connected) attempt = 0
                    when (presencePolicyAction(close?.code?.toInt(), close?.message)) {
                        PresencePolicyAction.RENEW_LEASE -> throw LeaseRejected()
                        PresencePolicyAction.REJECT_PROTOCOL -> throw ProtocolRejected()
                        PresencePolicyAction.RECONNECT -> Unit
                    }
                    if (session.expiresAt <= System.currentTimeMillis() + 10_000) continue
                } catch (_: AccountRejected) {
                    if (snapshot.current(generation)) rejectedIdentity = identity
                    return
                } catch (_: ProtocolRejected) {
                    if (snapshot.current(generation)) rejectedIdentity = identity
                    return
                } catch (_: LeaseRejected) {
                    snapshot.clear(generation)
                    recovery.leaseRejected(monotonicMillis(), connectedAt?.let { monotonicMillis() - it } ?: 0)
                    val stale = owned
                    owned = null
                    stale?.let { withTimeoutOrNull(3_000) { revoke(endpoint, it) } }
                } catch (error: ClientRequestException) {
                    currentCoroutineContext().ensureActive()
                    if (presenceHandshakeNeedsNewLease(error.response.status.value)) {
                        snapshot.clear(generation)
                        recovery.leaseRejected(monotonicMillis(), 0)
                        val stale = owned
                        owned = null
                        stale?.let { withTimeoutOrNull(3_000) { revoke(endpoint, it) } }
                    }
                } catch (error: WebSocketException) {
                    currentCoroutineContext().ensureActive()
                    val status = presenceHandshakeStatus(error.message)
                    if (status != null && presenceHandshakeNeedsNewLease(status)) {
                        snapshot.clear(generation)
                        recovery.leaseRejected(monotonicMillis(), 0)
                        val stale = owned
                        owned = null
                        stale?.let { withTimeoutOrNull(3_000) { revoke(endpoint, it) } }
                    }
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                }
                snapshot.clear(generation)
                delay(reconnectDelay(++attempt))
            }
        } finally {
            snapshot.clear(generation)
            withContext(NonCancellable) {
                owned?.let { withTimeoutOrNull(3_000) { revoke(endpoint, it) } }
            }
        }
    }

    private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

    private suspend fun revoke(endpoint: PresenceEndpoint, session: Session) {
        try {
            http.delete(endpoint.sessionUrl) { bearerAuth(session.token) }
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
        }
    }

    private fun shutdown() {
        shuttingDown = true
        val owned = synchronized(lock) {
            generation++
            snapshot.begin(generation)
            worker.also { worker = null; identity = null; it?.cancel() }
        }
        runBlocking { withTimeoutOrNull(2_000) { owned?.join() } }
        if (initialized.get()) http.close()
        scope.cancel()
    }
}
