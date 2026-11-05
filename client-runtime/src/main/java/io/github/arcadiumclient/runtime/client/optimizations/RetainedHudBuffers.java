// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.render.vertex.*;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.*;

public final class RetainedHudBuffers {
    private static final int MAX_ENTRIES = 8, MAX_BYTES = 2 * 1024 * 1024;
    private static final Set<RetainedHudBuffers> live = Collections.newSetFromMap(new IdentityHashMap<>());
    private final LinkedList<Entry> entries = new LinkedList<>();
    private int bytes;
    private boolean failed;
    public static long hits, uploads, savedBytes, bufferReuses, snapshotAllocations, buffersCreated, buffersDeleted;
    private record Entry(VertexFormat format, ByteBuffer snapshot, VertexBuffer gpu, int mode) { }
    @FunctionalInterface public interface Drawer { void draw(VertexBuffer gpu, VertexFormat format); }

    public boolean render(BufferBuilder builder, Drawer drawer) {
        if (!BetaOptimizationsConfig.getRetainedHudBatches()) { close(); return false; }
        if (failed || builder.getVertexCount() == 0 || builder.getDrawMode() != GL11.GL_QUADS) return false;
        ByteBuffer data = builder.getBuffer().duplicate();
        if (data.remaining() > MAX_BYTES) return false;
        VertexFormat format = builder.getFormat();
        if (!format.hasColor() || format.hasNormal()) return false;
        Entry selected = null, recycled = null;
        try {
            for (var iterator = entries.iterator(); iterator.hasNext();) {
                Entry entry = iterator.next();
                if (entry.format == format && entry.snapshot.remaining() == data.remaining() && entry.snapshot.mismatch(data) < 0) {
                    selected = entry; iterator.remove(); hits++; savedBytes += data.remaining(); break;
                }
            }
            if (selected == null) {
                int retainedBytes = data.remaining();
                while (entries.size() >= MAX_ENTRIES || bytes + retainedBytes > MAX_BYTES) {
                    Entry entry = removeLast();
                    if (recycled == null && entry.format == format && entry.mode == builder.getDrawMode() && entry.snapshot.capacity() >= data.remaining()) {
                        recycled = entry; retainedBytes = entry.snapshot.capacity();
                    }
                    else discard(entry);
                }
                if (recycled == null) {
                    var snapshot = ByteBuffer.allocate(data.remaining()); snapshotAllocations++;
                    snapshot.put(data.duplicate()).flip();
                    var gpu = new VertexBuffer(format); buffersCreated++;
                    try { gpu.upload(data.duplicate()); }
                    catch (Throwable error) { gpu.delete(); buffersDeleted++; throw error; }
                    selected = new Entry(format, snapshot, gpu, builder.getDrawMode());
                } else {
                    selected = recycled; recycled = null;
                    try {
                        selected.snapshot.clear(); selected.snapshot.put(data.duplicate()).flip();
                        selected.gpu.upload(data.duplicate());
                    } catch (Throwable error) { discard(selected); throw error; }
                    bufferReuses++;
                }
                bytes += selected.snapshot.capacity(); uploads++;
                live.add(this);
            }
            entries.addFirst(selected);
            drawer.draw(selected.gpu, selected.format);
            builder.clear(); return true;
        } catch (Throwable error) {
            if (recycled != null) try { discard(recycled); } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
            failed = true; close();
            LogManager.getLogger("Arcadium/Optimizations").warn("Retained HUD batches disabled", error); return false;
        }
    }

    private Entry removeLast() { Entry entry = entries.removeLast(); bytes -= entry.snapshot.capacity(); return entry; }
    private static void discard(Entry entry) { entry.gpu.delete(); buffersDeleted++; }
    private void evict() { discard(removeLast()); }
    public void close() { while (!entries.isEmpty()) evict(); live.remove(this); }
    public static void closeAll() { for (var cache : List.copyOf(live)) cache.close(); }
    public static String status() {
        return BetaOptimizationsConfig.getRetainedHudBatches() ? "HUD batches: " + hits + " reused | " + uploads + " uploaded" : "HUD batches: Off";
    }
}
