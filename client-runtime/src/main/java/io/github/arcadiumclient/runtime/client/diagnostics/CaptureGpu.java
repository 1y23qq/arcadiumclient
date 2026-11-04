// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import org.lwjgl.opengl.*;

final class CaptureGpu implements AutoCloseable {
    interface Backend {
        int bits(); int create(); void stamp(int id); boolean available(int id); long result(int id); void delete(int id);
    }
    private static final class Native implements Backend {
        public int bits() {
            var caps = GL.getCapabilities();
            return caps.OpenGL33 || caps.GL_ARB_timer_query ? GL15.glGetQueryi(GL33.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS) : 0;
        }
        public int create() { return GL15.glGenQueries(); }
        public void stamp(int id) { GL33.glQueryCounter(id, GL33.GL_TIMESTAMP); }
        public boolean available(int id) { return GL15.glGetQueryObjecti(id, GL15.GL_QUERY_RESULT_AVAILABLE) != 0; }
        public long result(int id) { return GL33.glGetQueryObjecti64(id, GL15.GL_QUERY_RESULT); }
        public void delete(int id) { GL15.glDeleteQueries(id); }
    }
    private final Backend backend;
    private final CaptureData data;
    private final int[][] queries = new int[16][2];
    private final long[] offsets = new long[16];
    private int head, count, frame, bits;
    private boolean selected, started, supported, closed;
    private long offset;

    CaptureGpu(CaptureData data) { this(data, new Native()); }
    CaptureGpu(CaptureData data, Backend backend) { this.data = data; this.backend = backend; }

    void initialize() {
        try {
            bits = backend.bits();
            if (bits <= 0) { data.gpuState = "Timestamp queries unavailable; no blocking fallback"; return; }
            for (int[] pair : queries) for (int i = 0; i < 2; i++) pair[i] = backend.create();
            supported = true; data.gpuState = "Asynchronous timestamp queries";
        } catch (Throwable failure) { disable(failure); }
    }

    void frame(long offset) {
        selected = started = false; this.offset = offset;
        if (!supported || closed || (++frame & 15) != 0) return;
        try {
            poll();
            if (count == queries.length) { data.gpuDropped++; return; }
            selected = true;
        } catch (Throwable failure) { disable(failure); }
    }

    void beginRender() {
        if (!selected || started) return;
        try { backend.stamp(queries[(head + count) % queries.length][0]); started = true; }
        catch (Throwable failure) { disable(failure); }
    }

    void beforePresent() {
        if (!selected || !started) return;
        try {
            int slot = (head + count) % queries.length;
            backend.stamp(queries[slot][1]); offsets[slot] = offset; count++;
            selected = started = false;
        } catch (Throwable failure) { disable(failure); }
    }

    void poll() {
        if (!supported || closed) return;
        while (count > 0) {
            int[] pair = queries[head];
            if (!backend.available(pair[1])) return;
            long first = backend.result(pair[0]), last = backend.result(pair[1]);
            long delta = last - first;
            if (bits < 64) delta &= (1L << bits) - 1;
            if (delta >= 0 && delta < 10_000_000_000L) data.gpu(offsets[head], delta);
            else data.gpuDropped++;
            head = (head + 1) % queries.length; count--;
        }
    }

    private void disable(Throwable failure) {
        data.gpuState = "Timestamp queries disabled (" + failure.getClass().getSimpleName() + "); CPU timings remain available";
        supported = selected = started = false; close();
    }

    @Override public void close() {
        if (closed) return;
        closed = true; selected = started = false;
        for (int[] pair : queries) for (int i = 0; i < 2; i++) if (pair[i] != 0) {
            try { backend.delete(pair[i]); } catch (Throwable ignored) { }
            pair[i] = 0;
        }
    }
}
