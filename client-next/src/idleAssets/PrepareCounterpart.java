// SPDX-License-Identifier: GPL-3.0-only
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PrepareCounterpart {
    private static final String SOURCE = "79b13548d7e9de9abfa23e062724a885e5341f371c500d6570056ecea7db00c2f7ace5a7288179ad8baec575b4aa992b91dccbc940c584cb5cddf0bc7aeb892f";
    private static final String NOTICE = "assets/dynamic_fps/textures/battery/license.txt";
    private static final List<String> LICENSES = List.of("Dynamic-FPS-MIT.txt", "Feather-MIT.txt", "Arcadium-assets-MIT.txt", "ASSET-PROVENANCE.txt");
    private static final List<String> MEDIA = List.of(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".bmp", ".ico", ".icns", ".tga", ".tif", ".tiff", ".ttf", ".otf", ".ttc", ".eot", ".woff", ".woff2", ".ogg", ".wav", ".mp3", ".flac", ".aac", ".m4a", ".mid", ".mp4", ".webm");
    private static int unchanged;
    private static final List<String> replaced = new ArrayList<>(), removed = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        byte[] source = Files.readAllBytes(Path.of(args[0]));
        require(hash(source).equals(SOURCE), "Unexpected Dynamic FPS source release");
        Path destination = Path.of(args[1]), replacements = Path.of(args[2]).toAbsolutePath().normalize(), licenses = Path.of(args[3]);
        byte[] derived;
        if (args.length > 5 && args[5].equals("--audit-only")) {
            derived = Files.readAllBytes(destination);
        } else {
            var entries = archive(derive(source, replacements, 0));
            for (String name : LICENSES) entries.put("META-INF/licenses/dynamic-fps/" + name, Files.readAllBytes(licenses.resolve(name)));
            derived = zip(entries);
        }
        audit(source, derived, replacements, licenses, "", 0);
        require(replaced.size() == 29 && removed.size() == 1, "Pinned recursive asset inventory changed");
        if (!(args.length > 5 && args[5].equals("--audit-only"))) {
            Files.createDirectories(destination.toAbsolutePath().getParent());
            Files.write(destination, derived);
        }
        String report = "{\n  \"sourceSha512\": " + quote(SOURCE) + ",\n  \"derivedSha512\": " + quote(hash(derived))
            + ",\n  \"unchangedCodeAndMetadataFiles\": " + unchanged + ",\n  \"replacedMediaPaths\": " + array(replaced)
            + ",\n  \"removedReservedAssetNotices\": " + array(removed) + ",\n  \"unreviewedNestedMedia\": [],\n  \"passed\": true\n}\n";
        Files.createDirectories(Path.of(args[4]).toAbsolutePath().getParent());
        Files.writeString(Path.of(args[4]), report);
        System.out.println("Dynamic FPS recursive asset audit: " + replaced.size() + " images replaced; " + unchanged + " code/metadata resources unchanged.");
    }

    private static byte[] derive(byte[] source, Path replacements, int depth) throws Exception {
        require(depth < 8, "Nested archive depth exceeded");
        var entries = archive(source);
        boolean changed = false;
        for (String name : List.copyOf(entries.keySet())) {
            byte[] original = entries.get(name);
            if (name.endsWith(".jar")) {
                byte[] nested = derive(original, replacements, depth + 1);
                if (!Arrays.equals(original, nested)) { entries.put(name, nested); changed = true; }
            } else if (media(name, original)) {
                entries.put(name, replacement(replacements, name)); changed = true;
            } else if (name.equals(NOTICE)) {
                entries.remove(name); changed = true;
            }
        }
        return changed ? zip(entries) : source;
    }

    private static void audit(byte[] source, byte[] derived, Path replacements, Path licenses, String prefix, int depth) throws Exception {
        require(depth < 8, "Nested archive depth exceeded");
        var before = archive(source); var after = archive(derived);
        for (var entry : before.entrySet()) {
            String name = entry.getKey(), path = prefix + name; byte[] actual = after.get(name);
            if (name.equals(NOTICE)) {
                require(actual == null, "Reserved artwork notice retained: " + path); removed.add(path);
            } else if (media(name, entry.getValue())) {
                require(actual != null && Arrays.equals(actual, replacement(replacements, name)), "Missing licensed asset: " + path);
                require(!Arrays.equals(actual, entry.getValue()), "Reserved upstream artwork retained: " + path); replaced.add(path);
            } else if (name.endsWith(".jar")) {
                require(actual != null, "Nested dependency removed: " + path);
                audit(entry.getValue(), actual, replacements, licenses, path + "!/", depth + 1);
            } else {
                require(actual != null && Arrays.equals(actual, entry.getValue()), "Code/metadata changed: " + path); unchanged++;
            }
        }
        for (var entry : after.entrySet()) {
            if (before.containsKey(entry.getKey())) continue;
            String name = entry.getKey().replace("META-INF/licenses/dynamic-fps/", "");
            require(prefix.isEmpty() && entry.getKey().equals("META-INF/licenses/dynamic-fps/" + name) && LICENSES.contains(name)
                && Arrays.equals(entry.getValue(), Files.readAllBytes(licenses.resolve(name))), "Unreviewed derived resource: " + prefix + entry.getKey());
        }
        if (prefix.isEmpty()) for (String name : LICENSES) require(after.containsKey("META-INF/licenses/dynamic-fps/" + name), "Missing license: " + name);
    }

    private static byte[] replacement(Path replacements, String name) throws Exception {
        Path file = replacements.resolve(name).normalize();
        require(file.startsWith(replacements) && Files.isRegularFile(file), "Unreviewed media resource: " + name);
        return Files.readAllBytes(file);
    }

    private static LinkedHashMap<String, byte[]> archive(byte[] content) throws Exception {
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) continue;
                require(entries.put(entry.getName(), zip.readAllBytes()) == null, "Duplicate archive entry: " + entry.getName());
            }
        }
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.setLevel(9);
            for (var entry : entries.entrySet()) {
                var item = new ZipEntry(entry.getKey()); item.setTime(315532800000L);
                zip.putNextEntry(item); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static boolean media(String name, byte[] bytes) {
        if (MEDIA.stream().anyMatch(name.toLowerCase(Locale.ROOT)::endsWith)) return true;
        if (bytes.length < 4) return false;
        String magic = new String(bytes, 0, Math.min(bytes.length, 12), StandardCharsets.ISO_8859_1);
        return magic.startsWith("\u0089PNG") || magic.startsWith("GIF8") || magic.startsWith("RIFF") || magic.startsWith("OggS") || magic.startsWith("OTTO") || magic.startsWith("wOFF") || magic.startsWith("wOF2") || magic.startsWith("ttcf") || magic.startsWith("ID3") || magic.startsWith("fLaC") || magic.startsWith("MThd") || (bytes[0] == 0 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0);
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes)); }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static String array(List<String> values) { return "[" + String.join(", ", values.stream().map(PrepareCounterpart::quote).toList()) + "]"; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
