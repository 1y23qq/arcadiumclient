// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DenseActorSlotsTest {
    @Test void reorderAndCompactionPreserveLogicalGenerations() {
        DenseActorSlots slots=new DenseActorSlots(4);Object world=new Object(),a=new Object(),b=new Object(),c=new Object();
        assertTrue(slots.prepare(world,List.of(a,b,c)));long aHandle=slots.handle(a),cHandle=slots.handle(c);
        assertTrue(slots.prepare(world,List.of(c,b,a)));assertEquals(0,slots.moves());assertEquals(2,slots.index(c));
        assertTrue(slots.prepare(world,List.of(c,a)));assertEquals(1,slots.index(c));assertEquals(cHandle,slots.handle(c));
        assertEquals(1,slots.moves());assertEquals(2,slots.moveFrom(0));assertEquals(1,slots.moveTo(0));assertTrue(slots.current(a,aHandle));
    }
    @Test void reusedIdentityAndWorldReplacementCannotKeepOldHandles() {
        DenseActorSlots slots=new DenseActorSlots(4);Object world=new Object(),actor=new Object();
        slots.prepare(world,List.of(actor));long handle=slots.handle(actor),epoch=slots.epoch();
        slots.prepare(world,List.of());slots.prepare(world,List.of(actor));assertFalse(slots.current(actor,handle));
        handle=slots.handle(actor);slots.prepare(new Object(),List.of(actor));assertFalse(slots.current(actor,handle));assertNotEquals(epoch,slots.epoch());
        slots.clear();assertEquals(0,slots.size());assertEquals(-1,slots.index(actor));
    }
    @Test void duplicateDrawsAndInvalidInputNeverAliasAnotherActor() {
        DenseActorSlots slots=new DenseActorSlots(2);Object world=new Object(),a=new Object(),b=new Object();
        slots.prepare(world,List.of(a,b));long handle=slots.handle(a);
        assertEquals(0,slots.claim(a,7));assertEquals(-1,slots.claim(a,7));assertEquals(0,slots.claim(a,8));
        assertFalse(slots.prepare(world,List.of(a,a)));assertFalse(slots.prepare(world,Arrays.asList(a,null)));
        assertFalse(slots.prepare(world,List.of(a,b,new Object())));assertTrue(slots.current(a,handle));assertEquals(2,slots.size());
    }
    @Test void identityEqualityIsIndependentOfMutableOrEqualEntityValues() {
        DenseActorSlots slots=new DenseActorSlots(2);Object world=new Object(),a=new String("id1"),b=new String("id1");
        assertTrue(slots.prepare(world,List.of(a,b)));assertNotEquals(slots.handle(a),slots.handle(b));assertNotEquals(slots.index(a),slots.index(b));
    }
}
