// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.modcompat;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinPackMetadataTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void repairsOnlyTheTwoKnownBuiltinDeclarationsWithoutMutatingOriginal() {
        for (String id : new String[]{"betterstats", "tcdcommons"}) {
            var original = metadata(75);
            var repaired = BuiltinPackMetadata.repair(id, true, original);
            assertNotSame(original, repaired);
            assertFalse(original.getAsJsonObject("pack").has("min_format"));
            assertEquals(75, repaired.getAsJsonObject("pack").get("min_format").getAsInt());
            assertEquals(75, repaired.getAsJsonObject("pack").get("max_format").getAsInt());
            assertEquals(original.getAsJsonObject("pack").get("description"), repaired.getAsJsonObject("pack").get("description"));
        }
    }
    @Test void userPacksUnknownBuiltinsAndOtherVersionsKeepNativeValidation() {
        var original = metadata(75);
        assertSame(original, BuiltinPackMetadata.repair("betterstats", false, original));
        assertSame(original, BuiltinPackMetadata.repair("file/betterstats.zip", true, original));
        assertSame(original, BuiltinPackMetadata.repair("unrelated-mod", true, original));
        var newer = metadata(76);
        assertSame(newer, BuiltinPackMetadata.repair("betterstats", true, newer));
        var partial = metadata(75);
        partial.getAsJsonObject("pack").addProperty("min_format", 75);
        assertSame(partial, BuiltinPackMetadata.repair("tcdcommons", true, partial));
    }
    @Test void repairedBuiltinPassesActualMinecraftCodecButUnchangedUserPackFails() {
        var original = metadata(75);
        var repaired = BuiltinPackMetadata.repair("betterstats", true, original);
        assertTrue(PackMetadataSection.CLIENT_TYPE.codec().parse(JsonOps.INSTANCE, repaired.get("pack")).isSuccess());
        assertFalse(PackMetadataSection.CLIENT_TYPE.codec().parse(JsonOps.INSTANCE, original.get("pack")).isSuccess());
    }
    private static JsonObject metadata(int version) {
        return JsonParser.parseString("{\"pack\":{\"description\":{\"text\":\"Builtin resources\"},\"pack_format\":" + version + "}}").getAsJsonObject();
    }
}
