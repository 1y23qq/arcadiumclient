// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.WeakHashMap;

final class TerrainResidentVersions {
    private final WeakHashMap<Object,Long> versions=new WeakHashMap<>();
    private final int limit;
    private long next;
    TerrainResidentVersions(int limit){if(limit<1||limit>4096)throw new IllegalArgumentException("Invalid resident terrain version budget");this.limit=limit;}
    synchronized long current(Object storage){
        if(storage==null)return -1;Long known=versions.get(storage);if(known!=null)return known;
        if(versions.size()>=limit)return -1;
        if(next==Long.MAX_VALUE)return -1;
        long value=++next;versions.put(storage,value);return value;
    }
    synchronized long changed(Object storage){
        if(storage==null||!versions.containsKey(storage)&&versions.size()>=limit)return -1;
        if(next==Long.MAX_VALUE)return -1;
        long value=++next;versions.put(storage,value);return value;
    }
    synchronized void released(Object storage){versions.remove(storage);}
    synchronized void clear(){versions.clear();}
    synchronized int retained(){return versions.size();}
}
