// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.resourcepack;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ResourcePackFolders {
    private static volatile Path clientDirectory;

    private ResourcePackFolders() {}

    public static void registerClientDirectory(File directory) {
        clientDirectory = directory.toPath().toAbsolutePath().normalize();
    }

    public static String repositoryId(Path file) {
        Path root = clientDirectory;
        Path path = file.toAbsolutePath().normalize();
        return "file/" + (root != null && path.startsWith(root) && !path.equals(root)
            ? root.relativize(path).toString().replace(File.separatorChar, '/') : file.getFileName().toString());
    }

    public static List<File> discover(File directory) {
        Path root = directory.toPath().toAbsolutePath().normalize();
        List<File> packs = new ArrayList<>();
        if (!Files.isDirectory(root)) return packs;
        try {
            Files.walkFileTree(root, java.util.Set.of(), 64, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) {
                    if (!path.equals(root) && Files.isRegularFile(path.resolve("pack.mcmeta"))) {
                        packs.add(path.toFile());
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        packs.add(path.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path path, IOException error) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
        } catch (IOException ignored) {
        }
        packs.sort(Comparator.comparing(file -> relativeId(directory, file), String.CASE_INSENSITIVE_ORDER));
        return packs;
    }

    public static String relativeId(File directory, File file) {
        Path root = directory.toPath().toAbsolutePath().normalize();
        Path path = file.toPath().toAbsolutePath().normalize();
        return path.startsWith(root) ? root.relativize(path).toString().replace(File.separatorChar, '/') : file.getName();
    }
}
