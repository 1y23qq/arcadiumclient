// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.resourcepack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomPackMakerRegressionTest {
    @TempDir Path root;

    private CustomPackMaker.Source folder(String name, Map<String, String> files) throws Exception {
        Path directory = root.resolve(name);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":1,\"description\":\"Test pack\"}}");
        for (var entry : files.entrySet()) {
            Path target = directory.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue());
        }
        return new CustomPackMaker.Source(name, directory);
    }

    private static String text(ZipFile output, String path) throws Exception {
        var entry = output.getEntry(path);
        assertNotNull(entry, path);
        try (var input = output.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void replacingAnAnimatedDependencyWithAStaticTextureDropsItsOldMetadata() throws Exception {
        String texture = "assets/custom/textures/weapons/shared.png";
        var base = folder("base", Map.of(texture, "animated base", texture + ".mcmeta", "old animation"));
        var swords = folder("swords", Map.of(
                "assets/minecraft/models/item/diamond_sword.json",
                "{\"textures\":{\"layer0\":\"custom:weapons/shared\"}}",
                texture, "static blade"));
        var result = CustomPackMaker.create(root.resolve("out"), "Static dependency", base,
                Map.of(CustomPackMaker.Element.SWORDS, swords));
        try (var output = new ZipFile(result.path().toFile())) {
            assertEquals("static blade", text(output, texture));
            assertNull(output.getEntry(texture + ".mcmeta"),
                    "A static replacement must not inherit the base texture's animation metadata");
        }
    }

    @Test
    void rejectedDependencyCannotAttachItsAnimationMetadataToAnExplicitTexture() throws Exception {
        String texture = "assets/minecraft/textures/blocks/shared.png";
        var base = folder("base", Map.of());
        var swords = folder("swords", Map.of(
                "assets/minecraft/models/item/diamond_sword.json",
                "{\"textures\":{\"layer0\":\"blocks/shared\"}}",
                texture, "animated sword texture", texture + ".mcmeta", "sword animation"));
        var blocks = folder("blocks", Map.of(texture, "explicit static block"));
        var result = CustomPackMaker.create(root.resolve("out"), "Explicit texture", base,
                Map.of(CustomPackMaker.Element.SWORDS, swords, CustomPackMaker.Element.BLOCKS, blocks));
        try (var output = new ZipFile(result.path().toFile())) {
            assertEquals("explicit static block", text(output, texture));
            assertNull(output.getEntry(texture + ".mcmeta"),
                    "An explicit category choice must keep its own texture and metadata together");
        }
    }

    @Test
    void retainedBaseItemKeepsItsParentWhenAnotherPackReplacesBlockModels() throws Exception {
        String model = "assets/minecraft/models/item/diamond_sword.json";
        String parent = "assets/minecraft/models/block/custom_sword_parent.json";
        String texture = "assets/custom/textures/weapons/retained.png";
        var base = folder("base", Map.of(
                model, "{\"parent\":\"block/custom_sword_parent\"}",
                parent, "{\"textures\":{\"layer0\":\"custom:weapons/retained\"}}",
                texture, "base blade"));
        var blocks = folder("blocks", Map.of("assets/minecraft/textures/blocks/stone.png", "chosen stone"));
        var result = CustomPackMaker.create(root.resolve("out"), "Retained base item", base,
                Map.of(CustomPackMaker.Element.BLOCKS, blocks));
        try (var output = new ZipFile(result.path().toFile())) {
            assertNotNull(output.getEntry(model));
            assertNotNull(output.getEntry(parent), "The retained base sword still needs its custom parent");
            assertEquals("base blade", text(output, texture));
            assertEquals("chosen stone", text(output, "assets/minecraft/textures/blocks/stone.png"));
        }
    }
}
