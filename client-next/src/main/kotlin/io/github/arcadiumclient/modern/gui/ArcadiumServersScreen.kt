// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.VerticalScrollbar
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBackground as background
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBorder as border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import net.minecraft.server.network.EventLoopGroupHolder
import net.minecraft.SharedConstants
import net.minecraft.client.server.LanServerDetection as LanServerQueryManager
import net.minecraft.client.multiplayer.ServerStatusPinger as MultiplayerServerListPinger
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.ServerData as ServerListEntry
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.Theme

class ArcadiumServersScreen(private val parent: Screen?) : ArcadiumScreen() {
    private val servers = ServerList(client)
    private var pinger = MultiplayerServerListPinger()
    private var previewPinger = MultiplayerServerListPinger()
    private var directPreview by mutableStateOf<ServerListEntry?>(null)
    @Volatile private var previewCycle = 0L
    private val lan = LanServerQueryManager.LanServerList()
    private var detector: LanServerQueryManager.LanServerDetector? = null
    private var pool = newPingPool()
    private var entries by mutableStateOf(emptyList<ServerListEntry>())
    private var listRevision by mutableIntStateOf(0)
    private var openRevision by mutableIntStateOf(0)
    private var local by mutableStateOf(emptyList<ServerListEntry>())
    private var lanAvailable by mutableStateOf(false)
    private var selected by mutableStateOf<ServerListEntry?>(null)
    private var query by mutableStateOf("")
    private var pingsRevision by mutableIntStateOf(0)
    private var ticks = 0
    @Volatile private var closed = true
    @Volatile private var cycle = 0L
    private var dialog by mutableStateOf<ServerDialog?>(null)
    private var serverName by mutableStateOf("")
    private var serverAddress by mutableStateOf("")
    private var packStatus by mutableStateOf(ServerListEntry.ServerPackStatus.PROMPT)
    private var error by mutableStateOf<String?>(null)
    private var dragging by mutableStateOf<ServerListEntry?>(null)

    override fun init() {
        super.init()
        if (!closed) return
        closed = false
        openRevision++
        cycle++
        pinger = MultiplayerServerListPinger()
        previewPinger = MultiplayerServerListPinger()
        if (pool.isShutdown) pool = newPingPool()
        servers.load()
        refreshEntries()
        entries.forEach(::ping)
        try {
            detector = LanServerQueryManager.LanServerDetector(lan).also { it.start() }
            lanAvailable = true
        } catch (failure: Exception) {
            lanAvailable = false
            LOGGER.warn("LAN discovery could not be started", failure)
        }
    }

    override fun tick() {
        super.tick()
        if (closed) return
        pinger.tick()
        previewPinger.tick()
        if (++ticks % 4 == 0) pingsRevision++
        lan.takeDirtyServers()?.let { found ->
            local = found.map { ServerListEntry("LAN Server", it.address, ServerListEntry.Type.LAN).apply { motd = Component.literal(it.motd) } }
        }
    }

    override fun removed() {
        closed = true
        cycle++
        detector?.interrupt()
        detector = null
        pool.shutdownNow()
        pinger.removeAll()
        previewCycle++
        previewPinger.removeAll()
        dragging = null
        super.removed()
    }

    private fun refreshEntries() {
        val previous = selected
        entries = (0 until servers.size()).map { servers.get(it) }
        if (previous != null && !previous.isLan) {
            selected = entries.firstOrNull { it === previous || (it.ip == previous.ip && it.name == previous.name) }
        }
        listRevision++
    }

    private fun ping(server: ServerListEntry) {
        val currentPinger = pinger
        val currentCycle = cycle
        server.setState(ServerListEntry.State.PINGING)
        server.ping = -2
        server.motd = Component.literal("Pinging...")
        server.status = Component.empty()
        pool.execute {
            if (closed || cycle != currentCycle) return@execute
            try {
                currentPinger.pingServer(server, { client.execute { pingsRevision++ } }, { client.execute { servers.save() } }, EventLoopGroupHolder.remote(client.options.useNativeTransport()))
            } catch (_: UnknownHostException) {
                server.ping = -1
                server.motd = Component.literal("§cCannot resolve hostname")
            } catch (_: Exception) {
                server.ping = -1
                server.motd = Component.literal("§cCannot connect to server")
            }
            if (closed || cycle != currentCycle) currentPinger.removeAll()
        }
    }

