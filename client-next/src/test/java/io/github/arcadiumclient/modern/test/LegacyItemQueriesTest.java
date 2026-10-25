// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test;

import io.github.arcadiumclient.modern.client.features.LegacyItemQueries;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LegacyItemQueriesTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
    }
    @Test void missingLegacyFamiliesUseTheActualFlatteningMap() {
        var wool = LegacyItemQueries.missing("minecraft:wool");
        assertEquals(16, wool.items().size());
        assertTrue(wool.matches(new ItemStack(Items.WOOL.white(), 32)));
        assertTrue(wool.matches(new ItemStack(Items.WOOL.red(), 5)));
        assertFalse(wool.matches(new ItemStack(Items.STONE, 64)));
        var inventory = List.of(new ItemStack(Items.WOOL.white(), 32), new ItemStack(Items.WOOL.red(), 5), new ItemStack(Items.STONE, 64));
        assertEquals(37, inventory.stream().filter(wool::matches).mapToInt(ItemStack::getCount).sum());
        var planks = LegacyItemQueries.missing("minecraft:planks");
        assertEquals(6, planks.items().size());
        assertTrue(planks.membership().containsAll(List.of(Items.OAK_PLANKS, Items.DARK_OAK_PLANKS)));
        assertFalse(planks.membership().contains(Items.CHERRY_PLANKS));
    }
    @Test void validModernIdsRemainExactIncludingAmbiguousLegacyNames() {
        for (String id : List.of("minecraft:stone", "minecraft:melon", "minecraft:nether_brick", "minecraft:white_wool", "minecraft:oak_planks", "minecraft:golden_apple"))
            assertTrue(LegacyItemQueries.missing(id).isEmpty(), id);
    }
    @Test void oneToOneAndLaterNativeRenamesWorkWithoutChangingSavedQueries() {
        String[] saved = {"minecraft:grass", "minecraft:reeds", "minecraft:sign"};
        assertEquals(List.of(Items.GRASS_BLOCK), LegacyItemQueries.missing(saved[0]).items());
        assertEquals(List.of(Items.SUGAR_CANE), LegacyItemQueries.missing(saved[1]).items());
        assertEquals(List.of(Items.OAK_SIGN), LegacyItemQueries.missing(saved[2]).items());
        assertArrayEquals(new String[]{"minecraft:grass", "minecraft:reeds", "minecraft:sign"}, saved);
        assertTrue(LegacyItemQueries.missing("modded:wool").isEmpty());
        assertTrue(LegacyItemQueries.missing("invalid id!").isEmpty());
    }
}
