// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacySkinMeshTest {
    @Test
    void classicSkinExpandsToModernLayoutWithoutInventingOuterLayers() {
        BufferedImage source = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(4, 20, 0xFF123456);
        source.setRGB(44, 20, 0xFFABCDEF);
        BufferedImage converted = LegacySkinImages.normalize(source);
        assertEquals(64, converted.getHeight());
        assertEquals(0xFF123456, converted.getRGB(23, 52));
        assertEquals(0xFFABCDEF, converted.getRGB(39, 52));
        assertEquals(0, converted.getRGB(20, 36));
        assertEquals(0xFF000000, converted.getRGB(8, 8));
        assertNotSame(source, converted);
    }

    @Test
    void modernSkinPreservesAllOuterPixelsAndBaseOpacity() {
        BufferedImage source = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(20, 36, 0xAABBCCDD);
        source.setRGB(53, 53, 0xFF134679);
        source.setRGB(36, 53, 0x00123456);
        BufferedImage converted = LegacySkinImages.normalize(source);
        assertEquals(0xAABBCCDD, converted.getRGB(20, 36));
        assertEquals(0xFF134679, converted.getRGB(53, 53));
        assertEquals(0xFF123456, converted.getRGB(36, 53));
        assertEquals(0x00123456, source.getRGB(36, 53));
        assertNull(LegacySkinImages.normalize(null));
        assertNull(LegacySkinImages.normalize(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)));
    }

    @Test
    void oneOuterPixelHasFrontAndSideFacesWithActualDepth() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(43, 11, 0xFFFFFFFF);
        float[] mesh = LegacySkinMesh.build(image, new LegacySkinMesh.Box(32, 0, 8, 8, 8, -4, -8, -4));
        assertEquals(5 * 4 * 8, mesh.length);
        HashSet<String> normals = new HashSet<>();
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int vertex = 0; vertex < mesh.length; vertex += 8) {
            normals.add(mesh[vertex] + ":" + mesh[vertex + 1] + ":" + mesh[vertex + 2]);
            minZ = Math.min(minZ, mesh[vertex + 5]);
            maxZ = Math.max(maxZ, mesh[vertex + 5]);
            assertTrue(mesh[vertex + 6] >= 0 && mesh[vertex + 6] <= 1);
            assertTrue(mesh[vertex + 7] >= 0 && mesh[vertex + 7] <= 1);
        }
        assertEquals(5, normals.size());
        assertTrue(maxZ - minZ > .5f);
    }

    @Test
    void neighboringOpaquePixelsOmitTheirSharedSides() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(43, 11, 0xFFFFFFFF);
        image.setRGB(44, 11, 0xFFFFFFFF);
        float[] mesh = LegacySkinMesh.build(image, new LegacySkinMesh.Box(32, 0, 8, 8, 8, -4, -8, -4));
        assertEquals(8 * 4 * 8, mesh.length);
    }

    @Test
    void emittedQuadWindingMatchesItsLightingNormal() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) image.setRGB(x, y, 0xFFFFFFFF);
        float[] mesh = LegacySkinMesh.build(image, new LegacySkinMesh.Box(32, 0, 8, 8, 8, -4, -8, -4));
        assertTrue(mesh.length > 0);
        for (int face = 0; face < mesh.length; face += 32) {
            float ax = mesh[face + 11] - mesh[face + 3], ay = mesh[face + 12] - mesh[face + 4], az = mesh[face + 13] - mesh[face + 5];
            float bx = mesh[face + 19] - mesh[face + 3], by = mesh[face + 20] - mesh[face + 4], bz = mesh[face + 21] - mesh[face + 5];
            float dot = (ay * bz - az * by) * mesh[face] + (az * bx - ax * bz) * mesh[face + 1] + (ax * by - ay * bx) * mesh[face + 2];
            assertTrue(dot > 0, "Backface culling must retain outward-facing voxel quads");
        }
    }
}
