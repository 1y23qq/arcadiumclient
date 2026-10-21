// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.Loader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PortLoader {
    static PortLoader getInstance() { return Instance.VALUE; }
    static void markForgeReady() { Instance.nativeReady = true; }
    Collection<PortModContainer> getAllMods();
    Optional<PortModContainer> getModContainer(String id);
    boolean isModLoaded(String id);
    boolean isDevelopmentEnvironment();
    Path getGameDir();
    Path getConfigDir();
    String getRawGameVersion();
    <T> List<PortEntrypoint<T>> getEntrypointContainers(String key, Class<T> type);

    final class Instance implements PortLoader {
        private static final Map<Path, Path> ROOTS = new java.util.concurrent.ConcurrentHashMap<>();
        private static final Instance VALUE = new Instance();
        private static boolean nativeReady;
        private final Map<String, Container> components = new LinkedHashMap<>();
        private final Path root;

        private Instance() {
            try {
                Path source = Path.of(PortLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                root = resourceRoot(source);
                try (var input = PortLoader.class.getResourceAsStream("/arcadium-forge-components.json")) {
                    if (input == null) throw new IllegalStateException("Arcadium component metadata is missing.");
                    for (JsonElement element : new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray()) {
                        JsonObject metadata = element.getAsJsonObject();
                        if (!metadata.has("id")) continue;
                        Container container = new Container(new Metadata(metadata), metadata, root);
                        components.put(container.getMetadata().getId(), container);
                        if (metadata.has("provides")) for (JsonElement alias : metadata.getAsJsonArray("provides"))
                            components.put(alias.getAsString(), container);
                    }
                }
                JsonObject minecraft = new JsonObject();
                minecraft.addProperty("id", "minecraft"); minecraft.addProperty("name", "Minecraft"); minecraft.addProperty("version", "1.7.10");
                components.put("minecraft", new Container(new Metadata(minecraft), minecraft, root));
                JsonObject forge = new JsonObject();
                forge.addProperty("id", "Forge"); forge.addProperty("name", "Minecraft Forge"); forge.addProperty("version", "10.13.4.1614");
                var forgeResource = PortLoader.class.getClassLoader().getResource("cpw/mods/fml/common/Loader.class");
                if (forgeResource == null) throw new IllegalStateException("Forge is missing from the launch classpath.");
                Path forgeSource = Path.of(((java.net.JarURLConnection) forgeResource.openConnection()).getJarFileURL().toURI());
                components.put("Forge", new Container(new Metadata(forge), forge, resourceRoot(forgeSource)));
            } catch (Exception error) { throw new ExceptionInInitializerError(error); }
        }

        @Override public Collection<PortModContainer> getAllMods() {
            Map<String, PortModContainer> result = new LinkedHashMap<>();
            for (Container component : components.values()) result.put(component.getMetadata().getId(), component);
            if (nativeReady) for (var mod : Loader.instance().getIndexedModList().values()) {
                if (result.containsKey(mod.getModId())) continue;
                JsonObject data = new JsonObject();
                data.addProperty("id", mod.getModId()); data.addProperty("name", mod.getName()); data.addProperty("version", mod.getVersion());
                Path source = mod.getSource() != null ? mod.getSource().toPath() : null;
                Path resources = source != null && java.nio.file.Files.exists(source) ? resourceRoot(source) : root;
                result.put(mod.getModId(), new Container(new Metadata(data), data, resources));
            }
            return List.copyOf(result.values());
        }
        @Override public Optional<PortModContainer> getModContainer(String id) {
            Container own = components.get(id);
            if (own != null) return Optional.of(own);
            return getAllMods().stream().filter(mod -> mod.getMetadata().getId().equals(id)).findFirst();
        }
        @Override public boolean isModLoaded(String id) {
            return components.containsKey(id) || nativeReady && Loader.isModLoaded(id);
        }
        @Override public boolean isDevelopmentEnvironment() { return false; }
        @Override public Path getGameDir() { return Path.of(System.getProperty("arcadium.userGameDir", ".")).toAbsolutePath().normalize(); }
        @Override public Path getConfigDir() { return getGameDir().resolve("config"); }
        @Override public String getRawGameVersion() { return "1.7.10"; }
        @Override public <T> List<PortEntrypoint<T>> getEntrypointContainers(String key, Class<T> type) {
            List<PortEntrypoint<T>> result = new ArrayList<>();
            for (Container container : components.values().stream().distinct().toList()) {
                JsonObject entries = container.json().getAsJsonObject("entrypoints");
                if (entries == null || !entries.has(key)) continue;
                for (JsonElement definition : entries.getAsJsonArray(key)) {
                    String name = definition.isJsonPrimitive() ? definition.getAsString() : definition.getAsJsonObject().get("value").getAsString();
                    result.add(new PortEntrypoint<>() {
                        @Override public T getEntrypoint() {
                            try {
                                Class<?> implementation = Class.forName(name);
                                try { return type.cast(implementation.getField("INSTANCE").get(null)); }
                                catch (NoSuchFieldException ignored) { return type.cast(implementation.getDeclaredConstructor().newInstance()); }
                            }
                            catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot initialize " + name, error); }
                        }
                        @Override public PortModContainer getProvider() { return container; }
                    });
                }
            }
            return List.copyOf(result);
        }

        private static Path resourceRoot(Path source) {
            Path path = source.toAbsolutePath().normalize();
            return ROOTS.computeIfAbsent(path, key -> {
                if (java.nio.file.Files.isDirectory(key)) return key;
                try { return FileSystems.newFileSystem(key).getPath("/"); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            });
        }

        private record Container(PortMetadata getMetadata, JsonObject json, Path root) implements PortModContainer {
            @Override public List<Path> getRootPaths() { return List.of(root); }
        }

        private record Metadata(JsonObject json) implements PortMetadata {
            private String text(String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
            @Override public String getId() { return text("id", "unknown"); }
            @Override public String getName() { return text("name", getId()); }
            @Override public String getDescription() { return text("description", ""); }
            @Override public String getType() { return getId().equals("minecraft") || getId().equals("Forge") ? "builtin" : "mod"; }
            @Override public PortVersion getVersion() { return PortVersion.parse(text("version", "0")); }
            private Collection<PortPerson> people(String key) {
                List<PortPerson> result = new ArrayList<>();
                JsonArray values = json.getAsJsonArray(key);
                if (values != null) for (JsonElement value : values) {
                    String name = value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("name").getAsString();
                    result.add(() -> name);
                }
                return result;
            }
            @Override public Collection<PortPerson> getAuthors() { return people("authors"); }
            @Override public Collection<PortPerson> getContributors() { return people("contributors"); }
            @Override public Optional<String> getIconPath(int size) {
                JsonElement icon = json.get("icon");
                return icon != null && icon.isJsonPrimitive() ? Optional.of(icon.getAsString()) : Optional.empty();
            }
        }
    }
}
