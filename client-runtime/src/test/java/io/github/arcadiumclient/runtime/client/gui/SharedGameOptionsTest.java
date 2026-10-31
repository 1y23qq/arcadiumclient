// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SharedGameOptionsTest {
    @TempDir Path directory;
    private static final String REQUIRED = "mouseSensitivity:0.5\nkey_key.attack:-100\n";

    @Test void newerSettingsSurviveLegacySaveAndSharedValuesChange() throws Exception {
        Path file = directory.resolve("options.txt");
        Files.writeString(file, REQUIRED + "useVbo:true\nentityShadows:false\nrenderClouds:fast\nmodelPart_hat:false\nkey_key.streamStartStop:64\nresourcePacks:[\"folder/pack.zip\"]\n");
        try (var transaction = SharedGameOptions.begin(file, true)) {
            Files.writeString(transaction.temporary(), REQUIRED + "clouds:true\nshowCape:false\ngamma:0.75\nkey_key.forward:34\n");
            transaction.complete();
            transaction.commit();
        }
        var saved = SharedGameOptions.read(file);
        assertEquals("true", saved.get("useVbo"));
        assertEquals("false", saved.get("entityShadows"));
        assertEquals("false", saved.get("modelPart_hat"));
        assertEquals("64", saved.get("key_key.streamStartStop"));
        assertEquals("[\"folder/pack.zip\"]", saved.get("resourcePacks"));
        assertEquals("0.75", saved.get("gamma"));
        assertEquals("34", saved.get("key_key.forward"));
        assertEquals("fast", saved.get("renderClouds"));
        assertEquals("false", saved.get("modelPart_cape"));
    }

    @Test void legacySettingsSurviveModernSaveAndCapeCloudsStaySynchronized() throws Exception {
        Path file = directory.resolve("options.txt");
        Files.writeString(file, REQUIRED + "advancedOpengl:true\nanisotropicFiltering:8\nclouds:true\nshowCape:true\nunknownModSetting:scheme:value\n");
        try (var transaction = SharedGameOptions.begin(file, false)) {
            Files.writeString(transaction.temporary(), REQUIRED + "renderClouds:false\nmodelPart_cape:false\n");
            transaction.complete();
            transaction.commit();
        }
        var saved = SharedGameOptions.read(file);
        assertEquals("true", saved.get("advancedOpengl"));
        assertEquals("8", saved.get("anisotropicFiltering"));
        assertEquals("scheme:value", saved.get("unknownModSetting"));
        assertEquals("false", saved.get("clouds"));
        assertEquals("false", saved.get("showCape"));
    }

    @Test void bothReadersUseTheCanonicalCloudAndCapeValues() {
        var options = Map.of("clouds", "false", "renderClouds", "fast", "showCape", "true", "modelPart_cape", "false");
        assertEquals("clouds:true", SharedGameOptions.forLoad("clouds:false", options, true));
        assertEquals("clouds:true", SharedGameOptions.forLoad("renderClouds:fast", options, true));
        assertEquals("showCape:false", SharedGameOptions.forLoad("modelPart_cape:false", options, true));
        assertEquals("renderClouds:fast", SharedGameOptions.forLoad("clouds:false", options, false));
        assertEquals("modelPart_cape:false", SharedGameOptions.forLoad("showCape:true", options, false));
        assertNull(SharedGameOptions.forLoad(null, options, true));
    }

    @Test void failedWritesPreserveTheOriginalAndRemoveOnlyTheirTemporaryFile() throws Exception {
        Path file = directory.resolve("options.txt");
        String original = REQUIRED + "renderClouds:fast\nunknownModSetting:kept\n";
        Files.writeString(file, original);
        Path temporary;
        try (var transaction = SharedGameOptions.begin(file, true)) {
            temporary = transaction.temporary();
            Files.writeString(temporary, REQUIRED + "clouds:false\n");
            transaction.fail();
            assertThrows(IOException.class, transaction::commit);
        }
        assertEquals(original, Files.readString(file));
        assertFalse(Files.exists(temporary));
    }

    @Test void incompleteVanillaWritesCannotReplaceSharedOptions() throws Exception {
        Path file = directory.resolve("options.txt");
        Files.writeString(file, REQUIRED + "guiScale:3\n");
        try (var transaction = SharedGameOptions.begin(file, true)) {
            Files.writeString(transaction.temporary(), "mouseSensitivity:0.1\n");
            assertThrows(IOException.class, transaction::commit);
        }
        assertEquals(REQUIRED + "guiScale:3\n", Files.readString(file));
    }

    @Test void completedWritesDoNotRequireAFilteredOrRenamedAttackBinding() throws Exception {
        Path file = directory.resolve("options.txt");
        Files.writeString(file, REQUIRED + "resourcePacks:[]\n");
        try (var transaction = SharedGameOptions.begin(file, false)) {
            Files.writeString(transaction.temporary(), "mouseSensitivity:0.73\ngamma:0.62\nresourcePacks:[\"日本語.zip\"]\n");
            transaction.complete();
            transaction.commit();
        }
        var saved = SharedGameOptions.read(file);
        assertEquals("0.73", saved.get("mouseSensitivity"));
        assertEquals("[\"日本語.zip\"]", saved.get("resourcePacks"));
        assertEquals("-100", saved.get("key_key.attack"));
    }

    @Test void minimumSmoothLightingSurvivesModernBooleanOptionsAndLaterChanges() throws Exception {
        Path file = directory.resolve("options.txt");
        Files.writeString(file, REQUIRED + "version:4671\nao:true\n");
        assertEquals("ao:2", SharedGameOptions.forLoad("ao:true", SharedGameOptions.read(file), false));
        try (var transaction = SharedGameOptions.begin(file, false)) {
            Files.writeString(transaction.temporary(), REQUIRED + "version:4671\nao:true\n");
            transaction.complete(); transaction.commit(1);
        }
        var saved = SharedGameOptions.read(file);
        assertEquals("true", saved.get("ao"));
        assertEquals("ao:1", SharedGameOptions.forLoad("ao:true", saved, false));
        saved.put("ao", "false");
        assertEquals("ao:0", SharedGameOptions.forLoad("ao:1", saved, true));
        saved.put("ao", "true"); saved.put("oneconfig_legacy_ao", "0");
        assertEquals("ao:2", SharedGameOptions.forLoad("ao:true", saved, false));
    }
}
