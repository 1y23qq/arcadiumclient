// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ActorSceneDataTest {
    private static void observe(ActorSceneData data,long tick){assertTrue(data.observe(0,7,tick,.5f,170,-170,-170,170,10,30,7,2,.5f,.9f,.1f));}
    private static boolean capture(ActorSceneData data,int job){return data.capture(0,7,job,new Matrix4f().translation(3,4,5),6,1,20.5f,-360,20,1,1,3,true,false,true,192,5);}
    @Test void sealedNativeEndpointsFeedExactFramePacketAndSeparateGpuInput(){
        var data=new ActorSceneData(4);data.begin(1);observe(data,20);assertTrue(capture(data,0));assertFalse(data.gpuReady(0));assertTrue(data.material(0,9));var batch=data.seal();
        var words=FloatBuffer.allocate(32);batch.cpu(0,words,0);assertEquals(3,words.get(12));assertEquals(6,words.get(16));assertEquals(-360,words.get(19));assertEquals(-1,words.get(29));assertEquals(0,words.get(30));
        var input=FloatBuffer.allocate(27);batch.input(0,input);assertEquals(27,input.position());assertEquals(.5f,input.get(16));assertEquals(0,input.get(17));assertEquals(0,input.get(18));assertEquals(9,input.get(19));assertEquals(192,input.get(20));assertEquals(1|2|5<<3,input.get(21));assertEquals(1|(3<<8),input.get(22));
    }
    @Test void tickReuseAndSameTickMutationPublishImmutableHistoryOnlyWhenNecessary(){
        var data=new ActorSceneData(2);data.begin(4);observe(data,20);var first=data.current(0);observe(data,20);assertSame(first,data.current(0));
        assertTrue(data.observe(0,7,20,.5f,170,-170,-170,170,11,30,7,2,.5f,.9f,.1f));assertNotSame(first,data.current(0));assertEquals(10,first.word(4));assertNull(data.previous(0));observe(data,21);assertNotNull(data.previous(0));assertTrue(data.current(0).revision>first.revision);
    }
    @Test void gpuOnlyCapturePerformsNoCpuPoseInterpolationUntilExactRecoveryIsRequested(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);assertTrue(data.captureGpuOnly(0,7,0,new Matrix4f().translation(3,4,5),.1234f,1,3,true,false,true,192,5,-0.0f,1.25f));assertTrue(data.material(0,1));var batch=data.seal();
        var input=FloatBuffer.allocate(27);batch.input(0,input);assertEquals(.1234f,input.get(26));assertEquals(0,data.captureValidationRows);assertEquals(0,data.cpuInterpolationRows);
        var exact=FloatBuffer.allocate(32);batch.cpu(0,exact,0);assertEquals(1,data.cpuInterpolationRows);assertEquals(6,exact.get(16));assertEquals(1,exact.get(17));assertEquals(20.5f,exact.get(18));assertEquals(-360,exact.get(19));assertEquals(20,exact.get(20));assertEquals(.1234f,exact.get(21));
        assertEquals(Float.floatToRawIntBits(-0.0f),Float.floatToRawIntBits(exact.get(30)));assertEquals(1.25f,exact.get(31));
    }
    @Test void nativeCallbackScalarIsRetainedExactlyRatherThanReinterpolated(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);assertTrue(data.capture(0,7,0,new Matrix4f(),6,1,20.5f,-360,20,.37f,1,3,true,false,true,192,5));var exact=FloatBuffer.allocate(32);data.seal().cpu(0,exact,0);assertEquals(.37f,exact.get(21));
    }
    @Test void capturedTickIsUnchangedWhenLaterTickInputArrivesInTheSameBatch(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);assertTrue(capture(data,0));var captured=data.current(0);observe(data,21);var batch=data.seal();assertSame(captured,batch.tick(0));assertEquals(20,batch.tick(0).tick);assertEquals(21,data.current(0).tick);
    }
    @Test void nativeConsumerMismatchAndUnobservedBatchKeepOriginalStreaming(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);assertFalse(data.capture(0,7,0,new Matrix4f(),6,1,20.5f,-360,21,1,1,3,true,false,true,192,5));data.begin(2);assertFalse(capture(data,0));
    }
    @Test void invalidMutationCannotReuseAnEarlierSameBatchObservation(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);assertFalse(data.observe(0,7,20,.5f,Float.NaN,0,0,0,0,0,0,0,0,0,0));assertFalse(capture(data,0));
    }
    @Test void denseMovesAndResourceEpochsDoNotAliasAnOldOwner(){
        var data=new ActorSceneData(3);data.begin(1);observe(data,20);var tick=data.current(0);data.move(0,1);assertSame(tick,data.current(1));assertNull(data.current(0));data.retain(1,8);assertNull(data.current(1));assertNull(data.previous(1));long epoch=data.epoch();data.clear();assertTrue(data.epoch()>epoch);observe(data,22);assertTrue(data.current(0).revision>tick.revision);
    }
    @Test void variantSharesTickButOwnsItsTargetTemplate(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);assertTrue(capture(data,0));assertTrue(data.material(0,4));assertTrue(data.variant(0,1,384));var batch=data.seal();assertSame(batch.tick(0),batch.tick(1));var a=FloatBuffer.allocate(32);var b=FloatBuffer.allocate(32);batch.cpu(0,a,0);batch.cpu(1,b,0);assertEquals(192,a.get(27));assertEquals(384,b.get(27));
    }
    @Test void sealedContentsCannotBeChangedByLateCaptureMaterialOrVariant(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);capture(data,0);data.material(0,8);var batch=data.seal();assertFalse(capture(data,0));assertFalse(data.material(0,0));assertFalse(data.variant(0,1,384));
        data.discard(0);assertFalse(data.active(0));var input=FloatBuffer.allocate(27);batch.input(0,input);assertEquals(8,input.get(19));
    }
    @Test void expiredBatchViewsRefuseToReadReusedArenaMemory(){
        var data=new ActorSceneData(2);data.begin(1);observe(data,20);capture(data,0);var batch=data.seal();data.begin(2);assertThrows(IllegalStateException.class,()->batch.cpu(0,FloatBuffer.allocate(32),0));assertThrows(IllegalStateException.class,()->batch.input(0,FloatBuffer.allocate(27)));
    }
    @Test void reservedWordsAndSignedZeroKeepTheNativeAbi(){
        var data=new ActorSceneData(2);data.begin(1);assertTrue(data.observe(0,1,2,.5f,0,0,0,0,0,0,-0.0f,0,0,0,0));assertTrue(data.capture(0,1,0,new Matrix4f(),-0.0f,0,2.5f,0,0,0,0,0,false,false,false,0,0,-0.0f,1.25f));
        var exact=FloatBuffer.allocate(32);data.seal().cpu(0,exact,0);assertEquals(Float.floatToRawIntBits(-0.0f),Float.floatToRawIntBits(exact.get(16)));assertEquals(Float.floatToRawIntBits(-0.0f),Float.floatToRawIntBits(exact.get(30)));assertEquals(1.25f,exact.get(31));
    }
    @Test void unknownNanAndLargeInputsRemainOnLegacyStream(){
        assertThrows(IllegalArgumentException.class,()->new ActorSceneData(8193));var data=new ActorSceneData(1);data.begin(1);assertFalse(data.observe(0,ActorSceneData.MAX_GPU_ID+1L,1,.5f,0,0,0,0,0,0,0,0,0,0,0));assertFalse(data.observe(0,1,1,2,0,0,0,0,0,0,0,0,0,0,0));assertFalse(data.observe(0,1,1,.5f,0,5000,0,0,0,0,0,0,0,0,0));observe(data,20);assertFalse(data.captureGpuOnly(0,7,0,new Matrix4f(),Float.NaN,0,0,false,false,false,0,0,0,0));assertFalse(data.captureGpuOnly(0,7,0,new Matrix4f(),0,0,0,false,false,false,0,0,Float.intBitsToFloat(0x7fa12345),0));
    }
    @Test void acceptedGpuOnlyBatchesReuseOneBoundedArenaWithoutPerActorFrameAllocations(){
        int actors=128,batches=100;var data=new ActorSceneData(actors);var root=new Matrix4f();
        for(int batch=1;batch<=batches;batch++){data.begin(batch);for(int actor=0;actor<actors;actor++){assertTrue(data.observe(actor,actor+1,20,.5f,0,0,0,0,0,0,0,0,0,0,0));assertTrue(data.captureGpuOnly(actor,actor+1,actor,root,0,0,0,false,false,false,0,0,0,0));assertTrue(data.material(actor,1));}var view=data.seal();assertSame(view,data.seal());}
        assertEquals(actors*batches,data.gpuOnlyCaptures);assertEquals(actors,data.tickWrites);assertEquals(0,data.cpuInterpolationRows);assertEquals(0,data.captureValidationRows);assertEquals(batches,data.sealedViews);assertEquals(0,ActorSceneData.PER_ACTOR_FRAME_ARRAY_ALLOCATIONS);assertEquals(0,ActorSceneData.PER_ACTOR_FRAME_OBJECT_ALLOCATIONS);
    }
}
