// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

final class TerrainResidentRegionData {
    final int[] words=new int[TerrainResidentFrustumPlan.SECTIONS*TerrainResidentFrustumPlan.WORDS];
    final long version;
    final int maximumElements,sectionsWithGeometry;
    TerrainResidentRegionData(int[] source,long version){
        if(source.length!=words.length||version<=0)throw new IllegalArgumentException("Invalid resident metadata snapshot");
        System.arraycopy(source,0,words,0,source.length);this.version=version;int maximum=0,populated=0;
        for(int section=0;section<256;section++){
            int elements=0;
            for(int face=0;face<7;face++){int first=words[section*20+4+face],count=words[section*20+12+face];if(first<0||count<0||count%6!=0)throw new IllegalArgumentException("Invalid resident quad span");elements=Math.addExact(elements,count);}
            if(elements!=0)populated++;maximum=Math.max(maximum,elements);
        }
        maximumElements=maximum;sectionsWithGeometry=populated;
    }
    TerrainResidentRegionData(RenderRegion region,SectionRenderDataStorage storage,long version){
        if(version<=0||RenderRegion.REGION_SIZE!=256||storage.getPrimitiveType().getVerticesPerPrimitive()!=4
                ||storage.getPrimitiveType().getIndexBufferElementsPerPrimitive()!=6)throw new IllegalArgumentException("Unsupported resident region metadata");
        this.version=version;int maximum=0,populated=0;
        for(int section=0;section<256;section++){
            int row=section*20;words[row]=region.getOriginX()+LocalSectionIndex.unpackX(section)*16;
            words[row+1]=region.getOriginY()+LocalSectionIndex.unpackY(section)*16;words[row+2]=region.getOriginZ()+LocalSectionIndex.unpackZ(section)*16;
            int mask=storage.getSliceMask(section);if(mask==0)continue;populated++;long pointer=storage.getDataPointer(section);int elements=0;
            for(int face=0;face<7;face++){
                if((mask&1<<face)==0)continue;
                int first=SectionRenderDataUnsafe.Strategy.COMPACT.getVertexOffset(pointer,face),end=SectionRenderDataUnsafe.Strategy.COMPACT.getRunVertexEnd(pointer,face,storage.getPrimitiveType());
                int count=SectionRenderDataUnsafe.elementsForVertices(end-first,storage.getPrimitiveType());
                if(first<0||end<first||count<0||count%6!=0)throw new IllegalArgumentException("Invalid native resident quad span");
                words[row+4+face]=first;words[row+12+face]=count;elements=Math.addExact(elements,count);
            }
            maximum=Math.max(maximum,elements);
        }
        maximumElements=maximum;sectionsWithGeometry=populated;
    }
}
