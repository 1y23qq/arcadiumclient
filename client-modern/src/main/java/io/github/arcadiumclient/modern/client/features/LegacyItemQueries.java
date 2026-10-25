// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.ItemStackTheFlatteningFix;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class LegacyItemQueries {
    public record Family(List<Item> items, Set<Item> membership) {
        public Item representative() { return items.getFirst(); }
        public boolean matches(ItemStack stack) { return membership.contains(stack.getItem()); }
        public boolean isEmpty() { return items.isEmpty(); }
    }
    public static final Family NONE = new Family(List.of(), Set.of());
    private static final ConcurrentHashMap<String, Family> CACHE = new ConcurrentHashMap<>();
    private LegacyItemQueries() {}

    public static Family missing(String query) {
        if (query == null || query.isBlank()) return NONE;
        String value = query.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) value = "minecraft:" + value.replace(' ', '_');
        if (CACHE.size() >= 512) CACHE.clear();
        return CACHE.computeIfAbsent(value, LegacyItemQueries::resolve);
    }

    private static Family resolve(String query) {
        Identifier id = Identifier.tryParse(query);
        if (id == null || !id.getNamespace().equals("minecraft") || current(query) != null) return NONE;
        var items = new LinkedHashSet<Item>();
        for (int metadata = 0; metadata < 16; metadata++) {
            String flattened = ItemStackTheFlatteningFix.updateItem(query, metadata);
            if (flattened != null) addCurrent(items, flattened);
        }
        if (items.isEmpty()) addCurrent(items, query);
        return items.isEmpty() ? NONE : new Family(List.copyOf(items), Set.copyOf(items));
    }

    private static void addCurrent(Set<Item> items, String name) {
        Item item = current(name);
        if (item == null) {
            var source = new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive(name));
            String renamed = DataFixers.getDataFixer().update(References.ITEM_NAME, source, 1519,
                SharedConstants.getCurrentVersion().dataVersion().version()).asString(name);
            item = current(renamed);
        }
        if (item != null) items.add(item);
    }

    private static Item current(String name) {
        Identifier id = Identifier.tryParse(name);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == Items.AIR ? null : item;
    }
}
