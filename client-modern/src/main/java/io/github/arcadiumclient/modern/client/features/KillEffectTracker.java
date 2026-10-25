// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

import java.util.LinkedHashMap;
import java.util.UUID;

public final class KillEffectTracker {
    public record Hit(int entityId, UUID uuid, long tick) {}
    private final LinkedHashMap<Integer, Hit> hits = new LinkedHashMap<>();

    public void hit(int entityId, UUID uuid, long tick) {
        hits.remove(entityId);
        hits.put(entityId, new Hit(entityId, uuid, tick));
        while (hits.size() > 32) hits.remove(hits.keySet().iterator().next());
    }

    public boolean confirm(int entityId, UUID uuid, long tick, int windowTicks) {
        Hit hit = hits.remove(entityId);
        return hit != null && hit.uuid.equals(uuid) && tick >= hit.tick && tick - hit.tick <= windowTicks;
    }

    public void expire(long tick, int windowTicks) {
        hits.values().removeIf(hit -> tick < hit.tick || tick - hit.tick > windowTicks);
    }

    public Hit[] pending() { return hits.values().toArray(Hit[]::new); }
    public int size() { return hits.size(); }
    public void clear() { hits.clear(); }
}
