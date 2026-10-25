// SPDX-License-Identifier: GPL-3.0-only
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;

public final class AuditCounterpart {
    private static final List<String> MEDIA = List.of(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".bmp", ".ico", ".icns", ".tga", ".tif", ".tiff", ".ttf", ".otf", ".ttc", ".eot", ".woff", ".woff2", ".ogg", ".wav", ".mp3", ".flac", ".aac", ".m4a", ".mid", ".mp4", ".webm");
    public static void main(String[] args) throws Exception {
        byte[] sourceBytes = Files.readAllBytes(Path.of(args[0])), outputBytes = Files.readAllBytes(Path.of(args[1]));
        Path replacements = Path.of(args[2]).toAbsolutePath().normalize(), report = Path.of(args[3]);
        var source = archive(sourceBytes); var output = archive(outputBytes);
        String sourceHash = hash(sourceBytes);
        require(sourceHash.equals("c45652252e2176cb8e2c44dfd9032c617135a100dcf161b9e6e6717803c14d1359d0f940fa10dc1aa6ad80669983f4b9c5636c0c2953d3314e06f893f0cdc736"), "Unexpected source release");
        List<String> replaced = new ArrayList<>(), removed = new ArrayList<>(), nested = new ArrayList<>();
        int unchanged = 0;
        for (var entry : source.entrySet()) {
            String name = entry.getKey(); byte[] actual = output.get(name);
            if (media(name, entry.getValue())) {
                Path owned = replacements.resolve(name).normalize();
                require(owned.startsWith(replacements) && Files.isRegularFile(owned), "Missing licensed replacement: " + name);
                require(actual != null && Arrays.equals(actual, Files.readAllBytes(owned)), "Replacement differs from its licensed source: " + name);
                require(!Arrays.equals(actual, entry.getValue()), "Upstream media was retained: " + name);
                replaced.add(name);
            } else if (name.equals("assets/dynamic_fps/textures/battery/license.txt")) {
                require(actual == null, "Removed artwork notice is still bundled"); removed.add(name);
            } else {
                require(actual != null && Arrays.equals(actual, entry.getValue()), "Upstream code/metadata changed: " + name); unchanged++;
                if (name.endsWith(".jar")) nestedMedia(name, actual, nested);
            }
        }
        for (var entry : output.entrySet()) {
            if (!source.containsKey(entry.getKey())) require(entry.getKey().startsWith("META-INF/licenses/dynamic-fps/"), "Unreviewed derived resource: " + entry.getKey());
            if (media(entry.getKey(), entry.getValue())) require(replaced.contains(entry.getKey()), "Unreviewed derived media: " + entry.getKey());
        }
        require(replaced.size() == 28, "Replacement inventory changed; review every asset");
        require(nested.isEmpty(), "Nested dependency contains unreviewed media: " + nested);
        for (String license : List.of("Dynamic-FPS-MIT.txt", "Feather-MIT.txt", "Arcadium-assets-MIT.txt", "ASSET-PROVENANCE.txt"))
            require(output.containsKey("META-INF/licenses/dynamic-fps/" + license), "Missing license: " + license);
        String json = "{\n  \"sourceSha512\": " + quote(sourceHash) + ",\n  \"derivedSha512\": " + quote(hash(outputBytes)) + ",\n  \"unchangedCodeAndMetadataFiles\": " + unchanged + ",\n  \"replacedMediaPaths\": " + array(replaced) + ",\n  \"removedReservedAssetNotices\": " + array(removed) + ",\n  \"unreviewedNestedMedia\": [],\n  \"passed\": true\n}\n";
        Files.createDirectories(report.toAbsolutePath().getParent()); Files.writeString(report, json);
        System.out.println("Dynamic FPS asset audit: " + replaced.size() + " media replaced; " + unchanged + " code/metadata resources unchanged.");
    }
    private static Map<String, byte[]> archive(byte[] content) throws Exception {
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) continue;
                require(entries.put(entry.getName(), zip.readAllBytes()) == null, "Duplicate archive entry: " + entry.getName());
            }
        }
        return entries;
    }
    private static void nestedMedia(String parent, byte[] bytes, List<String> found) throws Exception {
        for (var entry : archive(bytes).entrySet()) {
            String name = parent + "!/" + entry.getKey();
            if (media(entry.getKey(), entry.getValue())) found.add(name);
            if (entry.getKey().endsWith(".jar")) nestedMedia(name, entry.getValue(), found);
        }
    }
    private static boolean media(String name, byte[] bytes) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (MEDIA.stream().anyMatch(lower::endsWith)) return true;
        if (bytes.length < 4) return false;
        String magic = new String(bytes, 0, Math.min(bytes.length, 12), StandardCharsets.ISO_8859_1);
        return magic.startsWith("\u0089PNG") || magic.startsWith("GIF8") || magic.startsWith("RIFF") || magic.startsWith("OggS") || magic.startsWith("OTTO") || magic.startsWith("wOFF") || magic.startsWith("wOF2") || magic.startsWith("ttcf") || magic.startsWith("ID3") || magic.startsWith("fLaC") || magic.startsWith("MThd") || (bytes[0] == 0 && bytes[1] == 1 && bytes[2] == 0 && bytes[3] == 0);
    }
    private static String hash(byte[] bytes) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes)); }
    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static String array(List<String> values) { return "[" + String.join(", ", values.stream().map(AuditCounterpart::quote).toList()) + "]"; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
