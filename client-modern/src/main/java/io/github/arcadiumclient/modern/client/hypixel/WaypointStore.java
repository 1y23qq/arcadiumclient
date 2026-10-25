// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import java.util.LinkedHashMap;
import java.util.List;

public final class WaypointStore {
    public record Waypoint(String owner, int x, int y, int z, boolean dangerous, long expiresAt) { }
    private final LinkedHashMap<String, Waypoint> points = new LinkedHashMap<>();
    public void put(String owner, HypixelChatPolicy.Coordinates coordinates, boolean dangerous, long now, int seconds) {
        String key = owner + ":" + coordinates.x() + ":" + coordinates.y() + ":" + coordinates.z();
        points.remove(key);
        points.put(key, new Waypoint(owner, coordinates.x(), coordinates.y(), coordinates.z(), dangerous,
            now + Math.max(5, Math.min(300, seconds)) * 1_000_000_000L));
        while (points.size() > 32) points.remove(points.keySet().iterator().next());
    }
    public void expire(long now) { points.values().removeIf(point -> now >= point.expiresAt()); }
    public List<Waypoint> snapshot() { return List.copyOf(points.values()); }
    public void clear() { points.clear(); }
    public int size() { return points.size(); }
}
