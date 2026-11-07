// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.List;
import java.util.Map;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.CompactChunkVertex;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainResidentPassIdentityTest {
    private static final TerrainRenderPass.PipelineState PIPELINE = new TerrainRenderPass.PipelineState() {
        public void setup() {}
        public void clear() {}
    };

    private static TerrainRenderPass pass(String name, boolean discard, boolean reverse, boolean sorted, boolean noLightmap) {
        return new TerrainRenderPass(name, PIPELINE, reverse, discard, sorted, noLightmap,
                new CompactChunkVertex(), QuadPrimitiveType.TRIANGULATED, Map.of());
    }

    private static RenderPassConfiguration<?> configuration(TerrainRenderPass solid, TerrainRenderPass cutout) {
        return new RenderPassConfiguration<>(Map.of(), Map.of(),
                new Material(solid, AlphaCutoffParameter.ZERO, true),
                new Material(cutout, cutout.supportsFragmentDiscard() ? AlphaCutoffParameter.ONE_TENTH : AlphaCutoffParameter.ZERO, true),
                new Material(pass("translucent", false, true, true, false), AlphaCutoffParameter.ZERO, true));
    }

    @Test void onlyTheActualDefaultPassIdentitiesAreAccepted() {
        var solid = pass("solid", false, false, false, false);
        var cutout = pass("cutout_mipped", true, false, false, false);
        var configuration = configuration(solid, cutout);
        assertTrue(TerrainResidentPassIdentity.accepts(solid, configuration));
        assertTrue(TerrainResidentPassIdentity.accepts(cutout, configuration));
        assertFalse(TerrainResidentPassIdentity.accepts(pass("solid", false, false, false, false), configuration));
        assertFalse(TerrainResidentPassIdentity.accepts(pass("cutout_mipped", true, false, false, false), configuration));
        assertFalse(TerrainResidentPassIdentity.accepts(configuration.defaultTranslucentMaterial().pass, configuration));
        assertFalse(TerrainResidentPassIdentity.accepts(null, configuration));
        assertFalse(TerrainResidentPassIdentity.accepts(solid, null));
    }

    @Test void sortedReverseUnlitAndDiscardMismatchesRemainNative() {
        for (int mode = 0; mode < 4; mode++) {
            var solid = pass("solid", mode == 0, mode == 1, mode == 2, mode == 3);
            var cutout = pass("cutout_mipped", mode != 0, mode == 1, mode == 2, mode == 3);
            var configuration = configuration(solid, cutout);
            assertFalse(TerrainResidentPassIdentity.accepts(solid, configuration));
            assertFalse(TerrainResidentPassIdentity.accepts(cutout, configuration));
        }
        var decal = pass("decal", true, false, false, false);
        assertFalse(TerrainResidentPassIdentity.accepts(decal, configuration(decal, decal)));
    }

    @Test void cutoutKeepsTheNativeFragmentDiscardShaderConstant() {
        var solid = pass("solid", false, false, false, false);
        var cutout = pass("cutout_mipped", true, false, false, false);
        var configuration = configuration(solid, cutout);
        assertSame(AlphaCutoffParameter.ONE_TENTH, configuration.defaultCutoutMippedMaterial().alphaCutoff);
        assertTrue(configuration.defaultCutoutMippedMaterial().mipped);
        assertTrue(new ChunkShaderOptions(List.of(), cutout).constants().getDefineStrings().contains("#define USE_FRAGMENT_DISCARD"));
        assertFalse(new ChunkShaderOptions(List.of(), solid).constants().getDefineStrings().contains("#define USE_FRAGMENT_DISCARD"));
    }

    @Test void equalityAliasesCannotSharePassResourcesOrManagerOwnership() {
        Object manager = new Object();
        var book = new TerrainResidentPassBook<AutoCloseable>(manager);
        var solid = new EqualPass();
        var cutout = new EqualPass();
        AutoCloseable first = () -> {}, second = () -> {};
        assertEquals(solid, cutout);
        assertTrue(book.live(manager));
        assertFalse(book.live(new Object()));
        assertTrue(book.put(solid, first));
        assertTrue(book.put(cutout, second));
        assertSame(first, book.get(solid));
        assertSame(second, book.get(cutout));
        assertNull(book.get(new EqualPass()));
        assertFalse(book.put(solid, () -> {}));
        assertFalse(book.put(new Object(), () -> {}));
        book.close();
        assertEquals(0, book.size());
        assertFalse(book.live(manager));
        assertFalse(book.put(new Object(), () -> {}));
    }

    @Test void failedPassCleanupRetainsOnlyTheFailedOwnerAndInvalidatesLookups() {
        Object manager = new Object(), solid = new Object(), cutout = new Object();
        var book = new TerrainResidentPassBook<AutoCloseable>(manager);
        int[] calls = {0, 0};
        boolean[] fail = {true};
        assertTrue(book.put(solid, () -> {
            calls[0]++;
            if (fail[0]) throw new IllegalStateException("Owner unavailable");
        }));
        assertTrue(book.put(cutout, () -> calls[1]++));
        assertThrows(IllegalStateException.class, book::close);
        assertEquals(1, book.size());
        assertNull(book.get(solid));
        assertFalse(book.live(manager));
        assertArrayEquals(new int[]{1, 1}, calls);
        fail[0] = false;
        book.close();
        assertEquals(0, book.size());
        assertArrayEquals(new int[]{2, 1}, calls);
    }

    private static final class EqualPass {
        @Override public boolean equals(Object other) { return other instanceof EqualPass; }
        @Override public int hashCode() { return 1; }
    }
}
