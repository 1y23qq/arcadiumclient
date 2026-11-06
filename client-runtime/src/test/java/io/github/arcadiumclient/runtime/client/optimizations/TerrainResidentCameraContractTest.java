// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TerrainResidentCameraContractTest {
    @Test void acceptsActualSeparateEyeAndRenderOriginSplits(){
        assertTrue(TerrainResidentCameraContract.axis(-1.776357262916724e-15,0,0));
        assertTrue(TerrainResidentCameraContract.axis(15.620000123977661,15,.6199951f));
        assertTrue(TerrainResidentCameraContract.axis(-28.00000002980233,-28,0));
        assertTrue(TerrainResidentCameraContract.axis(14,14,0));
    }
    @Test void acceptsBoundedPositiveNegativeAndQuantizedCoordinates(){
        assertTrue(TerrainResidentCameraContract.axis(-13.25,-13,-.25f));
        assertTrue(TerrainResidentCameraContract.axis(29_999_999.999999,29_999_999,1));
        assertTrue(TerrainResidentCameraContract.axis(-29_999_999.999999,-29_999_999,-1));
        assertTrue(TerrainResidentCameraContract.axis(-0.0,0,-0.0f));
    }
    @Test void rejectsUnknownOrInconsistentSplits(){
        assertFalse(TerrainResidentCameraContract.axis(15.62,14,.62f));
        assertFalse(TerrainResidentCameraContract.axis(15.62,15,.61f));
        assertFalse(TerrainResidentCameraContract.axis(15.62,15,1.01f));
        assertFalse(TerrainResidentCameraContract.axis(Double.NaN,0,0));
        assertFalse(TerrainResidentCameraContract.axis(Double.POSITIVE_INFINITY,0,0));
        assertFalse(TerrainResidentCameraContract.axis(0,0,Float.NaN));
        assertFalse(TerrainResidentCameraContract.axis(0,0,Float.NEGATIVE_INFINITY));
        assertFalse(TerrainResidentCameraContract.axis(1e9,1_000_000_000,0));
    }
}
