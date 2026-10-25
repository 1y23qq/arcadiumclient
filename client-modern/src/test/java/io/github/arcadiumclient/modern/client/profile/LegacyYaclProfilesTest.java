// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

public class LegacyYaclProfilesTest {
    @TempDir Path folder;
    public static class Settings {
        @SerialEntry public boolean disableNightVision = false;
        @SerialEntry public boolean cleanerNightVision = true;
        @SerialEntry public float containerBackgroundOpacity = 81.5f;
        @SerialEntry public Color containerLabelColor = new Color(0xFF404040, true);
        @SerialEntry public Color lightningColor = new Color(0xFF112233, true);
        @SerialEntry public float lightningAlpha = 102;
        @SerialEntry public boolean lightningChroma = false;
        @SerialEntry public float lightningSaturation = 1, lightningBrightness = 1, lightningSpeed = .25f;
        @SerialEntry public List<Item> uchItemList = List.of(Items.APPLE);
    }
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private ConfigClassHandler<Settings> handler() {
        return ConfigClassHandler.createBuilder(Settings.class)
            .serializer(owner -> GsonConfigSerializerBuilder.create(owner).setPath(folder.resolve("native.json")).build()).build();
    }
    private Gson gson(ConfigClassHandler<?> handler) throws Exception {
        var field = handler.serializer().getClass().getDeclaredField("gson"); field.setAccessible(true); return (Gson) field.get(handler.serializer());
    }
    private LegacyYaclSchema.Spec spec(String mod) { return LegacyYaclSchema.SPECS.stream().filter(value -> value.modId().equals(mod)).findFirst().orElseThrow(); }
    private JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private JsonObject fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/legacy-settings/profile-" + name + ".json")) { assertNotNull(input); return JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject(); }
    }

    @Test void realNativeGsonDecodesLegacyColorAndSplitAnimationFields() throws Exception {
        var handler = handler(); Gson gson = gson(handler);
        var raw = fixture("rendertweaksv2");
        var converted = LegacyYaclSchema.toNative(spec("rendertweaks"), raw, new JsonObject(), new JsonObject(), NativeYaclFields.capture(handler, gson, true));
        NativeYaclFields.apply(NativeYaclFields.decode(NativeYaclFields.live(handler), gson, converted));
        assertEquals(0x4C72727F, handler.instance().lightningColor.getRGB());
        assertEquals(76, handler.instance().lightningAlpha);
        assertEquals(.072f, handler.instance().lightningSpeed, .00001);
        assertEquals(0x4C72727F, NativeYaclFields.capture(handler, gson, false).get("lightningColor").getAsInt());
        assertTrue(raw.get("lightningColor").isJsonObject());
    }

    @Test void namedRawFilesStayIsolatedAndKeepNestedUnknownValues() throws Exception {
        var handler = handler(); Gson gson = gson(handler);
        var spec = spec("betterscreens");
        Path root = folder.resolve("config"), named = folder.resolve("profiles/Low");
        ModernSettingsMigration.write(root.resolve(spec.legacyFile()), fixture("betterscreens"));
        byte[] before = Files.readAllBytes(root.resolve(spec.legacyFile()));
        var raw = json("{\"containerBackgroundOpacity\":17,\"containerLabelColor\":{\"class\":\"org.polyfrost.compose.render.PolyColor\",\"value\":{\"rgba\":[34,160,255,192],\"chroma\":false,\"custom\":\"retain\"}},\"unknown\":42}");
        ModernSettingsMigration.write(named.resolve(spec.legacyFile()), raw);
        var plan = LegacyYaclStore.load(spec, named, "betterscreens.json", NativeYaclFields.capture(handler, gson, true));
        NativeYaclFields.apply(NativeYaclFields.decode(NativeYaclFields.live(handler), gson, plan.desired())); plan.commit();
        assertEquals(17, handler.instance().containerBackgroundOpacity);
        assertEquals(0xC022A0FF, handler.instance().containerLabelColor.getRGB());
        handler.instance().containerBackgroundOpacity = 64;
        LegacyYaclStore.save(spec, named, NativeYaclFields.capture(handler, gson, false));
        var saved = ModernSettingsMigration.read(named.resolve(spec.legacyFile()));
        assertEquals(64, saved.get("containerBackgroundOpacity").getAsFloat());
        assertEquals(42, saved.get("unknown").getAsInt());
        assertEquals("retain", saved.getAsJsonObject("containerLabelColor").getAsJsonObject("value").get("custom").getAsString());
        assertArrayEquals(before, Files.readAllBytes(root.resolve(spec.legacyFile())));
    }

    @Test void realRegistryCodecExpandsOnlyExplicitlyLegacyItemLists() throws Exception {
        var handler = handler(); Gson gson = gson(handler); var spec = spec("droppeditemtweaks");
        var raw = fixture("droppeditemtweaksv2");
        ModernSettingsMigration.write(folder.resolve(spec.legacyFile()), raw);
        var plan = LegacyYaclStore.load(spec, folder, "droppeditemtweaks.json", NativeYaclFields.capture(handler, gson, true));
        NativeYaclFields.apply(NativeYaclFields.decode(NativeYaclFields.live(handler), gson, plan.desired())); plan.commit();
        assertTrue(handler.instance().uchItemList.containsAll(List.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.PLAYER_HEAD, Items.SKELETON_SKULL)));
        assertEquals(12, handler.instance().uchItemList.size());
        handler.instance().uchItemList = List.of(Items.DIAMOND);
        LegacyYaclStore.save(spec, folder, NativeYaclFields.capture(handler, gson, false));
        var saved = ModernSettingsMigration.read(folder.resolve(spec.legacyFile()));
        assertEquals(raw.get("uhcItemListLegacy"), saved.get("uhcItemListLegacy"));
        var again = LegacyYaclStore.load(spec, folder, "droppeditemtweaks.json", NativeYaclFields.capture(handler, gson, true));
        assertEquals(List.of("minecraft:diamond"), gson.fromJson(again.desired().get("uchItemList"), List.class));
        saved.add("uhcItemListLegacy", JsonParser.parseString("[\"minecraft:apple\"]"));
        ModernSettingsMigration.write(folder.resolve(spec.legacyFile()), saved);
        assertEquals("minecraft:apple", LegacyYaclStore.load(spec, folder, "droppeditemtweaks.json", NativeYaclFields.capture(handler, gson, true)).desired().getAsJsonArray("uchItemList").get(0).getAsString());
    }

    @Test void existingModernChoicesWinOnceAndPersistUntilTheLegacyFieldChanges() throws Exception {
        var handler = handler(); Gson gson = gson(handler); var spec = spec("rendertweaks");
        var raw = fixture("rendertweaksv2");
        ModernSettingsMigration.write(folder.resolve(spec.legacyFile()), raw);
        ModernSettingsMigration.write(folder.resolve("rendertweaks.json"), json("{\"lightningColor\":-16776961,\"lightningAlpha\":180}"));
        var first = LegacyYaclStore.load(spec, folder, "rendertweaks.json", NativeYaclFields.capture(handler, gson, true)); first.commit();
        assertEquals(0xFF0000FF, first.desired().get("lightningColor").getAsInt());
        assertFalse(first.desired().has("fishingLineColor"));
        assertEquals(raw.get("fishingLineColor"), first.marker().getAsJsonObject("legacy").get("fishingLineColor"));
        assertEquals(first.desired(), LegacyYaclStore.load(spec, folder, "rendertweaks.json", NativeYaclFields.capture(handler, gson, true)).desired());
        raw.add("lightningColor", JsonParser.parseString("{\"class\":\"org.polyfrost.compose.render.PolyColor\",\"value\":[0,255,0,120]}"));
        ModernSettingsMigration.write(folder.resolve(spec.legacyFile()), raw);
        var changed = LegacyYaclStore.load(spec, folder, "rendertweaks.json", NativeYaclFields.capture(handler, gson, true));
        assertEquals(0x7800FF00, changed.desired().get("lightningColor").getAsInt());
        assertEquals(120, changed.desired().get("lightningAlpha").getAsInt());
    }

    @Test void invalidRawFilesAndTypedDecodeFailuresNeverRewriteUserData() throws Exception {
        var handler = handler(); Gson gson = gson(handler); var spec = spec("betterscreens");
        Path file = folder.resolve(spec.legacyFile()); Files.writeString(file, "{broken");
        assertThrows(java.io.IOException.class, () -> LegacyYaclStore.load(spec, folder, "betterscreens.json", NativeYaclFields.capture(handler, gson, true)));
        assertThrows(java.io.IOException.class, () -> LegacyYaclStore.save(spec, folder, NativeYaclFields.capture(handler, gson, false)));
        assertEquals("{broken", Files.readString(file));
        var before = handler.instance().containerBackgroundOpacity;
        assertThrows(RuntimeException.class, () -> NativeYaclFields.decode(NativeYaclFields.live(handler), gson, json("{\"containerBackgroundOpacity\":\"invalid\"}")));
        assertEquals(before, handler.instance().containerBackgroundOpacity);
    }
}
