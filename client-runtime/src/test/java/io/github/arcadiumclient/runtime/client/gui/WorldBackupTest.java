// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBackupTest {
    @TempDir Path game;

    @Test void backupContainsTheWholeWorldAndLeavesOriginalFilesUntouched() throws Exception {
        Path world = game.resolve("saves/Shared world");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "metadata");
        Files.write(world.resolve("region/r.0.0.mca"), new byte[]{1, 2, 3, 4});
        Path backup = WorldBackup.create(world, "1.7.10");
        assertEquals(game.resolve("backups"), backup.getParent());
        assertTrue(backup.getFileName().toString().contains("before-1.7.10"));
        try (var zip = new ZipFile(backup.toFile())) {
            assertEquals("metadata", new String(zip.getInputStream(zip.getEntry("Shared world/level.dat")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertArrayEquals(new byte[]{1, 2, 3, 4}, zip.getInputStream(zip.getEntry("Shared world/region/r.0.0.mca")).readAllBytes());
        }
        assertEquals("metadata", Files.readString(world.resolve("level.dat")));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(world.resolve("region/r.0.0.mca")));
    }

    @Test void missingMetadataCannotProduceAnApparentlyValidBackup() throws Exception {
        Path world = game.resolve("saves/Incomplete");
        Files.createDirectories(world);
        Files.writeString(world.resolve("data.txt"), "keep");
        assertThrows(IOException.class, () -> WorldBackup.create(world, "1.7.10"));
        try (var files = Files.list(game.resolve("backups"))) { assertEquals(0, files.count()); }
        assertEquals("keep", Files.readString(world.resolve("data.txt")));
    }

    @Test void anUnusableBackupDestinationNeverChangesTheWorld() throws Exception {
        Path world = game.resolve("saves/World");
        Files.createDirectories(world);
        Files.writeString(world.resolve("level.dat"), "keep");
        Files.writeString(game.resolve("backups"), "existing user file");
        assertThrows(IOException.class, () -> WorldBackup.create(world, "1.7.10"));
        assertEquals("keep", Files.readString(world.resolve("level.dat")));
        assertEquals("existing user file", Files.readString(game.resolve("backups")));
    }
}
