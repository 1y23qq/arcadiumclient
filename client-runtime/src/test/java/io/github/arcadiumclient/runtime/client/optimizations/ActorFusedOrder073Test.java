// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ActorFusedOrder073Test {
    private static IntBuffer markers(int... jobs){
        var words=IntBuffer.allocate(jobs.length*19);
        for(int i=0;i<jobs.length;i++)words.put(i*19,Float.floatToRawIntBits(-(jobs[i]*16+1f)))
            .put(i*19+1,Float.floatToRawIntBits(-9876543f)).put(i*19+2,Float.floatToRawIntBits(-8765432f));
        return words;
    }

    @Test void orderedRepeatedAndUnorderedSparseMarkersKeepExactSelection(){
        var selected=new ActorFusedRows();int[] repeated=new int[64*12];
        for(int i=0;i<repeated.length;i++)repeated[i]=i/12;
        var payload=markers(repeated);assertTrue(selected.collect(payload,64));
        assertEquals(64,selected.count());for(int i=0;i<64;i++)assertEquals(i,selected.at(i));
        assertEquals(0,payload.position());assertEquals(repeated.length*19,payload.limit());
        assertTrue(selected.collect(markers(8191,3,3,0,100),8192));
        assertEquals(4,selected.count());for(int i=0;i<4;i++)assertEquals(new int[]{0,3,100,8191}[i],selected.at(i));
        assertFalse(selected.collect(markers(64),64));assertFalse(selected.collect(IntBuffer.allocate(18),64));
        assertTrue(selected.collect(markers(3,0,3),4));assertEquals(2,selected.count());assertEquals(0,selected.at(0));assertEquals(3,selected.at(1));
    }

    @Test void outOfOrderActorSlotsKeepCorrectTickAddressesAndUploadRanges(){
        var scene=new ActorSceneData(4);scene.begin(1);
        for(int slot:new int[]{0,2})assertTrue(scene.observe(slot,slot+1,100+slot,.5f,0,1,2,3,4,5,6,.2f,.1f,0,0));
        assertTrue(scene.captureGpuOnly(2,3,0,new Matrix4f(),0,0,0,false,false,false,0,0,0,0));
        assertTrue(scene.captureGpuOnly(0,1,1,new Matrix4f(),0,0,0,false,false,false,0,0,0,0));
        var selected=new ActorFusedRows();assertTrue(selected.collect(markers(1,0,1),2));
        var view=new ActorFusedInput().prepare(new Object(),scene.seal().raw(),FloatBuffer.allocate(64),2,selected);
        assertNotNull(view);assertEquals(2,view.taggedRows);
        assertEquals(view.capacity*36+2*16,view.word(view.metadataBase+1));
        assertEquals(view.capacity*36,view.word(view.metadataBase+5));
        int end=0;for(int i=0;i<view.ranges();i++){assertTrue(view.start(i)>=end);end=view.start(i)+view.length(i);}
    }
}
