// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import pl.tomgirl.pylon.window.GpuSurface;
import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class MetalCompositeSurfaceTest {
    private static final class Fixture {
        final List<String> events=new ArrayList<>();
        final Host host=new Host();final Calls calls=new Calls();final Gl gl=new Gl();
        final MetalCompositeSurface surface=new MetalCompositeSurface(gl,calls,host,MetalCompositeSurface.RGBA8_LINEAR,()->events.add("ready"));
        void open(){assertEquals(77,surface.createWindow("Arcadium",1920,1080,true));}
        final class Host implements MetalCompositeSurface.Host {
            boolean main;int width=1920,height=1080;
            @Override public <T>T main(Supplier<T> task){assertFalse(main);main=true;try{return task.get();}finally{main=false;}}
            @Override public long cocoaWindow(long window){assertTrue(main);assertEquals(77,window);return 88;}
            @Override public int width(){return width;}
            @Override public int height(){return height;}
        }
        final class Calls implements CurrentFrameCalls {
            boolean attached,visible;int createResult,prepareResult,publishResult,hideResult,releaseResult,destroyResult;
            int releases,prepares,hides,destroys;long prepared,lastAddress;MemorySegment lastFrame;
            @Override public int create(long window){assertTrue(host.main);assertEquals(88,window);events.add("attach");attached=createResult==0;return createResult;}
            @Override public int prepare(MemorySegment frame){
                assertFalse(host.main);assertTrue(attached);prepares++;lastFrame=frame;
                if(lastAddress!=0)assertEquals(lastAddress,frame.address());lastAddress=frame.address();
                long id=frame.get(JAVA_LONG,0);assertTrue(id>prepared);prepared=id;
                assertEquals(host.width,frame.get(JAVA_INT,8));assertEquals(host.height,frame.get(JAVA_INT,12));
                assertEquals(0,frame.get(JAVA_INT,16));assertEquals(0x0405,frame.get(JAVA_INT,20));
                assertEquals(1,frame.get(JAVA_INT,24));assertEquals(0,frame.get(JAVA_INT,28));
                events.add("prepare"+id);return prepareResult;
            }
            @Override public int publish(long id){assertTrue(host.main);assertEquals(prepared,id);events.add("publish"+id);visible=publishResult==0;return publishResult;}
            @Override public int hide(){assertTrue(host.main);events.add("hide");hides++;if(hideResult==0)visible=false;return hideResult;}
            @Override public int releaseGl(){assertFalse(host.main);assertTrue(gl.current);events.add("releaseGL");releases++;return releaseResult;}
            @Override public int destroy(){assertTrue(host.main);events.add("detach");destroys++;if(destroyResult==0){attached=visible=false;}return destroyResult;}
            @Override public boolean attached(){return attached;}
            @Override public String error(){return "Owned presentation test rejection";}
            @Override public long query(int key){return key==6&&attached?1:0;}
        }
        final class Gl implements GpuSurface {
            boolean current;int swaps,destroys;
            @Override public long createWindow(String title,int width,int height,boolean resizable){assertEquals("Arcadium",title);assertEquals(1920,width);assertEquals(1080,height);assertTrue(resizable);events.add("createGL");current=true;return 77;}
            @Override public void makeCurrent(){events.add("makeCurrent");current=true;}
            @Override public void releaseCurrent(){events.add("releaseCurrent");current=false;}
            @Override public void swapBuffers(){assertFalse(calls.visible,"A stale Metal layer covered the current GL frame");assertTrue(current);events.add("swapGL");swaps++;}
            @Override public void setVSyncEnabled(boolean enabled){events.add("vsync"+enabled);}
            @Override public void destroy(){assertFalse(calls.attached,"SDL destruction happened before child-view detach");events.add("destroyGL");destroys++;current=false;}
        }
    }

    @Test void successfulFramesKeepTheOriginalWindowAndUseCurrentFrameIdentifiers() {
        Fixture f=new Fixture();f.open();
        try {
            f.surface.setVSyncEnabled(false);f.surface.swapBuffers();f.surface.swapBuffers();
            assertEquals(2,f.surface.submittedFrames());assertEquals(0,f.gl.swaps);assertEquals(0,f.surface.fallbackFrames());
            assertEquals(List.of("prepare1","publish1","prepare2","publish2"),f.events.stream().filter(e->e.startsWith("prepare")||e.startsWith("publish")).toList());
            assertTrue(f.events.contains("vsyncfalse"));
        } finally {f.surface.destroy();}
        assertFalse(f.calls.lastFrame.scope().isAlive());assertEquals(1,f.calls.releases);assertEquals(1,f.calls.destroys);assertEquals(1,f.gl.destroys);
        assertTrue(f.events.indexOf("releaseGL")<f.events.indexOf("detach"));assertTrue(f.events.indexOf("detach")<f.events.indexOf("destroyGL"));
    }
    @Test void aBusyFrameHidesThePriorMetalImageBeforeStickySameFrameGlFallback() {
        Fixture f=new Fixture();f.open();
        try {
            f.surface.swapBuffers();f.calls.prepareResult=2;f.surface.swapBuffers();f.surface.swapBuffers();
            assertEquals(1,f.surface.submittedFrames());assertEquals(2,f.gl.swaps);assertEquals(2,f.surface.fallbackFrames());
            assertEquals(2,f.calls.prepares);assertEquals(1,f.calls.hides);
            assertTrue(f.events.indexOf("hide")<f.events.indexOf("swapGL"));assertFalse(f.events.contains("publish2"));
        } finally {f.surface.destroy();}
    }
    @Test void aPublishFailureAlsoHidesBeforeDelegatingGl() {
        Fixture f=new Fixture();f.open();f.calls.publishResult=-3;
        try {
            f.surface.swapBuffers();assertEquals(1,f.calls.prepares);assertEquals(1,f.calls.hides);assertEquals(1,f.gl.swaps);
            assertTrue(f.events.indexOf("publish1")<f.events.indexOf("hide"));assertTrue(f.events.indexOf("hide")<f.events.indexOf("swapGL"));
        } finally {f.surface.destroy();}
    }
    @Test void failedHidingNeverSwapsGlOrRetriesSilently() {
        Fixture f=new Fixture();f.open();f.calls.prepareResult=2;f.calls.hideResult=-3;
        try {
            assertThrows(IllegalStateException.class,f.surface::swapBuffers);assertEquals(1,f.calls.hides);assertEquals(0,f.gl.swaps);
            assertThrows(IllegalStateException.class,f.surface::swapBuffers);assertEquals(1,f.calls.prepares);assertEquals(1,f.calls.hides);
        } finally {f.surface.destroy();}
    }
    @Test void cleanupFailuresKeepTheParentAliveAndCanBeRetriedWithoutReleasingGlTwice() {
        Fixture f=new Fixture();f.open();f.surface.swapBuffers();f.calls.releaseResult=-3;
        assertThrows(IllegalStateException.class,f.surface::destroy);assertEquals(0,f.gl.destroys);assertTrue(f.calls.attached);
        assertThrows(IllegalStateException.class,f.surface::swapBuffers);
        f.calls.releaseResult=0;f.calls.destroyResult=-3;
        assertThrows(IllegalStateException.class,f.surface::destroy);assertEquals(2,f.calls.releases);assertEquals(0,f.gl.destroys);
        f.calls.destroyResult=0;f.surface.destroy();f.surface.destroy();
        assertEquals(2,f.calls.releases);assertEquals(2,f.calls.destroys);assertEquals(1,f.gl.destroys);assertFalse(f.calls.lastFrame.scope().isAlive());
    }
    @Test void detachRestoresTheOriginalContextOnlyForCleanupThenAllowsOrdinaryGl() {
        Fixture f=new Fixture();f.open();f.surface.releaseCurrent();
        f.surface.detachPresentation();assertFalse(f.gl.current);assertFalse(f.calls.attached);
        assertTrue(f.events.indexOf("makeCurrent")<f.events.indexOf("releaseGL"));
        f.surface.makeCurrent();f.surface.swapBuffers();assertEquals(1,f.gl.swaps);assertEquals(0,f.calls.prepares);
        f.surface.destroy();assertEquals(1,f.gl.destroys);
    }
    @Test void creationFailureLeavesNoChildAndKeepsTheExistingGlWindow() {
        Fixture f=new Fixture();f.calls.createResult=-2;f.open();
        f.surface.swapBuffers();assertEquals(1,f.gl.swaps);assertEquals(0,f.calls.prepares);assertFalse(f.calls.attached);
        f.surface.destroy();assertEquals(0,f.calls.releases);assertEquals(0,f.calls.destroys);assertEquals(1,f.gl.destroys);
    }
    @Test void invalidResizeAndForeignThreadsCannotPublishOrReuseAPreviousFrame() throws Exception {
        Fixture f=new Fixture();f.open();
        try {
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread foreign=new Thread(()->{try{f.surface.swapBuffers();}catch(Throwable error){failure.set(error);}});foreign.start();foreign.join(2000);
            assertFalse(foreign.isAlive());assertInstanceOf(IllegalStateException.class,failure.get());assertEquals(0,f.calls.prepares);
            f.surface.swapBuffers();f.host.width=0;f.surface.swapBuffers();
            assertEquals(1,f.calls.prepares);assertEquals(1,f.calls.hides);assertEquals(1,f.gl.swaps);assertEquals(1,f.surface.submittedFrames());
        } finally {f.surface.destroy();}
    }
}
