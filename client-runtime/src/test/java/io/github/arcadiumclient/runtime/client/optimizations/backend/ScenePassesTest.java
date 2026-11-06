// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScenePassesTest {
    private static ScenePasses.Pass pass(int pipeline,int first,int offset,int loads,int stores) {
        return new ScenePasses.Pass(pipeline,first,1,offset,80,loads,stores,ScenePasses.SCISSOR,
                0,0,64,48,2,3,20,30,0,.5f,1,1,1,0,1);
    }
    private static ByteBuffer packed() {
        ByteBuffer bytes=ByteBuffer.allocateDirect(224).order(ByteOrder.BIG_ENDIAN);bytes.position(8).limit(200);
        ScenePasses.write(bytes,List.of(pass(1,0,0,5,3),pass(2,1,256,0,3)));return bytes;
    }
    @Test void wireMatchesTheNativeFixedLayoutAndPreservesTheCallerWindow() {
        ByteBuffer bytes=packed(),view=bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(8,bytes.position());assertEquals(200,bytes.limit());assertEquals(ByteOrder.BIG_ENDIAN,bytes.order());
        assertEquals(1,view.getInt(8));assertEquals(80,view.getInt(24));assertEquals(5,view.getInt(28));
        assertEquals(0,view.getInt(100));assertEquals(2,view.getInt(104));assertEquals(256,view.getInt(116));
        assertEquals(Float.floatToRawIntBits(.5f),view.getInt(76));
        assertEquals(3,ScenePasses.validate(bytes,2,2,64,48,512,0));
    }
    @Test void undefinedAttachmentsAndOmittedCommandsFailBeforeNativeExecution() {
        ByteBuffer bytes=packed(),v=bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        v.putInt(28,0);assertThrows(IllegalArgumentException.class,()->ScenePasses.validate(bytes,2,2,64,48,512,0));
        assertEquals(3,ScenePasses.validate(bytes,2,2,64,48,512,3));
        v.putInt(28,5).putInt(108,0);assertThrows(IllegalArgumentException.class,()->ScenePasses.validate(bytes,2,2,64,48,512,0));
        v.putInt(108,1).putInt(32,1);assertThrows(IllegalArgumentException.class,()->ScenePasses.validate(bytes,2,2,64,48,512,0));
    }
    @Test void malformedNativeBuffersCannotSmuggleOverflowOrUnknownState() {
        int[] offsets={116,152,196,28,188};int[] invalid={4,Integer.MAX_VALUE,1,15,Float.floatToRawIntBits(Float.NaN)};
        for(int i=0;i<offsets.length;i++) {
            ByteBuffer bytes=packed();bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN).putInt(offsets[i],invalid[i]);
            assertThrows(IllegalArgumentException.class,()->ScenePasses.validate(bytes,2,2,64,48,512,0));
        }
        assertThrows(IllegalArgumentException.class,()->ScenePasses.validate(packed(),65,2,64,48,512,0));
        assertThrows(IllegalArgumentException.class,()->ScenePasses.validate(packed(),2,2,64,48,257,0));
    }
    @Test void packingInvalidDestinationsDoesNotPartiallyOverwriteThem() {
        ByteBuffer bytes=ByteBuffer.allocateDirect(95);for(int i=0;i<95;i++)bytes.put(i,(byte)0x5a);
        assertThrows(IllegalArgumentException.class,()->ScenePasses.write(bytes,List.of(pass(1,0,0,5,3))));
        for(int i=0;i<95;i++)assertEquals((byte)0x5a,bytes.get(i));
        assertThrows(NullPointerException.class,()->ScenePasses.write(bytes,Arrays.asList((ScenePasses.Pass)null)));
        assertThrows(IllegalArgumentException.class,()->ScenePasses.write(ByteBuffer.allocateDirect(96).asReadOnlyBuffer(),List.of(pass(1,0,0,5,3))));
    }
    @Test void pipelineWireAndUnsupportedDepthOrAlphaStatesAreExplicit() {
        var state=new ScenePasses.Pipeline(3,3,2,0,1,0,1,0,0,0,15);
        ByteBuffer bytes=ByteBuffer.allocateDirect(56);bytes.position(4);state.write(bytes);
        assertEquals(4,bytes.position());var view=bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(3,view.getInt(4));assertEquals(15,view.getInt(44));assertEquals(0,view.getInt(48));
        assertThrows(IllegalArgumentException.class,()->new ScenePasses.Pipeline(2,3,0,0,1,0,1,0,0,0,15));
        assertThrows(IllegalArgumentException.class,()->new ScenePasses.Pipeline(4,3,0,0,1,0,2,0,0,0,15));
    }
    @Test void anUnalignedDirectSliceIsRejectedEvenWhenItsPositionIsZero() {
        ByteBuffer owner=ByteBuffer.allocateDirect(193);for(int i=0;i<193;i++)owner.put(i,(byte)0x5a);
        owner.position(1);ByteBuffer unaligned=owner.slice();
        assertEquals(0,unaligned.position());assertEquals(1,unaligned.alignmentOffset(0,4));
        assertThrows(IllegalArgumentException.class,()->ScenePasses.write(unaligned,List.of(pass(1,0,0,5,3))));
        for(int i=0;i<193;i++)assertEquals((byte)0x5a,owner.get(i));
        unaligned.put(packed().duplicate());unaligned.position(0);
        assertThrows(IllegalArgumentException.class,()->ScenePasses.validate(unaligned,2,2,64,48,512,0));
    }
}
