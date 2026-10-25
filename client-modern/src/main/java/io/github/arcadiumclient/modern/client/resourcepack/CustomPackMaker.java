// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.resourcepack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class CustomPackMaker {
    private static final int MAX_ENTRIES = 100000;
    private static final long MAX_FILE = 256L * 1024 * 1024;
    private static final long MAX_TOTAL = 2L * 1024 * 1024 * 1024;

    public enum Element {
        SWORDS("Swords"), TOOLS("Tools"), BLOCKS("Blocks"), ITEMS("Other items"),
        SKY("Sky"), FIREBALL("Fireball"), ARMOR("Armor"), ENTITIES("Mobs and entities"),
        PARTICLES("Particles"), FIRE("Fire"), WATER("Water and lava"), GUI("Interface"),
        FONT("Fonts"), SOUNDS("Sounds");

        public final String label;
        Element(String label) { this.label = label; }

        public boolean includes(String path) {
            String name = path.toLowerCase(Locale.ROOT);
            if (!name.startsWith("assets/")) return false;
            String file = name.substring(name.lastIndexOf('/') + 1);
            boolean item = name.contains("/textures/items/") || name.contains("/textures/item/") || name.contains("/models/item/") || name.contains("/items/");
            boolean sword = item && file.matches("(?:wooden|stone|iron|golden|diamond|netherite)_sword\\..*");
            boolean tool = item && file.matches(".*_(?:pickaxe|axe|shovel|hoe)\\..*");
            boolean ball = item && file.startsWith("fire_charge.") || name.contains("/textures/entity/fireball");
            boolean fire = (name.contains("/textures/blocks/fire_layer_") || name.contains("/textures/block/fire_") || name.contains("/textures/block/soul_fire_")) || name.contains("/blockstates/fire.")
                    || name.contains("/models/block/fire");
            boolean water = name.contains("/textures/blocks/water_") || name.contains("/textures/blocks/lava_") || name.contains("/textures/block/water_") || name.contains("/textures/block/lava_");
            boolean armor = (name.contains("/textures/models/armor/") || name.contains("/textures/entity/equipment/") || name.contains("/equipment/")) || item && file.matches(".*_(?:helmet|chestplate|leggings|boots)\\..*");
            return switch (this) {
                case SWORDS -> sword;
                case TOOLS -> tool;
                case BLOCKS -> !fire && !water && (name.contains("/textures/blocks/") || name.contains("/textures/block/") || name.contains("/models/block/") || name.contains("/blockstates/"));
                case ITEMS -> item && !sword && !tool && !ball && !armor;
                case SKY -> name.contains("/mcpatcher/sky/") || name.contains("/optifine/sky/")
                        || name.contains("/textures/environment/sun.") || name.contains("/textures/environment/moon_phases.")
                        || name.endsWith("/mcpatcher/color.properties") || name.endsWith("/optifine/color.properties");
                case FIREBALL -> ball;
                case ARMOR -> armor;
                case ENTITIES -> name.contains("/textures/entity/") && !ball;
                case PARTICLES -> name.contains("/textures/particle/") || name.contains("/particles/");
                case FIRE -> fire;
                case WATER -> water;
                case GUI -> name.contains("/textures/gui/");
                case FONT -> name.contains("/textures/font/") || name.contains("/font/");
                case SOUNDS -> name.contains("/sounds/") || name.endsWith("/sounds.json");
            };
        }
    }

    public record Source(String name, Path path) { }
    public record Result(Path path, int files) { }
    private record Content(Pack pack, String entry) { }

    private CustomPackMaker() { }

    public static Result create(Path directory, String name, Source base, Map<Element, Source> choices) throws IOException {
        if (base == null) throw new IOException("Choose a base pack.");
        String clean = name == null ? "" : name.strip();
        if (clean.toLowerCase(Locale.ROOT).endsWith(".zip")) clean = clean.substring(0, clean.length() - 4).strip();
        if (clean.isBlank() || clean.length() > 80 || clean.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*")
                || clean.endsWith(".") || clean.equals("..") || clean.matches("(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))
            throw new IOException("Enter a pack name without file path characters.");
        Files.createDirectories(directory);
        Map<Path, Pack> packs = new LinkedHashMap<>();
        Path temporary = null;
        try {
            Pack basePack = open(base, packs);
            if (!basePack.entries.containsKey("pack.mcmeta")) throw new IOException("The base pack has no pack.mcmeta.");
            Map<String, Content> contents = new TreeMap<>();
            basePack.entries.forEach((entry, value) -> contents.put(entry, new Content(basePack, value)));
            Map<String, String> origins = new LinkedHashMap<>();
            Set<String> explicit = new HashSet<>();
            List<Content> selectedContents = new ArrayList<>();
            origins.put("base", base.name);
            for (Element element : Element.values()) {
                Source choice = choices.get(element);
                if (choice == null) continue;
                Pack pack = open(choice, packs);
                List<String> selected = pack.entries.keySet().stream().filter(element::includes).toList();
                if (selected.isEmpty()) throw new IOException(choice.name + " has no matching files for " + element.label + ".");
                contents.keySet().removeIf(element::includes);
                for (String entry : selected) {
                    Content value = new Content(pack, pack.entries.get(entry));
                    contents.put(entry, value);
                    explicit.add(entry);
                    selectedContents.add(value);
                }
                origins.put(element.label, choice.name);
            }
            copyDependencies(contents.values().stream().filter(value -> value.pack == basePack).toList(), explicit, contents);
            copyDependencies(selectedContents, explicit, contents);
            int sourceIndex = 0;
            for (Pack pack : packs.values()) {
                String prefix = "arcadium-sources/" + ++sourceIndex + "/";
                pack.entries.forEach((entry, value) -> {
                    if (!entry.startsWith("assets/") && !entry.equals("pack.png"))
                        contents.put(prefix + entry, new Content(pack, value));
                });
            }
            contents.remove("pack.mcmeta");
            contents.remove("arcadium-pack.json");
            if (contents.size() > MAX_ENTRIES) throw new IOException("The combined pack has too many files.");
            temporary = Files.createTempFile(directory, ".arcadium-pack-", ".tmp");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                try (InputStream input = basePack.read(basePack.entries.get("pack.mcmeta"))) {
                    byte[] original = input.readNBytes(2 * 1024 * 1024 + 1);
                    if (original.length > 2 * 1024 * 1024) throw new IOException("The base pack metadata is too large.");
                    var metadata = JsonParser.parseString(new String(original, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!metadata.has("pack") || !metadata.get("pack").isJsonObject()) throw new IOException("The base pack has invalid pack metadata.");
                    metadata.getAsJsonObject("pack").addProperty("description", clean);
                    write(output, "pack.mcmeta", new GsonBuilder().create().toJson(metadata));
                }
                write(output, "arcadium-pack.json", new GsonBuilder().setPrettyPrinting().create().toJson(origins));
                byte[] buffer = new byte[32768];
                long total = 0;
                for (Map.Entry<String, Content> item : contents.entrySet()) {
                    ZipEntry entry = new ZipEntry(item.getKey());
                    entry.setTime(0);
                    output.putNextEntry(entry);
                    long size = 0;
                    Content content = item.getValue();
                    try (InputStream input = content.pack.read(content.entry)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            size += count;
                            total += count;
                            if (size > MAX_FILE || total > MAX_TOTAL) throw new IOException("The combined pack exceeds the supported size.");
                            output.write(buffer, 0, count);
                        }
                    }
                    output.closeEntry();
                }
            }
            Path target = directory.resolve(clean + ".zip");
            for (int copy = 2; Files.exists(target); copy++) target = directory.resolve(clean + " (" + copy + ").zip");
            Files.move(temporary, target);
            temporary = null;
            return new Result(target, contents.size() + 2);
        } finally {
            for (Pack pack : packs.values()) pack.close();
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private static void write(ZipOutputStream output, String name, String value) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        output.putNextEntry(entry);
        output.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void copyDependencies(List<Content> selected, Set<String> explicit, Map<String, Content> output) throws IOException {
        ArrayDeque<Content> queue = new ArrayDeque<>(selected);
        Set<Content> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Content content = queue.removeFirst();
            if (!visited.add(content) || !content.entry.endsWith(".json")
                    || !(content.entry.contains("/models/") || content.entry.contains("/blockstates/") || content.entry.contains("/items/"))) continue;
            byte[] bytes;
            try (InputStream input = content.pack.read(content.entry)) { bytes = input.readNBytes(2 * 1024 * 1024 + 1); }
            if (bytes.length > 2 * 1024 * 1024) throw new IOException("A model file is too large: " + content.entry);
            List<String> references = new ArrayList<>();
            try { references(new JsonParser().parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)), references, content.entry.contains("/blockstates/")); }
            catch (RuntimeException invalid) { throw new IOException("Could not read the model: " + content.entry); }
            for (String reference : references) {
                if (reference.startsWith("#")) continue;
                int colon = reference.indexOf(':');
                String namespace = colon < 0 ? "minecraft" : reference.substring(0, colon);
                String asset = colon < 0 ? reference : reference.substring(colon + 1);
                String path = "assets/" + namespace + "/" + asset;
                if (!valid(path) || !content.pack.entries.containsKey(path)) continue;
                if (explicit.contains(path)) continue;
                if (path.endsWith(".png") && !explicit.contains(path + ".mcmeta")) output.remove(path + ".mcmeta");
                for (String dependency : List.of(path, path + ".mcmeta")) {
                    if (!content.pack.entries.containsKey(dependency)) continue;
                    Content value = new Content(content.pack, content.pack.entries.get(dependency));
                    if (!explicit.contains(dependency)) output.put(dependency, value);
                    queue.addLast(value);
                }
            }
            if (visited.size() + queue.size() > MAX_ENTRIES) throw new IOException("Too many model dependencies.");
        }
    }

    private static void references(JsonElement json, List<String> output, boolean blockstate) {
        if (json == null) return;
        if (json.isJsonArray()) { for (JsonElement value : json.getAsJsonArray()) references(value, output, blockstate); }
        else if (json.isJsonObject()) for (var field : json.getAsJsonObject().entrySet()) {
            JsonElement value = field.getValue();
            if ((field.getKey().equals("parent") || field.getKey().equals("model")) && value.isJsonPrimitive()) {
                output.add(resourceReference(value.getAsString(), blockstate && field.getKey().equals("model") && !value.getAsString().substring(value.getAsString().indexOf(':') + 1).contains("/") ? "models/block/" : "models/", ".json"));
            } else if (field.getKey().equals("textures") && value.isJsonObject()) {
                for (var texture : value.getAsJsonObject().entrySet()) if (texture.getValue().isJsonPrimitive())
                    output.add(resourceReference(texture.getValue().getAsString(), "textures/", ".png"));
            } else references(value, output, blockstate);
        }
    }

    private static String resourceReference(String value, String directory, String extension) {
        if (value.startsWith("#")) return value;
        int colon = value.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : value.substring(0, colon);
        String name = colon < 0 ? value : value.substring(colon + 1);
        return namespace + ":" + directory + name + (name.endsWith(extension) ? "" : extension);
    }

    private static Pack open(Source source, Map<Path, Pack> packs) throws IOException {
        Path path = source.path.toAbsolutePath().normalize();
        Pack existing = packs.get(path);
        if (existing != null) return existing;
        Pack pack = new Pack(path);
        packs.put(path, pack);
        return pack;
    }

    private static boolean valid(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) return false;
        for (String part : name.split("/")) if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return name.equals("pack.mcmeta") || name.equals("pack.png") || name.equals("arcadium-pack.json")
                || name.startsWith("assets/") || name.startsWith("arcadium-sources/")
                || name.toLowerCase(Locale.ROOT).matches("(?:license|licence|copying|notice|credits|authors|readme)(?:[._ -].*)?");
    }

    private static final class Pack implements AutoCloseable {
        final Path root;
        final ZipFile zip;
        final Map<String, String> entries = new LinkedHashMap<>();

        Pack(Path path) throws IOException {
            root = path;
            zip = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? null : new ZipFile(path.toFile());
            try {
                if (zip == null) {
                    try (var files = Files.walk(root, 64)) {
                        var iterator = files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)).iterator();
                        while (iterator.hasNext()) {
                            Path file = iterator.next();
                            String name = root.relativize(file).toString().replace('\\', '/');
                            if (valid(name)) entries.put(name, name);
                            if (entries.size() > MAX_ENTRIES) throw new IOException("A source pack has too many files.");
                        }
                    }
                } else {
                    var iterator = zip.entries();
                    while (iterator.hasMoreElements()) {
                        ZipEntry entry = iterator.nextElement();
                        if (!entry.isDirectory() && valid(entry.getName())) entries.put(entry.getName(), entry.getName());
                        if (entries.size() > MAX_ENTRIES) throw new IOException("A source pack has too many files.");
                    }
                }
            } catch (IOException | RuntimeException error) { close(); throw error; }
        }

        InputStream read(String entry) throws IOException {
            if (zip != null) return zip.getInputStream(zip.getEntry(entry));
            Path file = root.resolve(entry).normalize();
            if (!file.startsWith(root) || !file.toRealPath().startsWith(root.toRealPath())) throw new IOException("Invalid pack file path.");
            return Files.newInputStream(file);
        }

        @Override public void close() throws IOException { if (zip != null) zip.close(); }
    }
}
