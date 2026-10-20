// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;

final class LegacyBadge040Qa {
    private static boolean complete;
    private LegacyBadge040Qa() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void verify(Minecraft client, Map<String, Object> result) throws Exception {
        if (complete) return;
        String type = "io.github.arcadiumclient.runtime.legacy.LegacyPlayerDirectory";
        Class<?> directory = Class.forName(type);
        Object cache = field(directory, "PROFILES");
        Object lock = field(cache, "lock");
        Map entries = (Map) field(cache, "entries");
        String raw = client.player.getName(), name = raw.toLowerCase(Locale.ROOT);
        Object priorEntry;
        boolean hadEntry;
        Map<String, Object> prior = new LinkedHashMap<>();
        for (String key : java.util.List.of("world", "sampledAt", "identities", "listed")) prior.put(key, field(directory, key));
        UUID unrelated = UUID.randomUUID();
        Class<?> entryClass = Class.forName("io.github.arcadiumclient.runtime.legacy.LegacyProfileCache$Entry");
        Constructor<?> constructor = entryClass.getDeclaredConstructor(UUID.class, long.class); constructor.setAccessible(true);
        synchronized (lock) {
            hadEntry = entries.containsKey(name); priorEntry = entries.get(name);
            entries.put(name, constructor.newInstance(unrelated, System.nanoTime() / 1_000_000 + 60_000));
        }
        try {
            set(directory, "sampledAt", 0L);
            stat(type, "observe", client, false);
            require(client.player.getUuid().equals(stat(type, "id", raw)), "A name lookup overrode the actual loaded server UUID");
            require(!(boolean) stat(type, "isOnline", (Object) null), "A null native Tab name was treated as online");
            require(!(boolean) stat(type, "isOnline", "Unknown_QA_040"), "An unknown native Tab name was treated as online");
            int queued = ((Number) call(cache, "queuedCount")).intValue();
            java.lang.reflect.Method read = directory.getMethod("id", String.class);
            for (int i = 0; i < 10_000; i++) read.invoke(null, "Unknown_QA_040");
            require(((Number) call(cache, "queuedCount")).intValue() == queued, "Reading a Tab badge scheduled render-thread profile lookups");
            PlayerInfo missing = new PlayerInfo("Unknown_QA_040");
            require(!(boolean) stat("io.github.arcadiumclient.runtime.client.branding.PlayerBadgeRenderer", "inTab", missing),
                "The native Tab renderer did not safely suppress an unknown identity");
            result.put("nativeBadgeIdentity", Map.of("loadedProfileWinsCachedName", true, "unknownAndNullNamesSafe", true,
                "renderReadsWithoutScheduling", 10_000));
            complete = true;
        } finally {
            synchronized (lock) { if (hadEntry) entries.put(name, priorEntry); else entries.remove(name); }
            for (Map.Entry<String, Object> entry : prior.entrySet()) set(directory, entry.getKey(), entry.getValue());
        }
    }

    private static void set(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
