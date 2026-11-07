// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

final class DenseActorSlots {
    private static final class Entry {
        final Object owner;
        final long handle;
        int index;
        long seen, claimed = Long.MIN_VALUE;
        Entry(Object owner, long handle, int index, long frame) { this.owner=owner;this.handle=handle;this.index=index;seen=frame; }
    }
    private final int capacity;
    private final IdentityHashMap<Object, Entry> active;
    private final IdentityHashMap<Object, Boolean> input;
    private final Entry[] dense;
    private final int[] moveFrom,moveTo;
    private Object[] orderedInput=new Object[0];
    private int orderedSize;
    private int size,moves;
    long stableRosterReuses;
    private long frame,handleSequence,epoch;
    private Object world;

    DenseActorSlots(int capacity) {
        if(capacity<1||capacity>8192)throw new IllegalArgumentException("Invalid actor capacity");
        this.capacity=capacity;active=new IdentityHashMap<>(Math.min(capacity,128));input=new IdentityHashMap<>(Math.min(capacity,128));
        dense=new Entry[capacity];moveFrom=new int[capacity];moveTo=new int[capacity];
    }

    boolean prepare(Object nextWorld,List<?> actors) {
        if(nextWorld==null){if(!actors.isEmpty())return false;clear();return true;}
        if(actors.size()>capacity)return false;
        if(world==nextWorld&&actors.size()==orderedSize){
            boolean same=true;
            for(int index=0;index<orderedSize;index++)if(actors.get(index)!=orderedInput[index]){same=false;break;}
            if(same){if(frame==Long.MAX_VALUE)throw new IllegalStateException("Actor frame generation exhausted");frame++;moves=0;stableRosterReuses++;return true;}
        }
        input.clear();
        try{
            for(int index=0;index<actors.size();index++){
                Object owner=actors.get(index);if(owner==null||input.put(owner,Boolean.TRUE)!=null)return false;
            }
            if(world!=nextWorld){clear();world=nextWorld;}
            if(frame==Long.MAX_VALUE)throw new IllegalStateException("Actor frame generation exhausted");
            long current=++frame;moves=0;
            for(int index=0;index<actors.size();index++){Entry entry=active.get(actors.get(index));if(entry!=null)entry.seen=current;}
            for(int index=size-1;index>=0;index--)if(dense[index].seen!=current){
                Entry removed=dense[index],last=dense[--size];active.remove(removed.owner);dense[size]=null;
                if(index!=size){dense[index]=last;last.index=index;moveFrom[moves]=size;moveTo[moves++]=index;}
            }
            for(int index=0;index<actors.size();index++){
                Object owner=actors.get(index);if(active.containsKey(owner))continue;
                if(handleSequence==Long.MAX_VALUE)throw new IllegalStateException("Actor handle generation exhausted");
                Entry entry=new Entry(owner,++handleSequence,size,current);active.put(owner,entry);dense[size++]=entry;
            }
            if(orderedInput.length<actors.size())orderedInput=new Object[Math.min(capacity,Math.max(actors.size(),Math.max(16,orderedInput.length*2)))];
            for(int index=0;index<actors.size();index++)orderedInput[index]=actors.get(index);
            if(actors.size()<orderedSize)Arrays.fill(orderedInput,actors.size(),orderedSize,null);orderedSize=actors.size();
            return true;
        }finally{input.clear();}
    }

    int index(Object owner){Entry entry=active.get(owner);return entry==null?-1:entry.index;}
    long handle(Object owner){Entry entry=active.get(owner);return entry==null?0:entry.handle;}
    boolean current(Object owner,long handle){return handle!=0&&handle(owner)==handle;}
    boolean canClaim(Object owner,long batch){Entry entry=active.get(owner);return entry!=null&&entry.claimed!=batch;}
    int claim(Object owner,long batch){
        Entry entry=active.get(owner);if(entry==null||entry.claimed==batch)return -1;
        entry.claimed=batch;return entry.index;
    }
    long handleAt(int index){if(index<0||index>=size)throw new IndexOutOfBoundsException(index);return dense[index].handle;}
    Object owner(int index){if(index<0||index>=size)throw new IndexOutOfBoundsException(index);return dense[index].owner;}
    int size(){return size;}
    int moves(){return moves;}
    int moveFrom(int index){if(index<0||index>=moves)throw new IndexOutOfBoundsException(index);return moveFrom[index];}
    int moveTo(int index){if(index<0||index>=moves)throw new IndexOutOfBoundsException(index);return moveTo[index];}
    long epoch(){return epoch;}
    void clear(){Arrays.fill(dense,0,size,null);Arrays.fill(orderedInput,0,orderedSize,null);orderedSize=0;active.clear();size=moves=0;world=null;epoch++;}
}
