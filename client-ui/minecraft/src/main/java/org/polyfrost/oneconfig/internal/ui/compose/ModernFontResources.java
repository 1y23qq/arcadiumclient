// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal.ui.compose;

//? if >= 1.21.11 && < 26.3 {
/*import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class ModernFontResources implements ResourceManager {
    public static final String VANILLA_NAMESPACE = "arcadium_vanilla";
    public static final String BUILTIN_NAMESPACE = "arcadium_builtin";
    private final ResourceManager parent;
    private final VanillaPackResources vanilla;
    private final Map<Identifier, Resource> fonts = new LinkedHashMap<>();

    public ModernFontResources(ResourceManager parent) {
        this.parent = parent;
        this.vanilla = Minecraft.getInstance().getVanillaPackResources();
        vanilla.listResources(PackType.CLIENT_RESOURCES, "minecraft", "font", (id, supplier) -> {
            if (!id.getPath().endsWith(".json")) return;
            Identifier alias = Identifier.fromNamespaceAndPath(VANILLA_NAMESPACE, id.getPath());
            fonts.put(alias, new Resource(vanilla, () -> vanillaFont(supplier)));
        });
        fonts.put(builtin("font/inter.json"), jsonResource(interDefinition()));
        fonts.put(builtin("font/emoji.json"), jsonResource(emojiDefinition()));
        JsonObject prefix = new JsonObject();
        JsonArray providers = new JsonArray();
        JsonObject reference = new JsonObject();
        reference.addProperty("type", "reference");
        reference.addProperty("id", "minecraft:default");
        providers.add(reference);
        prefix.add("providers", providers);
        fonts.put(builtin("font/prefix.json"), jsonResource(prefix));
        fonts.put(builtin("font/mod.json"), jsonResource(prefix));
    }

    private static Identifier builtin(String path) { return Identifier.fromNamespaceAndPath(BUILTIN_NAMESPACE, path); }

    private Resource jsonResource(JsonObject json) {
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        return new Resource(vanilla, () -> new ByteArrayInputStream(bytes));
    }

    private static InputStream vanillaFont(IoSupplier<InputStream> supplier) throws IOException {
        JsonObject json;
        try (InputStream stream = supplier.get()) {
            json = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonArray providers = json.getAsJsonArray("providers");
        if (providers != null) providers.forEach(element -> {
            JsonObject provider = element.getAsJsonObject();
            for (String key : List.of("id", "file", "hex_file")) {
                if (!provider.has(key)) continue;
                String id = provider.get(key).getAsString();
                if (!id.contains(":")) id = "minecraft:" + id;
                if (id.startsWith("minecraft:")) provider.addProperty(key, VANILLA_NAMESPACE + id.substring("minecraft".length()));
            }
        });
        return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject interDefinition() {
        JsonObject root = new JsonObject();
        JsonArray providers = new JsonArray();
        JsonObject inter = new JsonObject();
        inter.addProperty("type", "ttf");
        inter.addProperty("file", BUILTIN_NAMESPACE + ":inter.ttf");
        inter.addProperty("size", 9);
        inter.addProperty("oversample", 2);
        JsonArray shift = new JsonArray(); shift.add(0); shift.add(0);
        inter.add("shift", shift);
        providers.add(inter);
        JsonObject fallback = new JsonObject();
        fallback.addProperty("type", "reference");
        fallback.addProperty("id", VANILLA_NAMESPACE + ":default");
        providers.add(fallback);
        root.add("providers", providers);
        return root;
    }

    private static JsonObject emojiDefinition() {
        JsonObject root = new JsonObject();
        JsonArray providers = new JsonArray();
        JsonObject atlas = new JsonObject();
        atlas.addProperty("type", "bitmap");
        atlas.addProperty("file", BUILTIN_NAMESPACE + ":emoji.png");
        atlas.addProperty("ascent", 7);
        atlas.addProperty("height", 8);
        JsonArray rows = new JsonArray();
        List<EmojiText.Entry> entries = EmojiText.entries();
        for (int row = 0; row < 8; row++) {
            StringBuilder chars = new StringBuilder(8);
            for (int column = 0; column < 8; column++) chars.append(entries.get(row * 8 + column).glyph());
            rows.add(chars.toString());
        }
        atlas.add("chars", rows);
        providers.add(atlas);
        root.add("providers", providers);
        return root;
    }

    @Override public Optional<Resource> getResource(Identifier id) {
        Resource font = fonts.get(id);
        if (font != null) return Optional.of(font);
        if (id.getNamespace().equals(VANILLA_NAMESPACE)) {
            IoSupplier<InputStream> supplier = vanilla.getResource(PackType.CLIENT_RESOURCES,
                Identifier.fromNamespaceAndPath("minecraft", id.getPath()));
            return supplier == null ? Optional.empty() : Optional.of(new Resource(vanilla, supplier));
        }
        if (id.getNamespace().equals(BUILTIN_NAMESPACE)) {
            String path = switch (id.getPath()) {
                case "font/inter.ttf" -> "/assets/oneconfig/fonts/Inter-Regular.ttf";
                case "textures/emoji.png" -> EmojiText.ATLAS;
                default -> null;
            };
            if (path != null) return Optional.of(new Resource(vanilla, () -> {
                InputStream stream = ModernFontResources.class.getResourceAsStream(path);
                if (stream == null) throw new IOException("Missing font resource " + path);
                return stream;
            }));
        }
        return parent.getResource(id);
    }

    @Override public List<Resource> getResourceStack(Identifier id) {
        if (id.getNamespace().equals(VANILLA_NAMESPACE) || id.getNamespace().equals(BUILTIN_NAMESPACE)) {
            return getResource(id).map(List::of).orElseGet(List::of);
        }
        return parent.getResourceStack(id);
    }

    @Override public Set<String> getNamespaces() {
        Set<String> namespaces = new HashSet<>(parent.getNamespaces());
        namespaces.add(VANILLA_NAMESPACE); namespaces.add(BUILTIN_NAMESPACE);
        return namespaces;
    }

    @Override public Map<Identifier, Resource> listResources(String prefix, Predicate<Identifier> filter) {
        Map<Identifier, Resource> resources = new HashMap<>(parent.listResources(prefix, filter));
        fonts.forEach((id, resource) -> { if (id.getPath().startsWith(prefix + "/") && filter.test(id)) resources.put(id, resource); });
        return resources;
    }

    @Override public Map<Identifier, List<Resource>> listResourceStacks(String prefix, Predicate<Identifier> filter) {
        Map<Identifier, List<Resource>> resources = new HashMap<>(parent.listResourceStacks(prefix, filter));
        fonts.forEach((id, resource) -> { if (id.getPath().startsWith(prefix + "/") && filter.test(id)) resources.put(id, List.of(resource)); });
        return resources;
    }

    @Override public Stream<PackResources> listPacks() { return parent.listPacks(); }
}
*///?}
