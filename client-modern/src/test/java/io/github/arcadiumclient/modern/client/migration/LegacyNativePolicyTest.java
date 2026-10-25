// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.backend.impl.FileBackend;
import org.polyfrost.oneconfig.api.config.v1.serialize.impl.NightConfigSerializer;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LegacyNativePolicyTest {
    @Test void disabledBlurAndNativeLimitDoNotRewriteTheStoredLegacyRadius() {
        int stored = 81;
        assertEquals(20, LegacyNativePolicy.blurRadius(true, stored));
        assertEquals(0, LegacyNativePolicy.blurRadius(false, stored));
        assertEquals(81, stored);
        assertEquals(8, LegacyNativePolicy.blurRadius(true, 8));
    }
    @Test void nativeHexAndSeparateAlphaPreserveEveryArgbChannel() {
        int color = 0x7F3AA0FF;
        assertEquals("#3AA0FF", LegacyNativePolicy.rgb(color));
        assertEquals(color, LegacyNativePolicy.argb(LegacyNativePolicy.rgb(color), color >>> 24));
        assertEquals(0x003AA0FF, LegacyNativePolicy.argb("#3AA0FF", 0));
    }
    @Test void disablingBounceKeepsItsConfiguredStrengthAvailableForReenable() {
        double strength = 1.65;
        assertEquals(0, LegacyNativePolicy.scrollingStrength(false, strength));
        assertEquals(1.65, LegacyNativePolicy.scrollingStrength(true, strength));
        assertEquals(1.65, strength);
    }
    @Test void legacyConfigSavesKeepModernUnknownFieldsAndChromaColorPayload(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("blur.json"), """
            {"enabled":false,"radius":81,"gradientStartColor":{"rgba":[58,160,255,127],"chroma":true,"chromaSpeed":2.5},"nativeOnly":{"retained":true},"gradientStart":"#3AA0FF","gradientStartAlpha":127}
            """);
        Tree modern = Tree.tree("blur.json");
        modern.put(Properties.simple("enabled", null, null, true, Boolean.class));
        modern.put(Properties.simple("radius", null, null, 8, Integer.class));
        var backend = new FileBackend(directory, NightConfigSerializer.JSON);
        modern = backend.register(modern).get();
        assertFalse((Boolean) modern.getProp("enabled").get());
        assertEquals(81, modern.getProp("radius").get());
        modern.getProp("enabled").setAs(true);
        assertTrue(backend.save(modern));
        var restored = JsonParser.parseString(Files.readString(directory.resolve("blur.json"))).getAsJsonObject();
        assertTrue(restored.getAsJsonObject("nativeOnly").get("retained").getAsBoolean());
        assertEquals(127, restored.getAsJsonObject("gradientStartColor").getAsJsonArray("rgba").get(3).getAsInt());
        assertTrue(restored.getAsJsonObject("gradientStartColor").get("chroma").getAsBoolean());
        assertEquals(2.5, restored.getAsJsonObject("gradientStartColor").get("chromaSpeed").getAsDouble());
    }
}
