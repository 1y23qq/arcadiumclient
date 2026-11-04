// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureGpuTest {
    private static class Fake implements CaptureGpu.Backend {
        int created, deleted, reads, stamps, bits = 64; boolean available;
        public int bits() { return bits; } public int create() { return ++created; }
        public void stamp(int id) { stamps++; } public boolean available(int id) { return available; }
        public long result(int id) { assertTrue(available, "A result read would block"); reads++; return (id & 1) == 1 ? 100_000 : 500_000; }
        public void delete(int id) { deleted++; }
    }
    @Test void unavailableResultsNeverBlockAndQueryStorageRemainsBounded() {
        var data = new CaptureData(Map.of(), Map.of(), Map.of(), 1); var backend = new Fake(); var timer = new CaptureGpu(data, backend);
        timer.initialize();
        for (int i = 0; i < 16 * 20; i++) { timer.frame(i); timer.beginRender(); timer.beforePresent(); }
        assertEquals(32, backend.created); assertEquals(0, backend.reads); assertEquals(4, data.gpuDropped);
        backend.available = true; timer.poll(); assertEquals(32, backend.reads); assertEquals(16, data.gpu.size());
        timer.close(); timer.close(); assertEquals(32, backend.deleted);
    }
    @Test void unsupportedTimersDoNotCreateQueriesOrFallBackToSynchronization() {
        var data = new CaptureData(Map.of(), Map.of(), Map.of(), 1); var backend = new Fake(); backend.bits = 0;
        var timer = new CaptureGpu(data, backend); timer.initialize();
        for (int i = 0; i < 100; i++) { timer.frame(i); timer.beginRender(); timer.beforePresent(); }
        timer.close(); assertEquals(0, backend.created); assertEquals(0, backend.stamps); assertEquals(0, backend.reads);
        assertTrue(data.gpuState.contains("no blocking fallback"));
    }
    @Test void partialInitializationFailureReleasesEveryCreatedQuery() {
        var data = new CaptureData(Map.of(), Map.of(), Map.of(), 1);
        var backend = new Fake() { public int create() { if (created == 3) throw new IllegalStateException(); return super.create(); } };
        var timer = new CaptureGpu(data, backend); timer.initialize(); timer.close();
        assertEquals(3, backend.deleted); assertTrue(data.gpuState.contains("disabled"));
    }
}
