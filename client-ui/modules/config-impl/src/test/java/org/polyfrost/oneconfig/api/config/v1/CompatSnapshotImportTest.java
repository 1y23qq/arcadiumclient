// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.config.v1;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompatSnapshotImportTest {
    @Test void importingCachedKeysPreservesOtherOptionsTreesAndDiskState() throws Exception {
        String profile = "oc_snapshot_import_" + UUID.randomUUID();
        var folder = ConfigManager.profileDir(profile);
        Files.createDirectories(folder);
        try {
            CompatSnapshots.importSerializedValues(profile, "other", Map.of("path:untouched", "kept"));
            CompatSnapshots.importSerializedValues(profile, "tooltipscroll", Map.of("path:keep", true, "path:speed", 1));
            CompatSnapshots.importSerializedValues(profile, "tooltipscroll", Map.of("path:speed", 7, "path:new", false));
            var field = CompatSnapshots.class.getDeclaredField("store"); field.setAccessible(true);
            var cached = (CompatSnapshotStore) field.get(CompatSnapshots.INSTANCE);
            assertEquals(7, cached.getValue(profile, "tooltipscroll", "path:speed"));
            var fresh = new CompatSnapshotStore();
            assertEquals(7, ((Number) fresh.getValue(profile, "tooltipscroll", "path:speed")).intValue());
            assertEquals(true, fresh.getValue(profile, "tooltipscroll", "path:keep"));
            assertEquals(false, fresh.getValue(profile, "tooltipscroll", "path:new"));
            assertEquals("kept", fresh.getValue(profile, "other", "path:untouched"));
            byte[] saved = Files.readAllBytes(folder.resolve(CompatSnapshotStore.FILE_NAME));
            assertThrows(IllegalArgumentException.class, () -> CompatSnapshots.importSerializedValues(profile, "tooltipscroll", Map.of("bad", new Object())));
            assertArrayEquals(saved, Files.readAllBytes(folder.resolve(CompatSnapshotStore.FILE_NAME)));
            cached.deleteProfile(profile);
        } finally {
            Files.deleteIfExists(folder.resolve(CompatSnapshotStore.FILE_NAME));
            Files.deleteIfExists(folder);
        }
    }
}
