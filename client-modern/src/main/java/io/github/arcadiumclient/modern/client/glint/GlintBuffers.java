// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class GlintBuffers {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final Set<Owner> LIVE = ConcurrentHashMap.newKeySet();
    private GlintBuffers() { }
    public static void initialize() { ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { for (var owner : LIVE.toArray(Owner[]::new)) owner.run(); }); }
    public static Owner owner(Object source) {
        var result = new Owner();
        result.cleanable = CLEANER.register(source, result);
        LIVE.add(result);
        return result;
    }
    public static final class Owner implements Runnable {
        private final ArrayList<ByteBufferBuilder> buffers = new ArrayList<>();
        private Cleaner.Cleanable cleanable;
        public synchronized ByteBufferBuilder allocate() { var buffer = new ByteBufferBuilder(1024); buffers.add(buffer); return buffer; }
        public synchronized void run() { buffers.forEach(ByteBufferBuilder::close); buffers.clear(); LIVE.remove(this); }
    }
}
