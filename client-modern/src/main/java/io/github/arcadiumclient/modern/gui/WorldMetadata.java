// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public record WorldMetadata(String name, long lastPlayed, int gameMode, boolean hardcore,
                            boolean cheats, int dataVersion, String versionName, boolean backup,
                            boolean readable) {
    private static final long MAX_BYTES = 16 * 1024 * 1024;

    public static WorldMetadata read(Path directory) {
        for (String file : new String[]{"level.dat", "level.dat_old"}) {
            Path path = directory.resolve(file);
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) continue;
            try (var input = Files.newInputStream(path)) {
                var result = readCompressed(input);
                return new WorldMetadata(result.name, result.lastPlayed, result.gameMode, result.hardcore,
                    result.cheats, result.dataVersion, result.versionName, file.endsWith("_old"), true);
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return new WorldMetadata("", 0, 0, false, false, 0, "", false, false);
    }

    public static WorldMetadata readCompressed(InputStream source) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(new LimitedInputStream(new GZIPInputStream(source))))) {
            if (input.readUnsignedByte() != 10) throw new IOException("World metadata must be an NBT compound");
            input.readUTF();
            var builder = new Builder();
            compound(input, "", 0, builder);
            if (!builder.dataFound) throw new IOException("World metadata has no Data compound");
            if (input.read() != -1) throw new IOException("World metadata contains trailing data");
            if (builder.versionName.isBlank() && builder.worldBorder && builder.dataVersion == 0 && builder.versionId == 0) {
                builder.versionName = "1.8";
            }
            return new WorldMetadata(builder.name, builder.lastPlayed, builder.gameMode, builder.hardcore,
                builder.cheats, Math.max(builder.dataVersion, builder.versionId), builder.versionName, false, true);
        }
    }

    public boolean newerThan189() {
        return newerThan("1.8.9");
    }

    public boolean newerThan(String currentVersion) {
        return newerThan(currentVersion, currentVersion.startsWith("1.8") || currentVersion.startsWith("1.7") ? 0 : Integer.MAX_VALUE);
    }

    public boolean newerThan(String currentVersion, int currentDataVersion) {
        if (dataVersion > currentDataVersion) return true;
        if (versionName.isBlank()) return false;
        var pattern = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:\\D.*)?$");
        var release = pattern.matcher(versionName);
        var current = pattern.matcher(currentVersion);
        if (!release.matches() || !current.matches()) return false;
        try {
            for (int part = 1; part <= 3; part++) {
                int saved = release.group(part) == null ? 0 : Integer.parseInt(release.group(part));
                int running = current.group(part) == null ? 0 : Integer.parseInt(current.group(part));
                if (saved != running) return saved > running;
            }
            return false;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    public boolean needsWarning() {
        return needsWarning("1.8.9");
    }

    public boolean needsWarning(String currentVersion) { return needsWarning(currentVersion, currentVersion.startsWith("1.8") || currentVersion.startsWith("1.7") ? 0 : Integer.MAX_VALUE); }

    public boolean needsWarning(String currentVersion, int currentDataVersion) {
        return !readable || backup || newerThan(currentVersion, currentDataVersion) || (!versionName.isBlank()
            && !versionName.matches("\\d+\\.\\d+(?:\\.\\d+)?(?:\\D.*)?"));
    }

    private static void compound(DataInputStream input, String path, int depth, Builder result) throws IOException {
        if (depth > 64) throw new IOException("World metadata is nested too deeply");
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) return;
            String name = input.readUTF();
            String key = path.isEmpty() ? name : path + "/" + name;
            if (type == 10 && (key.equals("Data") || key.equals("Data/Version"))) {
                if (key.equals("Data")) result.dataFound = true;
                compound(input, key, depth + 1, result);
            } else if (key.equals("Data/LevelName") && type == 8) result.name = input.readUTF();
            else if (key.equals("Data/LastPlayed") && type == 4) result.lastPlayed = input.readLong();
            else if (key.equals("Data/GameType") && type == 3) result.gameMode = input.readInt();
            else if (key.equals("Data/hardcore") && type == 1) result.hardcore = input.readByte() != 0;
            else if (key.equals("Data/allowCommands") && type == 1) result.cheats = input.readByte() != 0;
            else if ((key.equals("Data/DataVersion") || key.equals("DataVersion")) && type == 3) result.dataVersion = input.readInt();
            else if (key.equals("Data/Version/Id") && type == 3) result.versionId = input.readInt();
            else if (key.equals("Data/Version/Name") && type == 8) result.versionName = input.readUTF();
            else if (key.equals("Data/BorderSize") || key.equals("Data/BorderCenterX") || key.equals("Data/BorderCenterZ")) {
                result.worldBorder = true;
                skip(input, type, depth);
            }
            else skip(input, type, depth + 1);
        }
    }

    private static void skip(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 64) throw new IOException("World metadata is nested too deeply");
        switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3, 5 -> input.readInt();
            case 4, 6 -> input.readLong();
            case 7, 11, 12 -> {
                int count = input.readInt();
                long bytes = (long) count * (type == 7 ? 1 : type == 11 ? 4 : 8);
                if (count < 0 || bytes > MAX_BYTES) throw new IOException("Invalid world metadata array");
                input.skipNBytes(bytes);
            }
            case 8 -> input.readUTF();
            case 9 -> {
                int child = input.readUnsignedByte();
                int count = input.readInt();
                if (count < 0 || count > 1_000_000 || (child == 0 && count != 0)) throw new IOException("Invalid world metadata list");
                for (int i = 0; i < count; i++) skip(input, child, depth + 1);
            }
            case 10 -> {
                int child;
                while ((child = input.readUnsignedByte()) != 0) {
                    input.readUTF();
                    skip(input, child, depth + 1);
                }
            }
            default -> throw new IOException("Invalid NBT tag: " + type);
        }
    }

    private static final class Builder {
        String name = "";
        long lastPlayed;
        int gameMode;
        boolean hardcore;
        boolean cheats;
        int dataVersion;
        int versionId;
        String versionName = "";
        boolean dataFound;
        boolean worldBorder;
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining = MAX_BYTES;

        LimitedInputStream(InputStream source) { super(source); }

        @Override public int read() throws IOException {
            if (remaining <= 0) throw new IOException("World metadata is too large");
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining <= 0) throw new IOException("World metadata is too large");
            int count = in.read(bytes, offset, (int) Math.min(length, remaining));
            if (count >= 0) remaining -= count;
            return count;
        }

        @Override public long skip(long count) throws IOException {
            if (remaining <= 0) throw new IOException("World metadata is too large");
            long skipped = in.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }
    }
}
