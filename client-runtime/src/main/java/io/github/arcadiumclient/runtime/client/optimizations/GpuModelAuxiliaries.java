// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.IdentityHashMap;
import java.util.Objects;

public final class GpuModelAuxiliaries {
    public static final int LIMIT=6;
    private static final IdentityHashMap<Object,Object> owners=new IdentityHashMap<>();
    private GpuModelAuxiliaries() { }
    public static boolean register(Object main,Object auxiliary) {
        Objects.requireNonNull(main);Objects.requireNonNull(auxiliary);
        if(main==auxiliary||owners.containsKey(main)||owners.containsValue(auxiliary))return false;
        Object owner=owners.get(auxiliary);
        if(owner!=null)return owner==main;
        if(owners.size()>=LIMIT)return false;
        owners.put(auxiliary,main);return true;
    }
    public static void unregister(Object auxiliary) { owners.remove(auxiliary); }
    public static boolean isAuxiliaryBackend(Object value) { return owners.containsKey(value); }
    public static int size() { return owners.size(); }
}
