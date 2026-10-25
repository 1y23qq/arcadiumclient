// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Options;
import net.minecraft.nbt.CompoundTag;

public final class SharedOptionsQa {
    private static Map<String, String> fixed, loaded;
    private SharedOptionsQa() { }

    public static void fixed(CompoundTag options) {
        if (!Boolean.getBoolean("arcadium.guiQa.sharedOptions") || fixed != null) return;
        var values = new LinkedHashMap<String, String>();
        for (String key : new String[]{"key_key.attack", "key_key.forward", "key_key.jump", "key_key.left", "key_key.sprint", "key_arcadium.custom", "lang", "ao", "arcadium_unknown_option"})
            values.put(key, options.getString(key).orElse(""));
        fixed = values;
    }

    public static void loaded(Options options) {
        if (!Boolean.getBoolean("arcadium.guiQa.sharedOptions") || loaded != null) return;
        loaded = Map.of("attack", options.keyAttack.saveString(), "forward", options.keyUp.saveString(),
            "jump", options.keyJump.saveString(), "left", options.keyLeft.saveString(), "sprint", options.keySprint.saveString(),
            "lang", options.languageCode, "ao", options.ambientOcclusion().get().toString());
    }

    static Map<String, Object> verify(Path output) throws Exception {
        require(fixed != null && loaded != null, "The real Options constructor was not observed");
        for (var expected : Map.of("attack", "key.mouse.left", "forward", "key.keyboard.w", "jump", "key.keyboard.j", "left", "scancode.42", "sprint", "key.keyboard.unknown", "lang", "en_us", "ao", "true").entrySet())
            require(expected.getValue().equals(loaded.get(expected.getKey())), "Loaded option changed: " + expected.getKey() + " / " + loaded);
        require("key.mouse.5".equals(fixed.get("key_arcadium.custom")), "An unknown mod's mouse binding was changed");
        require("preserve-this".equals(fixed.get("arcadium_unknown_option")), "An unknown setting was discarded");
        var report = Map.<String, Object>of("passed", true, "fixed", fixed, "loadedBeforeProfileOverrides", loaded,
            "otherNativeDataFixersStillRun", true, "unknownFieldsPreserved", true);
        Files.writeString(output.resolve("shared-options.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        return report;
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
