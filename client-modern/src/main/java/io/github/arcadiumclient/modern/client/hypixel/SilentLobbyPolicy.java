// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SilentLobbyPolicy {
    private SilentLobbyPolicy() { }
    public static Set<String> categories(String identifier) {
        String id = identifier.toLowerCase(Locale.ROOT);
        Set<String> categories = new HashSet<>();
        if (id.contains("firework")) categories.add("firework");
        if (id.contains("explode") || id.contains("explosion")) categories.add("explosion");
        if (id.contains("tnt.primed")) categories.add("tnt");
        if (id.contains("villager") || id.contains("wandering_trader")) categories.add("delivery");
        if (id.endsWith(".step")) categories.add("step");
        if (id.contains("portal")) categories.add("portal");
        if (id.contains("note_block")) categories.add("note");
        if (id.contains("door") || id.contains("fence_gate")) categories.add("door");
        if (id.contains("wither")) categories.add("wither");
        if (id.contains("entity.bat.")) categories.add("bat");
        if (id.contains("slime") || id.contains("magma_cube")) categories.add("slime");
        if (id.contains("entity.arrow.") || id.contains("entity.spectral_arrow.")) categories.add("arrow");
        if (id.contains("ender_dragon")) categories.add("dragon");
        if (id.contains("enderman")) categories.add("enderman");
        if (id.contains("entity.item.pickup")) categories.add("item");
        if (id.contains("experience_orb.pickup")) categories.add("experience");
        if (id.contains("block.fire.") || id.contains("entity.player.burn")) categories.add("fire");
        if (id.contains("player.levelup")) categories.add("level");
        return Set.copyOf(categories);
    }
    public static boolean muted(String identifier, boolean hypixel, boolean lobby, boolean enabled, Set<String> muted) {
        return hypixel && lobby && enabled && categories(identifier).stream().anyMatch(muted::contains);
    }
}
