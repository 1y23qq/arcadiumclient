// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.*;
import java.nio.file.Path;
import java.util.Arrays;
import static java.lang.foreign.ValueLayout.*;

public final class UnsupportedPresentationBindingsProbe {
    public static void main(String[] arguments) {
        if(arguments.length!=1)throw new IllegalArgumentException("Pass the unsupported-platform presentation library");
        SymbolLookup symbols=SymbolLookup.libraryLookup(Path.of(arguments[0]),Arena.global());
        MetalPresentationCalls calls=new MetalPresentationCalls(symbols,MemorySegment.NULL);
        require(calls.create(0)==-2&&!calls.attached(),"Unsupported create did not leave a null bridge");
        require(calls.error().contains("unavailable"),"The bounded native error was not copied through FFM");
        int checked=0;
        try(Arena memory=Arena.ofConfined()) {
            MemorySegment frame=memory.allocate(32,8);
            for(int encoding=1;encoding<=2;encoding++) {
                MetalPresentFrame.write(frame,encoding,1920,1080,encoding);prepare(calls,frame,-2);checked++;
            }
            MetalPresentFrame.write(frame,3,8192,4096,1);prepare(calls,frame,-2);checked++;
            int[] offsets={8,12,16,20,24,28};int[] invalid={0,0,1,0x0404,0,1};
            for(int i=0;i<offsets.length;i++) {
                MetalPresentFrame.write(frame,4+i,800,600,1);frame.set(JAVA_INT,offsets[i],invalid[i]);prepare(calls,frame,-1);checked++;
            }
            MetalPresentFrame.write(frame,10,800,600,1);frame.set(JAVA_LONG,0,0);prepare(calls,frame,-1);checked++;
            MetalPresentFrame.write(frame,11,800,600,1);frame.set(JAVA_INT,8,16384);frame.set(JAVA_INT,12,16384);prepare(calls,frame,-1);checked++;
        }
        require(calls.hide()==0&&calls.releaseGl()==0&&calls.destroy()==0&&!calls.attached(),"Null-bridge cleanup is not idempotent");
        require(calls.publish(1)==-2&&calls.query(9)==0,"An unsupported bridge claimed presentation");
        System.out.printf("{\"passed\":true,\"nativeFrameChecks\":%d,\"actualFfmCalls\":true,\"windowPresentation\":false}%n",checked);
    }
    private static void prepare(MetalPresentationCalls calls,MemorySegment frame,int expected) {
        byte[] before=frame.toArray(JAVA_BYTE);
        require(calls.prepare(frame)==expected,"Native frame validation disagreed with the Java ABI descriptor");
        require(Arrays.equals(before,frame.toArray(JAVA_BYTE)),"Native prepare modified the caller-owned frame descriptor");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
