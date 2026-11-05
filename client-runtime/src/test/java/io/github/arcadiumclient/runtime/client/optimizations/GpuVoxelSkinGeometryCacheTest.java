// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinGeometryCacheTest {
    private static final GpuVoxelSkinMath.Options OPTIONS = new GpuVoxelSkinMath.Options(1.15f, 1.05f, 1.18f, 1.1f, 1.1f);
    private static final GpuVoxelSkinMath.Offset HEAD = GpuVoxelSkinMath.offset("HEAD", false, false, false, OPTIONS);
    @Test void meshIdentityAndNormalLayoutKeepIndependentImmutableGeometry() {
        GpuVoxelSkinGeometryCache cache = new GpuVoxelSkinGeometryCache(256, 8);
        Object first = new String("same"), second = new String("same"); float[] quads = GpuVoxelSkinMathTest.quad();
        var a = cache.mesh(first, quads, .0625f, HEAD);
        assertSame(a, cache.mesh(first, quads, .0625f, HEAD));
        assertNotSame(a, cache.mesh(second, quads, .0625f, HEAD));
        int preserved = cache.words()[0]; quads[3] = 99;
        assertSame(a, cache.mesh(first, quads, .0625f, HEAD)); assertEquals(preserved, cache.words()[0]);
        var body = cache.mesh(first, GpuVoxelSkinMathTest.quad(), .0625f, GpuVoxelSkinMath.offset("BODY", false, false, false, OPTIONS));
        assertNotSame(a, body);
        assertNotEquals(cache.words()[a.firstVertex()*9+5], cache.words()[body.firstVertex()*9+5]);
    }

    @Test void disposalNeverMovesQueuedGeometryBeforeTheNextBatch() {
        GpuVoxelSkinGeometryCache cache = new GpuVoxelSkinGeometryCache(256, 8);
        Object mesh = new Object(); var first = cache.mesh(mesh, GpuVoxelSkinMathTest.quad(), .0625f, HEAD);
        var survivor = cache.mesh(new Object(), GpuVoxelSkinMathTest.quad(), .0625f, HEAD);
        int before = survivor.firstVertex(); long generation = cache.generation();
        cache.dispose(mesh);
        assertTrue(first.disposed()); assertEquals(before, survivor.firstVertex()); assertEquals(generation, cache.generation());
        cache.beginBatch();
        assertEquals(0, survivor.firstVertex()); assertTrue(cache.generation() > generation); assertEquals(1, cache.meshCount());
    }

    @Test void budgetFallbackDoesNotDiscardCurrentGeometryAndReclaimsOnlyAtABatchBoundary() {
        GpuVoxelSkinGeometryCache cache = new GpuVoxelSkinGeometryCache(72, 2);
        Object a = new Object(), b = new Object(), c = new Object();
        var first = cache.mesh(a, GpuVoxelSkinMathTest.quad(), .0625f, HEAD);
        cache.mesh(b, GpuVoxelSkinMathTest.quad(), .0625f, HEAD);
        assertNull(cache.mesh(c, GpuVoxelSkinMathTest.quad(), .0625f, HEAD)); assertEquals(72, cache.sizeWords());
        for (int batch = 0; batch < 3; batch++) { cache.beginBatch(); cache.mesh(a, GpuVoxelSkinMathTest.quad(), .0625f, HEAD); cache.mesh(c, GpuVoxelSkinMathTest.quad(), .0625f, HEAD); }
        assertFalse(first.disposed()); assertNotNull(cache.mesh(c, GpuVoxelSkinMathTest.quad(), .0625f, HEAD));
        assertTrue(cache.sizeWords() <= 72); assertTrue(cache.compactions > 0);
    }

    @Test void resetInvalidatesAllAtlasRangesAndReclaimsTheCpuArena() {
        GpuVoxelSkinGeometryCache cache = new GpuVoxelSkinGeometryCache();
        cache.mesh(new Object(), GpuVoxelSkinMathTest.quad(), .0625f, HEAD); long generation = cache.generation();
        cache.reset(); assertEquals(0, cache.sizeWords()); assertEquals(0, cache.meshCount()); assertTrue(cache.generation() > generation);
    }
}
