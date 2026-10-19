// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import io.github.arcadiumclient.runtime.client.resourcepack.ResourcePackFolders

class ResourcePackFoldersTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `finds nested zip and folder packs without scanning pack contents`() {
        fun file(name: String) {
            val path = root.resolve(name)
            Files.createDirectories(path.parent)
            Files.writeString(path, "fixture")
        }
        file("Top.zip")
        file("PVP/Favorites/One.zip")
        file("PVP/Favorites/Two.ZIP")
        file("Unpacked/pack.mcmeta")
        file("Unpacked/assets/minecraft/ignored.zip")
        file("Unpacked/nested/pack.mcmeta")
        file("Organization/Nested folder pack/pack.mcmeta")
        file("notes.txt")
        val found = ResourcePackFolders.discover(root.toFile()).map { ResourcePackFolders.relativeId(root.toFile(), it) }
        assertEquals(listOf("Organization/Nested folder pack", "PVP/Favorites/One.zip", "PVP/Favorites/Two.ZIP", "Top.zip", "Unpacked"), found)
        assertFalse(found.any { it.contains("assets") || it.contains("nested") })
    }

    @Test
    fun `uses stable unique relative ids and preserves top level ids`() {
        val first = root.resolve("First/Pack.zip").toFile()
        val second = root.resolve("Second/Pack.zip").toFile()
        assertEquals("First/Pack.zip", ResourcePackFolders.relativeId(root.toFile(), first))
        assertEquals("Second/Pack.zip", ResourcePackFolders.relativeId(root.toFile(), second))
        assertEquals("Pack.zip", ResourcePackFolders.relativeId(root.toFile(), root.resolve("Pack.zip").toFile()))
        assertEquals("First/Pack.zip", ResourcePackFolders.relativeId(root.resolve(".").toFile(), root.resolve("First/../First/Pack.zip").toFile()))
        ResourcePackFolders.registerClientDirectory(root.toFile())
        assertEquals("file/First/Pack.zip", ResourcePackFolders.repositoryId(first.toPath()))
        assertEquals("file/Second/Pack.zip", ResourcePackFolders.repositoryId(second.toPath()))
        assertEquals("file/Pack.zip", ResourcePackFolders.repositoryId(root.resolve("Pack.zip")))
        assertEquals("file/Server.zip", ResourcePackFolders.repositoryId(root.parent.resolve("Server.zip")))
    }

    @Test
    fun `missing directories have no packs and symlink loops are not traversed`() {
        assertEquals(emptyList<java.io.File>(), ResourcePackFolders.discover(root.resolve("missing").toFile()))
        Files.createDirectories(root.resolve("Group"))
        runCatching { Files.createSymbolicLink(root.resolve("Group/loop"), root) }
        Files.writeString(root.resolve("Group/Pack.zip"), "fixture")
        assertEquals(listOf("Group/Pack.zip"), ResourcePackFolders.discover(root.toFile()).map { ResourcePackFolders.relativeId(root.toFile(), it) })
    }
}