    private fun close() = client.execute { client.gui.setScreen(parent) }

    override fun onClose() {
        if (dialog != null) dialog = null else close()
    }

    private fun preview(address: String) {
        previewCycle++
        val generation = previewCycle
        previewPinger.removeAll()
        directPreview = null
        if (address.isBlank()) return
        val server = ServerListEntry(address, address, ServerListEntry.Type.OTHER).apply {
            ping = -2
            motd = Component.literal("Pinging...")
            status = Component.empty()
        }
        directPreview = server
        pool.execute {
            if (closed || generation != previewCycle) return@execute
            try {
                previewPinger.pingServer(server, { client.execute { pingsRevision++ } }, {}, EventLoopGroupHolder.remote(client.options.useNativeTransport()))
            } catch (_: UnknownHostException) {
                server.ping = -1
                server.motd = Component.literal("§cCannot resolve hostname")
            } catch (_: Exception) {
                server.ping = -1
                server.motd = Component.literal("§cCannot connect to server")
            }
        }
    }

    private fun connect(server: ServerListEntry) = client.execute {
        if (dialog == ServerDialog.Direct) {
            client.options.lastMpIp = server.ip
            client.options.save()
        }
        dialog = null
        ConnectScreen.startConnecting(this@ArcadiumServersScreen, client, ServerAddress.parseString(server.ip), server, false, null)
    }

    private fun edit(server: ServerListEntry? = null) {
        serverName = server?.name ?: ""
        serverAddress = server?.ip ?: ""
        packStatus = server?.resourcePackStatus ?: ServerListEntry.ServerPackStatus.PROMPT
        dialog = ServerDialog.Edit(server)
    }

    private fun save(server: ServerListEntry?) {
        if (serverName.isBlank() || serverAddress.isBlank()) return
        val index = server?.let { entries.indexOf(it) } ?: -1
        if (server != null && index < 0) return
        val entry = ServerListEntry(serverName.trim(), serverAddress.trim(), ServerListEntry.Type.OTHER)
        entry.resourcePackStatus = packStatus
        try {
            if (server == null) servers.add(entry, false) else servers.replace(index, entry)
            servers.save()
            refreshEntries()
            selected = entry
            dialog = null
            ping(entry)
        } catch (failure: Exception) {
            LOGGER.error("Could not save the server list", failure)
            error = "Could not save the server list."
        }
    }

    private fun delete(server: ServerListEntry) {
        val index = entries.indexOf(server)
        if (index < 0) return
        servers.remove(server)
        servers.save()
        selected = null
        dialog = null
        refreshEntries()
    }

    private fun reorder(server: ServerListEntry, target: Int) {
        val from = entries.indexOf(server)
        val reordered = entries.toMutableList()
        if (!ServerListDrag.reorder(reordered, from, target)) return
        reordered.forEachIndexed { i, entry -> servers.replace(i, entry) }
        entries = reordered
    }

