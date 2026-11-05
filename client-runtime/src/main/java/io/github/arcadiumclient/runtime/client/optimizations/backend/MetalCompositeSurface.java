// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import pl.tomgirl.pylon.window.DisplaySdl;
import pl.tomgirl.pylon.window.GpuSurface;
import pl.tomgirl.pylon.window.MainThread;
import static org.lwjgl.sdl.SDLProperties.SDL_GetPointerProperty;
import static org.lwjgl.sdl.SDLVideo.*;

public final class MetalCompositeSurface implements GpuSurface {
    public static final int RGBA8_LINEAR=1,RGBA8_SRGB=2;
    interface Host {
        <T>T main(Supplier<T> task);
        long cocoaWindow(long sdlWindow);
        int width();
        int height();
    }
    static final class PylonHost implements Host {
        @Override public <T>T main(Supplier<T> task) { return MainThread.call(task); }
        @Override public long cocoaWindow(long window) {
            int properties=SDL_GetWindowProperties(window);
            return properties==0?0:SDL_GetPointerProperty(properties,SDL_PROP_WINDOW_COCOA_WINDOW_POINTER,0);
        }
        @Override public int width() { return DisplaySdl.instance().getWidth(); }
        @Override public int height() { return DisplaySdl.instance().getHeight(); }
    }
    private final Thread owner=Thread.currentThread();
    private final GpuSurface delegate;
    private final CurrentFrameCalls calls;
    private final Host host;
    private final Runnable ready;
    private final int encoding;
    private final Arena memory;
    private final MemorySegment frame;
    private boolean attempted,created,current,detached,destroyed,fallback,glReleased,closing;
    private long identifier,submittedFrames,fallbackFrames;
    private String mainError,fallbackReason="Not attached";

    public static MetalCompositeSurface prototype(MetalResidentSceneBackend scene,GpuSurface original,int sourceColorEncoding) {
        scene.presentationAvailable();
        MetalCompositeSurface surface=new MetalCompositeSurface(original,new MetalPresentationCalls(scene.presentationSymbols(),scene.presentationContext()),new PylonHost(),sourceColorEncoding,scene::presentationReady);
        scene.ownPresentation(surface::detachPresentation);
        return surface;
    }
    MetalCompositeSurface(GpuSurface delegate,CurrentFrameCalls calls,Host host,int encoding,Runnable ready) {
        if(encoding!=RGBA8_LINEAR&&encoding!=RGBA8_SRGB)throw new IllegalArgumentException("Specify the actual default-backbuffer color encoding");
        this.delegate=Objects.requireNonNull(delegate);this.calls=Objects.requireNonNull(calls);
        this.host=Objects.requireNonNull(host);this.ready=Objects.requireNonNull(ready);this.encoding=encoding;
        memory=Arena.ofConfined();frame=memory.allocate(MetalPresentFrame.BYTES,8);
    }
    private void owner() { if(Thread.currentThread()!=owner)throw new IllegalStateException("The presentation surface belongs to its render thread"); }
    private void live() { owner();if(!created||destroyed)throw new IllegalStateException("The presentation surface is not open"); }
    private int main(IntSupplier operation) {
        return host.main(()->{int result=operation.getAsInt();mainError=result==0?null:calls.error();return result;});
    }
    @Override public long createWindow(String title,int width,int height,boolean resizable) {
        owner();if(attempted||destroyed)throw new IllegalStateException("The presentation surface has already been created");
        if(!detached)ready.run();attempted=true;
        long window=delegate.createWindow(title,width,height,resizable);created=current=true;
        if(detached)return window;
        try {
            int result=main(()->calls.create(host.cocoaWindow(window)));
            if(result!=0) {
                if(calls.attached())throw new IllegalStateException("Native presentation creation did not roll back its window attachment");
                fallback=true;fallbackReason=mainError;
            } else {
                if(!calls.attached())throw new IllegalStateException("Native presentation returned no bridge");
                fallbackReason="Current-frame Metal compositor prototype";
            }
            return window;
        } catch(RuntimeException|Error failure) {
            try { detachPresentation();delegate.destroy();created=current=false;destroyed=true; }
            catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @Override public void makeCurrent() { live();delegate.makeCurrent();current=true; }
    @Override public void releaseCurrent() { live();delegate.releaseCurrent();current=false; }
    @Override public void setVSyncEnabled(boolean enabled) { owner();if(destroyed)throw new IllegalStateException("The surface is closed");delegate.setVSyncEnabled(enabled); }
    @Override public void swapBuffers() {
        live();if(!current)throw new IllegalStateException("The original OpenGL context must be current");
        if(closing&&!detached)throw new IllegalStateException("Presentation cleanup must finish before another frame");
        if(!fallback) {
            ready.run();
            String rejection=null;Throwable cause=null;
            try {
                if(identifier==Long.MAX_VALUE)throw new IllegalStateException("Presentation frame identifiers are exhausted");
                MetalPresentFrame.write(frame,++identifier,host.width(),host.height(),encoding);
                int result=calls.prepare(frame);
                if(result!=0)rejection=calls.error();
                else if(main(()->calls.publish(identifier))!=0)rejection=mainError;
                else {submittedFrames++;return;}
            } catch(RuntimeException failure) {
                rejection="Current-frame presentation failed: "+failure.getClass().getSimpleName();cause=failure;
            }
            enterFallback(rejection,cause);
        }
        fallbackFrames++;delegate.swapBuffers();
    }
    private void enterFallback(String reason,Throwable cause) {
        try {
            if(calls.attached()&&main(calls::hide)!=0)throw new IllegalStateException("Cannot hide the Metal child layer before OpenGL fallback: "+mainError,cause);
        } catch(RuntimeException|Error failure) { closing=true;throw failure; }
        fallback=true;fallbackReason=reason;
    }
    public void detachPresentation() {
        owner();if(detached)return;
        closing=true;
        boolean restoreRelease=created&&!current;
        if(restoreRelease){delegate.makeCurrent();current=true;}
        try {
            if(calls.attached()) {
                if(!glReleased) {
                    if(calls.releaseGl()!=0)throw new IllegalStateException("Cannot release presentation GL storage: "+calls.error());
                    glReleased=true;
                }
                if(main(calls::destroy)!=0)throw new IllegalStateException("Cannot detach the Metal child view: "+mainError);
                if(calls.attached())throw new IllegalStateException("Native presentation still owns a child view");
            }
            memory.close();fallback=true;detached=true;
        } finally {
            if(restoreRelease){delegate.releaseCurrent();current=false;}
        }
    }
    @Override public void destroy() {
        owner();if(destroyed)return;
        detachPresentation();delegate.destroy();created=current=false;destroyed=true;
    }
    public String status() { return fallback?"OpenGL: "+fallbackReason:fallbackReason; }
    public long submittedFrames() { return submittedFrames; }
    public long fallbackFrames() { return fallbackFrames; }
    public long nativeCounter(int key) { owner();return calls.query(key); }
}
