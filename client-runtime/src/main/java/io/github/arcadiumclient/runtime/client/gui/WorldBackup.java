// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class WorldBackup {
    private WorldBackup() {}

    public static Path create(Path directory, String targetVersion) throws IOException {
        if (Files.isSymbolicLink(directory)) throw new IOException("The world directory is a link.");
        Path world = directory.toRealPath();
        Path backups = world.getParent().getParent().resolve("backups");
        Files.createDirectories(backups);
        if (backups.toRealPath().startsWith(world)) throw new IOException("The backup directory points into the world.");
        String name = world.getFileName().toString().replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        String version = targetVersion.replaceAll("[^0-9A-Za-z._-]", "_");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss"));
        Path completed = backups.resolve(name + "-before-" + version + "-" + stamp + "-" + UUID.randomUUID() + ".zip");
        Path temporary = Files.createTempFile(backups, ".arcadium-world-", ".tmp");
        var originals = new LinkedHashMap<Path, BasicFileAttributes>();
        try {
            try (var output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                output.setLevel(1);
                Files.walkFileTree(world, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                        if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(world)) throw new IOException("The world contains a directory link.");
                        return FileVisitResult.CONTINUE;
                    }

                    @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) throw new IOException("The world contains a file link.");
                        if (Thread.currentThread().isInterrupted()) throw new IOException("World backup was interrupted.");
                        originals.put(path, attributes);
                        String relative = world.getFileName() + "/" + world.relativize(path).toString().replace('\\', '/');
                        var entry = new ZipEntry(relative);
                        entry.setTime(attributes.lastModifiedTime().toMillis());
                        output.putNextEntry(entry);
                        Files.copy(path, output);
                        output.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            for (var original : originals.entrySet()) {
                var after = Files.readAttributes(original.getKey(), BasicFileAttributes.class);
                var before = original.getValue();
                if (after.size() != before.size() || !after.lastModifiedTime().equals(before.lastModifiedTime())) {
                    throw new IOException("The world changed while the backup was being created.");
                }
            }
            if (!originals.containsKey(world.resolve("level.dat")) && !originals.containsKey(world.resolve("level.dat_old"))) {
                throw new IOException("The world metadata is missing.");
            }
            Files.move(temporary, completed);
            return completed;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
