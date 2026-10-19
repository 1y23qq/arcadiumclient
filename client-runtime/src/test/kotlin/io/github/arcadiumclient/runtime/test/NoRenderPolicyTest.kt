// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy
import io.github.arcadiumclient.runtime.client.features.NoRenderPolicy.*

class NoRenderPolicyTest {
    @Test
    fun `master off ignores all individually selected options`() {
        val off = NoRenderPolicy.create(false, -1L, -1L, -1L, -1L)
        assertSame(NoRenderPolicy.OFF, off)
        BlockPart.entries.forEach { assertFalse(off.hides(it)) }
        EntityPart.entries.forEach { assertFalse(off.hides(it)) }
        Effect.entries.forEach { assertFalse(off.hides(it)) }
        (-1..100).forEach { assertFalse(off.hidesParticle(it)) }
        assertEquals(0, off.chunkKey())
    }

    @Test
    fun `each entity and effect option is independent`() {
        for (selected in EntityPart.entries) {
            val policy = NoRenderPolicy.create(true, 0, NoRenderPolicy.bit(selected), 0, 0)
            for (candidate in EntityPart.entries) assertEquals(selected == candidate, policy.hides(candidate))
        }
        for (selected in Effect.entries) {
            val policy = NoRenderPolicy.create(true, 0, 0, 0, NoRenderPolicy.bit(selected))
            for (candidate in Effect.entries) assertEquals(selected == candidate, policy.hides(candidate))
        }
    }

    @Test
    fun `short grass tall grass ferns and flowers are separate`() {
        assertEquals(BlockPart.GRASS, NoRenderPolicy.blockPart(31, 1))
        assertEquals(BlockPart.TALL_GRASS, NoRenderPolicy.blockPart(175, 2))
        assertEquals(BlockPart.FERNS, NoRenderPolicy.blockPart(31, 2))
        assertEquals(BlockPart.FERNS, NoRenderPolicy.blockPart(175, 3))
        assertEquals(BlockPart.DEAD_BUSHES, NoRenderPolicy.blockPart(31, 0))
        for (variant in listOf(0, 1, 4, 5)) assertEquals(BlockPart.FLOWERS, NoRenderPolicy.blockPart(175, variant))
        val grassOnly = NoRenderPolicy.create(true, NoRenderPolicy.bit(BlockPart.GRASS), 0, 0, 0)
        assertTrue(grassOnly.hides(NoRenderPolicy.blockPart(31, 1)))
        assertFalse(grassOnly.hides(NoRenderPolicy.blockPart(175, 2)))
        assertFalse(grassOnly.hides(NoRenderPolicy.blockPart(31, 2)))
        assertFalse(grassOnly.hides(NoRenderPolicy.blockPart(37, 0)))
    }

    @Test
    fun `plant and decorative filters do not remove terrain or fluids`() {
        val essential = listOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 20,
            35, 41, 42, 45, 46, 47, 48, 49, 56, 57, 58, 60, 61, 62, 79, 80, 82, 86, 87, 88, 89, 98, 112, 133, 152)
        for (id in essential) for (metadata in 0..15) assertNull(NoRenderPolicy.blockPart(id, metadata), "Block $id:$metadata")
        assertNull(NoRenderPolicy.blockPart(4096, 0))
    }

    @Test
    fun `every block filter has a distinct discoverable vanilla block`() {
        val seen = (0..197).flatMap { id -> (0..7).mapNotNull { NoRenderPolicy.blockPart(id, it) } }.toSet()
        assertEquals(BlockPart.entries.toSet(), seen)
    }

    @Test
    fun `all particle switch includes unknown mod particles while named switches do not`() {
        val all = NoRenderPolicy.create(true, 0, 0, NoRenderPolicy.bit(ParticlePart.ALL), 0)
        val smoke = NoRenderPolicy.create(true, 0, 0, NoRenderPolicy.bit(ParticlePart.SMOKE), 0)
        for (id in listOf(-1, 42, 500)) {
            assertTrue(all.hidesParticle(id))
            assertFalse(smoke.hidesParticle(id))
        }
        assertTrue(smoke.hidesParticle(11))
        assertTrue(smoke.hidesParticle(12))
        assertFalse(smoke.hidesParticle(0))
        assertFalse(smoke.hidesParticle(26))
    }

    @Test
    fun `all vanilla particles belong to exactly one named group`() {
        for (id in 0..41) {
            assertEquals(1, java.lang.Long.bitCount(NoRenderPolicy.particleMask(id)), "Particle $id")
            assertEquals(0, NoRenderPolicy.particleMask(id) and NoRenderPolicy.bit(ParticlePart.ALL))
        }
        val seen = (0..41).map { NoRenderPolicy.particleMask(it) }.toSet()
        assertEquals(ParticlePart.entries.filter { it != ParticlePart.ALL }.map(NoRenderPolicy::bit).toSet(), seen)
    }

    @Test
    fun `only mesh affecting options change the chunk key`() {
        val baseline = NoRenderPolicy.OFF.chunkKey()
        for (part in BlockPart.entries) {
            assertNotEquals(baseline, NoRenderPolicy.create(true, NoRenderPolicy.bit(part), 0, 0, 0).chunkKey())
        }
        for (part in EntityPart.entries) {
            val key = NoRenderPolicy.create(true, 0, NoRenderPolicy.bit(part), 0, 0).chunkKey()
            assertEquals(part == EntityPart.ITEM_FRAMES, key != baseline)
        }
        assertEquals(baseline, NoRenderPolicy.create(true, 0, 0, -1L, -1L).chunkKey())
        assertNotEquals(NoRenderPolicy.create(true, -1L ushr 40, 0, 0, 0).chunkKey(),
            NoRenderPolicy.create(true, -1L ushr 40, NoRenderPolicy.bit(EntityPart.ITEM_FRAMES), 0, 0).chunkKey())
    }

    @Test
    fun `chunk invalidation rebuilds once for each effective change in the same world`() {
        val tracker = NoRenderPolicy.RebuildTracker()
        val firstWorld = Any()
        val secondWorld = Any()
        assertFalse(tracker.update(null, 0))
        assertFalse(tracker.update(firstWorld, 0))
        assertFalse(tracker.update(firstWorld, 0))
        assertTrue(tracker.update(firstWorld, 1))
        assertFalse(tracker.update(firstWorld, 1))
        assertTrue(tracker.update(firstWorld, 3))
        assertTrue(tracker.update(firstWorld, 0))
        assertFalse(tracker.update(secondWorld, 1))
        assertFalse(tracker.update(null, 0))
        assertFalse(tracker.update(firstWorld, 1))
    }
}
