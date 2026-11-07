// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainResidentOrderedBuffersTest {
    @Test void passSnapshotsOwnTheirWordsAndNeverBorrowMutableUploadArrays() {
        int[] shared = mesh();
        face(shared, 3, 0, 100, 6);
        var solid = new TerrainResidentRegionData(shared, 1);
        Arrays.fill(shared, 0);
        face(shared, 7, 6, 700, 12);
        var cutout = new TerrainResidentRegionData(shared, 2);
        Arrays.fill(shared, 0);
        assertEquals(100, solid.words[3 * 20 + 4]);
        assertEquals(0, solid.words[7 * 20 + 18]);
        assertEquals(700, cutout.words[7 * 20 + 10]);
        assertEquals(0, cutout.words[3 * 20 + 12]);
        assertEquals(6, solid.maximumElements);
        assertEquals(12, cutout.maximumElements);
        assertEquals(1, solid.sectionsWithGeometry);
        assertEquals(1, cutout.sectionsWithGeometry);
    }

    @Test void orderedCommandsFollowRelocationAndRemovalWithoutChangingNativeRanks() {
        int[] data = mesh();
        face(data, 3, 0, 100, 6);
        face(data, 3, 1, 104, 12);
        face(data, 3, 2, 120, 6);
        face(data, 7, 6, 124, 6);
        byte[] ranks = {7, 3};
        var before = new TerrainResidentRegionData(data, 1);
        int[] commands = ordered(before, ranks);
        assertArrayEquals(new int[]{6, 1, 0, 124, 0, 18, 1, 0, 100, 0, 6, 1, 0, 120, 0}, commands);
        for (int section = 0; section < 256; section++) for (int face = 0; face < 7; face++) {
            if (data[section * 20 + 12 + face] != 0) data[section * 20 + 4 + face] += 4096;
        }
        var relocated = new TerrainResidentRegionData(data, 2);
        int[] relocatedCommands = ordered(relocated, ranks);
        assertEquals(commands.length, relocatedCommands.length);
        for (int word = 0; word < commands.length; word++) {
            assertEquals(commands[word] + (word % 5 == 3 ? 4096 : 0), relocatedCommands[word]);
        }
        for (int face = 0; face < 7; face++) data[3 * 20 + 12 + face] = 0;
        assertArrayEquals(new int[]{6, 1, 0, 4220, 0}, ordered(new TerrainResidentRegionData(data, 3), ranks));
        Arrays.fill(data, 0);
        assertEquals(0, ordered(new TerrainResidentRegionData(data, 4), ranks).length);
        assertArrayEquals(commands, ordered(before, ranks));
    }

    @Test void nativeRankPrefixDoesNotBecomeAnAllResidentVisibilityGate() {
        int[] data = mesh();
        face(data, 3, 6, 100, 6);
        face(data, 7, 6, 200, 6);
        face(data, 200, 6, 300, 6);
        var snapshot = new TerrainResidentRegionData(data, 1);
        assertArrayEquals(new int[]{6, 1, 0, 200, 0}, ordered(snapshot, new byte[]{7}));
        float[] allVisible = new float[16];
        allVisible[15] = 1;
        int[] resident = TerrainResidentFrustumPlan.reference(snapshot.words, allVisible,
                0, 0, 0, 0, 0, 0, 0, 0, 0, false);
        assertArrayEquals(new int[]{6, 1, 0, 100, 0, 6, 1, 0, 200, 0, 6, 1, 0, 300, 0}, resident);
        assertEquals(3, snapshot.sectionsWithGeometry);
    }

    @Test void sameSizePublicationsAdvanceOnlyTheirActualPassAndSection() {
        var publications = new TerrainResidentPublications(4);
        Object solid = new Object(), cutout = new Object();
        long first = publications.current(solid), second = publications.current(cutout);
        long edit = publications.changed(solid, 244);
        assertTrue(edit > second);
        assertEquals(edit, publications.slot(solid, 244));
        assertEquals(first, publications.slot(solid, 243));
        assertEquals(second, publications.current(cutout));
        long relocation = publications.changed(solid);
        for (int section = 0; section < 256; section++) assertEquals(relocation, publications.slot(solid, section));
        publications.released(solid);
        assertEquals(-1, publications.slot(solid, 244));
        assertEquals(second, publications.slot(cutout, 244));
        long replacement = publications.current(solid);
        assertTrue(replacement > relocation);
        assertTrue(publications.changed(solid, 244) > replacement);
        publications.clear();
        assertEquals(0, publications.retained());
        assertTrue(publications.current(solid) > replacement);
    }

    @Test void boundedPublicationsAndInvalidMetadataCannotSilentlyReplaceOwners() {
        var publications = new TerrainResidentPublications(1);
        Object first = new Object(), denied = new Object();
        long generation = publications.current(first);
        assertTrue(generation > 0);
        assertEquals(-1, publications.current(denied));
        assertEquals(generation, publications.current(first));
        assertEquals(-1, publications.changed(first, 256));
        assertEquals(generation, publications.current(first));
        publications.released(first);
        assertTrue(publications.current(denied) > generation);

        assertThrows(IllegalArgumentException.class, () -> new TerrainResidentRegionData(new int[20], 1));
        assertThrows(IllegalArgumentException.class, () -> new TerrainResidentRegionData(mesh(), 0));
        int[] partialQuad = mesh();
        face(partialQuad, 7, 6, 100, 5);
        assertThrows(IllegalArgumentException.class, () -> new TerrainResidentRegionData(partialQuad, 1));
        face(partialQuad, 7, 6, -1, 6);
        assertThrows(IllegalArgumentException.class, () -> new TerrainResidentRegionData(partialQuad, 1));
    }

    private static int[] ordered(TerrainResidentRegionData data, byte[] ranks) {
        return TerrainCommandPlan.reference(data.words, ranks, ranks.length, 0, 0, 0, false, 4, 6);
    }

    private static int[] mesh() { return new int[256 * 20]; }

    private static void face(int[] data, int section, int face, int firstVertex, int elements) {
        data[section * 20 + 4 + face] = firstVertex;
        data[section * 20 + 12 + face] = elements;
    }
}
