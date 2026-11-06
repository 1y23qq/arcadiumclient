// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuResidentBodyJobsTest {
    @Test void sparseJobsUseOneWriterPerPartAndMergeDuplicateMasks(){
        GpuResidentBodyJobs jobs=new GpuResidentBodyJobs();jobs.add(8191,3);jobs.add(4,0x800);jobs.add(8191,0x40);
        assertEquals(2,jobs.count());assertEquals(8192,jobs.rows());assertEquals(8191|(0x43<<16),jobs.token(0));assertEquals(4|(0x800<<16),jobs.token(1));
        assertTrue(jobs.contains(8191,0x41));assertFalse(jobs.contains(8191,0x80));assertFalse(jobs.contains(8190,1));
    }
    @Test void aFrozenDrawCannotUseAnotherJobOrUnpreparedPart(){
        GpuResidentBodyJobs jobs=new GpuResidentBodyJobs();jobs.add(7,3);IntBuffer records=IntBuffer.allocate(19);records.put(16,7|(3<<16));
        assertFalse(jobs.covers(records));jobs.freeze();assertTrue(jobs.covers(records));assertFalse(jobs.canAdd(8,1));
        assertThrows(IllegalArgumentException.class,()->jobs.add(8,1));records.put(16,7|(4<<16));assertFalse(jobs.covers(records));records.put(16,8|(1<<16));assertFalse(jobs.covers(records));
    }
    @Test void aNewBatchCannotRetainASparseRowOrFrozenSubmission(){
        GpuResidentBodyJobs jobs=new GpuResidentBodyJobs();jobs.add(8191,0xfff);jobs.freeze();jobs.begin();
        assertEquals(0,jobs.count());assertEquals(0,jobs.rows());assertTrue(jobs.collecting());assertFalse(jobs.contains(8191,1));
        jobs.add(2,1);assertEquals(1,jobs.count());assertEquals(3,jobs.rows());assertFalse(jobs.contains(8191,1));
    }
    @Test void theMaximumJobSetIsBoundedAndInvalidJobsCannotChangeIt(){
        GpuResidentBodyJobs jobs=new GpuResidentBodyJobs();for(int i=0;i<8192;i++)jobs.add(i,0xfff);
        assertEquals(8192,jobs.count());assertEquals(8192,jobs.rows());int last=jobs.token(8191);
        assertThrows(IllegalArgumentException.class,()->jobs.add(8192,1));assertThrows(IllegalArgumentException.class,()->jobs.add(-1,1));
        assertThrows(IllegalArgumentException.class,()->jobs.add(1,0));assertThrows(IllegalArgumentException.class,()->jobs.add(1,0x1000));
        assertEquals(8192,jobs.count());assertEquals(last,jobs.token(8191));
    }
    @Test void nativeCopiesStayInsideTheirOwnedRangeAndRejectUnsafeBuffers(){
        GpuResidentBodyJobs jobs=new GpuResidentBodyJobs();jobs.add(0,1);jobs.add(4000,0x800);
        IntBuffer target=ByteBuffer.allocateDirect(6*4).order(ByteOrder.nativeOrder()).asIntBuffer();for(int i=0;i<6;i++)target.put(i,17);target.position(2).limit(4);
        jobs.copy(target);assertEquals(4,target.position());target.clear();assertEquals(17,target.get(0));assertEquals(17,target.get(1));assertEquals(jobs.token(0),target.get(2));assertEquals(jobs.token(1),target.get(3));assertEquals(17,target.get(4));assertEquals(17,target.get(5));
        assertThrows(IllegalArgumentException.class,()->jobs.copy(IntBuffer.allocate(2)));
        target.position(0).limit(1);assertThrows(IllegalArgumentException.class,()->jobs.copy(target));assertEquals(0,target.position());
    }
    @Test void generationRolloverCannotMakeAnOldRowVisible()throws Exception{
        GpuResidentBodyJobs jobs=new GpuResidentBodyJobs();jobs.add(8191,0xfff);
        var generation=GpuResidentBodyJobs.class.getDeclaredField("generation");generation.setAccessible(true);generation.setInt(jobs,Integer.MAX_VALUE);
        jobs.begin();assertFalse(jobs.contains(8191,1));jobs.add(1,1);assertTrue(jobs.contains(1,1));assertFalse(jobs.contains(8191,1));
    }
}
