// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AutoTextEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Message",
    val text: String = "",
    val enabled: Boolean = true,
    val keys: List<Int> = emptyList(),
    val mouse: List<Int> = emptyList(),
    val modifiers: Int = 0,
)

internal object AutoTextData {
    const val MAX_ENTRIES = 128
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun checked(entry: AutoTextEntry): AutoTextEntry {
        val id = UUID.fromString(entry.id).toString()
        require(entry.keys.size <= 512 && entry.keys.all { it in 1..512 })
        require(entry.mouse.size <= 16 && entry.mouse.all { it in 0..15 })
        require(entry.modifiers in 0..15 && entry.text.length <= 100)
        return entry.copy(id = id, name = entry.name.take(48), keys = entry.keys.distinct(), mouse = entry.mouse.distinct())
    }

    fun snapshot(entries: List<AutoTextEntry>): List<AutoTextEntry> {
        require(entries.size <= MAX_ENTRIES)
        val result = entries.map(::checked)
        require(result.map { it.id }.distinct().size == result.size)
        return result
    }

    fun read(file: Path): List<AutoTextEntry> {
        require(Files.size(file) <= 1024 * 1024)
        return snapshot(json.decodeFromString<List<AutoTextEntry>>(Files.readString(file)))
    }

    fun encode(entries: List<AutoTextEntry>): String = json.encodeToString(entries)

    fun edit(entries: List<AutoTextEntry>, id: String, change: (AutoTextEntry) -> AutoTextEntry): List<AutoTextEntry> =
        entries.map { old -> if (old.id == id) checked(change(old).copy(id = old.id)) else old }

    fun validMessage(text: String): Boolean = text.isNotBlank() && text.length <= 100 && text.none {
        it < ' ' || it in '\u007f'..'\u009f' || it == '\u00a7' || it == '\u2028' || it == '\u2029'
    }
}

internal class AutoTextFileStore(private val file: Path, private val onFailure: () -> Unit) : AutoCloseable {
    private val writer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Arcadium-AutoText-Save").apply { isDaemon = true }
    }
    private var pending: ScheduledFuture<*>? = null
    private var latest: List<AutoTextEntry>? = null
    private var closed = false

    @Synchronized
    fun schedule(entries: List<AutoTextEntry>) {
        if (closed) return
        val snapshot = AutoTextData.snapshot(entries)
        latest = snapshot
        pending?.cancel(false)
        pending = writer.schedule({ write(snapshot) }, 250, TimeUnit.MILLISECONDS)
    }

    private fun write(snapshot: List<AutoTextEntry>) {
        try {
            Files.createDirectories(file.toAbsolutePath().parent)
            val temporary = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(temporary, AutoTextData.encode(snapshot))
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: Exception) { onFailure() }
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            pending?.cancel(false)
            latest?.let { snapshot -> writer.execute { write(snapshot) } }
            writer.shutdown()
        }
        if (!writer.awaitTermination(3, TimeUnit.SECONDS)) writer.shutdownNow()
    }
}
