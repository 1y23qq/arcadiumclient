// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModernGlintShaderPatchTest {
    private static final String VERTEX = "#version 130\nout vec2 vGlintCoord;\nvoid main(){gl_Position = gl_ProjectionMatrix * eyePosition;if (uGlintPass >= 0) {originalVanilla();}}";
    private static final String FRAGMENT = "#version 130\nin vec2 vGlintCoord;\nvoid main(){vec4 color=tinted(vTexCoord);if (uGlintPass >= 0 || uItemGlintPass == 1) {originalVanilla();}}";

    @Test void vertexKeepsTheNativeNonnegativeBranchAndUsesCapturedModernMatrix() {
        String patched = ModernGlintShaderPatch.vertex(VERTEX);
        assertTrue(patched.startsWith("#version 430 compatibility"));
        assertTrue(patched.contains("binding=7"));
        assertTrue(patched.contains("aEffectTime <= -1.0"));
        assertTrue(patched.contains("arcadiumMatrix < uArcadiumModernGlintCount"));
        assertTrue(patched.contains("arcadiumGlintMatrices[arcadiumMatrix] * vec4(baseTexCoord, 0.0, 1.0)"));
        assertTrue(patched.contains("else if (uGlintPass >= 0) {originalVanilla();}"));
    }

    @Test void privateMvpModePreservesTheNativePositionControlAndUsesOrderedFma() {
        String patched = ModernGlintShaderPatch.vertex(VERTEX);
        assertTrue(patched.contains("uArcadiumModernArmorMvp != 0"));
        assertTrue(patched.contains(": gl_ProjectionMatrix * eyePosition"));
        assertTrue(patched.contains("int arcadiumMvp = int(aBoxTexture.x) - 1"));
        assertTrue(patched.indexOf("fma(m[0]") < patched.indexOf("fma(m[1]"));
        assertTrue(patched.indexOf("fma(m[1]") < patched.indexOf("fma(m[2]"));
        assertTrue(patched.indexOf("fma(m[2]") < patched.indexOf("fma(m[3]"));
        assertThrows(IllegalArgumentException.class, () -> ModernGlintShaderPatch.vertex(VERTEX.replace("gl_Position = gl_ProjectionMatrix * eyePosition;", "")));
    }

    @Test void fragmentPreservesNativePathsAndUsesOneModernSample() {
        String patched = ModernGlintShaderPatch.fragment(FRAGMENT);
        assertTrue(patched.contains("if (arcadiumModernGlint != 0)"));
        assertTrue(patched.contains("fragColor = shaded(color);\n        return;"));
        assertTrue(patched.contains("if (uGlintPass >= 0 || uItemGlintPass == 1) {originalVanilla();}"));
    }

    @Test void unknownShadersAndOldGlKeepTheirOriginalSource() {
        assertSame(VERTEX, ModernGlintShaderPatch.patch("argentum:entity_instancing.vert", VERTEX, FRAGMENT, true, true));
        assertSame(VERTEX, ModernGlintShaderPatch.patch("argentum:entity_instancing.vert", VERTEX, FRAGMENT, false, true));
        assertSame(VERTEX, ModernGlintShaderPatch.patch("other:entity_instancing.vert", VERTEX, FRAGMENT, true, true));
        assertThrows(IllegalArgumentException.class, () -> ModernGlintShaderPatch.vertex(VERTEX.replace("out vec2 vGlintCoord;", "")));
        assertThrows(IllegalArgumentException.class, () -> ModernGlintShaderPatch.fragment(FRAGMENT + "\nin vec2 vGlintCoord;"));
    }

    @Test void aSingleUnknownShaderCannotChangeThePairInterface() {
        String vertex = "357ab22c211148962f7218b63bdaeaafb38074b565d82b25e8b55203143581df";
        String fragment = "3d666a259d5dabbce045d0119b2d58f916424ffc967a0b93e5a2e50ec9e47b3d";
        assertTrue(ModernGlintShaderPatch.supportedHashes(vertex, fragment));
        assertFalse(ModernGlintShaderPatch.supportedHashes("unknown", fragment));
        assertFalse(ModernGlintShaderPatch.supportedHashes(vertex, "unknown"));
        assertFalse(ModernGlintShaderPatch.supportedHashes(null, fragment));
        assertEquals("argentum:entity_instancing.frag", ModernGlintShaderPatch.peer("argentum:entity_instancing.vert"));
        assertNull(ModernGlintShaderPatch.peer("other:entity_instancing.vert"));
    }

    @Test void shaderStorageMustBeAvailableToTheVertexStageAtBindingSeven() {
        assertFalse(ModernGlintShaderPatch.supportedCapabilities(false, 32, 32));
        assertFalse(ModernGlintShaderPatch.supportedCapabilities(true, 0, 8));
        assertFalse(ModernGlintShaderPatch.supportedCapabilities(true, 1, 7));
        assertFalse(ModernGlintShaderPatch.supportedCapabilities(true, -1, 32));
        assertTrue(ModernGlintShaderPatch.supportedCapabilities(true, 1, 8));
        assertTrue(ModernGlintShaderPatch.supportedCapabilities(true, 32, 32));
        boolean unsupported = ModernGlintShaderPatch.supportedCapabilities(true, 0, 8);
        assertSame(VERTEX, ModernGlintShaderPatch.patch("argentum:entity_instancing.vert", VERTEX, FRAGMENT, unsupported, true));
        assertSame(FRAGMENT, ModernGlintShaderPatch.patch("argentum:entity_instancing.frag", FRAGMENT, VERTEX, unsupported, true));
    }

    @Test void absentContextOrMissingPairSourceCannotIntroduceAnInterface() {
        assertSame(VERTEX, ModernGlintShaderPatch.patch("argentum:entity_instancing.vert", VERTEX, null, true, true));
        assertSame(FRAGMENT, ModernGlintShaderPatch.patch("argentum:entity_instancing.frag", FRAGMENT, VERTEX, false, true));
        assertNull(ModernGlintShaderPatch.patch("argentum:entity_instancing.vert", null, FRAGMENT, true, true));
    }

    @Test void unqualifiedProductionKeepsBothNativeSourcesWhileExplicitQaCanQualify() {
        assertFalse(ModernGlintShaderPatch.qualificationEnabled(false, false));
        assertTrue(ModernGlintShaderPatch.qualificationEnabled(false, true));
        assertTrue(ModernGlintShaderPatch.qualificationEnabled(true, false));
        assertTrue(ModernGlintShaderPatch.qualificationEnabled(true, true));
        boolean disabled = ModernGlintShaderPatch.qualificationEnabled(false, false);
        assertSame(VERTEX, ModernGlintShaderPatch.patch("argentum:entity_instancing.vert", VERTEX, FRAGMENT, true, disabled));
        assertSame(FRAGMENT, ModernGlintShaderPatch.patch("argentum:entity_instancing.frag", FRAGMENT, VERTEX, true, disabled));
    }
}