    @Composable
    override fun compose() {
        Theme {
            LaunchedEffect(dialog, serverAddress) {
                if (dialog == ServerDialog.Direct) {
                    delay(350)
                    preview(serverAddress.trim())
                } else {
                    previewCycle++
                    previewPinger.removeAll()
                    directPreview = null
                }
            }
            val visible = remember(entries, query, listRevision) { entries.filter { query.isBlank() || it.name.contains(query, true) || it.ip.contains(query, true) } }
            val locals = remember(local, query) { local.filter { query.isBlank() || it.motd.string.contains(query, true) || it.ip.contains(query, true) } }
            val state = rememberLazyListState()
            LaunchedEffect(openRevision) {
                state.requestScrollToItem(if (OneConfigConfig.serverListStartPosition == 1) visible.size + maxOf(1, locals.size) else 0)
            }
            val scope = rememberCoroutineScope()
            val latestEntries by rememberUpdatedState(entries)
            val generation = pingsRevision + listRevision
            ArcadiumMenuWindow("Multiplayer", error, onEscape = ::onClose, onKey = {
                    if (dialog != null || it.type != KeyEventType.KeyDown || TextInputFocus.isTyping) false
                    else when (it.key) {
                        Key.Enter, Key.NumPadEnter -> { selected?.let(::connect); true }
                        Key.DirectionDown, Key.DirectionUp -> {
                            val all = visible + locals
                            val next = (all.indexOf(selected) + if (it.key == Key.DirectionDown) 1 else -1).coerceIn(0, (all.size - 1).coerceAtLeast(0))
                            selected = all.getOrNull(next)
                            if (all.isNotEmpty()) scope.launch { state.animateScrollToItem(next) }
                            true
                        }
                        else -> false
                    }
                }) {
                UiTextField(query, { query = it }, Modifier.fillMaxWidth(), "Search servers", MENU_ICONS + "search.svg", 160)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        itemsIndexed(visible, key = { _, entry -> entry }) { _, server ->
                            var center by remember { mutableStateOf(0f) }
                            val draggable = if (query.isEmpty()) Modifier.pointerInput(server) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = server
                                        selected = server
                                        center = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key === server }?.let { it.offset + it.size / 2f } ?: 0f
                                    },
                                    onDragEnd = { dragging = null; servers.save() },
                                    onDragCancel = { dragging = null; servers.save() },
                                ) { change, delta ->
                                    change.consume()
                                    center += delta.y
                                    val target = state.layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - center) }
                                    target?.let { if (latestEntries.isNotEmpty()) reorder(server, it.index.coerceIn(latestEntries.indices)) }
                                    val info = state.layoutInfo
                                    if (center < info.viewportStartOffset + 30 || center > info.viewportEndOffset - 30) {
                                        scope.launch { state.scrollBy(if (center < info.viewportStartOffset + 30) -18f else 18f) }
                                    }
                                }
                            } else Modifier
                            ServerCard(server, server === selected, server === dragging, generation, draggable, { selected = server }, { connect(server) })
                        }
                        item(key = "lan-heading") {
                            UiText("Local network", 12.sp, color = UiTextSecondary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        }
                        itemsIndexed(locals, key = { _, entry -> "lan:${entry.ip}" }) { _, server ->
                            ServerCard(server, selected?.isLan == true && server.ip == selected?.ip, false, generation, Modifier, { selected = server }, { connect(server) })
                        }
                        if (locals.isEmpty()) item(key = "lan-searching") { UiText(if (lanAvailable) "Scanning for LAN worlds..." else "LAN discovery unavailable", 12.sp, color = UiTextSecondary) }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    if (visible.isEmpty() && locals.isEmpty() && (query.isNotEmpty() || entries.isEmpty())) {
                        UiText(if (query.isEmpty()) "Add a server to get started" else "No matching servers", 14.sp, Modifier.align(Alignment.Center), color = UiTextSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Join server", filled = true, enabled = selected != null, modifier = Modifier.weight(1f)) { selected?.let(::connect) }
                    UiButton("Direct connect", modifier = Modifier.weight(1f)) { serverAddress = client.options.lastMpIp.orEmpty(); dialog = ServerDialog.Direct }
                    UiButton("Back", modifier = Modifier.width(100.dp)) { close() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Add server", modifier = Modifier.weight(1f)) { edit() }
                    UiButton("Edit", enabled = selected in entries, modifier = Modifier.weight(1f)) { selected?.let(::edit) }
                    UiButton("Delete", enabled = selected in entries, modifier = Modifier.weight(1f), danger = true) { selected?.let { dialog = ServerDialog.Delete(it) } }
                    UiIconButton(MENU_ICONS + "refresh.svg", tooltip = "Refresh servers") { client.execute { client.gui.setScreen(ArcadiumServersScreen(parent)) } }
                }
            }
            dialog?.let { current ->
                UiModalScrim({ dialog = null }) {
                    ModalPanel(440.dp) {
                        when (current) {
                            is ServerDialog.Delete -> {
                                UiText("Delete server?", 19.sp, fontWeight = FontWeight.SemiBold)
                                UiText("\"${current.server.name}\" will be removed from your server list.", 13.sp, maxLines = 4)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UiButton("Cancel", modifier = Modifier.weight(1f)) { dialog = null }
                                    UiButton("Delete", filled = true, danger = true, modifier = Modifier.weight(1f)) { delete(current.server) }
                                }
                            }
                            is ServerDialog.Edit -> {
                                UiText(if (current.server == null) "Add server" else "Edit server", 19.sp, fontWeight = FontWeight.SemiBold)
                                UiTextField(serverName, { serverName = it }, Modifier.fillMaxWidth(), "Server name", maxLength = 128)
                                UiTextField(serverAddress, { serverAddress = it }, Modifier.fillMaxWidth(), "Server address", maxLength = 255, onSubmit = { save(current.server) })
                                UiDropdown("Server resource packs", ServerListEntry.ServerPackStatus.values().toList(), packStatus, { when (it) {
                                    ServerListEntry.ServerPackStatus.ENABLED -> "Enabled"
                                    ServerListEntry.ServerPackStatus.DISABLED -> "Disabled"
                                    else -> "Prompt"
                                } }, Modifier.fillMaxWidth()) { packStatus = it }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UiButton("Cancel", modifier = Modifier.weight(1f)) { dialog = null }
                                    UiButton("Save", filled = true, enabled = serverName.isNotBlank() && serverAddress.isNotBlank(), modifier = Modifier.weight(1f)) { save(current.server) }
                                }
                            }
                            ServerDialog.Direct -> {
                                UiText("Direct connect", 19.sp, fontWeight = FontWeight.SemiBold)
                                UiTextField(serverAddress, { serverAddress = it }, Modifier.fillMaxWidth(), "Server address", maxLength = 255, onSubmit = {
                                    if (serverAddress.isNotBlank()) connect(ServerListEntry("Direct connect", serverAddress.trim(), ServerListEntry.Type.OTHER))
                                })
                                directPreview?.let { server ->
                                    ServerCard(server, false, false, pingsRevision, Modifier, {}, { connect(server) })
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UiButton("Cancel", modifier = Modifier.weight(1f)) { dialog = null }
                                    UiButton("Join server", filled = true, enabled = serverAddress.isNotBlank(), modifier = Modifier.weight(1f)) {
                                        connect(ServerListEntry("Direct connect", serverAddress.trim(), ServerListEntry.Type.OTHER))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val LOGGER = LogManager.getLogger("Arcadium Servers")
        fun newPingPool() = Executors.newFixedThreadPool(2) { task -> Thread(task, "Arcadium Server Pinger").apply { isDaemon = true } }
    }
}

private sealed interface ServerDialog {
    data class Edit(val server: ServerListEntry?) : ServerDialog
    data class Delete(val server: ServerListEntry) : ServerDialog
    data object Direct : ServerDialog
}

@Composable
private fun ServerCard(server: ServerListEntry, selected: Boolean, dragging: Boolean, revision: Int, modifier: Modifier, onSelect: () -> Unit, onConnect: () -> Unit) {
    val (interaction, hovered) = rememberUiHover()
    val border by animateColorAsState(if (selected || dragging) Accent else if (hovered) UiHoverBorder else UiBorderColor)
    val view = remember(server, revision) { ServerStatusView(server.name, componentFormatting(server.motd), componentFormatting(server.status), server.ping, componentFormatting(server.version), server.protocol, server.isLan, server.iconBytes?.let { Base64.getEncoder().encodeToString(it) }) }
    val icon = remember(view.icon) {
        view.icon?.let { encoded -> if (encoded.length > 128 * 1024) null else runCatching { menuImage(Base64.getDecoder().decode(encoded)) }.getOrNull() }
    }
    Row(
        modifier.fillMaxWidth().clip(uiShape(7.dp)).background(if (selected || dragging) UiControlBackground else UiCardBackground)
            .border(1.dp, border, uiShape(7.dp)).hoverable(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, onDoubleClick = onConnect, onClick = onSelect).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuImage(icon, MENU_ICONS + "profiles.svg")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(view.name, color = UiTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MinecraftText(view.motd, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = UiTextSecondary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (view.local) UiText("LAN", 11.sp, color = UiTextSecondary)
            else {
                MinecraftText(if (view.ping >= 0 && view.protocol != SharedConstants.getProtocolVersion()) view.version else view.status,
                    color = if (view.ping >= 0 && view.protocol != SharedConstants.getProtocolVersion()) UiDangerColor else UiTextSecondary,
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                UiText(when { view.ping == -2L -> "Pinging..."; view.ping < 0 -> "Offline"; else -> "${view.ping} ms" }, 11.sp,
                    color = when { view.ping < 0 -> UiTextSecondary; view.ping < 150 -> Color(0xFF55FF55); view.ping < 300 -> Color(0xFFFFFF55); else -> Color(0xFFFF5555) })
            }
        }
    }
}

private data class ServerStatusView(val name: String, val motd: String, val status: String, val ping: Long, val version: String, val protocol: Int, val local: Boolean, val icon: String?)
