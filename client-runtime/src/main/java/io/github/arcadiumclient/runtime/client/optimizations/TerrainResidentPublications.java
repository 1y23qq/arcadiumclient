// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;

final class TerrainResidentPublications {
    private static final class State {
        long storage;
        final long[] slots=new long[256];
        State(long generation){storage=generation;Arrays.fill(slots,generation);}
    }
    private final GpuVoxelSkinWeakIdentityMap<State> states;
    private long next;
    TerrainResidentPublications(int capacity){if(capacity<1||capacity>4096)throw new IllegalArgumentException("Invalid terrain publication bound");states=new GpuVoxelSkinWeakIdentityMap<>(capacity);}
    synchronized long current(Object storage){State state=known(storage);return state==null?-1:state.storage;}
    synchronized long slot(Object storage,int slot){if(slot<0||slot>=256)return -1;State state=states.get(storage);return state==null?-1:state.slots[slot];}
    synchronized long changed(Object storage,int slot){
        if(slot<0||slot>=256||next==Long.MAX_VALUE)return -1;State state=known(storage);if(state==null||next==Long.MAX_VALUE)return -1;
        state.storage=++next;state.slots[slot]=next;return next;
    }
    synchronized long changed(Object storage){
        if(next==Long.MAX_VALUE)return -1;State state=known(storage);if(state==null||next==Long.MAX_VALUE)return -1;
        state.storage=++next;Arrays.fill(state.slots,next);return next;
    }
    private State known(Object storage){
        if(storage==null)return null;State state=states.get(storage);if(state!=null)return state;
        if(next==Long.MAX_VALUE||!states.canAdmit(storage))return null;
        state=new State(++next);return states.put(storage,state)?state:null;
    }
    synchronized void released(Object storage){states.remove(storage);}
    synchronized void clear(){states.clear();}
    synchronized int retained(){return states.size();}
    synchronized long epoch(){return next;}
}
