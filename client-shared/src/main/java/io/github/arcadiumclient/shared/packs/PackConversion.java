// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.packs;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.*;

public final class PackConversion {
    private static final int REVISION = 4, MAX_ENTRIES = 100_000, MAX_FILE = 64 * 1024 * 1024;
    private static final long MAX_TOTAL = 2L * 1024 * 1024 * 1024;
    private static final String DATA = "/assets/arcadium/pack-conversion/";
    private static final Map<String, JsonElement> TABLES = new ConcurrentHashMap<>();
    private static final Set<String> READY = ConcurrentHashMap.newKeySet();
    private static final Map<String, Path> SESSION = new ConcurrentHashMap<>();
    public record Target(int major, int minor) {
        public Target { if (major < 1 || minor < 0) throw new IllegalArgumentException("Invalid pack format"); }
        public boolean legacy() { return major == 1; }
        public String id() { return major + "." + minor; }
    }
    private PackConversion() { }

    public static boolean prepared(Path source, Target target) { return READY.contains(key(source, target)); }

    public static synchronized Path preview(Path source, Path cache, Target target) throws IOException {
        source = source.toAbsolutePath().normalize();
        JsonObject metadata = metadata(source);
        if (metadata == null || compatible(metadata.getAsJsonObject("pack"), target)) return source;
        byte[] icon;
        try { icon = smallResource(source, "pack.png", 4 * 1024 * 1024); }
        catch (IOException invalidIcon) { icon = null; }
        byte[] content = targetMetadata(metadata, target).toString().getBytes(StandardCharsets.UTF_8);
        String hash;
        try {
            var digest = MessageDigest.getInstance("SHA-256"); digest.update((source + ":" + REVISION).getBytes(StandardCharsets.UTF_8));
            digest.update(content); if (icon != null) digest.update(icon); hash = HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        Path directory = cache.toAbsolutePath().normalize().resolve(target.id()).resolve("catalog");
        Path output = directory.resolve(hash + ".zip");
        if (!validCache(output, target)) {
            Files.createDirectories(directory); Path temporary = Files.createTempFile(directory,"catalog-",".tmp");
            try {
                try (var zip = new ZipOutputStream(Files.newOutputStream(temporary))) { put(zip,"pack.mcmeta",content); if (icon != null) put(zip,"pack.png",icon); }
                Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        }
        READY.add(key(source,target)); return output;
    }

    private static JsonObject metadata(Path source) throws IOException {
        byte[] bytes = smallResource(source,"pack.mcmeta",1024 * 1024);
        if (bytes == null) return null;
        try {
            JsonObject value = JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            return value.has("pack") && value.get("pack").isJsonObject() ? value : null;
        } catch (RuntimeException invalid) { throw new IOException("Invalid pack.mcmeta",invalid); }
    }
    private static byte[] smallResource(Path source,String name,int limit) throws IOException {
        if (Files.isSymbolicLink(source)) throw new IOException("Symbolic pack paths are not converted");
        if (Files.isDirectory(source)) {
            Path file = source.resolve(name); if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) return null;
            try (var in = Files.newInputStream(file)) { byte[] data = in.readNBytes(limit + 1); if (data.length > limit) throw new IOException("Oversized pack metadata or icon"); return data; }
        }
        try (var zip = new ZipFile(source.toFile())) {
            var entry = zip.getEntry(name); if (entry == null) return null;
            try (var in = zip.getInputStream(entry)) { byte[] data = in.readNBytes(limit + 1); if (data.length > limit) throw new IOException("Oversized pack metadata or icon"); return data; }
        }
    }

    public static synchronized Path prepare(Path source, Path cache, Target target, Function<String, byte[]> vanilla) throws IOException {
        source = source.toAbsolutePath().normalize();
        JsonObject originalMetadata = metadata(source);
        if (originalMetadata == null || compatible(originalMetadata.getAsJsonObject("pack"), target)) return source;
        try (Input pack = new Input(source)) {
            byte[] metadata = pack.read("pack.mcmeta");
            if (metadata == null) return source;
            JsonObject root;
            try { root = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject(); }
            catch (RuntimeException error) { throw new IOException("Invalid pack.mcmeta", error); }
            JsonObject info = root.getAsJsonObject("pack");
            if (info == null) return source;
            boolean old = pack.names.stream().anyMatch(n -> n.contains("/textures/blocks/") || n.contains("/textures/items/"));
            int format = format(info);
            boolean legacy = old || format <= 3;
            if (compatible(info, target) && target.legacy() == legacy) return source;
            String fingerprint = pack.fingerprint(source + ":" + target.id() + ":" + REVISION);
            Path directory = cache.toAbsolutePath().normalize().resolve(target.id());
            Path output = directory.resolve(fingerprint + ".zip");
            String sessionKey = key(source, target) + ":" + fingerprint;
            if (SESSION.containsKey(sessionKey) && Files.isRegularFile(output)) return output;
            Files.createDirectories(directory);
            if (!validCache(output, target)) {
                Path temporary = Files.createTempFile(directory, "convert-", ".tmp");
                try {
                    convert(pack, root, legacy, format, target, vanilla, temporary);
                    if (!validCache(temporary, target)) throw new IOException("Converted pack validation failed");
                    try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
            }
            READY.add(key(source, target));
            SESSION.put(sessionKey, output);
            return output;
        }
    }

    private static String key(Path path, Target target) { return path.toAbsolutePath().normalize() + ":" + target.id(); }
    private static boolean validCache(Path path, Target target) {
        if (!Files.isRegularFile(path)) return false;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry("pack.mcmeta");
            if (entry == null) return false;
            try (var in = zip.getInputStream(entry)) {
                JsonObject pack = JsonParser.parseString(new String(in.readNBytes(1024 * 1024), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
                return compatible(pack, target);
            }
        } catch (IOException | RuntimeException failure) { return false; }
    }
    private static int format(JsonObject pack) {
        try {
            JsonElement value = pack.has("pack_format") ? pack.get("pack_format") : pack.get("min_format");
            return value == null ? 1 : value.isJsonArray() ? value.getAsJsonArray().get(0).getAsInt() : value.getAsInt();
        } catch (RuntimeException failure) { return Integer.MAX_VALUE; }
    }
    private static long bound(JsonElement value, boolean upper) {
        if (value == null) throw new IllegalArgumentException("Missing format");
        if (value.isJsonArray()) {
            var pair = value.getAsJsonArray();
            return pair.get(0).getAsLong() * 1_000_000L + (pair.size() > 1 ? pair.get(1).getAsLong() : upper ? 999_999 : 0);
        }
        return value.getAsLong() * 1_000_000L + (upper ? 999_999 : 0);
    }
    public static boolean compatible(JsonObject pack, Target target) {
        try {
            long min, max;
            if (pack.has("min_format")) { min = bound(pack.get("min_format"), false); max = bound(pack.get("max_format"), true); }
            else if (pack.has("supported_formats")) {
                var formats = pack.get("supported_formats");
                if (formats.isJsonObject()) { min = bound(formats.getAsJsonObject().get("min_inclusive"), false); max = bound(formats.getAsJsonObject().get("max_inclusive"), true); }
                else if (formats.isJsonArray()) { min = bound(formats.getAsJsonArray().get(0), false); max = bound(formats.getAsJsonArray().get(1), true); }
                else { min = bound(formats, false); max = bound(formats, true); }
            } else { var value = pack.has("pack_format") ? pack.get("pack_format") : new JsonPrimitive(1); min = bound(value, false); max = bound(value, true); }
            long current = target.major * 1_000_000L + target.minor;
            return min <= current && current <= max;
        } catch (RuntimeException error) { return false; }
    }

    private static void convert(Input input, JsonObject metadata, boolean old, int sourceFormat, Target target,
                                Function<String, byte[]> vanilla, Path output) throws IOException {
        Map<String, String> paths = new TreeMap<>();
        for (String name : input.names) {
            if (name.equals("pack.mcmeta") || !safe(name)) continue;
            if (name.contains("/shaders/") || old != target.legacy() && name.contains("/blockstates/")) continue;
            String mapped = rename(name, old, sourceFormat, target);
            if (old != target.legacy() && (name.contains("/models/block/") || name.contains("/models/item/"))) {
                if (!target.legacy() && name.contains("/models/block/")) continue;
            }
            if (!paths.containsKey(mapped) || mapped.equals(name)) paths.put(mapped, name);
            if (target.legacy() && name.startsWith("assets/minecraft/")) {
                for (String kind : List.of("block", "item")) {
                    String prefix="/textures/"+kind+"/";int at=name.indexOf(prefix);if(at<0)continue;
                    String filename=name.substring(at+prefix.length());int dot=filename.indexOf('.');if(dot<0)continue;
                    for(String alias:reverseAliases(filename.substring(0,dot),kind+"s"))
                        paths.putIfAbsent(name.substring(0,at)+"/textures/"+kind+"s/"+alias+filename.substring(dot),name);
                }
            }
        }
        Map<String, byte[]> generated = new TreeMap<>();
        if (old && !target.legacy()) sliceAtlases(input, generated);
        if (!old && target.legacy()) mergeAtlases(input, generated, vanilla);
        armor(input, generated, old, target);
        if (sourceFormat < 88 && target.major >= 88) slices262(input, generated, false, vanilla);
        if (sourceFormat >= 88 && target.major < 88) slices262(input, generated, true, vanilla);
        legacyBeds(input, generated, old, target);
        targetMetadata(metadata, target);
        generated.put("pack.mcmeta", metadata.toString().getBytes(StandardCharsets.UTF_8));
        long written = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var entry : paths.entrySet()) {
                byte[] bytes = input.read(entry.getValue());
                if (bytes == null) continue;
                if (entry.getKey().startsWith("assets/minecraft/") && entry.getKey().endsWith(".json") && old != target.legacy()) {
                    try {
                        JsonElement json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                        bytes = rewrite(json, old, sourceFormat, target).toString().getBytes(StandardCharsets.UTF_8);
                    } catch (RuntimeException ignored) { }
                }
                if (!generated.containsKey(entry.getKey())) {
                    written += bytes.length; if (written > MAX_TOTAL) throw new IOException("Converted pack exceeds 2 GiB");
                    put(zip, entry.getKey(), bytes);
                }
            }
            for (var entry : generated.entrySet()) {
                written += entry.getValue().length; if (written > MAX_TOTAL) throw new IOException("Converted pack exceeds 2 GiB");
                put(zip, entry.getKey(), entry.getValue());
            }
        }
    }
    private static JsonObject targetMetadata(JsonObject metadata, Target target) {
        JsonObject info = metadata.getAsJsonObject("pack");
        for (String remove : List.of("supported_formats", "min_format", "max_format", "overlays", "filter")) info.remove(remove);
        info.addProperty("pack_format", target.major);
        if (!target.legacy()) {
            JsonArray version = new JsonArray(); version.add(target.major); version.add(target.minor);
            info.add("min_format", version); info.add("max_format", version.deepCopy());
        }
        metadata.remove("overlays"); metadata.remove("filter");
        return metadata;
    }
    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
    }
    private static JsonElement rewrite(JsonElement value, boolean old, int source, Target target) {
        if (value.isJsonObject()) {
            JsonObject result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(e -> {
                if(e.getKey().equals("parent") && e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                    String parent=e.getValue().getAsString();
                    if(!target.legacy() && parent.equals("builtin/generated"))parent="item/generated";
                    else if(target.legacy() && (parent.equals("item/generated") || parent.equals("item/handheld") || parent.equals("minecraft:item/generated") || parent.equals("minecraft:item/handheld")))parent="builtin/generated";
                    result.addProperty(e.getKey(),parent);
                } else result.add(e.getKey(), rewrite(e.getValue(), old, source, target));
            });
            return result;
        }
        if (value.isJsonArray()) { JsonArray result = new JsonArray(); value.getAsJsonArray().forEach(v -> result.add(rewrite(v, old, source, target))); return result; }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            if (text.equals("builtin/generated") && !target.legacy()) return new JsonPrimitive("item/generated");
            if (text.equals("item/generated") && target.legacy()) return new JsonPrimitive("builtin/generated");
            int colon = text.indexOf(':'); String namespace = colon < 0 ? "" : text.substring(0, colon + 1);
            if (!namespace.isEmpty() && !namespace.equals("minecraft:")) return value.deepCopy();
            String resource = colon < 0 ? text : text.substring(colon + 1);
            if (resource.startsWith("blocks/") || resource.startsWith("block/") || resource.startsWith("items/") || resource.startsWith("item/")) {
                String kind = resource.startsWith("block") ? "blocks" : "items";
                String name = resource.substring(resource.indexOf('/') + 1);
                return new JsonPrimitive(namespace + (target.legacy() ? kind : kind.substring(0, kind.length() - 1)) + "/" + remap(name, kind, old, source, target));
            }
        }
        return value.deepCopy();
    }
    public static String rename(String path, boolean old, int sourceFormat, Target target) {
        if(!path.startsWith("assets/minecraft/"))return path;
        if (sourceFormat >= 88 && target.major < 88) path = mapped(table("textures-262").getAsJsonObject(), path, true);
        path = renameBase(path, old, sourceFormat, target);
        return sourceFormat < 88 && target.major >= 88 ? mapped(table("textures-262").getAsJsonObject(), path, false) : path;
    }
    private static String renameBase(String path, boolean old, int sourceFormat, Target target) {
        for (String kind : List.of("blocks", "items")) {
            for (String prefix : List.of("/textures/" + kind + "/", "/textures/" + kind.substring(0, kind.length() - 1) + "/", "/models/" + kind.substring(0, kind.length() - 1) + "/")) {
                int start = path.indexOf(prefix);
                if (start < 0) continue;
                String file = path.substring(start + prefix.length()); int extension = file.indexOf('.');
                String name = extension < 0 ? file : file.substring(0, extension), suffix = extension < 0 ? "" : file.substring(extension);
                String directory = prefix.startsWith("/models/") ? prefix : "/textures/" + (target.legacy() ? kind : kind.substring(0, kind.length() - 1)) + "/";
                String pbr="";
                if(!prefix.startsWith("/models/") && (name.endsWith("_n") || name.endsWith("_s") || name.endsWith("_e"))) {pbr=name.substring(name.length()-2);name=name.substring(0,name.length()-2);}
                return path.substring(0, start) + directory + remap(name, kind, old, sourceFormat, target) + pbr + suffix;
            }
        }
        if (path.contains("/textures/entity/")) {
            int slash = path.lastIndexOf('/'), dot = path.indexOf('.', slash);
            if (dot > slash) {
                String name = path.substring(slash + 1, dot);
                var map = table("entities").getAsJsonObject().getAsJsonObject("*");
                String changed = mapped(map, name, target.legacy());
                return path.substring(0, slash + 1) + changed + path.substring(dot);
            }
        }
        return path;
    }
    private static String remap(String name, String kind, boolean old, int source, Target target) {
        var groups = table(kind).getAsJsonObject();
        List<String> stages = new ArrayList<>(groups.keySet());
        if (target.legacy()) Collections.reverse(stages);
        for (String stage : stages) {
            int threshold = switch (stage) { case "1_13" -> 4; case "1_14" -> 4; case "1_17" -> 7; case "1_19" -> 9; default -> 22; };
            if (target.legacy() ? !old : old || source <= threshold) name = mapped(groups.getAsJsonObject(stage), name, target.legacy());
        }
        return name;
    }
    private static String mapped(JsonObject map, String name, boolean reverse) {
        if (!reverse) return map.has(name) ? map.get(name).getAsString() : name;
        for (var entry : map.entrySet()) if (entry.getValue().getAsString().equals(name)) return entry.getKey();
        return name;
    }
    private static Set<String> reverseAliases(String name,String kind) {
        String suffix="";if(name.endsWith("_n") || name.endsWith("_s") || name.endsWith("_e")){suffix=name.substring(name.length()-2);name=name.substring(0,name.length()-2);}
        Set<String> values=new LinkedHashSet<>(List.of(name));var groups=table(kind).getAsJsonObject();List<String> stages=new ArrayList<>(groups.keySet());Collections.reverse(stages);
        for(String stage:stages) {
            var map=groups.getAsJsonObject(stage);Set<String> next=new LinkedHashSet<>();
            for(String current:values) { boolean found=false;for(var entry:map.entrySet())if(entry.getValue().getAsString().equals(current)){next.add(entry.getKey());found=true;}if(!found)next.add(current); }
            values=next;
        }
        if(!suffix.isEmpty()){Set<String> suffixed=new LinkedHashSet<>();for(String value:values)suffixed.add(value+suffix);return suffixed;}
        return values;
    }
    private static JsonElement table(String name) {
        return TABLES.computeIfAbsent(name, key -> {
            try (InputStream in = PackConversion.class.getResourceAsStream(DATA + key + ".json")) {
                if (in == null) throw new IllegalStateException("Missing pack conversion table: " + key);
                return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException error) { throw new UncheckedIOException(error); }
        });
    }
    private static List<JsonObject> atlases() {
        List<JsonObject> specs = new ArrayList<>();
        for (var element : table("gui").getAsJsonArray()) {
            JsonObject copy = element.getAsJsonObject().deepCopy(); copy.addProperty("prefix", "assets/minecraft/textures/gui/"); specs.add(copy);
        }
        for (String kind : List.of("particles", "mob_effect", "paintings_kristoffer_zetterstrand")) {
            JsonObject copy = table(kind).getAsJsonObject().deepCopy();
            copy.addProperty("prefix", kind.equals("particles") ? "assets/minecraft/textures/particle/" : "assets/minecraft/textures/");
            specs.add(copy);
        }
        return specs;
    }
    private static String tilePath(JsonObject spec, JsonObject tile) {
        String prefix = spec.get("prefix").getAsString();
        if (!prefix.endsWith("/gui/") && !prefix.endsWith("/particle/")) {
            if (spec.get("path").getAsString().contains("painting")) prefix += "painting/";
            else prefix += "mob_effect/";
        }
        return prefix + tile.get("path").getAsString();
    }
    private static void sliceAtlases(Input input, Map<String, byte[]> generated) throws IOException {
        for (JsonObject spec : atlases()) {
            String atlasPath = spec.get("prefix").getAsString() + spec.get("path").getAsString();
            byte[] encoded = input.read(atlasPath); if (encoded == null) continue;
            BufferedImage image = image(encoded); if (image == null) continue;
            int rw = spec.get("width").getAsInt(), rh = spec.get("height").getAsInt();
            for (JsonElement element : spec.getAsJsonArray("textures")) {
                JsonObject tile = element.getAsJsonObject(); if (tile.has("remove") && tile.get("remove").getAsBoolean()) continue;
                var box = tile.getAsJsonObject("box"); if (box == null || !tile.has("path")) continue;
                int x = box.get("x").getAsInt() * image.getWidth() / rw, y = box.get("y").getAsInt() * image.getHeight() / rh;
                int w = box.get("width").getAsInt() * image.getWidth() / rw, h = box.get("height").getAsInt() * image.getHeight() / rh;
                if (w <= 0 || h <= 0 || x < 0 || y < 0 || x + w > image.getWidth() || y + h > image.getHeight()) continue;
                String path = tilePath(spec, tile); if (input.names.contains(path)) continue;
                generated.putIfAbsent(path, png(image.getSubimage(x, y, w, h)));
                if (tile.has("metadata")) generated.putIfAbsent(path + ".mcmeta", tile.get("metadata").toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    private static void mergeAtlases(Input input, Map<String, byte[]> generated, Function<String, byte[]> vanilla) throws IOException {
        if (vanilla == null) return;
        for (JsonObject spec : atlases()) {
            String path = spec.get("prefix").getAsString() + spec.get("path").getAsString();
            if (input.names.contains(path)) continue;
            boolean contains = false;
            for (var element : spec.getAsJsonArray("textures")) if (element.isJsonObject() && element.getAsJsonObject().has("path") && input.names.contains(tilePath(spec, element.getAsJsonObject()))) { contains = true; break; }
            if (!contains) continue;
            byte[] fallback = vanilla.apply(path); if (fallback == null) continue;
            BufferedImage atlas = image(fallback); if (atlas == null) continue;
            int scale = 1;
            for (var element : spec.getAsJsonArray("textures")) {
                var tile = element.getAsJsonObject(); if (!tile.has("box") || !tile.has("path")) continue;
                byte[] bytes = input.read(tilePath(spec, tile)); if (bytes == null) continue;
                BufferedImage piece = image(bytes); if (piece == null) continue;
                int w = tile.getAsJsonObject("box").get("width").getAsInt();
                scale = Math.max(scale, Math.min(8, piece.getWidth() / Math.max(1, w)));
            }
            int rw = spec.get("width").getAsInt(), rh = spec.get("height").getAsInt();
            BufferedImage result = new BufferedImage(rw * scale, rh * scale, BufferedImage.TYPE_INT_ARGB);
            var graphics = result.createGraphics();
            try {
                graphics.setComposite(java.awt.AlphaComposite.Src); graphics.drawImage(atlas, 0, 0, result.getWidth(), result.getHeight(), null);
                for (var element : spec.getAsJsonArray("textures")) {
                    var tile = element.getAsJsonObject(); if (!tile.has("box") || !tile.has("path")) continue;
                    byte[] bytes = input.read(tilePath(spec, tile)); if (bytes == null) continue;
                    BufferedImage piece = image(bytes); if (piece == null) continue;
                    var box = tile.getAsJsonObject("box");
                    int x = box.get("x").getAsInt() * scale, y = box.get("y").getAsInt() * scale;
                    int w = box.get("width").getAsInt() * scale, h = box.get("height").getAsInt() * scale;
                    graphics.drawImage(piece, x, y, w, h, null);
                }
            } finally { graphics.dispose(); }
            generated.put(path, png(result));
        }
    }
    private static void armor(Input input, Map<String, byte[]> generated, boolean old, Target target) throws IOException {
        for (String material : List.of("leather", "chainmail", "iron", "gold", "diamond")) for (int layer = 1; layer <= 2; layer++) for (String overlay : List.of("", "_overlay")) {
            String legacy = "assets/minecraft/textures/models/armor/" + material + "_layer_" + layer + overlay + ".png";
            String modern = "assets/minecraft/textures/entity/equipment/" + (layer == 1 ? "humanoid/" : "humanoid_leggings/") + (material.equals("gold") ? "gold" : material) + overlay + ".png";
            String from = target.legacy() ? modern : legacy, to = target.legacy() ? legacy : modern;
            if (old != target.legacy() && !input.names.contains(to)) { byte[] bytes = input.read(from); if (bytes != null) generated.put(to, bytes); }
        }
        if (!target.legacy() && old) {
            byte[] glint = input.read("assets/minecraft/textures/misc/enchanted_item_glint.png");
            if (glint != null) for (String name : List.of("enchanted_glint_item", "enchanted_glint_entity")) generated.put("assets/minecraft/textures/misc/" + name + ".png", glint);
        }
    }
    private static void legacyBeds(Input input, Map<String, byte[]> generated, boolean old, Target target) throws IOException {
        String[][] faces = {{"head_top", "head_up"}, {"feet_top", "foot_up"}, {"head_side", "head_east"}, {"head_side", "head_west"}, {"feet_side", "foot_east"}, {"feet_side", "foot_west"}, {"feet_end", "foot_south"}, {"head_end", "head_north"}};
        for (String[] face : faces) {
            String legacy = "assets/minecraft/textures/blocks/bed_" + face[0] + ".png";
            String modern = "assets/minecraft/textures/block/red_bed_" + face[1] + ".png";
            if (old && target.major >= 88 && !input.names.contains(modern)) { byte[] bytes = input.read(legacy); if (bytes != null) generated.putIfAbsent(modern, bytes); }
            if (!old && target.legacy() && !input.names.contains(legacy)) { byte[] bytes = input.read(modern); if (bytes != null) generated.putIfAbsent(legacy, bytes); }
        }
    }
    private static List<JsonObject> parts(JsonObject output) {
        if (output.has("boxes")) {
            List<JsonObject> parts = new ArrayList<>(); output.getAsJsonArray("boxes").forEach(v -> parts.add(v.getAsJsonObject())); return parts;
        }
        JsonObject part = new JsonObject(); part.add("box", output.get("box")); part.add("transformers", new JsonArray()); part.addProperty("offsetX",0); part.addProperty("offsetY",0);
        return List.of(part);
    }
    private static int integer(JsonObject object, String key) { return object.get(key).getAsInt(); }
    private static void slices262(Input input, Map<String, byte[]> generated, boolean reverse, Function<String, byte[]> vanilla) throws IOException {
        for (var element : table("slices-262").getAsJsonArray()) {
            JsonObject spec = element.getAsJsonObject(); String source = "assets/" + spec.get("path").getAsString();
            BufferedImage atlas;
            if (reverse) {
                if (input.names.contains(source) || vanilla == null) continue;
                boolean found = false; for (var out : spec.getAsJsonArray("outputs")) if (input.names.contains("assets/" + out.getAsJsonObject().get("path").getAsString())) { found = true; break; }
                if (!found) continue;
                byte[] bytes = generated.containsKey(source) ? generated.get(source) : vanilla.apply(source); if (bytes == null) continue; atlas = image(bytes);
            } else { byte[] bytes = input.read(source); if (bytes == null) continue; atlas = image(bytes); }
            if (atlas == null) continue;
            for (var outputElement : spec.getAsJsonArray("outputs")) {
                JsonObject output = outputElement.getAsJsonObject(); String destination = "assets/" + output.get("path").getAsString();
                List<JsonObject> boxes = parts(output); var firstBox = boxes.getFirst().getAsJsonObject("box");
                int rw = integer(firstBox,"totalW"), rh = integer(firstBox,"totalH");
                if (!reverse) {
                    if (input.names.contains(destination)) continue;
                    int w = output.has("width") ? integer(output,"width") : integer(firstBox,"w"), h = output.has("height") ? integer(output,"height") : integer(firstBox,"h");
                    BufferedImage result = new BufferedImage(Math.max(1,w * atlas.getWidth() / rw),Math.max(1,h * atlas.getHeight() / rh),BufferedImage.TYPE_INT_ARGB);
                    var graphics = result.createGraphics(); graphics.setComposite(java.awt.AlphaComposite.Src);
                    try {
                        for (var part : boxes) {
                            var box = part.getAsJsonObject("box"); int x = integer(box,"x") * atlas.getWidth() / rw, y = integer(box,"y") * atlas.getHeight() / rh;
                            int cw = integer(box,"w") * atlas.getWidth() / rw, ch = integer(box,"h") * atlas.getHeight() / rh;
                            if (cw <= 0 || ch <= 0 || x + cw > atlas.getWidth() || y + ch > atlas.getHeight()) continue;
                            BufferedImage crop = transform(atlas.getSubimage(x,y,cw,ch),part.getAsJsonArray("transformers"),false);
                            graphics.drawImage(crop,integer(part,"offsetX") * atlas.getWidth() / rw,integer(part,"offsetY") * atlas.getHeight() / rh,null);
                        }
                    } finally { graphics.dispose(); }
                    result = transform(result,output.getAsJsonArray("transformers"),false);
                    generated.putIfAbsent(destination,png(result));
                } else {
                    byte[] bytes = input.read(destination); if (bytes == null) continue; BufferedImage piece = image(bytes); if (piece == null) continue;
                    piece = transform(piece,output.getAsJsonArray("transformers"),true);
                    int outW = output.has("width") ? integer(output,"width") : integer(firstBox,"w"), outH = output.has("height") ? integer(output,"height") : integer(firstBox,"h");
                    var graphics = atlas.createGraphics(); graphics.setComposite(java.awt.AlphaComposite.Src);
                    try {
                        for (var part : boxes) {
                            var box = part.getAsJsonObject("box"); int bw = integer(box,"w"), bh = integer(box,"h");
                            for (var op : part.getAsJsonArray("transformers")) if (op.getAsString().equals("rotate90") || op.getAsString().equals("rotate270")) { int temp=bw;bw=bh;bh=temp; }
                            int x = integer(part,"offsetX") * piece.getWidth() / outW, y = integer(part,"offsetY") * piece.getHeight() / outH;
                            int cw = bw * piece.getWidth() / outW, ch = bh * piece.getHeight() / outH;
                            if (cw <= 0 || ch <= 0 || x + cw > piece.getWidth() || y + ch > piece.getHeight()) continue;
                            BufferedImage crop = transform(piece.getSubimage(x,y,cw,ch),part.getAsJsonArray("transformers"),true);
                            graphics.drawImage(crop,integer(box,"x") * atlas.getWidth() / rw,integer(box,"y") * atlas.getHeight() / rh,integer(box,"w") * atlas.getWidth() / rw,integer(box,"h") * atlas.getHeight() / rh,null);
                        }
                    } finally { graphics.dispose(); }
                }
            }
            if (reverse) generated.put(source,png(atlas));
        }
    }
    private static BufferedImage transform(BufferedImage image, JsonArray operations, boolean reverse) {
        if (operations == null) return image;
        List<String> names = new ArrayList<>(); operations.forEach(op -> names.add(op.getAsString())); if (reverse) Collections.reverse(names);
        for (String name : names) {
            if (reverse) name = name.equals("rotate90") ? "rotate270" : name.equals("rotate270") ? "rotate90" : name;
            int w = image.getWidth(), h = image.getHeight(); boolean quarter = name.equals("rotate90") || name.equals("rotate270");
            BufferedImage result = new BufferedImage(quarter ? h : w, quarter ? w : h,BufferedImage.TYPE_INT_ARGB);
            for (int y=0;y<h;y++) for(int x=0;x<w;x++) {
                int dx=x,dy=y;
                switch(name) { case "rotate90" -> { dx=h-1-y;dy=x; } case "rotate180" -> { dx=w-1-x;dy=h-1-y; } case "rotate270" -> { dx=y;dy=w-1-x; } case "mirrorVertically" -> dy=h-1-y; case "mirrorHorizontally" -> dx=w-1-x; default -> throw new IllegalArgumentException("Unknown image transform"); }
                result.setRGB(dx,dy,image.getRGB(x,y));
            }
            image=result;
        }
        return image;
    }
    private static BufferedImage image(byte[] bytes) throws IOException {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream); if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(stream); int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > 16_777_216) throw new IOException("Pack image exceeds 16 megapixels");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
    private static byte[] png(BufferedImage image) throws IOException { var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); return bytes.toByteArray(); }
    private static boolean safe(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) return false;
        for (String part : name.split("/")) if (part.equals(".") || part.equals("..") || part.isBlank()) return false;
        return !name.contains("\u0000");
    }
    private static final class Input implements AutoCloseable {
        final Path source; final ZipFile zip; final SortedSet<String> names = new TreeSet<>(); long total;
        Input(Path source) throws IOException {
            this.source = source;
            if (Files.isSymbolicLink(source)) throw new IOException("Symbolic pack paths are not converted");
            if (Files.isDirectory(source)) {
                zip = null;
                Files.walkFileTree(source, EnumSet.noneOf(FileVisitOption.class), 64, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        if (attrs.isRegularFile() && !Files.isSymbolicLink(path)) add(source.relativize(path).toString().replace('\\', '/'), attrs.size());
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                zip = new ZipFile(source.toFile());
                try { var entries = zip.entries(); while (entries.hasMoreElements()) { var entry = entries.nextElement(); if (!entry.isDirectory()) add(entry.getName(), entry.getSize()); } }
                catch (IOException | RuntimeException failure) { zip.close(); throw failure; }
            }
        }
        private void add(String name, long size) throws IOException {
            if (!safe(name)) throw new IOException("Unsafe path in resource pack");
            if (size < 0 || size > MAX_FILE || (total += size) > MAX_TOTAL || names.size() >= MAX_ENTRIES) throw new IOException("Resource pack exceeds conversion limits");
            if (!names.add(name)) throw new IOException("Duplicate resource in pack: " + name);
        }
        byte[] read(String name) throws IOException {
            if (!names.contains(name)) return null;
            try (InputStream stream = zip == null ? Files.newInputStream(source.resolve(name)) : zip.getInputStream(zip.getEntry(name))) {
                byte[] bytes = stream.readNBytes(MAX_FILE + 1); if (bytes.length > MAX_FILE) throw new IOException("Oversized resource"); return bytes;
            }
        }
        String fingerprint(String seed) throws IOException {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(seed.getBytes(StandardCharsets.UTF_8));
                for (String name : names) {
                    digest.update(name.getBytes(StandardCharsets.UTF_8));
                    String data = zip == null ? Files.size(source.resolve(name)) + ":" + Files.getLastModifiedTime(source.resolve(name)).toMillis()
                        : zip.getEntry(name).getSize() + ":" + zip.getEntry(name).getCrc();
                    digest.update(data.getBytes(StandardCharsets.UTF_8));
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
        @Override public void close() throws IOException { if (zip != null) zip.close(); }
    }
}
