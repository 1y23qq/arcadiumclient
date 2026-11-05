// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.IdentityHashMap;
import java.util.Objects;

final class GpuVoxelSkinAuxiliaries {
    private final IdentityHashMap<Object,Object> owners = new IdentityHashMap<>();
    private final int limit;
    GpuVoxelSkinAuxiliaries(int limit) { if(limit<1)throw new IllegalArgumentException("Invalid auxiliary budget");this.limit=limit; }
    boolean register(Object main,Object auxiliary) {
        Objects.requireNonNull(main);Objects.requireNonNull(auxiliary);
        if(main==auxiliary)throw new IllegalArgumentException("An auxiliary cannot own itself");
        if(owners.containsKey(auxiliary))return owners.get(auxiliary)==main;
        if(owners.size()==limit)return false;
        owners.put(auxiliary,main);return true;
    }
    boolean contains(Object auxiliary) { return owners.containsKey(auxiliary); }
    void unregister(Object auxiliary) { owners.remove(auxiliary); }
    int size() { return owners.size(); }
}
