// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.test

import io.github.arcadiumclient.modern.client.features.NoRender
import io.github.arcadiumclient.modern.client.features.NoRenderPolicy.BlockPart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ModernRenderPolicyTest {
    @Test fun modernBlockNamesKeepIndependentPlantFilters() {
        assertEquals(BlockPart.GRASS, NoRender.classifyBlock("short_grass"))
        assertEquals(BlockPart.TALL_GRASS, NoRender.classifyBlock("tall_grass"))
        assertEquals(BlockPart.FERNS, NoRender.classifyBlock("large_fern"))
        assertEquals(BlockPart.FLOWERS, NoRender.classifyBlock("wildflowers"))
        assertEquals(BlockPart.LEAVES, NoRender.classifyBlock("pale_oak_leaves"))
        assertEquals(BlockPart.SAPLINGS, NoRender.classifyBlock("mangrove_propagule"))
        assertEquals(BlockPart.CROPS, NoRender.classifyBlock("carrots"))
        assertEquals(BlockPart.CARPETS, NoRender.classifyBlock("white_carpet"))
        assertNull(NoRender.classifyBlock("grass_block"))
        assertNull(NoRender.classifyBlock("redstone_block"))
        assertNull(NoRender.classifyBlock("oak_planks"))
    }
}
