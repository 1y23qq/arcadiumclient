// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinBatchStateTest {
    @Test void anUnpreparedOrStaleBatchCannotCaptureGeometry() {
        var batch=new GpuVoxelSkinBatchState();assertFalse(batch.accepts(1));assertThrows(IllegalStateException.class,()->batch.queued(1));
        batch.prepare(1);assertTrue(batch.accepts(1));assertFalse(batch.accepts(2));assertEquals(-1,batch.claim(2));assertTrue(batch.accepts(1));
    }
    @Test void shadowFlushAndFallbackCannotDrawTheSamePendingBatchTwice() {
        var batch=new GpuVoxelSkinBatchState();batch.prepare(1);for(int i=0;i<18;i++)batch.queued(1);
        assertEquals(18,batch.claim(1));assertEquals(-1,batch.claim(1));assertFalse(batch.accepts(1));
        batch.prepare(2);for(int i=0;i<6;i++)batch.queued(2);assertEquals(6,batch.claim(2));assertEquals(-1,batch.claim(2));
    }
    @Test void emptyFailedOrAbandonedFlushesNeverLeakIntoTheNextCameraFrame() {
        var batch=new GpuVoxelSkinBatchState();batch.prepare(1);assertEquals(0,batch.claim(1));
        batch.prepare(2);batch.queued(2);assertEquals(1,batch.claim(2));assertEquals(-1,batch.claim(2));
        batch.prepare(3);batch.queued(3);batch.discard();batch.prepare(4);assertEquals(-1,batch.claim(3));assertEquals(0,batch.claim(4));
    }
}
