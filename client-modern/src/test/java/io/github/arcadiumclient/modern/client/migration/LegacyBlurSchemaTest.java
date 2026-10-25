// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import com.google.gson.JsonParser;
import eu.midnightdust.blur.BlurConfig.Easing;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.backend.impl.FileBackend;
import org.polyfrost.oneconfig.api.config.v1.serialize.impl.NightConfigSerializer;
import static org.junit.jupiter.api.Assertions.*;

class LegacyBlurSchemaTest {
    @Test void realLegacyEnumClassLoadsAndSavesWithItsCrossVersionIdentity(@TempDir Path folder) throws Exception {
        var path = folder.resolve("blur.json");
        Files.writeString(path, """
            {"blurAnimationCurve":{"class":"eu.midnightdust.blur.BlurConfig$Easing","value":"SINE"},"backgroundAnimationCurve":{"class":"eu.midnightdust.blur.BlurConfig$Easing","value":"ELASTIC"},"radius":81,"future":{"retained":true}}
            """);
        var tree = Tree.tree("blur.json");
        tree.put(Properties.simple("blurAnimationCurve", null, null, Easing.FLAT, Easing.class));
        tree.put(Properties.simple("backgroundAnimationCurve", null, null, Easing.FLAT, Easing.class));
        tree.put(Properties.simple("radius", null, null, 8, Integer.class));
        var backend = new FileBackend(folder, NightConfigSerializer.JSON);
        tree = backend.register(tree).get();
        assertEquals(Easing.SINE, tree.getProp("blurAnimationCurve").get());
        assertEquals(Easing.ELASTIC, tree.getProp("backgroundAnimationCurve").get());
        assertEquals(81, tree.getProp("radius").get());
        assertTrue(backend.save(tree));
        var saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertEquals("eu.midnightdust.blur.BlurConfig$Easing", saved.getAsJsonObject("blurAnimationCurve").get("class").getAsString());
        assertTrue(saved.getAsJsonObject("future").get("retained").getAsBoolean());
    }
    @Test void onlyCoreObjectWrappersBypassTheNativeMidnightParser() {
        assertTrue(LegacyNativePolicy.coreBlurSchema(JsonParser.parseString("{\"gradientStartColor\":{\"class\":\"org.polyfrost.compose.render.PolyColor\",\"value\":[0,0,0,75]}}").getAsJsonObject()));
        assertTrue(LegacyNativePolicy.coreBlurSchema(JsonParser.parseString("{\"blurAnimationCurve\":{\"class\":\"eu.midnightdust.blur.BlurConfig$Easing\",\"value\":\"FLAT\"}}").getAsJsonObject()));
        assertFalse(LegacyNativePolicy.coreBlurSchema(JsonParser.parseString("{\"blurAnimationCurve\":\"FLAT\",\"gradientStart\":\"#334455\",\"gradientStartAlpha\":75}").getAsJsonObject()));
    }
}
