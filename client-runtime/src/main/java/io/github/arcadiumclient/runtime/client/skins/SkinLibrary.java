// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public final class SkinLibrary {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final class Entry {
        public String id;
        public String name;
        public String variant;
        public int height;

        public Entry(String id, String name, String variant, int height) {
            this.id = id; this.name = name; this.variant = variant; this.height = height;
        }

        public Entry copy() { return new Entry(id, name, variant, height); }
    }

    public record Image(byte[] png, int height) {}
    private static final class LocalSelection {
        String id;
        String variant;

        LocalSelection(String id, String variant) { this.id = id; this.variant = variant; }
    }
    private static final class Index {
        List<Entry> skins = new ArrayList<>();
        Map<String, LocalSelection> localSelections = new HashMap<>();
    }
    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Index index;

    public SkinLibrary(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public synchronized List<Entry> list() throws IOException {
        load();
        return index.skins.stream().map(Entry::copy).toList();
    }

    public synchronized Entry add(Path source, String name, String variant) throws IOException {
        load();
        if (index.skins.size() >= 128) throw new IOException("Keep fewer than 128 saved skins.");
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_BYTES) throw new IOException("Choose a PNG smaller than 1 MB.");
        Image image = canonicalize(Files.readAllBytes(source));
        Entry entry = new Entry(UUID.randomUUID().toString(), name(name), image.height == 32 ? "classic" : variant(variant), image.height);
        Path target = imagePath(entry.id);
        atomicWrite(target, image.png);
        index.skins.add(entry);
        try { save(); }
        catch (IOException failure) { index.skins.remove(entry); Files.deleteIfExists(target); throw failure; }
        return entry.copy();
    }

    public synchronized Entry edit(String id, String name, String variant) throws IOException {
        load();
        Entry entry = find(id), previous = entry.copy();
        entry.name = name(name);
        entry.variant = entry.height == 32 ? "classic" : variant(variant);
        try { save(); }
        catch (IOException failure) { entry.name = previous.name; entry.variant = previous.variant; throw failure; }
        return entry.copy();
    }

    public synchronized void delete(String id) throws IOException {
        load();
        Entry entry = find(id);
        Path target = imagePath(entry.id);
        Map<String, LocalSelection> previousSelections = new HashMap<>(index.localSelections);
        index.skins.remove(entry);
        index.localSelections.entrySet().removeIf(value -> value.getValue().id.equals(id));
        try { save(); }
        catch (IOException failure) { index.skins.add(entry); index.localSelections = previousSelections; throw failure; }
        Files.deleteIfExists(target);
    }

    public synchronized byte[] png(String id) throws IOException {
        load();
        Path file = imagePath(find(id).id);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_BYTES)
            throw new IOException("The saved skin image is missing or invalid. Import it again.");
        return canonicalize(Files.readAllBytes(file)).png;
    }

    public synchronized Entry localSelection(UUID account) throws IOException {
        load();
        LocalSelection selection = index.localSelections.get(account.toString());
        if (selection == null) return null;
        Entry entry = find(selection.id).copy();
        entry.variant = entry.height == 32 ? "classic" : variant(selection.variant);
        return entry;
    }

    public synchronized void selectLocal(UUID account, String id) throws IOException {
        load();
        selectLocal(account, id, id == null ? null : find(id).variant);
    }

    public synchronized void selectLocal(UUID account, String id, String model) throws IOException {
        load();
        Entry entry = id == null ? null : find(id);
        String key = account.toString();
        LocalSelection previous = index.localSelections.get(key);
        if (id == null) index.localSelections.remove(key);
        else index.localSelections.put(key, new LocalSelection(id, entry.height == 32 ? "classic" : variant(model)));
        try { save(); }
        catch (IOException failure) {
            if (previous == null) index.localSelections.remove(key); else index.localSelections.put(key, previous);
            throw failure;
        }
    }

    private Entry find(String id) throws IOException {
        return index.skins.stream().filter(entry -> entry.id.equals(id)).findFirst()
                .orElseThrow(() -> new IOException("The saved skin could not be found."));
    }

    private void load() throws IOException {
        if (index != null) return;
        Files.createDirectories(root);
        Path file = root.resolve("index.json");
        if (!Files.exists(file)) { index = new Index(); return; }
        if (Files.isSymbolicLink(file)) throw new IOException("The saved skin index path is invalid.");
        if (Files.size(file) > MAX_BYTES) throw new IOException("The saved skin index is too large.");
        try (Reader reader = Files.newBufferedReader(file)) {
            Index loaded = gson.fromJson(reader, Index.class);
            if (loaded == null || loaded.skins == null || loaded.skins.size() > 128) throw new IllegalArgumentException();
            ArrayList<Entry> clean = new ArrayList<>();
            for (Entry entry : loaded.skins) {
                if (entry == null || entry.id == null || clean.stream().anyMatch(value -> value.id.equals(entry.id))) continue;
                imagePath(entry.id);
                entry.name = name(entry.name);
                entry.height = entry.height == 32 ? 32 : 64;
                entry.variant = entry.height == 32 ? "classic" : variant(entry.variant);
                clean.add(entry);
            }
            loaded.skins = clean;
            if (loaded.localSelections == null) loaded.localSelections = new HashMap<>();
            loaded.localSelections.entrySet().removeIf(entry -> {
                try { UUID.fromString(entry.getKey()); }
                catch (RuntimeException invalid) { return true; }
                LocalSelection selection = entry.getValue();
                return selection == null || clean.stream().noneMatch(skin -> skin.id.equals(selection.id));
            });
            index = loaded;
        } catch (RuntimeException invalid) { throw new IOException("The saved skin index is invalid. Your PNG files have been kept."); }
    }

    private void save() throws IOException { atomicWrite(root.resolve("index.json"), gson.toJson(index).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    private Path imagePath(String id) throws IOException {
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException();
            Path path = root.resolve(id + ".png").normalize();
            if (!path.getParent().equals(root) || Files.isSymbolicLink(path)) throw new IllegalArgumentException();
            return path;
        } catch (RuntimeException invalid) { throw new IOException("The saved skin path is invalid."); }
    }

    private static String name(String name) {
        String value = name == null ? "Skin" : name.strip().replaceAll("[\\p{Cntrl}]", "");
        return value.isBlank() ? "Skin" : value.substring(0, Math.min(40, value.length()));
    }

    private static String variant(String variant) { return "slim".equals(variant) ? "slim" : "classic"; }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(temp, bytes, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public static Image canonicalize(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES || bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G')
            throw new IOException("Choose a Minecraft skin PNG smaller than 1 MB.");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("The PNG could not be read.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width != 64 || (height != 32 && height != 64)) throw new IOException("Use a 64 by 64 or 64 by 32 Minecraft skin PNG.");
                BufferedImage source = reader.read(0);
                BufferedImage image = new BufferedImage(64, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                try { graphics.drawImage(source, 0, 0, null); } finally { graphics.dispose(); }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "PNG", output)) throw new IOException("The PNG could not be saved.");
                return new Image(output.toByteArray(), height);
            } finally { reader.dispose(); }
        } catch (RuntimeException invalid) { throw new IOException("The PNG could not be read."); }
    }
}
