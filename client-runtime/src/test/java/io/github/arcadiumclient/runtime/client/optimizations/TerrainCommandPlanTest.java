// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainCommandPlanTest {
    @Test void faceVisibilityKeepsTheThreeBlockGeometryTolerance() {
        assertEquals(64 | 8 | 16 | 32, TerrainCommandPlan.visibleFaces(-3, -3, -3, 0, 0, 0));
        assertEquals(127, TerrainCommandPlan.visibleFaces(-2, -2, -2, 0, 0, 0));
        assertEquals(127, TerrainCommandPlan.visibleFaces(18, 18, 18, 0, 0, 0));
        assertEquals(64 | 1 | 2 | 4, TerrainCommandPlan.visibleFaces(19, 19, 19, 0, 0, 0));
        assertEquals(TerrainCommandPlan.visibleFaces(0, 0, 0, 0, 0, 0),
                TerrainCommandPlan.visibleFaces(-29_999_872, -16, 29_999_872, -29_999_872, -16, 29_999_872));
    }

    @Test void cameraKeyNeverReusesCommandsAfterAnySectionFaceBecomesVisible() {
        for (int origin : new int[]{-29_999_872, -256, 0, 29_999_872}) {
            for (int axis = 0; axis < 3; axis++) {
                Map<Integer, int[]> byKey = new HashMap<>();
                for (int offset = -160; offset <= 288; offset++) {
                    int x = origin + (axis == 0 ? offset : 11), y = origin + (axis == 1 ? offset : 11), z = origin + (axis == 2 ? offset : 11);
                    int key = TerrainCommandPlan.cameraKey(x, y, z, origin, origin, origin);
                    int[] faces = new int[256]; int index = 0;
                    for (int sx = 0; sx < 8; sx++) for (int sy = 0; sy < 4; sy++) for (int sz = 0; sz < 8; sz++) {
                        faces[index++] = TerrainCommandPlan.visibleFaces(x, y, z, origin + sx * 16, origin + sy * 16, origin + sz * 16);
                    }
                    int[] previous = byKey.putIfAbsent(key, faces);
                    if (previous != null) assertArrayEquals(previous, faces, "A reused camera key delayed a face visibility change");
                }
            }
        }
    }

    @Test void commandsPreserveCurrentSectionOrderAndOnlyMergeAdjacentGeometryWithinASection() {
        int[] mesh = new int[256 * 20];
        face(mesh, 3, 0, 100, 6); face(mesh, 3, 1, 104, 12); face(mesh, 3, 2, 120, 6);
        face(mesh, 7, 6, 124, 6);
        assertArrayEquals(new int[]{6, 1, 0, 124, 0, 18, 1, 0, 100, 0, 6, 1, 0, 120, 0},
                TerrainCommandPlan.reference(mesh, new byte[]{7, 3}, 2, 0, 0, 0, false, 4, 6));
    }

    @Test void newlyVisibleSectionsAndCameraSidesAffectTheCurrentCommandList() {
        int[] mesh = new int[256 * 20];
        face(mesh, 0, 0, 100, 6); face(mesh, 0, 3, 200, 6); face(mesh, 1, 6, 300, 6);
        assertArrayEquals(new int[]{6, 1, 0, 200, 0}, TerrainCommandPlan.reference(mesh, new byte[]{0}, 1, -10, 0, 0, true, 4, 6));
        assertArrayEquals(new int[]{6, 1, 0, 100, 0, 6, 1, 0, 300, 0},
                TerrainCommandPlan.reference(mesh, new byte[]{0, 1}, 2, 30, 0, 0, true, 4, 6));
        assertEquals(0, TerrainCommandPlan.reference(mesh, new byte[0], 0, 0, 0, 0, true, 4, 6).length);
    }

    @Test void maximumSceneKeepsEveryDisjointFaceWithoutOverflow() {
        int[] mesh = new int[256 * 20]; byte[] sections = new byte[256];
        for (int section = 0; section < 256; section++) {
            sections[section] = (byte) section;
            for (int face = 0; face < 7; face++) face(mesh, section, face, section * 128 + face * 8, 6);
        }
        int[] result = TerrainCommandPlan.reference(mesh, sections, 256, 0, 0, 0, false, 4, 6);
        assertEquals(256 * 7 * 5, result.length);
        assertEquals(255 * 128 + 6 * 8, result[result.length - 2]);
    }

    private static void face(int[] mesh, int section, int face, int vertex, int elements) {
        mesh[section * 20 + 4 + face] = vertex;
        mesh[section * 20 + 12 + face] = elements;
    }
}
