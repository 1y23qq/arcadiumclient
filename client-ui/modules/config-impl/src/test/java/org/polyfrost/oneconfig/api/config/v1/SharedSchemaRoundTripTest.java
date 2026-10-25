// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.config.v1;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.polyfrost.oneconfig.api.config.v1.backend.impl.FileBackend;
import org.polyfrost.oneconfig.api.config.v1.serialize.impl.NightConfigSerializer;
import static org.junit.jupiter.api.Assertions.*;

class SharedSchemaRoundTripTest {
    @Test
    void savingBothVersionSchemasPreservesUnknownOptionsAndHudPlacement(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("shared.json"), """
            {"enabled":true,"legacyOnly":"guild-tab","tabs":{"party":"/pc"},"hud":{"x":140.5,"y":20,"anchor":"TOP_RIGHT"}}
            """);
        Tree modern = Tree.tree("shared.json");
        modern.put(Properties.simple("enabled", null, null, false, Boolean.class));
        modern.put(Properties.simple("modernOnly", null, null, true, Boolean.class));
        modern.getOrPutChild("hud").put(Properties.simple("x", null, null, 0f, Float.class));
        FileBackend modernBackend = new FileBackend(directory, NightConfigSerializer.JSON);
        modern = modernBackend.register(modern).get();
        assertTrue((Boolean) modern.getProp("enabled").get());
        assertEquals(140.5f, ((Number) modern.getChild("hud").getProp("x").get()).floatValue());
        modern.getChild("hud").getProp("x").setAs(64.25f);
        modern.getProp("enabled").setAs(false);
        assertTrue(modernBackend.save(modern));

        Tree legacy = Tree.tree("shared.json");
        legacy.put(Properties.simple("enabled", null, null, true, Boolean.class));
        legacy.put(Properties.simple("legacyOnly", null, null, "default", String.class));
        FileBackend legacyBackend = new FileBackend(directory, NightConfigSerializer.JSON);
        legacy = legacyBackend.register(legacy).get();
        assertFalse((Boolean) legacy.getProp("enabled").get());
        assertEquals("guild-tab", legacy.getProp("legacyOnly").get());
        assertEquals("/pc", legacy.getChild("tabs").getProp("party").get());
        assertTrue((Boolean) legacy.getProp("modernOnly").get());
        assertEquals(64.25f, ((Number) legacy.getChild("hud").getProp("x").get()).floatValue());
        assertEquals("TOP_RIGHT", legacy.getChild("hud").getProp("anchor").get());
        assertTrue(legacyBackend.save(legacy));

        Tree restored = new FileBackend(directory, NightConfigSerializer.JSON).load("shared.json");
        assertNotNull(restored);
        assertTrue((Boolean) restored.getProp("modernOnly").get());
        assertEquals("guild-tab", restored.getProp("legacyOnly").get());
        assertEquals("/pc", restored.getChild("tabs").getProp("party").get());
        assertEquals(64.25f, ((Number) restored.getChild("hud").getProp("x").get()).floatValue());
        assertEquals(20, ((Number) restored.getChild("hud").getProp("y").get()).intValue());
        assertEquals("TOP_RIGHT", restored.getChild("hud").getProp("anchor").get());
    }
}
