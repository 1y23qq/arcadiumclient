// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkinInstancingTest {
    @Test void onlyAttachedVoxelPartsUseTheMeshPath() {
        var part=new MeshPart();assertFalse(SkinLayersBridge.hasMesh(part));part.skinLayers$mesh=new Object();assertTrue(SkinLayersBridge.hasMesh(part));
        assertFalse(SkinLayersBridge.hasMesh(new Object()));part.skinLayers$mesh=null;assertFalse(SkinLayersBridge.hasMesh(part));
    }
    private static final class MeshPart { private Object skinLayers$mesh; }
}
