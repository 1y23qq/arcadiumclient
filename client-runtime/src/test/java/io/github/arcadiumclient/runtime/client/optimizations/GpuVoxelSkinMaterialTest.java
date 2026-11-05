// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Vector4fc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinMaterialTest {
    @Test void analyticSubmissionUsesWhiteTintAndFullyTransparentOverlay() throws Exception {
        Class<?> renderer = Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinRenderer");
        var white = renderer.getDeclaredField("WHITE");
        var clear = renderer.getDeclaredField("CLEAR");
        white.setAccessible(true); clear.setAccessible(true);
        Vector4fc tint = (Vector4fc)white.get(null), overlay = (Vector4fc)clear.get(null);
        assertArrayEquals(new float[]{1,1,1,1}, new float[]{tint.x(),tint.y(),tint.z(),tint.w()});
        assertArrayEquals(new float[]{0,0,0,0}, new float[]{overlay.x(),overlay.y(),overlay.z(),overlay.w()});
    }
}
