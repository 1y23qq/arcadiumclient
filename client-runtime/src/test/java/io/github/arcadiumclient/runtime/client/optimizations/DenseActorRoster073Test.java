// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DenseActorRoster073Test {
    @Test void unchangedOrderedRosterReusesStableHandlesAndResetsMoves(){
        var slots=new DenseActorSlots(4);var world=new Object();var a=new Object();var b=new Object();var c=new Object();
        assertTrue(slots.prepare(world,List.of(a,b,c)));long ah=slots.handle(a),ch=slots.handle(c);
        for(int i=0;i<1000;i++)assertTrue(slots.prepare(world,List.of(a,b,c)));
        assertEquals(1000,slots.stableRosterReuses);assertEquals(ah,slots.handle(a));assertEquals(ch,slots.handle(c));
        assertTrue(slots.prepare(world,List.of(a,c)));assertEquals(1,slots.moves());assertEquals(1,slots.index(c));
        assertTrue(slots.prepare(world,List.of(a,c)));assertEquals(0,slots.moves());assertEquals(ch,slots.handle(c));
        assertEquals(0,slots.claim(a,7));assertEquals(-1,slots.claim(a,7));
        assertTrue(slots.prepare(world,List.of(a,c)));assertEquals(-1,slots.claim(a,7));assertEquals(0,slots.claim(a,8));
    }

    @Test void mutationReorderingDuplicatesAndWorldSwitchCannotReuseStaleInput(){
        var slots=new DenseActorSlots(4);var world=new Object();var a=new Object();var b=new Object();var c=new Object();
        var input=new ArrayList<>(List.of(a,b));assertTrue(slots.prepare(world,input));long ah=slots.handle(a),bh=slots.handle(b);
        input.set(0,c);assertTrue(slots.prepare(world,input));assertEquals(0,slots.handle(a));assertEquals(bh,slots.handle(b));
        assertFalse(slots.prepare(world,Arrays.asList(c,c)));assertFalse(slots.prepare(world,Arrays.asList(c,null)));
        assertTrue(slots.prepare(world,List.of(b,c)));assertEquals(bh,slots.handle(b));assertEquals(0,slots.stableRosterReuses);
        assertTrue(slots.prepare(world,List.of(b,c)));assertEquals(1,slots.stableRosterReuses);
        assertTrue(slots.prepare(new Object(),List.of(b,c)));assertNotEquals(bh,slots.handle(b));
        slots.clear();assertEquals(0,slots.size());assertTrue(slots.prepare(world,List.of(a,b)));assertNotEquals(ah,slots.handle(a));
        assertTrue(slots.prepare(world,List.of()));assertEquals(0,slots.size());
        assertTrue(slots.prepare(world,List.of()));assertEquals(2,slots.stableRosterReuses);
    }
}
