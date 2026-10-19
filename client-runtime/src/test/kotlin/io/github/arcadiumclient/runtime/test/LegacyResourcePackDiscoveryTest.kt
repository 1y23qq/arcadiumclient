// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.FileFilter
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import net.minecraft.client.resource.metadata.ResourceMetadataSerializers
import net.minecraft.client.resource.metadata.ResourcePackMetadata
import net.minecraft.client.resource.metadata.serializer.PackMetadataSerializer
import net.minecraft.client.resource.pack.DirectoryResourcePack
import net.minecraft.client.resource.pack.ResourcePacks
import net.minecraft.client.resource.pack.ZippedResourcePack
import net.minecraft.resource.Identifier
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class LegacyResourcePackDiscoveryTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `native discovery and loading accept folder and zip packs together`() {
        val directory = Files.createDirectories(root.resolve("resourcepacks"))
        val folder = Files.createDirectories(directory.resolve("My Folder Pack"))
        val metadata = """{"pack":{"pack_format":1,"description":"Pack fixture"}}""".toByteArray()
        val texturePath = "assets/minecraft/textures/blocks/stone.png"
        val folderTexture = "folder asset".toByteArray()
        val zipTexture = "zip asset".toByteArray()
        val icon = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
        Files.write(folder.resolve("pack.mcmeta"), metadata)
        Files.write(folder.resolve("pack.png"), icon)
        Files.createDirectories(folder.resolve(texturePath).parent)
        Files.write(folder.resolve(texturePath), folderTexture)
        ZipOutputStream(Files.newOutputStream(directory.resolve("My Zip Pack.zip"))).use { zip ->
            for ((name, contents) in listOf("pack.mcmeta" to metadata, "pack.png" to icon, texturePath to zipTexture)) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents)
                zip.closeEntry()
            }
        }
        Files.createDirectories(directory.resolve("Not A Pack"))
        Files.writeString(directory.resolve("notes.txt"), "Not a resource pack")

        val serializers = ResourceMetadataSerializers().apply {
            register(PackMetadataSerializer(), ResourcePackMetadata::class.java)
        }
        val filter = ResourcePacks::class.java.getDeclaredField("FILE_FILTER").apply { isAccessible = true }.get(null) as FileFilter
        val available = directory.toFile().listFiles(filter)!!.associate { file ->
            file.name to if (file.isDirectory) DirectoryResourcePack(file) else ZippedResourcePack(file)
        }
        assertEquals(setOf("My Folder Pack", "My Zip Pack.zip"), available.keys)
        assertInstanceOf(DirectoryResourcePack::class.java, available.getValue("My Folder Pack"))
        val texture = Identifier("minecraft", "textures/blocks/stone.png")
        assertArrayEquals(folderTexture, available.getValue("My Folder Pack").getResource(texture).use { it.readBytes() })
        assertArrayEquals(zipTexture, available.getValue("My Zip Pack.zip").getResource(texture).use { it.readBytes() })
        for (pack in available.values) {
            assertEquals(1, pack.getMetadataSection<ResourcePackMetadata>(serializers, "pack").format)
            (pack as? Closeable)?.close()
        }
    }
}
