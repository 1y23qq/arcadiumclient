// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;

final class GpuVoxelSkinTextureBudget {
    static final int MAX_DIMENSION=128,MAX_POOLS=4,MAX_TEXTURES=1024;
    static final long MAX_BYTES=32L*1024*1024;
    record Descriptor(Object key,int width,int height,int layers) {}
    private final HashMap<Object,Descriptor> pools=new HashMap<>();
    private final IdentityHashMap<Object,Boolean> textures=new IdentityHashMap<>();
    private final HashSet<Object> identifiers=new HashSet<>();
    private long bytes;
    boolean admit(Object source,Descriptor descriptor) {
        return admit(source,source,descriptor);
    }
    boolean admit(Object source,Object identifier,Descriptor descriptor) {
        if(source==null||identifier==null||descriptor==null||descriptor.key==null||descriptor.width<1||descriptor.height<1||descriptor.layers<1
                ||descriptor.width>MAX_DIMENSION||descriptor.height>MAX_DIMENSION||descriptor.layers>256)return false;
        long required=(long)descriptor.width*descriptor.height*descriptor.layers*4;
        Descriptor known=pools.get(descriptor.key);
        if(known!=null&&(known.width!=descriptor.width||known.height!=descriptor.height||known.layers!=descriptor.layers))return false;
        if(!textures.containsKey(source)&&textures.size()==MAX_TEXTURES)return false;
        if(!identifiers.contains(identifier)&&identifiers.size()==MAX_TEXTURES)return false;
        if(known==null&&(pools.size()==MAX_POOLS||required>MAX_BYTES-bytes))return false;
        if(known==null){pools.put(descriptor.key,descriptor);bytes+=required;}
        textures.put(source,Boolean.TRUE);identifiers.add(identifier);return true;
    }
    long bytes(){return bytes;}
    int pools(){return pools.size();}
    int textures(){return textures.size();}
    int identifiers(){return identifiers.size();}
    void reset(){pools.clear();textures.clear();identifiers.clear();bytes=0;}
}
