// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBackground as background
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBorder as border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.nio.file.Files
import java.nio.file.Path
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen
import org.apache.logging.log4j.LogManager
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import io.github.arcadiumclient.runtime.client.MenuPreferences
import io.github.arcadiumclient.runtime.ClientBuild

internal const val MENU_ICONS = "assets/oneconfig/ico/"

class ArcadiumWorldsScreen(private val parent: Screen?) : ComposeScreen(RenderMode.CONTINUOUS) {
    private var worlds by mutableStateOf(emptyList<WorldView>())
    private var selected by mutableStateOf<String?>(null)
    private var query by mutableStateOf("")
    private var hideNewer by mutableStateOf(MenuPreferences.hideNewerWorlds)
    private var loading by mutableStateOf(true)
    private var revision by mutableStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var dialog by mutableStateOf<WorldAction?>(null)
    private var rename by mutableStateOf("")
    private var opening by mutableStateOf(false)

    override fun init() {
        super.init()
        revision++
    }

    private fun close() = client.tell { client.openScreen(parent) }

    private fun play(world: WorldView) {
        if (world.metadata.needsWarning(ClientBuild.MINECRAFT_VERSION)) dialog = WorldAction.Open(world)
        else open(world)
    }

    private fun open(world: WorldView) {
        dialog = null
        client.tell {
            try {
                client.startGame(world.id, world.title, null)
            } catch (failure: Exception) {
                LOGGER.error("Could not open world {}", world.id, failure)
                error = "Could not open this world. Check the game log for details."
            }
        }
    }

    private suspend fun backupAndOpen(world: WorldView) {
        opening = true
        try {
            val backup = withContext(Dispatchers.IO) { WorldBackup.create(world.directory, ClientBuild.MINECRAFT_VERSION) }
            LOGGER.info("World backup created: {}", backup)
            if (client.screen === this) open(world)
        } catch (failure: Exception) {
            LOGGER.error("Could not back up world {}", world.id, failure)
            error = "Could not create the world backup. The world was not opened. Check available storage and the game log."
            dialog = null
        } finally {
            opening = false
        }
    }

    private fun recreate(world: WorldView) = client.tell {
        try {
            val storage = client.worldStorageSource.get(world.id, false)
            val data = storage.loadData() ?: throw IllegalStateException("World metadata is unavailable")
            storage.forceSave()
            val create = CreateWorldScreen(this)
            create.copyWorld(data)
            client.openScreen(create)
        } catch (failure: Exception) {
            LOGGER.error("Could not recreate world {}", world.id, failure)
            error = "Could not read the world settings."
        }
    }

    private fun renameWorld(world: WorldView) {
        val name = rename.trim()
        if (name.isEmpty()) return
        dialog = null
        client.tell {
            try {
                client.worldStorageSource.rename(world.id, name)
                revision++
            } catch (failure: Exception) {
                LOGGER.error("Could not rename world {}", world.id, failure)
                error = "Could not rename this world."
            }
        }
    }

    private fun deleteWorld(world: WorldView) {
        dialog = null
        client.tell {
            try {
                client.worldStorageSource.flush()
                if (!client.worldStorageSource.delete(world.id)) throw IllegalStateException("World deletion failed")
                selected = null
                revision++
            } catch (failure: Exception) {
                LOGGER.error("Could not delete world {}", world.id, failure)
                error = "Could not delete this world. Check that the folder is accessible."
            }
        }
    }

