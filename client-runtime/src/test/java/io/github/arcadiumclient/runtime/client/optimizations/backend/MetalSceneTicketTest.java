// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class MetalSceneTicketTest {
    private static final class Fixture implements AutoCloseable {
        final ArrayList<String> events=new ArrayList<>();final Set<Long> claimed=new HashSet<>();
        boolean main,sceneSupported=true,attached=true,throwPrepare,failDrop,gpuFailed;int prepareResult,publishResult,hideResult;long prepared,submitted,completed,displayed;
        MemorySegment observed;int submits,prepares;long next=100;
        Object reserved;
        final Map<Long,Integer> handles=new HashMap<>();final Map<Long,Long> frames=new HashMap<>();
        final MetalDeviceLease device=new MetalDeviceLease(new MetalDeviceLease.Calls() {
            @Override public void ownerLive(){}
            @Override public MetalDeviceLease.Acquisition acquire(int kind,long frame){long token=++next;handles.put(token,kind);frames.put(token,frame);return new MetalDeviceLease.Acquisition(0,token);}
            @Override public MetalDeviceLease.InfoResult info(long token){int kind=handles.get(token);return new MetalDeviceLease.InfoResult(0,new MetalDeviceLease.Info(kind==1?0:frames.get(token),kind==1?0:2,kind==1?0:192,kind==1?0:96,11,12,kind==1?0:13));}
            @Override public int release(long token){
                int kind=handles.get(token);String name=kind==1?"retire-device":claimed.contains(token)?"drop-target":"retire-target";events.add(name);
                if(kind==2&&failDrop){failDrop=false;return -3;}handles.remove(token);return 0;
            }
        });
        final CurrentFrameCalls calls=new CurrentFrameCalls() {
            @Override public boolean supportsSwapInterval(){return true;}
            @Override public boolean supportsSceneInput(){return sceneSupported;}
            @Override public boolean reserveSceneOwner(Object owner){if(reserved!=null)return false;reserved=owner;return true;}
            @Override public void releaseSceneOwner(Object owner){assertSame(reserved,owner);reserved=null;}
            @Override public int prepareScene(long token,MemorySegment frame){
                assertFalse(main);events.add("prepare-scene");prepares++;observed=frame;
                assertEquals(32,frame.byteSize());assertEquals(frames.get(token).longValue(),frame.get(JAVA_LONG,0));assertEquals(192,frame.get(JAVA_INT,8));
                assertEquals(96,frame.get(JAVA_INT,12));assertEquals(0,frame.get(JAVA_INT,16));assertEquals(0,frame.get(JAVA_INT,20));assertEquals(1,frame.get(JAVA_INT,24));assertEquals(0,frame.get(JAVA_INT,28));
                if(prepareResult==0){claimed.add(token);prepared=frame.get(JAVA_LONG,0);}
                if(throwPrepare)throw new IllegalStateException("Unknown native acknowledgement");return prepareResult;
            }
            @Override public int create(long window){throw new AssertionError("Window creation is outside this API");}
            @Override public int setSwapInterval(int interval){throw new AssertionError("Pacing mutation is outside this API");}
            @Override public int prepare(MemorySegment frame){throw new AssertionError("No GL backbuffer source is permitted");}
            @Override public int publish(long id){assertTrue(main);events.add("publish-"+id);if(publishResult==0)submitted=id;return publishResult;}
            @Override public int hide(){assertTrue(main);events.add("hide");return hideResult;}
            @Override public int releaseGl(){throw new AssertionError("GL release is outside this API");}
            @Override public int destroy(){throw new AssertionError("Window destruction is outside this API");}
            @Override public boolean attached(){return attached;}
            @Override public String error(){return "Owned rejection";}
            @Override public long query(int key){return key==1?prepared:key==2?submitted:key==3?completed:key==4?displayed:key==84&&gpuFailed?1:0;}
        };
        final MetalSceneTicket ticket=new MetalSceneTicket(calls,op->{assertFalse(main);main=true;try{return op.getAsInt();}finally{main=false;}});
        Fixture(){device.open();}
        MetalDeviceLease.Target target(long frame) {
            var target=device.target(frame);target.createResource((t,w,h)->(AutoCloseable)()->events.add("close-brt"));
            target.createResource((t,w,h)->(AutoCloseable)()->events.add("close-surface"));return target;
        }
        void submit(){events.add("submit-external");submits++;}
        @Override public void close(){ticket.close();device.close();assertTrue(handles.isEmpty());}
    }
    @Test void submissionChildrenClaimHandleDropAndMainPublishHaveExactOrder() {
        try(Fixture f=new Fixture()) {
            assertTrue(f.ticket.prepare(f.target(7),f::submit));assertTrue(f.ticket.publish());
            assertEquals(List.of("submit-external","close-surface","close-brt","prepare-scene","drop-target","publish-7"),f.events);
            f.completed=7;f.displayed=6;assertTrue(f.ticket.completed());assertFalse(f.ticket.displayed());
            f.displayed=7;assertTrue(f.ticket.displayed());
        }
    }
    @Test void missingOptionalApiDoesNotRunCallbacksOrConsumeTheTarget() {
        try(Fixture f=new Fixture()) {
            var target=f.target(7);f.sceneSupported=false;assertFalse(f.ticket.prepare(target,f::submit));
            assertEquals(0,f.submits);assertTrue(f.events.isEmpty());assertTrue(f.device.blocksBackend());
            target.close();assertTrue(f.events.contains("retire-target"));
        }
    }
    @Test void rejectedPrepareCanRetryWithoutRepeatingExternalSubmissionOrChildClose() {
        try(Fixture f=new Fixture()) {
            var target=f.target(7);f.prepareResult=2;assertFalse(f.ticket.prepare(target,f::submit));
            assertFalse(f.events.contains("drop-target"));f.prepareResult=0;assertTrue(f.ticket.prepare(target,f::submit));
            assertEquals(1,f.submits);assertEquals(2,f.prepares);assertEquals(1,Collections.frequency(f.events,"close-surface"));assertTrue(f.ticket.publish());
        }
    }
    @Test void failedOriginalHandleDropRetainsPreparedTicketAndPreventsEarlyPublish() {
        try(Fixture f=new Fixture()) {
            var target=f.target(7);f.failDrop=true;assertThrows(IllegalStateException.class,()->f.ticket.prepare(target,f::submit));
            assertThrows(IllegalStateException.class,f.ticket::publish);assertTrue(f.device.blocksBackend());
            assertTrue(f.ticket.prepare(target,f::submit));assertEquals(1,f.submits);assertEquals(1,f.prepares);assertTrue(f.ticket.publish());
        }
    }
    @Test void uncertainNativeAcknowledgementMustCancelBeforeFurtherWork() {
        try(Fixture f=new Fixture()) {
            var target=f.target(7);f.throwPrepare=true;assertThrows(IllegalStateException.class,()->f.ticket.prepare(target,f::submit));
            assertThrows(IllegalStateException.class,()->f.ticket.prepare(target,f::submit));f.ticket.cancel();
            assertTrue(f.events.indexOf("hide")<f.events.indexOf("drop-target"));assertFalse(f.device.blocksBackend());
        }
    }
    @Test void failedHideRetainsSourceAndArenaForExplicitCleanupRetry() {
        Fixture f=new Fixture();assertTrue(f.ticket.prepare(f.target(7),f::submit));f.hideResult=-3;
        assertThrows(IllegalStateException.class,f.ticket::close);assertTrue(f.observed.scope().isAlive());
        f.hideResult=0;f.close();assertFalse(f.observed.scope().isAlive());
    }
    @Test void throwingSubmissionIsNotImplicitlyReplayedAndResourcesRemainOwned() {
        try(Fixture f=new Fixture()) {
            var target=f.target(7);assertThrows(IllegalStateException.class,()->f.ticket.prepare(target,()->{f.submit();throw new IllegalStateException("submit");}));
            assertEquals(0,f.prepares);assertTrue(f.device.blocksBackend());f.ticket.cancel();assertEquals(1,f.submits);assertFalse(f.device.blocksBackend());
        }
    }
    @Test void staleFramesAndReentrantOperationsFailBeforeAnotherNativeClaim() {
        try(Fixture f=new Fixture()) {
            assertTrue(f.ticket.prepare(f.target(7),()->{assertThrows(IllegalStateException.class,f.ticket::cancel);f.submit();}));assertTrue(f.ticket.publish());
            var stale=f.target(7);assertThrows(IllegalArgumentException.class,()->f.ticket.prepare(stale,f::submit));assertEquals(1,f.prepares);stale.close();
        }
    }
    @Test void aWrongThreadCannotPreparePublishOrCancel() throws Exception {
        try(Fixture f=new Fixture()) {
            var target=f.target(7);AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread worker=new Thread(()->{try{assertThrows(IllegalStateException.class,()->f.ticket.prepare(target,f::submit));assertThrows(IllegalStateException.class,f.ticket::publish);assertThrows(IllegalStateException.class,f.ticket::cancel);}catch(Throwable e){failure.set(e);}});
            worker.start();worker.join(3000);assertFalse(worker.isAlive());assertNull(failure.get());assertEquals(0,f.submits);assertTrue(f.events.isEmpty());target.close();
        }
    }
    @Test void oneBridgeCannotHaveTwoCompetingTicketOwners() {
        try(Fixture f=new Fixture()) {
            assertThrows(IllegalStateException.class,()->new MetalSceneTicket(f.calls,op->op.getAsInt()));
            f.ticket.close();try(var second=new MetalSceneTicket(f.calls,op->{f.main=true;try{return op.getAsInt();}finally{f.main=false;}})){assertFalse(second.displayed());}
            assertThrows(NullPointerException.class,()->new MetalSceneTicket(null,op->op.getAsInt()));
        }
    }
    @Test void rejectedPublishDoesNotBecomeDisplayedAndRequiresExplicitCancellation() {
        try(Fixture f=new Fixture()) {
            assertTrue(f.ticket.prepare(f.target(7),f::submit));f.publishResult=-3;assertFalse(f.ticket.publish());
            f.completed=f.displayed=7;assertFalse(f.ticket.completed());assertFalse(f.ticket.displayed());
            var next=f.target(8);assertThrows(IllegalStateException.class,()->f.ticket.prepare(next,f::submit));next.close();f.ticket.cancel();
        }
    }
    @Test void gpuFailureCannotBeConvertedIntoAQualifiedCompletedOrDisplayedTicket() {
        try(Fixture f=new Fixture()) {
            assertTrue(f.ticket.prepare(f.target(7),f::submit));assertTrue(f.ticket.publish());f.completed=f.displayed=7;f.gpuFailed=true;
            assertFalse(f.ticket.completed());assertFalse(f.ticket.displayed());f.gpuFailed=false;assertTrue(f.ticket.displayed());
        }
    }
    @Test void anOlderDisplayEventCannotQualifyAfterTheNativePreparedSourceAdvanced() {
        try(Fixture f=new Fixture()) {
            assertTrue(f.ticket.prepare(f.target(7),f::submit));assertTrue(f.ticket.publish());f.completed=f.displayed=7;
            f.prepared=f.submitted=8;assertFalse(f.ticket.completed());assertFalse(f.ticket.displayed());
        }
    }
}
