// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBackground as background
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBorder as border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import java.awt.image.BufferedImage
import java.io.File
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.resource.pack.ResourcePacks
import org.apache.logging.log4j.LogManager
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.internal.FontPreferences
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import io.github.arcadiumclient.runtime.mixin.client.access.ResourcePackEntryAccessor
import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackSearch
import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackCompatibility
import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackImages
import io.github.arcadiumclient.runtime.client.MenuPreferences
import io.github.arcadiumclient.runtime.client.resourcepack.CustomPackMaker

private const val PACK_ICONS = "assets/oneconfig/ico/"
private val PACK_FORMATTING = Regex("§[0-9a-fk-or]", RegexOption.IGNORE_CASE)

class ArcadiumResourcePacksScreen(private val parent: Screen?) : ComposeScreen(RenderMode.CONTINUOUS) {
    private val repository = client.resourcePacks
    private var packs by mutableStateOf(emptyList<PackView>())
    private var active by mutableStateOf(emptyList<String>())
    private var applied = emptyList<String>()
    private var expanded by mutableStateOf(setOf<String>())
    private var query by mutableStateOf("")
    private var sort by mutableStateOf(PackSort.NAME)
    private var onlyCompatible by mutableStateOf(MenuPreferences.compatibleResourcePacksOnly)
    private var pending by mutableStateOf<PackView?>(null)
    private var pendingIndex = 0
    private var dragging by mutableStateOf<PackDrag?>(null)
    private var dragPosition by mutableStateOf(Offset.Zero)
    private val cardBounds = mutableMapOf<PackDrag, Rect>()
    private var availableBounds = Rect.Zero
    private var activeBounds = Rect.Zero
    private var availableListBounds = Rect.Zero
    private var activeListBounds = Rect.Zero
    private var dragRoot = Offset.Zero
    private var dragControlWidth = 1f
    private var applying by mutableStateOf(false)
    private var closeAfterApply = false
    private var error by mutableStateOf<String?>(null)
    private var vanillaGameFont by mutableStateOf(OneConfigConfig.useVanillaFontInGame)
    private var vanillaModFont by mutableStateOf(OneConfigConfig.useVanillaFontForModText)
    private val defaultIcon by lazy { runCatching { repository.defaultPack.icon.toPackBitmap() }.getOrNull() }

    init {
        refresh(false)
    }

    private fun refresh(keepSelection: Boolean = true) {
        try {
            val selection = if (keepSelection) active else repository.selectedPacks.asReversed().map { it.name }
            repository.reload()
            packs = repository.availablePacks.map { PackView(it) }
            active = selection.filter { id -> packs.any { it.id == id } }
            if (!keepSelection) applied = active.toList()
            error = null
        } catch (failure: Exception) {
            LOGGER.warn("Could not refresh resource packs", failure)
            error = "Could not read resource packs. Check that the folder is accessible."
        }
    }

    private fun add(pack: PackView, index: Int = 0) {
        if (pack.format < 0 || pack.id in active) return
        if (pack.compatible || pack.id in client.options.incompatibleResourcePacks) {
            active = active.toMutableList().apply { add(index.coerceIn(0, size), pack.id) }
        } else {
            pending = pack
            pendingIndex = index
        }
    }

    private fun move(id: String, offset: Int) {
        val index = active.indexOf(id)
        val target = index + offset
        if (index < 0 || target !in active.indices) return
        active = active.toMutableList().apply { add(target, removeAt(index)) }
    }

    private fun remove(id: String) {
        active = active - id
    }

    private fun confirmPending() {
        pending?.let { pack -> if (pack.id !in active) active = active.toMutableList().apply { add(pendingIndex.coerceIn(0, size), pack.id) } }
        pending = null
    }

    private fun dropIndex(): Int {
        val visible = active.mapIndexedNotNull { index, id -> cardBounds[PackDrag(id, true)]?.let { index to it } }
        return visible.firstOrNull { dragPosition.y < it.second.center.y }?.first
            ?: visible.lastOrNull()?.let { it.first + 1 } ?: 0
    }

    private fun dropPack(drag: PackDrag) {
        if (applying || pending != null) return
        if (activeBounds.contains(dragPosition)) {
            val index = dropIndex()
            if (drag.fromActive) {
                val from = active.indexOf(drag.id)
                if (from < 0) return
                active = active.toMutableList().apply {
                    removeAt(from)
                    add((if (from < index) index - 1 else index).coerceIn(0, size), drag.id)
                }
            } else packs.firstOrNull { it.id == drag.id }?.let { add(it, index) }
        } else if (drag.fromActive && availableBounds.contains(dragPosition)) {
            remove(drag.id)
            var prefix = ""
            for (part in drag.id.split('/').dropLast(1)) {
                prefix = if (prefix.isEmpty()) part else "$prefix/$part"
                expanded = expanded + prefix
            }
        }
    }

