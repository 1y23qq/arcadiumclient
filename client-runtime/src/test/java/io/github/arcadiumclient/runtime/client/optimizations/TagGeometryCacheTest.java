// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TagGeometryCacheTest {
    private static int bits(float value){return Float.floatToRawIntBits(value);}
    private static int[] quad(){return new int[]{bits(1),bits(2),bits(9),bits(.1f),bits(.2f),0x40aabbcc,
            bits(3),bits(2),bits(9),bits(.3f),bits(.2f),0x40aabbcc,
            bits(3),bits(4),bits(9),bits(.3f),bits(.4f),0x40aabbcc,
            bits(1),bits(4),bits(9),bits(.1f),bits(.4f),0x40aabbcc};}

    @Test void triangulatesWithoutChangingPositionsOrUv(){
        var cache=new TagGeometryCache();int[] source=quad();var mesh=cache.mesh(source,source.length,6,5,false,false);
        assertEquals(6,mesh.vertexCount());int[] order={0,1,2,0,2,3};
        for(int i=0;i<6;i++)for(int word=0;word<6;word++)assertEquals(source[order[i]*6+word],cache.words()[i*6+word]);
    }
    @Test void textAlphaAndIgnoredZDoNotDuplicateGpuGeometry(){
        var cache=new TagGeometryCache();int[] source=quad();var first=cache.mesh(source,source.length,6,5,true,true);int size=cache.size();
        for(int i=0;i<4;i++){source[i*6+2]=bits(77);source[i*6+5]=0xffaabbcc;}
        var second=cache.mesh(source,source.length,6,5,true,true);
        assertSame(first,second);assertEquals(size,cache.size());assertEquals(0,first.quads()[2]);assertEquals(0xffaabbcc,first.quads()[5]);
    }
    @Test void mutableSourcesCannotCorruptOrReuseOldShapes(){
        var cache=new TagGeometryCache();int[] source=quad();var first=cache.mesh(source,source.length,6,5,true,true);
        source[0]=bits(31);source[5]=0xff123456;var changed=cache.mesh(source,source.length,6,5,true,true);
        assertNotSame(first,changed);assertEquals(bits(1),first.quads()[0]);assertEquals(bits(31),changed.quads()[0]);
        source=quad();assertSame(first,cache.mesh(source,source.length,6,5,true,true));
    }
    @Test void backgroundColorAndDepthRemainIndependent(){
        var cache=new TagGeometryCache();int[] source={bits(-8),bits(0),bits(.1f),0x20000000,bits(8),bits(0),bits(.1f),0x20000000,
                bits(8),bits(9),bits(.1f),0x20000000,bits(-8),bits(9),bits(.1f),0x20000000};
        var mesh=cache.mesh(source,source.length,4,3,false,false);
        assertEquals(bits(.1f),mesh.quads()[2]);assertEquals(0x20000000,mesh.quads()[5]);assertEquals(0,mesh.quads()[3]);
        source[3]=0x90000000;assertNotSame(mesh,cache.mesh(source,source.length,4,3,false,false));
    }
    @Test void rejectsIncompleteQuads(){var cache=new TagGeometryCache();assertThrows(IllegalArgumentException.class,()->cache.mesh(new int[6],6,6,5,true,true));}

    @Test void repeatedMutableQuadsWithCollidingPolynomialHashesRemainDistinct(){
        var cache=new TagGeometryCache();int[][] shapes=new int[256][];var meshes=new TagGeometryCache.Mesh[256];int[] shared=quad();
        for(int i=0;i<shapes.length;i++){
            shapes[i]=quad();shapes[i][0]+=i;shapes[i][1]-=31*i;
            assertEquals(java.util.Arrays.hashCode(shapes[0]),java.util.Arrays.hashCode(shapes[i]));
            System.arraycopy(shapes[i],0,shared,0,shared.length);meshes[i]=cache.mesh(shared,shared.length,6,5,false,false);
            assertArrayEquals(shapes[i],meshes[i].quads());
            if(i>0)assertNotEquals(meshes[i-1].firstVertex(),meshes[i].firstVertex());
        }
        for(int round=0;round<4;round++)for(int i=0;i<shapes.length;i++){
            System.arraycopy(shapes[i],0,shared,0,shared.length);
            assertSame(meshes[i],cache.mesh(shared,shared.length,6,5,false,false));
        }
        assertEquals(shapes.length,cache.misses);
    }
}
