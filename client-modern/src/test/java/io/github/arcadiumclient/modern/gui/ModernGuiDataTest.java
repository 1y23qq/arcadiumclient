// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui;

import com.google.gson.JsonParser;
import io.github.arcadiumclient.modern.client.resourcepack.CustomPackMaker;
import io.github.arcadiumclient.modern.client.resourcepack.ResourcePackSearch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public final class ModernGuiDataTest {
    @TempDir Path temporary;

    @Test void detectsNewerWorldsWithoutHidingCurrentWorlds() {
        var current = new WorldMetadata("Current", 0, 0, false, false, 4671, "1.21.11", false, true);
        var newer = new WorldMetadata("Newer", 0, 0, false, false, 5000, "26.1", false, true);
        assertFalse(current.newerThan("1.21.11", 4671));
        assertFalse(current.needsWarning("1.21.11", 4671));
        assertTrue(newer.newerThan("1.21.11", 4671));
        assertTrue(newer.needsWarning("1.21.11", 4671));
    }

    @Test void searchesFormattedNamesAndNestedFolders() {
        String text = ResourcePackSearch.text("PvP/Bedwars/§bＣｌｅａｎ 16x.zip", "§dClean", "A soft pack");
        assertTrue(ResourcePackSearch.matches(ResourcePackSearch.terms("clean bedwars"), text));
        assertFalse(ResourcePackSearch.matches(ResourcePackSearch.terms("64x"), text));
    }

    @Test void combinesModernItemDefinitionsAndPreservesPackFormat() throws Exception {
        Path base = temporary.resolve("base");
        Path sword = temporary.resolve("nested/sword");
        write(base, "pack.mcmeta", "{\"pack\":{\"description\":\"Base\",\"min_format\":[75,0],\"max_format\":[75,0]}}");
        write(base, "assets/minecraft/textures/block/stone.png", "base block");
        write(sword, "pack.mcmeta", "{\"pack\":{\"description\":\"Sword\",\"min_format\":[75,0],\"max_format\":[75,0]}}");
        write(sword, "assets/minecraft/items/diamond_sword.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/glass_sword\"}}");
        write(sword, "assets/minecraft/models/item/glass_sword.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/glass_sword\"}}");
        write(sword, "assets/minecraft/textures/item/glass_sword.png", "replacement sword");
        var result = CustomPackMaker.create(temporary.resolve("output"), "Combined", new CustomPackMaker.Source("Base", base),
                Map.of(CustomPackMaker.Element.SWORDS, new CustomPackMaker.Source("Sword", sword)));
        try (var output = new ZipFile(result.path().toFile())) {
            assertNotNull(output.getEntry("assets/minecraft/items/diamond_sword.json"));
            assertNotNull(output.getEntry("assets/minecraft/models/item/glass_sword.json"));
            assertNotNull(output.getEntry("assets/minecraft/textures/item/glass_sword.png"));
            assertNotNull(output.getEntry("assets/minecraft/textures/block/stone.png"));
            try (var metadata = output.getInputStream(output.getEntry("pack.mcmeta"))) {
                var pack = JsonParser.parseString(new String(metadata.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("pack");
                assertEquals("Combined", pack.get("description").getAsString());
                assertEquals(75, pack.getAsJsonArray("min_format").get(0).getAsInt());
                assertEquals(75, pack.getAsJsonArray("max_format").get(0).getAsInt());
            }
        }
    }

    private static void write(Path root, String file, String content) throws Exception {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
