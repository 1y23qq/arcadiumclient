// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.CompactChunkVertex;

final class TerrainResidentPassIdentity {
    private TerrainResidentPassIdentity(){}
    static boolean accepts(TerrainRenderPass pass,RenderPassConfiguration<?> configuration){
        if(pass==null||configuration==null||pass.getClass()!=TerrainRenderPass.class||pass.isSorted()||pass.isReverseOrder()||pass.hasNoLightmap()
                ||pass.primitiveType()!=QuadPrimitiveType.TRIANGULATED||pass.vertexType()==null||pass.vertexType().getClass()!=CompactChunkVertex.class)return false;
        return pass.name().equals("solid")&&configuration.defaultSolidMaterial()!=null&&configuration.defaultSolidMaterial().pass==pass&&!pass.supportsFragmentDiscard()
                ||pass.name().equals("cutout_mipped")&&configuration.defaultCutoutMippedMaterial()!=null&&configuration.defaultCutoutMippedMaterial().pass==pass&&pass.supportsFragmentDiscard();
    }
}
