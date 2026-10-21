// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.arcadiumclient.runtime.client.gui.WorldMetadata;

import static org.junit.jupiter.api.Assertions.*;

class WorldMetadataTest {
    @TempDir Path directory;

    @Test void legacyWorldWithoutDataVersionRemainsVisible() throws IOException {
        var metadata = WorldMetadata.readCompressed(new ByteArrayInputStream(world(false, false)));
        assertEquals("Legacy world", metadata.name());
        assertEquals(123456789L, metadata.lastPlayed());
        assertEquals(1, metadata.gameMode());
        assertFalse(metadata.newerThan189());
        assertFalse(metadata.needsWarning());
    }

    @Test void newerWorldWithModernTagsIsRecognizedWithoutNativeWorldLoading() throws IOException {
        var metadata = WorldMetadata.readCompressed(new ByteArrayInputStream(world(true, true)));
        assertEquals(3955, metadata.dataVersion());
        assertEquals("1.21", metadata.versionName());
        assertTrue(metadata.newerThan189());
        assertTrue(metadata.needsWarning());
    }

    @Test void positiveDataVersionAloneIsNewerThan189() {
        assertTrue(new WorldMetadata("", 0, 0, false, false, 100, "", false, true).newerThan189());
        assertFalse(new WorldMetadata("", 0, 0, false, false, 0, "1.8.9", false, true).newerThan189());
        assertTrue(new WorldMetadata("", 0, 0, false, false, 0, "1.9", false, true).newerThan189());
        assertTrue(new WorldMetadata("", 0, 0, false, false, 0, "1.21.4", false, true).newerThan189());
    }

    @Test void corruptedPrimaryUsesBackupAndRequiresExplicitWarning() throws IOException {
        byte[] primary = "corrupt metadata".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] backup = world(false, false);
        Files.write(directory.resolve("level.dat"), primary);
        Files.write(directory.resolve("level.dat_old"), backup);
        var metadata = WorldMetadata.read(directory);
        assertTrue(metadata.readable());
        assertTrue(metadata.backup());
        assertTrue(metadata.needsWarning());
        assertArrayEquals(primary, Files.readAllBytes(directory.resolve("level.dat")));
        assertArrayEquals(backup, Files.readAllBytes(directory.resolve("level.dat_old")));
    }

    @Test void unreadableWorldIsNotMisclassifiedAsVerifiedLegacy() throws IOException {
        Files.writeString(directory.resolve("level.dat"), "bad");
        var metadata = WorldMetadata.read(directory);
        assertFalse(metadata.readable());
        assertTrue(metadata.needsWarning());
    }

    @Test void unknownVersionStillRequiresWarning() {
        assertTrue(new WorldMetadata("", 0, 0, false, false, 0, "unknown snapshot", false, true).needsWarning());
        assertFalse(new WorldMetadata("", 0, 0, false, false, 0, "1.8.9", false, true).needsWarning());
    }

    @Test void invalidArrayLengthIsRejected() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(7); out.writeUTF("oversized"); out.writeInt(Integer.MAX_VALUE);
            out.writeByte(0);
        }
        assertThrows(IOException.class, () -> WorldMetadata.readCompressed(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test void worldBorderTagsIdentify189WorldsWhenOpenedBy1710() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(10); out.writeUTF("Data");
            out.writeByte(8); out.writeUTF("LevelName"); out.writeUTF("Shared world");
            out.writeByte(6); out.writeUTF("BorderSize"); out.writeDouble(60000000);
            out.writeByte(0); out.writeByte(0);
        }
        var metadata = WorldMetadata.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("1.8", metadata.versionName());
        assertTrue(metadata.newerThan("1.7.10"));
        assertTrue(metadata.needsWarning("1.7.10"));
        assertFalse(metadata.newerThan("1.8.9"));
        assertFalse(metadata.needsWarning("1.8.9"));
        assertFalse(new WorldMetadata("", 0, 0, false, false, 0, "1.7.10", false, true).newerThan("1.7.10"));
    }

    @Test void missingDataCompoundIsRejected() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(0);
        }
        assertThrows(IOException.class, () -> WorldMetadata.readCompressed(new ByteArrayInputStream(bytes.toByteArray())));
    }

    private static byte[] world(boolean newer, boolean modernTags) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeUTF("");
            out.writeByte(10); out.writeUTF("Data");
            out.writeByte(8); out.writeUTF("LevelName"); out.writeUTF("Legacy world");
            out.writeByte(4); out.writeUTF("LastPlayed"); out.writeLong(123456789L);
            out.writeByte(3); out.writeUTF("GameType"); out.writeInt(1);
            if (modernTags) {
                out.writeByte(10); out.writeUTF("WorldGenSettings");
                out.writeByte(12); out.writeUTF("ignored longs"); out.writeInt(2); out.writeLong(123); out.writeLong(456);
                out.writeByte(9); out.writeUTF("ignored compound list"); out.writeByte(10); out.writeInt(1);
                out.writeByte(8); out.writeUTF("ignored text"); out.writeUTF("skipped"); out.writeByte(0);
                out.writeByte(0);
            }
            if (newer) {
                out.writeByte(3); out.writeUTF("DataVersion"); out.writeInt(3955);
                out.writeByte(10); out.writeUTF("Version");
                out.writeByte(3); out.writeUTF("Id"); out.writeInt(3955);
                out.writeByte(8); out.writeUTF("Name"); out.writeUTF("1.21");
                out.writeByte(0);
            }
            out.writeByte(0); out.writeByte(0);
        }
        return bytes.toByteArray();
    }
}
