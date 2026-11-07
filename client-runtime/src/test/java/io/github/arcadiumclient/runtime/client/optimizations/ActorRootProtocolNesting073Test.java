package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorRootProtocolNesting073Test {
    @Test void ownerReturningBeforeOuterFinishDrainsInnerFirst() {
        var access=new Access();var protocol=new ActorRootProtocol073(access);
        State outerState=access.owned.copy();var outer=protocol.capture();
        access.owned=State.pattern(200);State innerState=access.owned.copy();var inner=protocol.capture();
        access.owned=State.pattern(300);access.current=access.foreign;

        assertFalse(protocol.finish(inner));assertEquals(1,protocol.pending());
        assertEquals(0,access.writes);assertEquals(0,access.foreignWrites);
        assertState(State.pattern(900),access.foreignState);
        access.current=access.owner;
        assertTrue(protocol.finish(outer));assertEquals(0,protocol.pending());assertEquals(0,protocol.depth());
        assertEquals(List.of(innerState.program,outerState.program),access.programRestores);
        assertState(outerState,access.owned);
        int writes=access.writes;protocol.require();
        assertEquals(writes,access.writes);assertState(outerState,access.owned);assertEquals(0,access.foreignWrites);
    }

    @Test void bothForeignUnwindsRestoreInnerThenOuter() {
        var access=new Access();var protocol=new ActorRootProtocol073(access);
        State outerState=access.owned.copy();var outer=protocol.capture();
        access.owned=State.pattern(200);State innerState=access.owned.copy();var inner=protocol.capture();
        access.owned=State.pattern(300);access.current=access.foreign;

        assertFalse(protocol.finish(inner));assertFalse(protocol.finish(outer));
        assertEquals(2,protocol.pending());assertEquals(0,protocol.depth());
        assertThrows(ActorRootProtocol073.Foreign.class,protocol::require);
        assertEquals(0,access.writes);assertEquals(0,access.foreignWrites);
        assertState(State.pattern(900),access.foreignState);
        access.current=access.owner;protocol.require();
        assertEquals(0,protocol.pending());assertEquals(List.of(innerState.program,outerState.program),access.programRestores);
        assertState(outerState,access.owned);assertEquals(0,access.foreignWrites);
    }

    @Test void interruptedDescendantDrainQueuesOuterBehindInner() {
        var access=new Access();var protocol=new ActorRootProtocol073(access);
        State outerState=access.owned.copy();var outer=protocol.capture();
        access.owned=State.pattern(200);State innerState=access.owned.copy();var inner=protocol.capture();
        access.owned=State.pattern(300);access.current=access.foreign;
        assertFalse(protocol.finish(inner));

        access.current=access.owner;access.flipAfterWrites=1;
        assertFalse(protocol.finish(outer));assertEquals(2,protocol.pending());assertEquals(0,protocol.depth());
        assertEquals(0,access.foreignWrites);assertTrue(access.programRestores.isEmpty());
        assertState(State.pattern(900),access.foreignState);
        access.current=access.owner;protocol.require();
        assertEquals(0,protocol.pending());assertEquals(List.of(innerState.program,outerState.program),access.programRestores);
        assertState(outerState,access.owned);assertEquals(0,access.foreignWrites);
    }

    private static void assertState(State expected,State actual) {
        assertEquals(expected.program,actual.program);assertEquals(expected.generic,actual.generic);
        assertEquals(expected.copyRead,actual.copyRead);assertEquals(expected.copyWrite,actual.copyWrite);
        assertArrayEquals(expected.buffers,actual.buffers);assertArrayEquals(expected.starts,actual.starts);assertArrayEquals(expected.sizes,actual.sizes);
    }

    private static final class State {
        int program,generic,copyRead,copyWrite;
        final int[] buffers=new int[8];final long[] starts=new long[8],sizes=new long[8];
        static State pattern(int seed) {
            var state=new State();state.program=seed+1;state.generic=seed+2;state.copyRead=seed+3;state.copyWrite=seed+4;
            for(int i=0;i<8;i++)if(i!=0&&i!=2){state.buffers[i]=seed+10+i;state.starts[i]=i*16L;state.sizes[i]=seed+128+i;}
            return state;
        }
        State copy() {
            var state=new State();state.program=program;state.generic=generic;state.copyRead=copyRead;state.copyWrite=copyWrite;
            System.arraycopy(buffers,0,state.buffers,0,8);System.arraycopy(starts,0,state.starts,0,8);System.arraycopy(sizes,0,state.sizes,0,8);return state;
        }
    }

    private static final class Access implements ActorRootProtocol073.Access {
        final Object owner=new Object(),foreign=new Object();Object current=owner;
        State owned=State.pattern(100);final State foreignState=State.pattern(900);
        final List<Integer> programRestores=new ArrayList<>();int writes,foreignWrites,flipAfterWrites=-1;
        public Object owner(){return owner;}
        public boolean owns(Object key){return key==owner&&current==owner;}
        private void check(){if(!owns(owner))throw new ActorRootProtocol073.Foreign();}
        private void write(Runnable action) {
            if(!owns(owner)){foreignWrites++;throw new ActorRootProtocol073.Foreign();}
            action.run();writes++;if(flipAfterWrites>0&&--flipAfterWrites==0)current=foreign;
        }
        public int program(){check();return owned.program;}public int generic(){check();return owned.generic;}
        public int copyRead(){check();return owned.copyRead;}public int copyWrite(){check();return owned.copyWrite;}
        public int buffer(int i){check();return owned.buffers[i];}public long start(int i){check();return owned.starts[i];}public long size(int i){check();return owned.sizes[i];}
        public void program(int name){write(()->{owned.program=name;programRestores.add(name);});}
        public void generic(int name){write(()->owned.generic=name);}public void copyRead(int name){write(()->owned.copyRead=name);}public void copyWrite(int name){write(()->owned.copyWrite=name);}
        public void base(int i,int name){write(()->{owned.buffers[i]=name;owned.starts[i]=owned.sizes[i]=0;owned.generic=name;});}
        public void range(int i,int name,long start,long size){write(()->{owned.buffers[i]=name;owned.starts[i]=start;owned.sizes[i]=size;owned.generic=name;});}
        public long fence(){throw new AssertionError("Fence policy is outside caller-order regression");}
        public void deleteFence(long name){throw new AssertionError("Fence policy is outside caller-order regression");}
    }
}