    @Composable
    override fun compose() {
        Theme {
            LaunchedEffect(revision) {
                loading = true
                error = null
                val result = withContext(Dispatchers.IO) { runCatching { readWorlds(client) } }
                result.onSuccess { worlds = it }.onFailure {
                    LOGGER.error("Could not enumerate worlds", it)
                    error = "Could not read the saves folder."
                }
                loading = false
            }
            val visible = remember(worlds, query, hideNewer) {
                worlds.filter { (!hideNewer || !it.metadata.newerThan(ClientBuild.MINECRAFT_VERSION)) && (query.isBlank()
                    || it.title.contains(query, true) || it.id.contains(query, true)) }
            }
            val current = visible.firstOrNull { it.id == selected }
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            ArcadiumMenuWindow("Singleplayer", error, onEscape = { if (!opening) { if (dialog != null) dialog = null else close() } }, onKey = {
                if (dialog != null || TextInputFocus.isTyping || it.type != KeyEventType.KeyDown) false
                else when (it.key) {
                    Key.Enter, Key.NumPadEnter -> { current?.let(::play); true }
                    Key.DirectionDown, Key.DirectionUp -> {
                        if (visible.isNotEmpty()) {
                            val next = (visible.indexOf(current) + if (it.key == Key.DirectionDown) 1 else -1).coerceIn(visible.indices)
                            selected = visible[next].id
                            scope.launch { state.animateScrollToItem(next) }
                        }
                        true
                    }
                    else -> false
                }
            }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UiTextField(query, { query = it }, Modifier.weight(1f), "Search worlds", MENU_ICONS + "search.svg", 160)
                    UiToggle("Hide newer worlds", hideNewer, Modifier.width(220.dp)) {
                        hideNewer = !hideNewer
                        MenuPreferences.hideNewerWorlds = hideNewer
                        MenuPreferences.save()
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(visible, key = { it.id }) { world ->
                            WorldCard(world, world.id == selected, onSelect = { selected = world.id }, onPlay = { play(world) })
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    if (visible.isEmpty()) {
                        UiText(
                            when { loading -> "Loading worlds..."; query.isNotBlank() -> "No matching worlds"; worlds.isEmpty() -> "No worlds yet"; else -> "Newer worlds are hidden" },
                            14.sp, Modifier.align(Alignment.Center), color = UiTextSecondary,
                        )
                    }
                }
                val hidden = if (hideNewer) worlds.count { it.metadata.newerThan(ClientBuild.MINECRAFT_VERSION) } else 0
                if (hidden > 0) UiText("$hidden ${if (hidden == 1) "newer world hidden" else "newer worlds hidden"}", 11.sp, color = UiTextSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Play", filled = true, enabled = current != null && !loading, modifier = Modifier.weight(1f)) { current?.let(::play) }
                    UiButton("Create world", modifier = Modifier.weight(1f)) { client.tell { client.openScreen(CreateWorldScreen(this@ArcadiumWorldsScreen)) } }
                    UiButton("Back", modifier = Modifier.width(100.dp)) { close() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Rename", enabled = current != null && !current.metadata.needsWarning(), modifier = Modifier.weight(1f)) {
                        current?.let { rename = it.title; dialog = WorldAction.Rename(it) }
                    }
                    UiButton("Re-create", enabled = current != null && !current.metadata.needsWarning(), modifier = Modifier.weight(1f)) { current?.let(::recreate) }
                    UiButton("Delete", danger = true, enabled = current != null, modifier = Modifier.weight(1f)) { current?.let { dialog = WorldAction.Delete(it) } }
                    UiIconButton(MENU_ICONS + "refresh.svg", tooltip = "Refresh worlds") { revision++ }
                }
            }
            dialog?.let { action ->
                UiModalScrim({ if (!opening) dialog = null }) {
                    ModalPanel(440.dp) {
                        when (action) {
                            is WorldAction.Open -> {
                                UiText("Open this world?", 19.sp, fontWeight = FontWeight.SemiBold)
                                UiText(action.world.title, 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                UiText(if (action.world.metadata.newerThan(ClientBuild.MINECRAFT_VERSION))
                                    "This world was saved in ${action.world.metadata.versionName().ifBlank { "a newer version of Minecraft" }}. Opening it in ${ClientBuild.MINECRAFT_VERSION} can remove newer blocks or damage the save. A backup will be created before opening."
                                    else "The world version could not be verified. Opening it in ${ClientBuild.MINECRAFT_VERSION} may damage the save. A backup will be created before opening.",
                                    13.sp, color = UiWarnColor)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UiButton("Cancel", modifier = Modifier.weight(1f), enabled = !opening) { dialog = null }
                                    UiButton(if (opening) "Creating backup..." else "Back up and open", filled = true, danger = true, enabled = !opening, modifier = Modifier.weight(1f)) { scope.launch { backupAndOpen(action.world) } }
                                }
                            }
                            is WorldAction.Delete -> {
                                UiText("Delete world?", 19.sp, fontWeight = FontWeight.SemiBold)
                                UiText("\"${action.world.title}\" will be permanently deleted. This cannot be undone.", 13.sp, maxLines = 4)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UiButton("Cancel", modifier = Modifier.weight(1f)) { dialog = null }
                                    UiButton("Delete", filled = true, danger = true, modifier = Modifier.weight(1f)) { deleteWorld(action.world) }
                                }
                            }
                            is WorldAction.Rename -> {
                                UiText("Rename world", 19.sp, fontWeight = FontWeight.SemiBold)
                                UiTextField(rename, { rename = it }, Modifier.fillMaxWidth(), "World name", maxLength = 255, onSubmit = { renameWorld(action.world) })
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UiButton("Cancel", modifier = Modifier.weight(1f)) { dialog = null }
                                    UiButton("Save", filled = true, enabled = rename.isNotBlank(), modifier = Modifier.weight(1f)) { renameWorld(action.world) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        val LOGGER = LogManager.getLogger("Arcadium Worlds")
    }
}

private class WorldView(val id: String, val directory: Path, val metadata: WorldMetadata) {
    val title = metadata.name().ifBlank { id }
    val date = if (metadata.lastPlayed() > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(metadata.lastPlayed())) else "Unknown date"
    val mode = if (metadata.hardcore()) "Hardcore" else when (metadata.gameMode()) { 1 -> "Creative"; 2 -> "Adventure"; 3 -> "Spectator"; else -> "Survival" }
    val icon: ImageBitmap? by lazy { menuImage(directory.resolve("icon.png")) }
}

private sealed interface WorldAction {
    val world: WorldView
    data class Open(override val world: WorldView) : WorldAction
    data class Delete(override val world: WorldView) : WorldAction
    data class Rename(override val world: WorldView) : WorldAction
}

private fun readWorlds(client: Minecraft): List<WorldView> {
    val root = client.gameDir.toPath().resolve("saves")
    if (!Files.exists(root)) return emptyList()
    return Files.list(root).use { directories ->
        directories.filter { Files.isDirectory(it) && !Files.isSymbolicLink(it) && (Files.exists(it.resolve("level.dat")) || Files.exists(it.resolve("level.dat_old"))) }
            .map { WorldView(it.fileName.toString(), it, WorldMetadata.read(it)) }.toList()
            .sortedWith(compareByDescending<WorldView> { it.metadata.lastPlayed() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
}

@Composable
private fun WorldCard(world: WorldView, selected: Boolean, onSelect: () -> Unit, onPlay: () -> Unit) {
    val (interaction, hovered) = rememberUiHover()
    val border by animateColorAsState(if (selected) Accent else if (hovered) UiHoverBorder else UiBorderColor)
    Row(
        Modifier.fillMaxWidth().clip(uiShape(7.dp)).background(if (selected) UiControlBackground else UiCardBackground)
            .border(1.dp, border, uiShape(7.dp)).hoverable(interaction)
            .combinedClickable(interactionSource = interaction, indication = null, onDoubleClick = onPlay, onClick = onSelect).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuImage(world.icon, MENU_ICONS + "folder.svg")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(world.title, color = UiTextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiText("${world.mode}  ·  ${world.date}", 11.sp, color = UiTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            UiText(world.id, 11.sp, color = UiTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (world.metadata.needsWarning(ClientBuild.MINECRAFT_VERSION)) {
            UiText(if (world.metadata.newerThan(ClientBuild.MINECRAFT_VERSION)) world.metadata.versionName().ifBlank { "Newer version" } else "Unverified version", 11.sp, color = UiWarnColor)
        }
    }
}

@Composable
internal fun ArcadiumMenuWindow(title: String, error: String?, onEscape: () -> Unit, onKey: (KeyEvent) -> Boolean = { false }, maxWidth: Dp = 1040.dp, maxHeight: Dp = Dp.Unspecified, dimBackground: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().then(if (dimBackground) Modifier.background(UiScrim) else Modifier).onPreviewKeyEvent {
            if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) { onEscape(); true } else onKey(it)
        }.padding(20.dp), contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().heightIn(max = maxHeight).fillMaxHeight().clip(uiShape(12.dp))
                .background(UiWindowBackground).border(1.dp, UiBorderColor, uiShape(12.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(UI_ASSETS + "logo.svg", UiTextPrimary, Modifier.size(25.dp))
                UiText(title, 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                UiIconButton(MENU_ICONS + "close.svg", tooltip = "Back", onClick = onEscape)
            }
            error?.let { UiText(it, 12.sp, color = UiDangerColor, maxLines = 3) }
            content()
        }
    }
}

@Composable
internal fun MenuImage(icon: ImageBitmap?, fallback: String) {
    if (icon != null) {
        Image(icon, null, Modifier.size(54.dp).clip(uiShape(4.dp)), filterQuality = FilterQuality.None)
    } else {
        Box(Modifier.size(54.dp).clip(uiShape(4.dp)).background(UiControlBackground), contentAlignment = Alignment.Center) {
            Icon(fallback, UiTextSecondary, Modifier.size(26.dp))
        }
    }
}

internal fun menuImage(path: Path): ImageBitmap? = runCatching {
    if (!Files.isRegularFile(path) || Files.size(path) > 1024 * 1024) return@runCatching null
    menuImage(Files.readAllBytes(path))
}.getOrNull()

internal fun menuImage(bytes: ByteArray): ImageBitmap? = runCatching {
    if (bytes.size > 1024 * 1024) return@runCatching null
    val image = SkiaImage.makeFromEncoded(bytes)
    if (image.width !in 1..256 || image.height !in 1..256) { image.close(); return@runCatching null }
    image.toComposeImageBitmap()
}.getOrNull()
