// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinUploadFactsTest {
    @BeforeEach void clear(){GpuVoxelSkinUploadFacts.shutdown();}
    @Test void AlphaIsReadBeforeUploadAndPublishedOnlyAfterSuccessfulReturn(){
        Source source=new Source();BufferedImage image=image(128);int[] calls={0};
        assertEquals(5,GpuVoxelSkinUploadFacts.upload(source,5,image,()->{
            calls[0]++;assertNull(GpuVoxelSkinUploadFacts.current(source,5,0));image.setRGB(0,0,0xFFFFFFFF);return 5;
        }));
        var fact=GpuVoxelSkinUploadFacts.current(source,5,0);assertNotNull(fact);assertEquals(128,fact.alpha[0]&255);
        assertEquals(4096,fact.alpha.length);assertEquals(1,calls[0]);assertEquals(4096,GpuVoxelSkinUploadFacts.retainedAlphaBytes());
    }
    @Test void FailedOrUnknownUploadsCannotKeepAFormerOpaqueFact(){
        Source source=new Source();upload(source,5,255);assertThrows(IllegalStateException.class,()->GpuVoxelSkinUploadFacts.upload(source,5,image(255),()->{throw new IllegalStateException("controlled failure");}));
        assertNull(GpuVoxelSkinUploadFacts.current(source,5,0));assertEquals(0,GpuVoxelSkinUploadFacts.retainedTextureIds());
        int[] calls={0};GpuVoxelSkinUploadFacts.upload(new Object(),5,image(255),()->{calls[0]++;return 5;});
        assertEquals(1,calls[0]);assertEquals(0,GpuVoxelSkinUploadFacts.retainedSources());
        upload(source,5,255);GpuVoxelSkinUploadFacts.upload(source,5,new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB),()->5);assertNull(GpuVoxelSkinUploadFacts.current(source,5,0));
    }
    @Test void InitialFactsDoNotProveAnEarlierFallbackLayerContainsThosePixels(){
        Object main=new Object();Source source=new Source();GpuVoxelSkinUploadFacts.copied(main,5,0,true);upload(source,5,255);
        var fact=GpuVoxelSkinUploadFacts.current(source,5,0);assertFalse(GpuVoxelSkinUploadFacts.matches(main,0,fact));
        GpuVoxelSkinUploadFacts.copied(main,5,0,true);assertTrue(GpuVoxelSkinUploadFacts.matches(main,0,fact));
    }
    @Test void MainAndAuxiliaryMustEachHaveActuallyCopiedTheCurrentUploadRevision(){
        Source source=new Source();Object main=new Object(),auxiliary=new Object();upload(source,5,128);
        var old=GpuVoxelSkinUploadFacts.current(source,5,0);GpuVoxelSkinUploadFacts.copied(main,5,2,true);GpuVoxelSkinUploadFacts.copied(auxiliary,5,3,true);
        assertTrue(GpuVoxelSkinUploadFacts.matches(main,2,old));assertTrue(GpuVoxelSkinUploadFacts.matches(auxiliary,3,old));
        upload(source,5,255);var current=GpuVoxelSkinUploadFacts.current(source,5,0);assertNotSame(old,current);assertTrue(current.revision>old.revision);
        assertFalse(GpuVoxelSkinUploadFacts.matches(main,2,current));assertFalse(GpuVoxelSkinUploadFacts.matches(auxiliary,3,current));
        GpuVoxelSkinUploadFacts.copied(main,5,2,true);assertTrue(GpuVoxelSkinUploadFacts.matches(main,2,current));assertFalse(GpuVoxelSkinUploadFacts.matches(auxiliary,3,current));
        GpuVoxelSkinUploadFacts.copied(auxiliary,5,3,true);assertTrue(GpuVoxelSkinUploadFacts.matches(auxiliary,3,current));
        GpuVoxelSkinUploadFacts.copied(main,5,2,false);assertFalse(GpuVoxelSkinUploadFacts.matches(main,2,current));
    }
    @Test void DeletionAndGenerationChangesDeclineStaleFactsAndCopyTags(){
        Source source=new Source();Object pool=new Object();upload(source,5,255);var old=GpuVoxelSkinUploadFacts.current(source,5,0);
        GpuVoxelSkinUploadFacts.copied(pool,5,0,true);source.generation++;assertNull(GpuVoxelSkinUploadFacts.current(source,5,1));
        GpuVoxelSkinUploadFacts.copied(pool,5,0,true);assertFalse(GpuVoxelSkinUploadFacts.matches(pool,0,old));
        upload(source,7,255);assertNotNull(GpuVoxelSkinUploadFacts.current(source,7,1));assertNull(GpuVoxelSkinUploadFacts.current(source,5,0));
        GpuVoxelSkinUploadFacts.invalidate(source);assertNull(GpuVoxelSkinUploadFacts.current(source,7,1));assertEquals(0,GpuVoxelSkinUploadFacts.retainedTextureIds());
    }
    @Test void ManagerDeletionDropsOnlyItsPoolMetadataAndKeepsLiveSourceFacts(){
        Source source=new Source();Object a=new Object(),b=new Object();upload(source,5,255);GpuVoxelSkinUploadFacts.copied(a,5,0,true);GpuVoxelSkinUploadFacts.copied(b,5,0,true);
        assertEquals(2,GpuVoxelSkinUploadFacts.retainedPools());GpuVoxelSkinUploadFacts.releaseManager(new Manager(Map.of(1,a,2,b)));
        assertEquals(0,GpuVoxelSkinUploadFacts.retainedPools());assertNotNull(GpuVoxelSkinUploadFacts.current(source,5,0));
        GpuVoxelSkinUploadFacts.shutdown();assertEquals(0,GpuVoxelSkinUploadFacts.retainedSources());assertEquals(0,GpuVoxelSkinUploadFacts.retainedAlphaBytes());
    }
    @Test void AlphaAndCopyMetadataHaveIndependentHardBounds(){
        List<Source> live=new ArrayList<>();for(int i=0;i<1025;i++){Source source=new Source();live.add(source);upload(source,100+i,255);}
        assertEquals(1024,GpuVoxelSkinUploadFacts.retainedSources());assertEquals(4L*1024*1024,GpuVoxelSkinUploadFacts.retainedAlphaBytes());assertEquals(1024,GpuVoxelSkinUploadFacts.retainedTextureIds());
        assertNull(GpuVoxelSkinUploadFacts.current(live.getLast(),1124,0));
        List<Object> pools=new ArrayList<>();for(int i=0;i<17;i++){Object pool=new Object();pools.add(pool);GpuVoxelSkinUploadFacts.copied(pool,100,0,true);}
        assertEquals(16,GpuVoxelSkinUploadFacts.retainedPools());assertFalse(GpuVoxelSkinUploadFacts.matches(pools.getLast(),0,GpuVoxelSkinUploadFacts.current(live.getFirst(),100,0)));
    }
    @Test void WeakIdentityKeysDoNotMergeObjectsWithIdenticalEqualsOrHashCodes(){
        var map=new GpuVoxelSkinWeakIdentityMap<String>(2);Object a=new Equal(),b=new Equal(),c=new Equal();assertTrue(map.put(a,"a"));assertTrue(map.put(b,"b"));
        assertEquals("a",map.get(a));assertEquals("b",map.get(b));assertFalse(map.put(c,"c"));assertTrue(map.put(a,"replacement"));
        assertEquals("replacement",map.remove(a));assertTrue(map.put(c,"c"));assertEquals("b",map.get(b));assertNull(map.get(null));assertFalse(map.canAdmit(null));
        map.clear();assertEquals(0,map.size());
    }
    private static void upload(Source source,int id,int alpha){GpuVoxelSkinUploadFacts.upload(source,id,image(alpha),()->id);}
    private static BufferedImage image(int alpha){BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);int[] pixels=new int[4096];Arrays.fill(pixels,alpha<<24|0x336699);image.setRGB(0,0,64,64,pixels,0,64);return image;}
    public static final class Source{int generation;public int argentum$getGeneration(){return generation;}}
    private record Manager(Map<Integer,Object> pools){}
    private static final class Equal{@Override public boolean equals(Object ignored){return true;}@Override public int hashCode(){return 0;}}
}
