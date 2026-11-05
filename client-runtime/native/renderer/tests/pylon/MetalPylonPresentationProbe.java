// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import pl.tomgirl.pylon.window.DisplaySdl;
import pl.tomgirl.pylon.window.GlSurface;
import pl.tomgirl.pylon.window.GpuSurface;
import pl.tomgirl.pylon.window.MainThread;
import static org.lwjgl.sdl.SDLVideo.*;

public final class MetalPylonPresentationProbe {
    private static final int PREPARED=1, SUBMITTED=2, COMPLETED=3, DISPLAYED=4, BYTES=5, ATTACHED=6, HIDDEN=7;
    private static final DisplaySdl display=DisplaySdl.instance();
    private static final List<Map<String,Object>> frames=new ArrayList<>();
    private static String stage="start";

    private static final class OriginalSurface implements GpuSurface {
        final GlSurface delegate=new GlSurface(new PixelFormat());
        MetalCompositeSurface owner;
        long window,context;
        int creates,swaps,destroys;
        @Override public long createWindow(String title,int width,int height,boolean resizable) {
            require(++creates==1,"Original window was created more than once");
            window=delegate.createWindow(title,width,height,resizable);context=SDL_GL_GetCurrentContext();
            require(context!=0,"Original SDL GL context is absent");return window;
        }
        @Override public void makeCurrent() { delegate.makeCurrent(); }
        @Override public void releaseCurrent() { delegate.releaseCurrent(); }
        @Override public void swapBuffers() {
            require(owner==null||owner.nativeCounter(ATTACHED)==0||owner.nativeCounter(HIDDEN)==1,
                    "OpenGL swap preceded confirmed child-layer hiding");
            swaps++;sameWindow();delegate.swapBuffers();
        }
        @Override public void setVSyncEnabled(boolean enabled) { delegate.setVSyncEnabled(enabled); }
        @Override public void destroy() {
            require(owner==null||owner.nativeCounter(ATTACHED)==0,"SDL destruction preceded native child detachment");
            destroys++;delegate.destroy();
        }
        void sameWindow() {
            require(display.getHandle()==window&&SDL_GL_GetCurrentWindow()==window,"The original SDL window changed");
            require(SDL_GL_GetCurrentContext()==context,"The original GL context changed");
            require((SDL_GetWindowFlags(window)&SDL_WINDOW_OPENGL)!=0,"The SDL window lost OpenGL ownership");
            require((SDL_GetWindowFlags(window)&SDL_WINDOW_METAL)==0,"The live SDL window was converted to Metal");
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length!=4||!List.of("transport","fallback").contains(args[3]))
            throw new IllegalArgumentException("Pass library, reference Metal shader, owned report path and transport|fallback");
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            System.out.println("SKIP: Real SDL/Metal presentation requires macOS");System.exit(77);return;
        }
        require(!MainThread.DISPATCH,"Use -XstartOnFirstThread for this explicit main-thread SDL probe");
        Path output=Path.of(args[2]);Map<String,Object> report=new LinkedHashMap<>();
        report.put("case",args[3]);
        report.put("minecraft",false);report.put("skikoOrCeraQualified",false);report.put("performanceClaim",false);
        Throwable failure=null;
        try {
            if(args[3].equals("transport")) {
                stage="current-frame-window";currentFrames(Path.of(args[0]),Path.of(args[1]),report);
            } else {
                stage="unsupported-color-fallback";fallback(Path.of(args[0]),Path.of(args[1]),report);
            }
            report.put("passed",true);stage="complete";
        } catch(Throwable caught) {
            failure=caught;report.put("passed",false);report.put("failureClass",caught.getClass().getName());
            report.put("failure",String.valueOf(caught.getMessage()));
        } finally {
            report.put("stage",stage);report.put("frames",frames);report.put("mainDispatch",MainThread.DISPATCH);
            Files.writeString(output,json(report)+"\n");
        }
        if(failure!=null)throw new AssertionError("Actual Pylon presentation probe failed at "+stage,failure);
        System.out.println("Actual Pylon/SDL Java Metal surface: current displayed IDs, resize, original GL fallback and teardown passed");
    }

    private static MetalResidentSceneBackend scene(Path library,Path shader) throws Exception {
        return MetalResidentSceneBackend.open(library,new ResidentSceneBackend.Layout(2,80,20,64),
                Files.readString(shader),"vertexMain","fragmentMain");
    }
    private static void create(MetalResidentSceneBackend backend,OriginalSurface original,int encoding) throws Exception {
        require(!display.isCreated(),"Previous SDL display remains open");
        display.setTitle("Arcadium Metal surface probe");display.setDisplayMode(new DisplayMode(64,64));
        display.setHighPixelDensity(true);display.setResizable(true);
        original.owner=MetalCompositeSurface.prototype(backend,original,encoding);
        display.setSurface(original.owner);display.create(original);original.sameWindow();
    }
    private static void currentFrames(Path library,Path shader,Map<String,Object> report) throws Exception {
        MetalResidentSceneBackend backend=scene(library,shader);OriginalSurface original=new OriginalSurface();
        try {
            create(backend,original,MetalCompositeSurface.RGBA8_LINEAR);
            for(int frame=1;frame<=3;frame++) {
                stage="current-frame-"+frame;
                if(frame==3) {
                    MainThread.run(()->require(SDL_SetWindowSize(original.window,96,80),"SDL resize was rejected"));
                    long limit=System.nanoTime()+5_000_000_000L;
                    while((display.getWindowWidth()!=96||display.getWindowHeight()!=80)&&System.nanoTime()<limit)pump();
                    require(display.getWindowWidth()==96&&display.getWindowHeight()==80,"SDL resize events did not settle");
                    original.owner.makeCurrent();
                }
                display.setVSyncEnabled(frame==2);draw(frame);original.owner.swapBuffers();
                awaitDisplayed(original,frame);original.sameWindow();
                require(original.swaps==0&&original.owner.submittedFrames()==frame&&original.owner.fallbackFrames()==0,
                        "The current-frame Metal path unexpectedly used OpenGL fallback: "+original.owner.status());
                require(original.owner.nativeCounter(PREPARED)==frame&&original.owner.nativeCounter(SUBMITTED)==frame,
                        "Current submitted frame IDs do not match");
                require(original.owner.nativeCounter(BYTES)>0&&original.owner.nativeCounter(BYTES)<=256L*1024*1024,
                        "Shared image ownership is absent or over budget");
                require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL state error after Metal presentation");
            }
            stage="backend-close-with-live-sdl-window";
            original.owner.releaseCurrent();require(SDL_GL_GetCurrentContext()==0,"Original context release failed");
            backend.close();require(SDL_GL_GetCurrentContext()==0,"Backend cleanup did not restore the released context state");
            require(original.owner.nativeCounter(ATTACHED)==0,"Backend close retained its child view");
            original.owner.makeCurrent();draw(4);original.owner.swapBuffers();original.sameWindow();
            require(original.swaps==1&&original.owner.fallbackFrames()==1,"The live original window did not resume GL swaps");
            report.put("displayedCurrentFrames",3);report.put("resize",true);report.put("originalWindowAndContextRetained",true);
            report.put("backendCloseRestoresReleasedContext",true);report.put("glFallbackAfterSuccessfulMetal",true);
        } finally {
            if(display.isCreated())display.destroy();backend.close();
            require(original.destroys==1&&!display.isCreated(),"Original SDL window was not destroyed exactly once");
        }
    }
    private static void fallback(Path library,Path shader,Map<String,Object> report) throws Exception {
        MetalResidentSceneBackend backend=scene(library,shader);OriginalSurface original=new OriginalSurface();
        try {
            create(backend,original,MetalCompositeSurface.RGBA8_SRGB);
            for(int frame=1;frame<=2;frame++) {
                draw(frame);original.owner.swapBuffers();original.sameWindow();display.processMessages();
                require(original.owner.submittedFrames()==0&&original.owner.nativeCounter(SUBMITTED)==0,
                        "Unsupported color transport submitted a Metal frame");
                require(original.owner.nativeCounter(HIDDEN)==1,"Fallback retained a visible stale Metal child");
                require(original.swaps==frame&&original.owner.fallbackFrames()==frame,"Sticky GL fallback did not swap the current frame");
            }
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL fallback state error");
            report.put("unsupportedColorStickyFallback",true);report.put("fallbackHidesBeforeGlSwap",true);
        } finally {
            if(display.isCreated())display.destroy();backend.close();
            require(original.destroys==1&&!display.isCreated(),"Fallback SDL window was not destroyed exactly once");
        }
    }
    private static void awaitDisplayed(OriginalSurface original,int frame) throws Exception {
        long limit=System.nanoTime()+5_000_000_000L;
        while((original.owner.nativeCounter(COMPLETED)!=frame||original.owner.nativeCounter(DISPLAYED)!=frame)
                &&System.nanoTime()<limit)pump();
        Map<String,Object> state=new LinkedHashMap<>();state.put("frame",frame);state.put("width",display.getWidth());
        state.put("height",display.getHeight());state.put("prepared",original.owner.nativeCounter(PREPARED));
        state.put("submitted",original.owner.nativeCounter(SUBMITTED));state.put("completed",original.owner.nativeCounter(COMPLETED));
        state.put("displayed",original.owner.nativeCounter(DISPLAYED));state.put("status",original.owner.status());
        state.put("sdlVisible",display.isVisible());state.put("sdlFlags",SDL_GetWindowFlags(original.window));frames.add(state);
        require(original.owner.nativeCounter(COMPLETED)==frame&&original.owner.nativeCounter(DISPLAYED)==frame,
                "Actual SDL Metal presentation did not report its current displayed frame");
    }
    private static void pump() throws Exception { display.processMessages();Thread.sleep(2); }
    private static void draw(int frame) {
        int width=display.getWidth(),height=display.getHeight();require(width>0&&height>0,"Invalid real framebuffer size");
        GL11.glViewport(0,0,width,height);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DITHER);GL11.glEnable(GL11.GL_SCISSOR_TEST);
        for(int corner=0;corner<4;corner++) {
            int value=(corner+frame)%4;GL11.glScissor((corner&1)*width/2,(corner>>1)*height/2,width/2,height/2);
            GL11.glClearColor((value+1)/5f,(4-value)/5f,.25f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private static String json(Object value) {
        if(value instanceof Map<?,?> map) {
            StringBuilder text=new StringBuilder("{");boolean first=true;
            for(var entry:map.entrySet()){if(!first)text.append(',');first=false;text.append(json(String.valueOf(entry.getKey()))).append(':').append(json(entry.getValue()));}
            return text.append('}').toString();
        }
        if(value instanceof List<?> list) {StringBuilder text=new StringBuilder("[");for(int i=0;i<list.size();i++){if(i>0)text.append(',');text.append(json(list.get(i)));}return text.append(']').toString();}
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value==null)return "null";
        return '"'+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+'"';
    }
}
