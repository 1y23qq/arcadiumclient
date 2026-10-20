// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.entity.living.player.PlayerEntity;

public final class LegacyPlayerDirectory {
    private static final LegacyProfileCache PROFILES = new LegacyProfileCache();
    private static Object world;
    private static long sampledAt;
    private static volatile Map<String, UUID> identities = Map.of();
    private static volatile Set<UUID> listed = Set.of();
    private LegacyPlayerDirectory() {}

    public static void observe(Minecraft client, boolean resolveNames) {
        if (client.world == null) {
            world = null; identities = Map.of(); listed = Set.of(); sampledAt = 0; PROFILES.pause(); return;
        }
        long now = System.nanoTime();
        if (world == client.world && now - sampledAt < 100_000_000L) return;
        world = client.world; sampledAt = now;
        Map<String, UUID> loaded = new LinkedHashMap<>();
        Map<String, UUID> next = new LinkedHashMap<>();
        for (Object entry : client.world.players) if (entry instanceof PlayerEntity player) {
            String name = player.getName(), key = LegacyProfileCache.canonical(name);
            if (key != null) { loaded.put(key, player.getUuid()); next.put(name, player.getUuid()); }
            if (loaded.size() >= 256) break;
        }
        Set<UUID> tab = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        if (client.getNetworkHandler() != null) for (Object entry : client.getNetworkHandler().onlinePlayers) {
            if (!(entry instanceof PlayerInfo player)) continue;
            String key = LegacyProfileCache.canonical(player.name);
            if (key == null) continue;
            UUID id = loaded.containsKey(key) ? loaded.get(key) : PROFILES.cached(key);
            if (id != null) { next.put(player.name, id); if (id.version() == 4) tab.add(id); }
            else if (resolveNames && missing.size() < 256) missing.add(key);
            if (tab.size() + missing.size() >= 256) break;
        }
        identities = Map.copyOf(next); listed = Set.copyOf(tab);
        if (resolveNames) PROFILES.watch(missing); else PROFILES.pause();
    }

    public static UUID id(String name) { return name == null ? null : identities.get(name); }
    public static Set<UUID> tabIds() { return listed; }
    public static boolean isOnline(String name) {
        UUID player = id(name);
        return player != null && io.github.arcadiumclient.runtime.client.presence.ArcadiumPresence.isOnline(player);
    }
}
