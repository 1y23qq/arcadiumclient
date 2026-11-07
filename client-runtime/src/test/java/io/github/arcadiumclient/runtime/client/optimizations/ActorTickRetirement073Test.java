// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ActorTickRetirement073Test {
    private static void observe(ActorSceneData data,int slot,long handle,long tick){
        assertTrue(data.observe(slot,handle,tick,.5f,0,1,2,3,4,5,6,.2f,.1f,0,0));
    }

    @Test void unchangedRosterRetainsTicksWithoutClearingUnusedCapacity(){
        var data=new ActorSceneData(8192);data.begin(1);
        observe(data,0,1,10);observe(data,63,64,10);
        var first=data.current(0);var last=data.current(63);
        for(int i=0;i<1000;i++)data.truncate(64);
        assertSame(first,data.current(0));assertSame(last,data.current(63));
        assertEquals(0,data.truncatedTickSlots);
        data.truncate(32);
        assertSame(first,data.current(0));assertNull(data.current(63));
        assertNull(data.previous(63));assertFalse(data.canCapture(63,64));
        assertEquals(32,data.truncatedTickSlots);
        data.truncate(64);assertNull(data.current(63));
        observe(data,63,65,11);assertEquals(65,data.current(63).handle);
        assertNull(data.previous(63));
    }

    @Test void compactionAndSparseReuseDoNotLeaveRemovedActorHistory(){
        var data=new ActorSceneData(8192);data.begin(1);
        observe(data,8191,9,10);observe(data,8191,9,11);
        var latest=data.current(8191);var prior=data.previous(8191);
        assertNotNull(prior);
        data.move(8191,1);data.truncate(2);
        assertSame(latest,data.current(1));assertSame(prior,data.previous(1));
        assertNull(data.current(8191));assertNull(data.previous(8191));
        assertFalse(data.canCapture(8191,9));assertTrue(data.canCapture(1,9));
        data.retain(1,10);assertNull(data.current(1));assertNull(data.previous(1));
        observe(data,8191,11,12);data.truncate(2);
        assertNull(data.current(8191));assertFalse(data.canCapture(8191,11));
    }

    @Test void resetInvalidObservationAndRosterBoundsKeepLifetimeGuards(){
        var data=new ActorSceneData(8192);data.begin(1);observe(data,8191,7,10);
        assertFalse(data.observe(8191,0,10,.5f,0,0,0,0,0,0,0,0,0,0,0));
        assertFalse(data.canCapture(8191,7));
        data.truncate(0);assertNull(data.current(8191));
        observe(data,8191,8,11);data.clear();
        assertNull(data.current(8191));assertNull(data.previous(8191));
        assertFalse(data.canCapture(8191,8));
        long cleared=data.truncatedTickSlots;data.truncate(0);assertEquals(cleared,data.truncatedTickSlots);
        observe(data,0,9,12);data.truncate(1);assertEquals(9,data.current(0).handle);
        assertThrows(IllegalArgumentException.class,()->data.truncate(-1));
        assertThrows(IllegalArgumentException.class,()->data.truncate(8193));
    }
}
