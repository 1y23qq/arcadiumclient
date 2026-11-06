// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import static java.lang.foreign.ValueLayout.*;

interface CurrentFrameCalls {
    boolean supportsSwapInterval();
    int create(long cocoaWindow);
    int setSwapInterval(int interval);
    int prepare(MemorySegment frame);
    int publish(long identifier);
    int hide();
    int releaseGl();
    int destroy();
    boolean attached();
    String error();
    long query(int key);
    default boolean supportsSceneInput(){return false;}
    default int prepareScene(long target,MemorySegment frame){return -2;}
    default boolean reserveSceneOwner(Object owner){return false;}
    default void releaseSceneOwner(Object owner){}
}

final class MetalPresentationCalls implements CurrentFrameCalls {
    private final MemorySegment scene;
    private final MethodHandle create,interval,prepare,publish,hide,release,destroy,error,query;
    private MethodHandle prepareScene;
    private Object sceneTicketOwner;
    private volatile MemorySegment bridge=MemorySegment.NULL;

    MetalPresentationCalls(SymbolLookup symbols,MemorySegment scene) {
        this.scene=scene;
        Linker linker=Linker.nativeLinker();
        try {
            MethodHandle abi=function(linker,symbols,"arc_present_abi",FunctionDescriptor.of(JAVA_INT));
            if((int)abi.invokeExact()!=1)throw new UnsupportedOperationException("Incompatible Metal presentation ABI");
            create=function(linker,symbols,"arc_present_create",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS,ADDRESS));
            interval=symbols.find("arc_present_set_swap_interval").map(address->linker.downcallHandle(address,
                    FunctionDescriptor.of(JAVA_INT,ADDRESS,JAVA_INT))).orElse(null);
            prepare=function(linker,symbols,"arc_present_prepare",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS));
            publish=function(linker,symbols,"arc_present_publish",FunctionDescriptor.of(JAVA_INT,ADDRESS,JAVA_LONG));
            hide=function(linker,symbols,"arc_present_hide",FunctionDescriptor.of(JAVA_INT,ADDRESS));
            release=function(linker,symbols,"arc_present_release_gl",FunctionDescriptor.of(JAVA_INT,ADDRESS));
            destroy=function(linker,symbols,"arc_present_destroy",FunctionDescriptor.of(JAVA_INT,ADDRESS));
            error=function(linker,symbols,"arc_present_error",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS,JAVA_LONG));
            query=function(linker,symbols,"arc_present_query",FunctionDescriptor.of(JAVA_LONG,ADDRESS,JAVA_INT));
            if(symbols.find("arc_present_scene_abi").isPresent()&&symbols.find("arc_present_prepare_scene").isPresent()) {
                MethodHandle sceneAbi=function(linker,symbols,"arc_present_scene_abi",FunctionDescriptor.of(JAVA_INT));
                if((int)sceneAbi.invokeExact()==1)prepareScene=function(linker,symbols,"arc_present_prepare_scene",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS,ADDRESS));
            }
        } catch(Throwable failure) { throw propagate(failure); }
    }
    @Override public boolean supportsSwapInterval() { return interval!=null; }
    @Override public int setSwapInterval(int value) {
        if(interval==null)return -2;
        try { return (int)interval.invokeExact(bridge,value); } catch(Throwable failure) { throw propagate(failure); }
    }
    private static MethodHandle function(Linker linker,SymbolLookup symbols,String name,FunctionDescriptor descriptor) {
        return linker.downcallHandle(symbols.find(name).orElseThrow(()->new UnsupportedOperationException("Metal presentation symbol is unavailable: "+name)),descriptor);
    }
    @Override public int create(long cocoaWindow) {
        if(attached())throw new IllegalStateException("The Metal presentation bridge is already attached");
        try(Arena output=Arena.ofConfined()) {
            MemorySegment pointer=output.allocate(ADDRESS);pointer.set(ADDRESS,0,MemorySegment.NULL);
            int result=(int)create.invokeExact(scene,MemorySegment.ofAddress(cocoaWindow),pointer);
            bridge=pointer.get(ADDRESS,0);
            return result;
        } catch(Throwable failure) { throw propagate(failure); }
    }
    @Override public int prepare(MemorySegment frame) {
        try { return (int)prepare.invokeExact(bridge,frame); } catch(Throwable failure) { throw propagate(failure); }
    }
    @Override public boolean supportsSceneInput(){return prepareScene!=null;}
    @Override public synchronized boolean reserveSceneOwner(Object owner) {
        if(owner==null||sceneTicketOwner!=null&&sceneTicketOwner!=owner)return false;
        sceneTicketOwner=owner;return true;
    }
    @Override public synchronized void releaseSceneOwner(Object owner) {
        if(sceneTicketOwner!=owner)throw new IllegalStateException("A different scene ticket owns this bridge");sceneTicketOwner=null;
    }
    @Override public int prepareScene(long target,MemorySegment frame) {
        if(prepareScene==null)return -2;
        try{return (int)prepareScene.invokeExact(bridge,MemorySegment.ofAddress(target),frame);}catch(Throwable failure){throw propagate(failure);}
    }
    @Override public int publish(long identifier) {
        try { return (int)publish.invokeExact(bridge,identifier); } catch(Throwable failure) { throw propagate(failure); }
    }
    @Override public int hide() {
        try { return (int)hide.invokeExact(bridge); } catch(Throwable failure) { throw propagate(failure); }
    }
    @Override public int releaseGl() {
        try { return (int)release.invokeExact(bridge); } catch(Throwable failure) { throw propagate(failure); }
    }
    @Override public int destroy() {
        try {
            int result=(int)destroy.invokeExact(bridge);
            if(result==0)bridge=MemorySegment.NULL;
            return result;
        } catch(Throwable failure) { throw propagate(failure); }
    }
    @Override public boolean attached() { return bridge.address()!=0; }
    @Override public String error() {
        try(Arena memory=Arena.ofConfined()) {
            MemorySegment text=memory.allocate(1024);text.fill((byte)0);
            int result=(int)error.invokeExact(bridge,text,1024L);
            return result==0?text.getString(0):"Native presentation error unavailable";
        } catch(Throwable failure) { return "Native presentation error: "+failure.getClass().getSimpleName(); }
    }
    @Override public long query(int key) {
        if(!attached())return 0;
        try { return (long)query.invokeExact(bridge,key); } catch(Throwable failure) { throw propagate(failure); }
    }
    private static RuntimeException propagate(Throwable failure) {
        if(failure instanceof RuntimeException runtime)return runtime;
        if(failure instanceof Error error)throw error;
        return new IllegalStateException("Native Metal presentation call failed",failure);
    }
}
