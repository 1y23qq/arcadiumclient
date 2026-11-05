// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.ref.*;
import java.util.HashMap;

final class GpuVoxelSkinWeakIdentityMap<V> {
    private static final class Key extends WeakReference<Object> {
        final int hash;
        Key(Object value,ReferenceQueue<Object> queue){super(value,queue);hash=System.identityHashCode(value);}
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||get()!=null&&other instanceof Key key&&get()==key.get();}
    }
    private static final class Lookup {
        Object value;
        @Override public int hashCode(){return System.identityHashCode(value);}
        @Override public boolean equals(Object other){return value!=null&&other instanceof Key key&&value==key.get();}
    }
    private final int limit;
    private final HashMap<Object,V> values=new HashMap<>();
    private final ReferenceQueue<Object> queue=new ReferenceQueue<>();
    private final Lookup lookup=new Lookup();
    GpuVoxelSkinWeakIdentityMap(int limit){if(limit<1)throw new IllegalArgumentException("Invalid weak identity bound");this.limit=limit;}
    synchronized V get(Object key){if(key==null)return null;clean();lookup.value=key;try{return values.get(lookup);}finally{lookup.value=null;}}
    synchronized boolean put(Object key,V value){
        if(key==null||value==null)throw new NullPointerException();clean();lookup.value=key;
        try{if(values.containsKey(lookup)){values.replace(lookup,value);return true;}if(values.size()==limit)return false;values.put(new Key(key,queue),value);return true;}
        finally{lookup.value=null;}
    }
    synchronized V remove(Object key){if(key==null)return null;clean();lookup.value=key;try{return values.remove(lookup);}finally{lookup.value=null;}}
    synchronized int size(){clean();return values.size();}
    synchronized boolean canAdmit(Object key){return key!=null&&(get(key)!=null||size()<limit);}
    synchronized void clear(){values.clear();lookup.value=null;while(queue.poll()!=null){}}
    private void clean(){Reference<?> expired;while((expired=queue.poll())!=null)values.remove(expired);}
}
