// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import static java.lang.foreign.ValueLayout.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetalPresentFrameTest {
    @Test void nativeContractOffsetsDescribeOnlyTheCurrentDefaultBackbuffer() {
        try(Arena memory=Arena.ofConfined()) {
            MemorySegment frame=memory.allocate(32,8);
            long identifier=0x123456789abcdef1L;
            MetalPresentFrame.write(frame,identifier,1920,1080,MetalPresentFrame.SRGB);
            assertEquals(identifier,frame.get(JAVA_LONG,0));
            assertEquals(1920,frame.get(JAVA_INT,8));assertEquals(1080,frame.get(JAVA_INT,12));
            assertEquals(0,frame.get(JAVA_INT,16));assertEquals(0x0405,frame.get(JAVA_INT,20));
            assertEquals(2,frame.get(JAVA_INT,24));assertEquals(0,frame.get(JAVA_INT,28));
            MetalPresentFrame.write(frame,identifier+1,800,600,MetalPresentFrame.LINEAR);
            assertEquals(identifier+1,frame.get(JAVA_LONG,0));assertEquals(1,frame.get(JAVA_INT,24));
        }
    }
    @Test void rejectedDescriptorsCannotPartiallyOverwriteAPreparedFrame() {
        try(Arena memory=Arena.ofConfined()) {
            MemorySegment frame=memory.allocate(32,8);frame.fill((byte)0x5a);
            assertThrows(IllegalArgumentException.class,()->MetalPresentFrame.write(frame,0,800,600,1));
            assertThrows(IllegalArgumentException.class,()->MetalPresentFrame.write(frame,1,0,600,1));
            assertThrows(IllegalArgumentException.class,()->MetalPresentFrame.write(frame,1,16385,1,1));
            assertThrows(IllegalArgumentException.class,()->MetalPresentFrame.write(frame,1,16384,16384,1));
            assertThrows(IllegalArgumentException.class,()->MetalPresentFrame.write(frame,1,800,600,3));
            for(long offset=0;offset<32;offset++)assertEquals((byte)0x5a,frame.get(JAVA_BYTE,offset));
            assertThrows(IllegalArgumentException.class,()->MetalPresentFrame.write(frame.asSlice(0,31),1,800,600,1));
            assertThrows(IllegalArgumentException.class,()->MetalPresentFrame.write(frame.asReadOnly(),1,800,600,1));
        }
    }
}
