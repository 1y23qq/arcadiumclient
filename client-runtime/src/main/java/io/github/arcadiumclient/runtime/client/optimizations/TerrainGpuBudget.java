// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.IdentityHashMap;

final class TerrainGpuBudget {
    static final long LIMIT=64L*1024*1024;
    private static final int OWNER_LIMIT=4096;
    private static final IdentityHashMap<Object,Long> owners=new IdentityHashMap<>();
    private static long bytes;
    private TerrainGpuBudget(){}
    static synchronized boolean reserve(Object owner,long requested){
        if(owner==null||requested<1||requested>LIMIT)return false;
        Long previous=owners.get(owner);
        if(previous==null&&owners.size()==OWNER_LIMIT)return false;
        long before=previous==null?0:previous;
        if(requested>LIMIT-(bytes-before))return false;
        owners.put(owner,requested);bytes=bytes-before+requested;return true;
    }
    static synchronized void release(Object owner){Long old=owners.remove(owner);if(old!=null)bytes-=old;}
    static synchronized long bytes(){return bytes;}
    static synchronized boolean fits(long requested){return requested>0&&requested<=LIMIT-bytes;}
    static synchronized int owners(){return owners.size();}
}
