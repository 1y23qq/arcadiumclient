// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetalDeviceLeaseTest {
    private static final class Calls implements MetalDeviceLease.Calls {
        final ArrayList<String> events=new ArrayList<>();
        final HashMap<Long,Integer> handles=new HashMap<>();
        final HashMap<Long,MetalDeviceLease.Info> infos=new HashMap<>();
        long sequence=100;boolean live=true,wrongFrame,badInfo;int acquireFailure,releaseFailure;
        @Override public void ownerLive(){if(!live)throw new IllegalStateException("Owner is closed");}
        @Override public MetalDeviceLease.Acquisition acquire(int kind,long frame) {
            long h=++sequence;handles.put(h,kind);events.add("acquire-"+kind);
            infos.put(h,new MetalDeviceLease.Info(kind==1?0:frame+(wrongFrame?1:0),kind==1?0:3,kind==1?0:64,kind==1?0:48,11,12,kind==1?0:13));
            return new MetalDeviceLease.Acquisition(acquireFailure==kind?-3:0,h);
        }
        @Override public MetalDeviceLease.InfoResult info(long h){return new MetalDeviceLease.InfoResult(badInfo?-1:0,infos.get(h));}
        @Override public int release(long h) {
            int kind=handles.get(h);events.add("release-"+kind);
            if(releaseFailure==kind){releaseFailure=0;return -3;}
            handles.remove(h);infos.remove(h);return 0;
        }
        MetalDeviceLease open(){MetalDeviceLease value=new MetalDeviceLease(this);value.open();return value;}
    }
    private static final class Resource implements AutoCloseable {
        final String name;final Calls calls;boolean fail;int closes;
        Resource(String name,Calls calls){this.name=name;this.calls=calls;}
        @Override public void close(){closes++;calls.events.add(name);if(fail){fail=false;throw new IllegalStateException("Injected close failure");}}
    }
    @Test void targetAndDeviceChildrenCloseInLifoOrderBeforeNativeRetirement() {
        Calls calls=new Calls();var device=calls.open();
        device.createResource((d,q)->{assertEquals(11,d);assertEquals(12,q);return new Resource("context",calls);});
        var target=device.target(7);assertEquals(7,target.frame());assertEquals(3,target.generation());assertEquals(64,target.width());
        target.createResource((texture,w,h)->{assertEquals(13,texture);assertEquals(48,h);return new Resource("brt",calls);});
        target.createResource((texture,w,h)->new Resource("surface",calls));
        target.close();assertFalse(device.blocksBackend());assertThrows(IllegalStateException.class,target::width);
        device.close();device.close();assertTrue(device.isClosed());assertTrue(calls.handles.isEmpty());
        assertEquals(List.of("acquire-1","acquire-2","surface","brt","release-2","context","release-1"),calls.events);
    }
    @Test void wrongThreadCannotInvokeFactoriesReadTargetOrReleaseNativeHandles() throws Exception {
        Calls calls=new Calls();var device=calls.open();var target=device.target(1);int before=calls.events.size();
        AtomicInteger factory=new AtomicInteger();AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(()->{try {
            assertThrows(IllegalStateException.class,()->device.createResource((d,q)->{factory.incrementAndGet();return ()->{};}));
            assertThrows(IllegalStateException.class,target::width);assertThrows(IllegalStateException.class,target::close);
        }catch(Throwable error){failure.set(error);}});worker.start();worker.join(3000);
        assertFalse(worker.isAlive());assertNull(failure.get());assertEquals(0,factory.get());assertEquals(before,calls.events.size());device.close();
    }
    @Test void throwingChildRetainsTheTargetAndRetriesItsCloseBeforeRetiring() {
        Calls calls=new Calls();var device=calls.open();var target=device.target(1);
        Resource surface=target.createResource((t,w,h)->new Resource("surface",calls));surface.fail=true;
        assertThrows(IllegalStateException.class,target::close);assertTrue(device.blocksBackend());
        assertFalse(calls.events.contains("release-2"));assertThrows(IllegalStateException.class,()->target.createResource((t,w,h)->()->{}));
        target.close();assertEquals(2,surface.closes);assertFalse(device.blocksBackend());device.close();assertTrue(calls.handles.isEmpty());
    }
    @Test void failedNativeRetirementKeepsOwnershipAndDoesNotCloseChildrenTwice() {
        Calls calls=new Calls();var device=calls.open();var target=device.target(1);
        Resource surface=target.createResource((t,w,h)->new Resource("surface",calls));calls.releaseFailure=2;
        assertThrows(IllegalStateException.class,target::close);assertTrue(device.blocksBackend());
        assertThrows(IllegalStateException.class,()->target.createResource((t,w,h)->()->{}));
        target.close();assertEquals(1,surface.closes);assertEquals(2,calls.events.stream().filter("release-2"::equals).count());device.close();
    }
    @Test void aWrongFrameSnapshotReleasesItsPartialTargetAndLeavesTheDeviceUsable() {
        Calls calls=new Calls();var device=calls.open();calls.wrongFrame=true;
        assertThrows(IllegalStateException.class,()->device.target(8));assertFalse(device.blocksBackend());
        assertEquals(1,calls.handles.size());assertTrue(calls.events.contains("release-2"));
        calls.wrongFrame=false;try(var target=device.target(8)){assertEquals(8,target.frame());}device.close();
    }
    @Test void childCapacityIsCheckedBeforeAllocationAndIdentityCannotBeOwnedTwice() {
        Calls calls=new Calls();var device=calls.open();Resource first=device.createResource((d,q)->new Resource("first",calls));
        assertThrows(IllegalArgumentException.class,()->device.createResource((d,q)->first));
        for(int i=1;i<8;i++)device.createResource((d,q)->new Resource("other",calls));
        AtomicInteger extra=new AtomicInteger();assertThrows(IllegalStateException.class,()->device.createResource((d,q)->{extra.incrementAndGet();return ()->{};}));
        assertEquals(0,extra.get());device.close();assertEquals(1,first.closes);
    }
    @Test void reentrantCloseAndCreationCannotInvalidateAFactoriesBorrowedObjects() {
        Calls calls=new Calls();var device=calls.open();AtomicInteger inner=new AtomicInteger();
        device.createResource((d,q)->{
            assertThrows(IllegalStateException.class,device::close);
            assertThrows(IllegalStateException.class,()->device.createResource((a,b)->{inner.incrementAndGet();return ()->{};}));
            return new Resource("context",calls);
        });assertEquals(0,inner.get());device.close();assertTrue(calls.handles.isEmpty());
    }
    @Test void acquisitionAndInfoFailuresKeepPartialHandlesReachableForCleanupRetry() {
        Calls calls=new Calls();calls.acquireFailure=1;var device=new MetalDeviceLease(calls);
        assertThrows(IllegalStateException.class,device::open);calls.releaseFailure=1;
        assertThrows(IllegalStateException.class,device::close);assertFalse(device.isClosed());assertTrue(device.blocksBackend());
        device.close();assertTrue(device.isClosed());assertTrue(calls.handles.isEmpty());
        calls.acquireFailure=0;calls.badInfo=true;var second=new MetalDeviceLease(calls);
        assertThrows(IllegalStateException.class,second::open);second.close();assertTrue(calls.handles.isEmpty());
    }
    @Test void invalidatedOwnerAndInvalidFrameFailBeforeFactoryOrTargetAcquisition() {
        Calls calls=new Calls();var device=calls.open();int before=calls.events.size();
        assertThrows(IllegalArgumentException.class,()->device.target(0));assertEquals(before,calls.events.size());
        calls.live=false;AtomicInteger factory=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->device.createResource((d,q)->{factory.incrementAndGet();return ()->{};}));
        assertEquals(0,factory.get());calls.live=true;device.close();
    }
    @Test void oneResourceCannotBeAdoptedByBothDeviceAndFrameScopes() {
        Calls calls=new Calls();var device=calls.open();Resource context=device.createResource((d,q)->new Resource("context",calls));
        try(var target=device.target(1)) {
            assertThrows(IllegalArgumentException.class,()->target.createResource((t,w,h)->context));
        }
        assertEquals(0,context.closes);device.close();assertEquals(1,context.closes);
    }
    @Test void deviceCloseDuringATargetFactoryDoesNotPartiallyRetireTheOwner() {
        Calls calls=new Calls();var device=calls.open();
        try(var target=device.target(1)) {
            target.createResource((t,w,h)->{assertThrows(IllegalStateException.class,device::close);return new Resource("surface",calls);});
        }
        assertFalse(device.blocksBackend());device.createResource((d,q)->new Resource("still-open",calls));device.close();
        assertTrue(calls.handles.isEmpty());
    }
}
