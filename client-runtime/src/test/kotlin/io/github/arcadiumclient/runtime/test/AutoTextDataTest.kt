// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import io.github.arcadiumclient.runtime.client.features.AutoTextData
import io.github.arcadiumclient.runtime.client.features.AutoTextEntry
import io.github.arcadiumclient.runtime.client.features.AutoTextFileStore
import io.github.arcadiumclient.runtime.client.features.AutoTextPressGate

class AutoTextDataTest {
    @Test fun storesMultipleMessagesCommandsAndShortcuts(@TempDir directory: Path) {
        val entries = listOf(
            AutoTextEntry(name = "Greeting", text = "Hello", keys = listOf(71)),
            AutoTextEntry(name = "Lobby", text = "/lobby", enabled = false, keys = listOf(76), modifiers = 1),
            AutoTextEntry(name = "Party", text = "/p list", mouse = listOf(4)),
        )
        val file = directory.resolve("autotext.json")
        AutoTextFileStore(file) { fail<Unit>("Save failed") }.use { it.schedule(entries) }
        assertEquals(entries, AutoTextData.read(file))
    }

    @Test fun editingCurrentEntryKeepsNewBindingAndOtherMessages() {
        val first = AutoTextEntry(name = "First", text = "before")
        val second = AutoTextEntry(name = "Second", text = "/lobby")
        val rebound = AutoTextData.edit(listOf(first, second), first.id) { it.copy(keys = listOf(72), modifiers = 2) }
        val edited = AutoTextData.edit(rebound, first.id) { it.copy(text = "after") }
        assertEquals(listOf(72), edited[0].keys)
        assertEquals(2, edited[0].modifiers)
        assertEquals("after", edited[0].text)
        assertEquals(second, edited[1])
        assertEquals("before", first.text)
    }

    @Test fun latestSnapshotWinsAndClosingFlushesPendingChanges(@TempDir directory: Path) {
        val file = directory.resolve("autotext.json")
        val entry = AutoTextEntry(text = "before")
        AutoTextFileStore(file) { fail<Unit>("Save failed") }.use { store ->
            store.schedule(listOf(entry))
            store.schedule(listOf(entry.copy(text = "after", enabled = false)))
        }
        assertEquals("after", AutoTextData.read(file).single().text)
        assertFalse(AutoTextData.read(file).single().enabled)
    }

    @Test fun savedSnapshotDoesNotFollowMutableInputLists(@TempDir directory: Path) {
        val keys = mutableListOf(71)
        val input = mutableListOf(AutoTextEntry(text = "message", keys = keys))
        val file = directory.resolve("autotext.json")
        AutoTextFileStore(file) { fail<Unit>("Save failed") }.use { store ->
            store.schedule(input)
            keys[0] = 72
            input.clear()
        }
        assertEquals(listOf(71), AutoTextData.read(file).single().keys)
    }

    @Test fun invalidFileIsKeptUnchanged(@TempDir directory: Path) {
        val file = directory.resolve("autotext.json")
        val contents = "[{\"id\":\"not-a-uuid\",\"text\":\"private message\"}]"
        Files.writeString(file, contents)
        assertThrows(IllegalArgumentException::class.java) { AutoTextData.read(file) }
        assertEquals(contents, Files.readString(file))
    }

    @Test fun duplicateIdsAndOversizedListsAreRejected() {
        val id = UUID.randomUUID().toString()
        assertThrows(IllegalArgumentException::class.java) {
            AutoTextData.snapshot(listOf(AutoTextEntry(id = id), AutoTextEntry(id = id.uppercase())))
        }
        assertThrows(IllegalArgumentException::class.java) { AutoTextData.snapshot(List(129) { AutoTextEntry() }) }
        assertEquals(128, AutoTextData.snapshot(List(128) { AutoTextEntry() }).size)
        assertThrows(IllegalArgumentException::class.java) { AutoTextData.checked(AutoTextEntry(text = "x".repeat(101))) }
    }

    @Test fun acceptsSingleLineMessagesAndCommandsOnly() {
        assertTrue(AutoTextData.validMessage("Hello"))
        assertTrue(AutoTextData.validMessage("/party invite Player"))
        assertTrue(AutoTextData.validMessage("こんにちは"))
        assertTrue(AutoTextData.validMessage("x".repeat(100)))
        for (bad in listOf("", "   ", "x".repeat(101), "a\nb", "a\rb", "a\tb", "a\u0000b", "§aHello", "a\u0085b", "a\u2028b")) {
            assertFalse(AutoTextData.validMessage(bad))
        }
    }

    @Test fun heldShortcutFiresOnceUntilReleased() {
        val gate = AutoTextPressGate()
        assertTrue(gate.press("first", true))
        repeat(10) { assertFalse(gate.press("first", true)) }
        assertTrue(gate.press("second", true))
        assertFalse(gate.press("first", false))
        assertTrue(gate.press("first", true))
    }

    @Test fun blockedTypingPressCannotFireWhenFocusChanges() {
        val gate = AutoTextPressGate()
        assertTrue(gate.press("message", true))
        assertFalse(gate.press("message", true))
        gate.press("message", false)
        assertTrue(gate.press("message", true))
        gate.forget("message")
        assertTrue(gate.press("message", true))
    }

    @Test fun writeFailureDoesNotOverwriteExistingFile(@TempDir directory: Path) {
        val blockedParent = directory.resolve("blocked")
        Files.writeString(blockedParent, "keep me")
        val failures = AtomicInteger()
        AutoTextFileStore(blockedParent.resolve("autotext.json")) { failures.incrementAndGet() }.use {
            it.schedule(listOf(AutoTextEntry(text = "message")))
        }
        assertTrue(failures.get() > 0)
        assertEquals("keep me", Files.readString(blockedParent))
    }
}
