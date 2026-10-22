// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import io.github.arcadiumclient.runtime.client.background.PanoramaCatalog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.imageio.ImageIO

class PanoramaCatalogTest {
    @Test
    fun `every supplied panorama has six full resolution faces and a preview`() {
        val entries = PanoramaCatalog.ENTRIES
        assertTrue(entries.size in 12..18)
        assertEquals(entries.size, entries.map { it.id() }.toSet().size)
        for (entry in entries) {
            for (index in 0..5) {
                val path = "/assets/" + entry.face(index).replace(':', '/')
                val image = PanoramaCatalog::class.java.getResourceAsStream(path)!!.use { ImageIO.read(it) }
                assertEquals(1024, image.width, path)
                assertEquals(1024, image.height, path)
            }
            val preview = PanoramaCatalog::class.java.getResourceAsStream(entry.preview())!!.use { ImageIO.read(it) }
            assertEquals(16f / 9f, preview.width.toFloat() / preview.height, .01f)
        }
    }

    @Test
    fun `invalid saved selection falls back and blur is bounded`() {
        assertEquals("lakeside-morning", PanoramaCatalog.find("removed-scene").id())
        assertEquals("lakeside-morning", PanoramaCatalog.find(null).id())
        assertEquals(0f, PanoramaCatalog.blurRadius(-100))
        assertEquals(0f, PanoramaCatalog.blurRadius(0))
        assertEquals(8f, PanoramaCatalog.blurRadius(40))
        assertEquals(20f, PanoramaCatalog.blurRadius(1000))
    }
}