    private fun Modifier.packDragGestures(): Modifier = pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (applying || pending != null) return@awaitEachGesture
            val point = down.position + dragRoot
            val candidate = cardBounds.entries.firstOrNull { (key, rect) ->
                (if (key.fromActive) activeListBounds else availableListBounds).contains(point)
                    && rect.contains(point) && point.x < rect.right - dragControlWidth * (if (key.fromActive) 90f else 48f)
                    && packs.any { it.id == key.id && it.format >= 0 }
            }?.key ?: return@awaitEachGesture
            val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                ?: return@awaitEachGesture
            dragging = candidate
            dragPosition = start.position + dragRoot
            try {
                val completed = drag(start.id) { change ->
                    dragPosition = change.position + dragRoot
                    change.consume()
                }
                if (completed) dropPack(candidate)
            } finally {
                dragging = null
            }
        }
    }

    @Composable
    private fun cardModifier(id: String, active: Boolean): Modifier {
        val key = remember(id, active) { PackDrag(id, active) }
        DisposableEffect(key) { onDispose { cardBounds.remove(key) } }
        return Modifier.onGloballyPositioned { cardBounds[key] = it.boundsInRoot() }
            .semantics { contentDescription = "Resource pack $id" }
            .alpha(if (dragging == key) 0.45f else 1f)
    }

    private fun finish(close: Boolean) {
        if (applying) return
        if (active == applied) {
            if (close) client.tell { client.openScreen(parent) }
            return
        }
        closeAfterApply = close
        applying = true
        error = null
    }

    private fun applySelection() {
        val oldSelection = repository.selectedPacks.toList()
        val oldPacks = client.options.resourcePacks.toList()
        val oldIncompatible = client.options.incompatibleResourcePacks.toList()
        try {
            val selected = active.asReversed().mapNotNull { id -> packs.firstOrNull { it.id == id } }
            repository.setSelectedPacks(selected.map { it.entry })
            client.options.resourcePacks.clear()
            client.options.resourcePacks.addAll(selected.map { it.id })
            client.options.incompatibleResourcePacks.clear()
            client.options.incompatibleResourcePacks.addAll(selected.filter { it.format != 1 }.map { it.id })
            client.reloadResources()
            client.options.save()
            applied = active.toList()
            if (closeAfterApply) client.openScreen(parent)
        } catch (failure: Exception) {
            LOGGER.error("Could not apply resource packs", failure)
            repository.setSelectedPacks(oldSelection)
            client.options.resourcePacks.clear()
            client.options.resourcePacks.addAll(oldPacks)
            client.options.incompatibleResourcePacks.clear()
            client.options.incompatibleResourcePacks.addAll(oldIncompatible)
            runCatching { client.reloadResources() }
            error = "Could not apply resource packs. Your previous selection has been restored."
        } finally {
            applying = false
        }
    }

    @Composable
    override fun compose() {
        Theme {
            val density = LocalDensity.current
            dragControlWidth = density.density
            val availableList = rememberLazyListState()
            val activeList = rememberLazyListState()
            LaunchedEffect(dragging) {
                while (dragging != null) {
                    val available = availableListBounds.contains(dragPosition)
                    val bounds = if (available) availableListBounds else activeListBounds
                    if (bounds.contains(dragPosition)) {
                        val margin = 36f * density.density
                        val amount = when {
                            dragPosition.y < bounds.top + margin -> -10f * density.density
                            dragPosition.y > bounds.bottom - margin -> 10f * density.density
                            else -> 0f
                        }
                        if (amount != 0f) (if (available) availableList else activeList).scrollBy(amount)
                    }
                    delay(16)
                }
            }
            LaunchedEffect(applying) {
                if (applying) {
                    delay(40)
                    client.tell { applySelection() }
                }
            }
            val byId = remember(packs) { packs.associateBy { it.id } }
            val tree = remember(packs, active, expanded, query, sort, onlyCompatible) {
                packRows(packs.filter { it.id !in active && (!onlyCompatible || it.compatible) }, expanded, query, sort)
            }
            val activeResults = remember(packs, active, query) {
                val terms = ResourcePackSearch.terms(query)
                active.filter { id -> byId[id]?.let { ResourcePackSearch.matches(terms, it.searchText) } == true }
            }
            Box(
                Modifier.fillMaxSize().background(UiScrim).onPreviewKeyEvent {
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                        if (pending != null) pending = null else finish(true)
                        true
                    } else false
                }.padding(20.dp).onGloballyPositioned { dragRoot = it.positionInRoot() }.packDragGestures(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.widthIn(max = 1120.dp).fillMaxWidth().fillMaxHeight()
                        .clip(uiShape(12.dp)).background(UiWindowBackground)
                        .border(1.dp, UiBorderColor, uiShape(12.dp)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(UI_ASSETS + "logo.svg", UiTextPrimary, Modifier.size(26.dp))
                        UiText("Resource Packs", 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        UiIconButton(PACK_ICONS + "close.svg", tooltip = "Done") { finish(true) }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PackPane(Modifier.weight(1f).onGloballyPositioned { availableBounds = it.boundsInRoot() }, "Available",
                            dragging != null && availableBounds.contains(dragPosition)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UiTextField(query, { query = it }, Modifier.weight(1f), "Search packs", PACK_ICONS + "search.svg", 160)
                                UiDropdown("", PackSort.entries, sort, { it.label }, Modifier.width(112.dp)) { sort = it }
                            }
                            UiToggle("Only compatible packs", onlyCompatible, Modifier.fillMaxWidth()) {
                                onlyCompatible = !onlyCompatible
                                MenuPreferences.compatibleResourcePacksOnly = onlyCompatible
                                MenuPreferences.save()
                            }
                            PackScrollList(tree, { it.key }, query, availableList, { availableListBounds = it }) { row ->
                                when (row) {
                                    is PackRow.Folder -> FolderRow(row, row.id in expanded || query.isNotBlank()) {
                                        expanded = if (row.id in expanded) expanded - row.id else expanded + row.id
                                    }
                                    is PackRow.Pack -> PackCard(row.pack, row.depth, cardModifier(row.pack.id, false)) {
                                        PackAction(PACK_ICONS + "right-arrow.svg", "Enable pack", enabled = row.pack.format >= 0) { add(row.pack) }
                                    }
                                }
                            }
                            if (tree.isEmpty()) {
                                UiText(if (query.isBlank()) "No available packs" else "No matching packs", 13.sp, color = UiTextSecondary)
                            }
                        }
                        PackPane(Modifier.weight(1f).onGloballyPositioned { activeBounds = it.boundsInRoot() }, "Active",
                            dragging != null && activeBounds.contains(dragPosition)) {
                            PackScrollList(activeResults, { it }, query, activeList, { activeListBounds = it }) { id ->
                                byId[id]?.let { pack ->
                                    PackCard(pack, 0, cardModifier(id, true)) {
                                        Column {
                                            PackAction(PACK_ICONS + "up.svg", "Move up", enabled = active.indexOf(id) > 0, compact = true) { move(id, -1) }
                                            PackAction(PACK_ICONS + "down.svg", "Move down", enabled = active.indexOf(id) < active.lastIndex, compact = true) { move(id, 1) }
                                        }
                                        PackAction(PACK_ICONS + "close.svg", "Disable pack") { remove(id) }
                                    }
                                }
                            }
                            DefaultPackCard(defaultIcon)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        UiToggle("Use vanilla font in-game", vanillaGameFont, Modifier.weight(1f)) {
                            vanillaGameFont = !vanillaGameFont
                            FontPreferences.setVanilla(false, vanillaGameFont)
                        }
                        UiToggle("Use vanilla font for mod text", vanillaModFont, Modifier.weight(1f)) {
                            vanillaModFont = !vanillaModFont
                            FontPreferences.setVanilla(true, vanillaModFont)
                        }
                    }
                    error?.let { UiText(it, 12.sp, color = UiDangerColor, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UiButton("Open folder", icon = PACK_ICONS + "folder.svg") {
                            Multithreading.submit { DesktopHelper.open(repository.directory) }
                        }
                        UiIconButton(PACK_ICONS + "refresh.svg", tooltip = "Refresh packs") { if (!applying) refresh() }
                        UiButton("Create pack", enabled = !applying && packs.isNotEmpty()) {
                            val sources = packs.filter { it.format >= 0 }.map {
                                CustomPackMaker.Source(it.id.removePrefix("file/"), it.file.toPath())
                            }
                            val initial = sources.firstOrNull { it.path().toFile() == byId[active.firstOrNull()]?.file }
                            client.tell { client.openScreen(CustomTexturePackScreen(this@ArcadiumResourcePacksScreen,
                                repository.directory.toPath(), sources, initial) { refresh() }) }
                        }
                        Spacer(Modifier.weight(1f))
                        UiButton("Apply", enabled = active != applied && !applying) { finish(false) }
                        UiButton("Done", filled = true, enabled = !applying, modifier = Modifier.width(100.dp)) { finish(true) }
                    }
                }
                dragging?.let { drag -> byId[drag.id]?.let { pack ->
                    Box(Modifier.align(Alignment.TopStart).zIndex(5f).offset {
                        IntOffset((dragPosition.x - dragRoot.x + 12f).roundToInt(), (dragPosition.y - dragRoot.y + 12f).roundToInt())
                    }.widthIn(max = 260.dp).clip(uiShape(6.dp)).background(UiControlBackground)
                        .border(1.dp, Accent, uiShape(6.dp)).padding(10.dp)) {
                        MinecraftText(pack.formattedTitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } }
                pending?.let { pack ->
                    UiModalScrim({ pending = null }) {
                        ModalPanel(440.dp) {
                            UiText("Use this pack?", 19.sp, fontWeight = FontWeight.SemiBold)
                            MinecraftText(pack.formattedTitle, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            UiText(
                                when (pack.compatibility) {
                                    ResourcePackCompatibility.Status.TOO_NEW -> "This pack was made for a newer version of Minecraft. Some textures may not work correctly."
                                    ResourcePackCompatibility.Status.TOO_OLD -> "This pack was made for an older version of Minecraft. Some textures may not work correctly."
                                    else -> "This pack does not declare compatibility with this version of Minecraft. Some textures may not work correctly."
                                },
                                13.sp, color = UiTextSecondary,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UiButton("Cancel", modifier = Modifier.weight(1f)) { pending = null }
                                UiButton("Use pack", filled = true, modifier = Modifier.weight(1f)) {
                                    confirmPending()
                                }
                            }
                        }
                    }
                }
                if (applying) {
                    UiModalScrim({}) {
                        ModalPanel(300.dp) { UiText("Applying resource packs...", 16.sp) }
                    }
                }
            }
        }
    }

    companion object {
        private val LOGGER = LogManager.getLogger("Arcadium Resource Packs")
    }
}

private class PackView(val entry: ResourcePacks.UnopenedPack) {
    private val access = entry as ResourcePackEntryAccessor
    val id: String = entry.name
    val file: File = access.`arcadium$file`()
    val title: String = file.name.replace(PACK_FORMATTING, "")
    val formattedTitle: String = file.name
    val formattedDescription: String = entry.description
    val description: String = formattedDescription.replace(PACK_FORMATTING, "")
    val searchText: String = ResourcePackSearch.text(id, title, description)
    val modified: Long = file.lastModified()
    val format: Int = runCatching { entry.format }.getOrDefault(-1)
    val compatibility = ResourcePackCompatibility.read(file.toPath(), 1, 0)
    val compatible: Boolean = compatibility == ResourcePackCompatibility.Status.COMPATIBLE
    val icon: ImageBitmap? by lazy {
        ResourcePackImages.read(file.toPath())?.let { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }
            ?: runCatching { access.`arcadium$icon`().toPackBitmap() }.getOrNull()
    }
}

private enum class PackSort(val label: String) {
    NAME("A to Z"), REVERSE_NAME("Z to A"), NEWEST("Newest"),
}

private data class PackDrag(val id: String, val fromActive: Boolean)

private sealed interface PackRow {
    val key: String
    val depth: Int
    data class Folder(val id: String, val title: String, override val depth: Int, val count: Int) : PackRow {
        override val key = "folder:$id"
    }
    data class Pack(val pack: PackView, override val depth: Int) : PackRow {
        override val key = "pack:${pack.id}"
    }
}

private fun packRows(packs: List<PackView>, expanded: Set<String>, query: String, sort: PackSort): List<PackRow> {
    val terms = ResourcePackSearch.terms(query)
    val matching = packs.filter { ResourcePackSearch.matches(terms, it.searchText) }
    val result = mutableListOf<PackRow>()
    fun append(prefix: String, depth: Int, contents: List<PackView>) {
        val grouped = contents.groupBy { it.id.removePrefix(prefix).substringBefore('/') }
        val comparator = when (sort) {
            PackSort.NAME -> String.CASE_INSENSITIVE_ORDER
            PackSort.REVERSE_NAME -> String.CASE_INSENSITIVE_ORDER.reversed()
            PackSort.NEWEST -> compareByDescending<String> { name -> grouped.getValue(name).maxOf { it.modified } }
        }
        val folders = grouped.keys.filter { name -> grouped.getValue(name).any { it.id.removePrefix(prefix).contains('/') } }.toSet()
        val ordered = folders.sortedWith(comparator) + (grouped.keys - folders).sortedWith(comparator)
        for (name in ordered) {
            val group = grouped.getValue(name)
            if (name in folders) {
                val id = prefix + name
                result += PackRow.Folder(id, name, depth, group.size)
                if (id in expanded || query.isNotBlank()) append("$id/", depth + 1, group)
            } else {
                result += PackRow.Pack(group.single(), depth)
            }
        }
    }
    append("", 0, matching)
    return result
}

@Composable
private fun PackPane(modifier: Modifier, title: String, highlighted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxHeight().clip(uiShape(8.dp)).background(UiSidebarBackground)
            .border(1.dp, if (highlighted) Accent else UiBorderColor, uiShape(8.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UiText(title, 17.sp, fontWeight = FontWeight.Medium)
        content()
    }
}

@Composable
private fun <T> ColumnScope.PackScrollList(rows: List<T>, key: (T) -> Any, query: String, state: LazyListState,
                                         onBounds: (Rect) -> Unit, content: @Composable (T) -> Unit) {
    LaunchedEffect(query) { state.scrollToItem(0) }
    Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { onBounds(it.boundsInRoot()) }) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(end = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(rows, key = key) { content(it) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun FolderRow(folder: PackRow.Folder, open: Boolean, onToggle: () -> Unit) {
    val (interaction, hovered) = rememberUiHover()
    val angle by animateFloatAsState(if (open) 90f else 0f)
    val color by animateColorAsState(if (hovered) UiControlBackground else Color.Transparent)
    Row(
        Modifier.fillMaxWidth().padding(start = (folder.depth.coerceAtMost(6) * 12).dp)
            .clip(uiShape(6.dp)).background(color).hoverable(interaction)
            .arcadiumClickable(onToggle).padding(horizontal = 8.dp, vertical = 9.dp).animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PACK_ICONS + "right-arrow.svg", UiTextSecondary, Modifier.size(12.dp).rotate(angle))
        Icon(PACK_ICONS + "folder.svg", UiTextSecondary, Modifier.size(18.dp))
        UiText(folder.title, 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        UiText(folder.count.toString(), 11.sp, color = UiTextSecondary)
    }
}

@Composable
private fun PackCard(pack: PackView, depth: Int, modifier: Modifier = Modifier, actions: @Composable () -> Unit) {
    val (interaction, hovered) = rememberUiHover()
    val border by animateColorAsState(if (hovered) UiHoverBorder else UiBorderColor)
    Row(
        modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(6) * 12).dp)
            .clip(uiShape(6.dp)).background(UiCardBackground)
            .border(1.dp, if (!pack.compatible) UiWarnColor.copy(alpha = 0.7f) else border, uiShape(6.dp))
            .hoverable(interaction).padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        PackImage(pack.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MinecraftText(pack.formattedTitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MinecraftText(pack.formattedDescription, color = UiTextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
private fun DefaultPackCard(icon: ImageBitmap?) {
    Row(
        Modifier.fillMaxWidth().clip(uiShape(6.dp)).border(1.dp, UiBorderColor, uiShape(6.dp)).padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        PackImage(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MinecraftText("Default Pack", fontSize = 13.sp)
            MinecraftText("The default look of Minecraft", fontSize = 11.sp, color = UiTextSecondary, maxLines = 2)
        }
        Icon(PACK_ICONS + "lock.svg", UiTextSecondary, Modifier.size(14.dp))
    }
}

@Composable
private fun PackImage(icon: ImageBitmap?) {
    if (icon != null) {
        Image(icon, null, Modifier.size(44.dp).clip(uiShape(4.dp)), filterQuality = FilterQuality.None)
    } else {
        Box(Modifier.size(44.dp).clip(uiShape(4.dp)).background(UiControlBackground), contentAlignment = Alignment.Center) {
            Icon(PACK_ICONS + "paintbrush.svg", UiTextSecondary, Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PackAction(icon: String, label: String, enabled: Boolean = true, compact: Boolean = false, onClick: () -> Unit) {
    val size = if (compact) 24.dp else 36.dp
    if (enabled) {
        Box(Modifier.size(size).clip(UiFieldShape).background(UiControlBackground)
            .border(1.dp, UiBorderColor, UiFieldShape).semantics { contentDescription = label }
            .arcadiumClickable(onClick), contentAlignment = Alignment.Center) {
            Icon(icon, UiTextPrimary, Modifier.size(if (compact) 15.dp else 21.dp))
        }
    } else {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(icon, UiTextSecondary.copy(alpha = 0.25f), Modifier.size(14.dp))
        }
    }
}

private fun BufferedImage.toPackBitmap(): ImageBitmap =
    SkiaImage.makeFromEncoded(ResourcePackImages.encode(this)).toComposeImageBitmap()
