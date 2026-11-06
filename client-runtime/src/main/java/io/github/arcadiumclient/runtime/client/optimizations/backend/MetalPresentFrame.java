// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.MemorySegment;
import static java.lang.foreign.ValueLayout.*;

final class MetalPresentFrame {
    static final int BYTES=32,LINEAR=1,SRGB=2;
    static final long MAX_IMAGE_BYTES=256L*1024*1024;
    private MetalPresentFrame() { }

    static void write(MemorySegment frame,long identifier,int width,int height,int encoding) {
        if(identifier<1||width<1||height<1||width>16384||height>16384
                ||(long)width*height*8>MAX_IMAGE_BYTES||(encoding!=LINEAR&&encoding!=SRGB))
            throw new IllegalArgumentException("Unsupported current-frame presentation descriptor");
        if(frame==null||frame.byteSize()!=BYTES||frame.isReadOnly()||!frame.scope().isAlive())
            throw new IllegalArgumentException("Presentation frame requires 32 writable bytes");
        frame.set(JAVA_LONG,0,identifier);
        frame.set(JAVA_INT,8,width);frame.set(JAVA_INT,12,height);
        frame.set(JAVA_INT,16,0);frame.set(JAVA_INT,20,0x0405);
        frame.set(JAVA_INT,24,encoding);frame.set(JAVA_INT,28,0);
    }
    static void writeScene(MemorySegment frame,long identifier,int width,int height) {
        if((long)width*height*4>64L*1024*1024)throw new IllegalArgumentException("Scene presentation exceeds its target budget");
        write(frame,identifier,width,height,LINEAR);frame.set(JAVA_INT,20,0);
    }
}
