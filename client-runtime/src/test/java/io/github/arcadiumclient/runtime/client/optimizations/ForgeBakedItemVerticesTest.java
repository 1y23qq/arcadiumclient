// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.client.render.model.block.BakedQuad;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

class ForgeBakedItemVerticesTest {
    private static final class LazyQuad extends BakedQuad {
        int calls;final boolean separate;
        LazyQuad(int words,boolean separate){super(new int[words],-1,Direction.UP);this.separate=separate;}
        @Override public int[] getVertices(){calls++;if(separate)return new int[28];int[] data=super.getVertices();data[0]=Float.floatToRawIntBits(1);return data;}
    }
    @Test void outsideColdItemScopeLeavesGetterAndDataAlone(){
        var quad=new LazyQuad(28,false);assertSame(quad,ForgeBakedItemVertices.consume(quad));assertEquals(0,quad.calls);
    }
    @Test void exactVanillaQuadIsNotRepacked(){
        int[] data=new int[28];data[0]=Float.floatToRawIntBits(.25f);var quad=new BakedQuad(data,-1,Direction.UP);long calls=ForgeBakedItemVertices.materializedQuads;int token=ForgeBakedItemVertices.begin();
        try{assertSame(quad,ForgeBakedItemVertices.consume(quad));assertSame(data,quad.getVertices());assertEquals(Float.floatToRawIntBits(.25f),data[0]);assertEquals(calls,ForgeBakedItemVertices.materializedQuads);}finally{ForgeBakedItemVertices.end(token);}
    }
    @Test void officialMaterializerCallsVirtualGetterOnceWithoutCopying(){
        var quad=new LazyQuad(28,false);int[] backing=raw(quad);ForgeBakedItemVertices.materialize(quad,DefaultVertexFormat.BLOCK_NORMALS);assertEquals(1,quad.calls);assertSame(backing,raw(quad));assertEquals(Float.floatToRawIntBits(1),backing[0]);
    }
    @Test void rejectsNonCanonicalVertexLayoutBeforeGetter(){
        var quad=new LazyQuad(28,false);var format=new VertexFormat().addElement(DefaultVertexFormat.POSITION_ELEMENT);assertThrows(ForgeBakedItemVertices.Rejected.class,()->ForgeBakedItemVertices.materialize(quad,format));assertEquals(0,quad.calls);
    }
    @Test void rejectsWrongBackingSizeBeforeGetter(){
        var quad=new LazyQuad(24,false);assertThrows(ForgeBakedItemVertices.Rejected.class,()->ForgeBakedItemVertices.materialize(quad,DefaultVertexFormat.BLOCK_NORMALS));assertEquals(0,quad.calls);
    }
    @Test void rejectsGetterThatReturnsAnotherArray(){
        var quad=new LazyQuad(28,true);assertThrows(ForgeBakedItemVertices.Rejected.class,()->ForgeBakedItemVertices.materialize(quad,DefaultVertexFormat.BLOCK_NORMALS));assertEquals(1,quad.calls);assertEquals(0,raw(quad)[0]);
    }
    @Test void unknownSubclassRequestsNativeFallbackWithoutCallingItsGetter(){
        var quad=new LazyQuad(28,true);int token=ForgeBakedItemVertices.begin();try{assertThrows(ForgeBakedItemVertices.Rejected.class,()->ForgeBakedItemVertices.consume(quad));assertEquals(0,quad.calls);}finally{ForgeBakedItemVertices.end(token);}assertSame(quad,ForgeBakedItemVertices.consume(quad));
    }
    @Test void rejectionCacheUsesIdentityAndClearsOnOwnerDisposal(){
        var cache=new ForgeBakedItemVertices.Rejections();Object first=new String("equal"),second=new String("equal");cache.add(first);assertTrue(cache.contains(first));assertFalse(cache.contains(second));cache.clear();assertFalse(cache.contains(first));assertEquals(0,cache.size());
    }
    @Test void rejectionCacheIsBounded(){
        var cache=new ForgeBakedItemVertices.Rejections();Object[] models=new Object[160];for(int i=0;i<models.length;i++){models[i]=new Object();cache.add(models[i]);}assertEquals(128,cache.size());assertTrue(cache.contains(models[159]));assertFalse(cache.contains(models[0]));
    }
    @Test void nestedColdScopesAreBoundedAndRestoreAfterRejection(){
        int[] tokens=new int[8];for(int i=0;i<tokens.length;i++)tokens[i]=ForgeBakedItemVertices.begin();try{assertThrows(ForgeBakedItemVertices.Rejected.class,ForgeBakedItemVertices::begin);}finally{for(int i=tokens.length-1;i>=0;i--)ForgeBakedItemVertices.end(tokens[i]);}assertSame("outside",ForgeBakedItemVertices.consume("outside"));
    }
    private static int[] raw(BakedQuad quad){try{for(String name:new String[]{"vertices","f_40952693"})try{var field=BakedQuad.class.getDeclaredField(name);field.setAccessible(true);return (int[])field.get(quad);}catch(NoSuchFieldException next){}throw new NoSuchFieldException();}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
}
