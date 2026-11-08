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
    public static long hits, uploads, savedBytes;
    private record Entry(VertexFormat format, ByteBuffer snapshot, VertexBuffer gpu, int mode) { }
    @FunctionalInterface public interface Drawer { void draw(VertexBuffer gpu, VertexFormat format); }

    public boolean render(BufferBuilder builder, Drawer drawer) {
        if (!BetaOptimizationsConfig.getRetainedHudBatches()) { close(); return false; }
        if (failed || builder.getVertexCount() == 0 || builder.getDrawMode() != GL11.GL_QUADS) return false;
        ByteBuffer data = builder.getBuffer().duplicate();
        if (data.remaining() > MAX_BYTES) return false;
        VertexFormat format = builder.getFormat();
        if (!format.hasColor() || format.hasNormal()) return false;
        Entry selected = null;
        try {
            for (var iterator = entries.iterator(); iterator.hasNext();) {
                Entry entry = iterator.next();
                if (entry.format == format && entry.snapshot.remaining() == data.remaining() && entry.snapshot.mismatch(data) < 0) {
                    selected = entry; iterator.remove(); hits++; savedBytes += data.remaining(); break;
                }
            }
            if (selected == null) {
                while (entries.size() >= MAX_ENTRIES || bytes + data.remaining() > MAX_BYTES) evict();
                var snapshot = ByteBuffer.allocate(data.remaining()); snapshot.put(data.duplicate()).flip();
                var gpu = new VertexBuffer(format);
                try { gpu.upload(data.duplicate()); }
                catch (Throwable error) { gpu.delete(); throw error; }
                selected = new Entry(format, snapshot, gpu, builder.getDrawMode()); bytes += snapshot.remaining(); uploads++;
                live.add(this);
            }
            entries.addFirst(selected);
            drawer.draw(selected.gpu, selected.format);
            builder.clear(); return true;
        } catch (Throwable error) {
            failed = true; close();
            LogManager.getLogger("Arcadium/Optimizations").warn("Retained HUD batches disabled", error); return false;
        }
    }

    private void evict() { Entry entry = entries.removeLast(); bytes -= entry.snapshot.remaining(); entry.gpu.delete(); }
    public void close() { if (entries.isEmpty()) return; while (!entries.isEmpty()) evict(); live.remove(this); }
    public static void closeAll() { for (var cache : List.copyOf(live)) cache.close(); }
    public static String status() {
        return BetaOptimizationsConfig.getRetainedHudBatches() ? "HUD batches: " + hits + " reused | " + uploads + " uploaded" : "HUD batches: Off";
    }
}
