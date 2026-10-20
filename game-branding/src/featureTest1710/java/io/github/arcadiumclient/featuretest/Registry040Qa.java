// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Registry040Qa {
    private Registry040Qa() {}

    static void capture(Map<String, Object> result) throws Exception {
        List<Map<String, Object>> configurations = new ArrayList<>(), trees = new ArrayList<>(), providers = new ArrayList<>();
        Object registry = instance("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
        for (Object config : (Collection<?>) call(registry, "getConfigs")) {
            configurations.add(Map.of("id", String.valueOf(call(config, "getId")), "title", title(call(config, "getTitle")),
                "source", String.valueOf(call(config, "getSource"))));
        }
        for (Object tree : (Collection<?>) call(stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active"), "trees")) {
            trees.add(Map.of("id", String.valueOf(call(tree, "getID")), "title", title(call(tree, "getTitle"))));
        }
        for (Object hud : (Collection<?>) call(instance("org.polyfrost.oneconfig.api.hud.v1.HudManager"), "providers")) {
            providers.add(Map.of("class", hud.getClass().getName(), "id", String.valueOf(field(hud, "id")), "title", title(field(hud, "title"))));
        }
        configurations.sort(Comparator.comparing(row -> row.get("id").toString()));
        trees.sort(Comparator.comparing(row -> row.get("id").toString()));
        providers.sort(Comparator.comparing(row -> row.get("class").toString()));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("visibleAndHiddenConfigRegistry", configurations); snapshot.put("allConfigManagerTrees", trees); snapshot.put("hudProviders", providers);
        result.put("moduleRegistrySnapshot", snapshot);
    }

    private static String title(Object value) throws Exception {
        return value == null ? "" : (String) stat("org.polyfrost.oneconfig.internal.ui.components.TextKt", "asRenderText", value);
    }
}
