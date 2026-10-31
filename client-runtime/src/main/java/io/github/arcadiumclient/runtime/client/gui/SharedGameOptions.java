// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SharedGameOptions {
    private SharedGameOptions() {}

    public static Map<String, String> read(Path file) throws IOException {
        var values = new LinkedHashMap<String, String>();
        if (!Files.exists(file)) return values;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int separator = line.indexOf(':');
            if (separator > 0) values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return values;
    }

    public static String forLoad(String line, Map<String, String> values, boolean legacy) {
        if (line == null) return null;
        int separator = line.indexOf(':');
        if (separator < 1) return line;
        String key = line.substring(0, separator);
        if (key.equals("ao") || key.equals("oneconfig_legacy_ao")) {
            String value = values.getOrDefault("ao", values.get("oneconfig_legacy_ao"));
            String hint = values.get("oneconfig_legacy_ao");
            if ("false".equals(value)) return "ao:0";
            if ("true".equals(value)) return "ao:" + ("1".equals(hint) ? "1" : "2");
            if ("0".equals(value) || "1".equals(value) || "2".equals(value)) return "ao:" + value;
        }
        if (key.equals("clouds") || key.equals("renderClouds")) {
            String clouds = values.getOrDefault("renderClouds", values.getOrDefault("clouds", "true"));
            return legacy ? "clouds:" + !clouds.equals("false") : "renderClouds:" + clouds;
        }
        if (key.equals("showCape") || key.equals("modelPart_cape")) {
            String cape = values.getOrDefault("modelPart_cape", values.getOrDefault("showCape", "true"));
            return (legacy ? "showCape:" : "modelPart_cape:") + cape;
        }
        return line;
    }

    static Map<String, String> merge(Map<String, String> previous, Map<String, String> written, boolean legacy) {
        var merged = new LinkedHashMap<>(previous);
        merged.putAll(written);
        String clouds;
        String cape;
        if (legacy) {
            String enabled = written.getOrDefault("clouds", "true");
            clouds = enabled.equals("false") ? "false"
                : previous.getOrDefault("renderClouds", "true").equals("fast") ? "fast" : "true";
            cape = written.getOrDefault("showCape", "true");
        } else {
            clouds = written.getOrDefault("renderClouds", "true");
            cape = written.getOrDefault("modelPart_cape", "true");
        }
        merged.put("renderClouds", clouds);
        merged.put("clouds", Boolean.toString(!clouds.equals("false")));
        merged.put("modelPart_cape", cape);
        merged.put("showCape", cape);
        return merged;
    }

    public static Transaction begin(Path file, boolean legacy) throws IOException {
        Path source = Files.exists(file) ? file.toRealPath() : file.toAbsolutePath().normalize();
        Files.createDirectories(source.getParent());
        var previous = read(source);
        return new Transaction(source, Files.createTempFile(source.getParent(), ".arcadium-options-", ".tmp"),
            previous, legacy);
    }

    public static final class Transaction implements AutoCloseable {
        private final Path source;
        private final Path temporary;
        private final Map<String, String> previous;
        private final boolean legacy;
        private boolean failed;
        private boolean completed;

        private Transaction(Path source, Path temporary, Map<String, String> previous, boolean legacy) {
            this.source = source;
            this.temporary = temporary;
            this.previous = previous;
            this.legacy = legacy;
        }

        public Path temporary() { return temporary; }
        public void fail() { failed = true; }
        public void complete() { completed = true; }

        public void commit() throws IOException { commit(-1); }

        public void commit(int ambientOcclusion) throws IOException {
            if (failed || !completed) throw new IOException("Minecraft could not finish writing its options.");
            var written = read(temporary);
            if (!written.containsKey("mouseSensitivity")) {
                throw new IOException("Minecraft did not finish writing its options.");
            }
            var merged = merge(previous, written, legacy);
            if (ambientOcclusion >= 0 && ambientOcclusion <= 2) {
                boolean modernAo = false;
                try { modernAo = Integer.parseInt(merged.getOrDefault("version", "0")) >= 3214; }
                catch (NumberFormatException ignored) { }
                merged.put("ao", modernAo ? Boolean.toString(ambientOcclusion != 0) : Integer.toString(ambientOcclusion));
                merged.put("oneconfig_legacy_ao", Integer.toString(ambientOcclusion));
            }
            var text = new StringBuilder();
            merged.forEach((key, value) -> text.append(key).append(':').append(value).append('\n'));
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                throw new IOException("The filesystem could not atomically save Minecraft options.");
            }
        }

        @Override public void close() throws IOException { Files.deleteIfExists(temporary); }
    }
}
