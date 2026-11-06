// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EntityInstancingShaderPatchTest {
    private static String source(String stage) throws Exception {
        String path = System.getProperty("arcadium.voxelSkin072.argentumJar");
        assertNotNull(path, "Pinned Argentum ABI input is required");
        try (ZipFile jar = new ZipFile(Path.of(path).toFile())) {
            return new String(jar.getInputStream(jar.getEntry("assets/argentum/shaders/entity_instancing." + stage)).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void nativePairComposesBothInterfacesWithOneVersionAndThreeStorageBlocks() throws Exception {
        String v = source("vert"), f = source("frag");
        String pv = EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, f, true, true, true, 3, 8);
        String pf = EntityInstancingShaderPatch.patch("argentum:entity_instancing.frag", f, v, true, true, true, 3, 8);
        assertNotEquals(v, pv); assertNotEquals(f, pf);
        assertEquals(1, pv.split("#version", -1).length - 1);
        assertTrue(pv.contains("#define " + GpuResidentBodyShaders.TAG));
        assertTrue(pv.contains("binding=0")); assertTrue(pv.contains("binding=1")); assertTrue(pv.contains("binding=7"));
        assertTrue(pv.contains("model=arcBodyMatrix(matrixOffset)"));
        assertTrue(pv.contains("uArcadiumModernArmorMvp != 0"));
        assertTrue(pv.contains("else if (uGlintPass >= 0)"));
        assertTrue(pf.contains("arcadiumModernGlint != 0"));
    }

    @Test void individualModesKeepTheirPreviouslyVerifiedSources() throws Exception {
        String v = source("vert"), f = source("frag");
        assertEquals(GpuResidentBodyShaders.vertex(v), EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, f, true, false, true, 2, 7));
        assertSame(f, EntityInstancingShaderPatch.patch("argentum:entity_instancing.frag", f, v, true, false, true, 2, 7));
        assertEquals(ModernGlintShaderPatch.vertex(v), EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, f, false, true, true, 1, 8));
        assertEquals(ModernGlintShaderPatch.fragment(f), EntityInstancingShaderPatch.patch("argentum:entity_instancing.frag", f, v, false, true, true, 1, 8));
    }

    @Test void insufficientCombinedCapacityChoosesOneValidInterface() throws Exception {
        String v = source("vert"), f = source("frag");
        assertEquals(new EntityInstancingShaderPatch.Selection(false, true), EntityInstancingShaderPatch.select(true, true, true, 2, 8));
        assertEquals(ModernGlintShaderPatch.vertex(v), EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, f, true, true, true, 2, 8));
        assertEquals(new EntityInstancingShaderPatch.Selection(true, false), EntityInstancingShaderPatch.select(true, true, true, 2, 7));
        assertEquals(new EntityInstancingShaderPatch.Selection(false, false), EntityInstancingShaderPatch.select(true, true, false, 32, 32));
    }

    @Test void oneUnknownStageCannotChangeThePairInterface() throws Exception {
        String v = source("vert"), f = source("frag"), unknown = f + "\n";
        assertSame(v, EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, unknown, true, true, true, 32, 32));
        assertSame(unknown, EntityInstancingShaderPatch.patch("argentum:entity_instancing.frag", unknown, v, true, true, true, 32, 32));
        assertSame(f, EntityInstancingShaderPatch.patch("argentum:entity_instancing.frag", f, null, true, true, true, 32, 32));
        assertSame(v, EntityInstancingShaderPatch.patch("unknown", v, f, true, true, true, 32, 32));
    }

    @Test void patchedOrUnsupportedInputsKeepTheirOwnSource() throws Exception {
        String v = source("vert"), f = source("frag");
        String pv = EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, f, true, true, true, 3, 8);
        assertSame(pv, EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", pv, f, true, true, true, 3, 8));
        assertSame(v, EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, f, false, false, true, 32, 32));
        assertSame(v, EntityInstancingShaderPatch.patch("argentum:entity_instancing.vert", v, f, true, true, true, 0, 0));
    }
}
