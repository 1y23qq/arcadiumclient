// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.resourcepack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public final class ResourcePackCompatibility {
    private static final int METADATA_LIMIT = 1024 * 1024;
    private ResourcePackCompatibility() { }

    public record Format(int major, int minor) implements Comparable<Format> {
        public Format { if (major < 0 || minor < 0) throw new IllegalArgumentException("Negative resource pack format"); }
        @Override public int compareTo(Format other) {
            int majorOrder = Integer.compare(major, other.major);
            return majorOrder != 0 ? majorOrder : Integer.compare(minor, other.minor);
        }
    }
    public enum Status { COMPATIBLE, TOO_OLD, TOO_NEW, UNKNOWN }

    public static Status read(Path pack, int major, int minor) {
        try {
            String metadata;
            if (Files.isDirectory(pack)) {
                try (InputStream input = Files.newInputStream(pack.resolve("pack.mcmeta"))) { metadata = metadata(input); }
            } else {
                try (ZipFile archive = new ZipFile(pack.toFile())) {
                    var entry = archive.getEntry("pack.mcmeta");
                    if (entry == null || entry.isDirectory()) return Status.UNKNOWN;
                    try (InputStream input = archive.getInputStream(entry)) { metadata = metadata(input); }
                }
            }
            return parse(metadata, major, minor);
        } catch (IOException | RuntimeException failure) { return Status.UNKNOWN; }
    }

    public static Status parse(String metadata, int major, int minor) {
        try {
            JsonObject pack = JsonParser.parseString(metadata).getAsJsonObject().getAsJsonObject("pack");
            if (pack == null) return Status.UNKNOWN;
            Format minimum, maximum;
            if (pack.has("min_format") || pack.has("max_format")) {
                minimum = bound(pack.get("min_format"), false);
                maximum = bound(pack.get("max_format"), true);
            } else if (pack.has("supported_formats")) {
                JsonElement supported = pack.get("supported_formats");
                if (supported.isJsonObject()) {
                    var range = supported.getAsJsonObject();
                    minimum = bound(range.get("min_inclusive"), false);
                    maximum = bound(range.get("max_inclusive"), true);
                } else if (supported.isJsonArray()) {
                    var range = supported.getAsJsonArray();
                    if (range.size() != 2) return Status.UNKNOWN;
                    minimum = bound(range.get(0), false);
                    maximum = bound(range.get(1), true);
                } else {
                    minimum = bound(supported, false);
                    maximum = bound(supported, true);
                }
            } else if (pack.has("pack_format")) {
                minimum = bound(pack.get("pack_format"), false);
                maximum = bound(pack.get("pack_format"), true);
            } else {
                return major == 1 && pack.has("description") ? Status.COMPATIBLE : Status.UNKNOWN;
            }
            if (minimum.compareTo(maximum) > 0) return Status.UNKNOWN;
            Format current = new Format(major, minor);
            if (current.compareTo(minimum) < 0) return Status.TOO_NEW;
            if (current.compareTo(maximum) > 0) return Status.TOO_OLD;
            return Status.COMPATIBLE;
        } catch (RuntimeException failure) { return Status.UNKNOWN; }
    }

    private static Format bound(JsonElement element, boolean maximum) {
        if (element == null) throw new IllegalArgumentException("Missing resource pack format bound");
        if (element.isJsonArray()) {
            var pair = element.getAsJsonArray();
            if (pair.size() < 1 || pair.size() > 2) throw new IllegalArgumentException("Expected major and optional minor format");
            return new Format(integer(pair.get(0)), pair.size() == 2 ? integer(pair.get(1)) : maximum ? Integer.MAX_VALUE : 0);
        }
        return new Format(integer(element), maximum ? Integer.MAX_VALUE : 0);
    }
    private static int integer(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer resource pack format");
        return value.getAsBigDecimal().intValueExact();
    }
    private static String metadata(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(METADATA_LIMIT + 1);
        if (bytes.length > METADATA_LIMIT) throw new IOException("Resource pack metadata exceeds its size limit");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
