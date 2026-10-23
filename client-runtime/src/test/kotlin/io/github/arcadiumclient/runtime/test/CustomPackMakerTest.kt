// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.resourcepack.CustomPackMaker
import io.github.arcadiumclient.runtime.client.resourcepack.CustomPackMaker.Element
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CustomPackMakerTest {
    @TempDir lateinit var root: Path

    private fun folder(name: String, files: Map<String, String>): CustomPackMaker.Source {
        val path = root.resolve(name)
        for ((file, value) in files + ("pack.mcmeta" to "{}")) {
            Files.createDirectories(path.resolve(file).parent)
            Files.writeString(path.resolve(file), value)
        }
        return CustomPackMaker.Source(name, path)
    }

    private fun zip(name: String, files: Map<String, String>): CustomPackMaker.Source {
        val path = root.resolve(name)
        ZipOutputStream(Files.newOutputStream(path)).use { out ->
            for ((file, value) in files + ("pack.mcmeta" to "{}")) {
                out.putNextEntry(ZipEntry(file)); out.write(value.toByteArray()); out.closeEntry()
            }
        }
        return CustomPackMaker.Source(name, path)
    }

    @Test fun `combines independent elements from nested folder and zip packs without modifying sources`() {
        val sword = "assets/minecraft/textures/items/diamond_sword.png"
        val block = "assets/minecraft/textures/blocks/stone.png"
        val sky = "assets/minecraft/mcpatcher/sky/world0/sky1.png"
        val fireball = "assets/minecraft/textures/items/fire_charge.png"
        val base = folder("base", mapOf(sword to "base sword", block to "base stone", "assets/minecraft/textures/gui/icons.png" to "base gui"))
        val swords = zip("swords.zip", mapOf(sword to "sharp", "$sword.mcmeta" to "animation", "LICENSE.txt" to "sword credit"))
        val blocks = folder("Group/blocks", mapOf(block to "new stone"))
        val skies = zip("sky.zip", mapOf(sky to "night sky", sky.replace("png", "properties") to "source=sky1.png"))
        val balls = folder("fireballs", mapOf(fireball to "new fireball"))
        val originalZip = Files.readAllBytes(swords.path())
        val output = CustomPackMaker.create(root.resolve("resourcepacks"), "My pack", base,
            mapOf(Element.SWORDS to swords, Element.BLOCKS to blocks, Element.SKY to skies, Element.FIREBALL to balls))
        ZipFile(output.path().toFile()).use { pack ->
            fun text(file: String) = pack.getInputStream(pack.getEntry(file)).reader().readText()
            assertEquals("sharp", text(sword)); assertEquals("animation", text("$sword.mcmeta"))
            assertEquals("new stone", text(block)); assertEquals("night sky", text(sky)); assertEquals("new fireball", text(fireball))
            assertEquals("base gui", text("assets/minecraft/textures/gui/icons.png"))
            assertTrue(text("pack.mcmeta").contains("My pack"))
            assertTrue(text("arcadium-pack.json").contains("swords.zip"))
            assertTrue(pack.entries().asSequence().any { it.name.startsWith("arcadium-sources/") && it.name.endsWith("LICENSE.txt") })
        }
        assertArrayEquals(originalZip, Files.readAllBytes(swords.path()))
        assertEquals("base sword", Files.readString(base.path().resolve(sword)))
        val second = CustomPackMaker.create(root.resolve("resourcepacks"), "My pack", base, emptyMap())
        assertNotEquals(output.path(), second.path())
        assertTrue(Files.isRegularFile(output.path()))
    }

    @Test fun `rejects missing element and unsafe names while excluding unsafe source paths`() {
        val source = zip("source.zip", mapOf("assets/minecraft/textures/blocks/stone.png" to "stone",
            "../secret.txt" to "private", "assets/../../secret" to "private"))
        val destination = root.resolve("out")
        for (name in listOf("../escape", "CON", "bad/name", "", "trailing."))
            assertThrows(java.io.IOException::class.java) { CustomPackMaker.create(destination, name, source, emptyMap()) }
        assertThrows(java.io.IOException::class.java) { CustomPackMaker.create(destination, "No swords", source, mapOf(Element.SWORDS to source)) }
        val output = CustomPackMaker.create(destination, "Safe", source, emptyMap())
        ZipFile(output.path().toFile()).use { pack -> assertFalse(pack.entries().asSequence().any { it.name.contains("..") }) }
        Files.list(destination).use { files -> assertEquals(1L, files.count()) }
    }

    @Test fun `includes custom model parents textures and animation metadata`() {
        val base = folder("base", mapOf("assets/minecraft/textures/blocks/stone.png" to "base blocks"))
        val swords = zip("models.zip", mapOf(
            "assets/minecraft/models/item/diamond_sword.json" to """{"parent":"item/special_parent","textures":{"layer0":"custom:items/my_sword"}}""",
            "assets/minecraft/models/item/special_parent.json" to """{"parent":"builtin/generated"}""",
            "assets/custom/textures/items/my_sword.png" to "custom sword",
            "assets/custom/textures/items/my_sword.png.mcmeta" to "animated",
        ))
        val result = CustomPackMaker.create(root.resolve("out"), "Models", base, mapOf(Element.SWORDS to swords))
        ZipFile(result.path().toFile()).use { pack ->
            for (name in listOf("assets/minecraft/models/item/special_parent.json", "assets/custom/textures/items/my_sword.png",
                "assets/custom/textures/items/my_sword.png.mcmeta")) assertNotNull(pack.getEntry(name), name)
        }
    }

    @Test fun `resolves legacy blockstate models inside the block model directory`() {
        val base = folder("base", emptyMap())
        val fire = folder("fire", mapOf(
            "assets/minecraft/blockstates/fire.json" to """{"variants":{"normal":{"model":"custom_flame"}}}""",
            "assets/minecraft/models/block/custom_flame.json" to """{"textures":{"particle":"blocks/custom_flame"}}""",
            "assets/minecraft/textures/blocks/custom_flame.png" to "custom fire",
        ))
        val result = CustomPackMaker.create(root.resolve("out"), "Fire", base, mapOf(Element.FIRE to fire))
        ZipFile(result.path().toFile()).use { pack ->
            assertNotNull(pack.getEntry("assets/minecraft/models/block/custom_flame.json"))
            assertNotNull(pack.getEntry("assets/minecraft/textures/blocks/custom_flame.png"))
        }
    }
}
