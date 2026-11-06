// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
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
    private static boolean serviceCocoaLoop;
    private static final long[] runloopResults=new long[5];
    private static long runloopCalls;
    private static final class CocoaLoop {
        static final Arena arena=Arena.ofAuto();
        static final SymbolLookup symbols=SymbolLookup.libraryLookup("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation",arena);
        static final Linker linker=Linker.nativeLinker();
        static final MethodHandle current=function("CFRunLoopGetCurrent",FunctionDescriptor.of(ValueLayout.ADDRESS));
        static final MethodHandle main=function("CFRunLoopGetMain",FunctionDescriptor.of(ValueLayout.ADDRESS));
        static final MethodHandle run=function("CFRunLoopRunInMode",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_DOUBLE,ValueLayout.JAVA_BYTE));
        static final MemorySegment mode=symbols.find("kCFRunLoopDefaultMode").orElseThrow()
                .reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS,0);
        static MethodHandle function(String name,FunctionDescriptor descriptor) {
            return linker.downcallHandle(symbols.find(name).orElseThrow(()->new UnsupportedOperationException("Required test runloop symbol absent: "+name)),descriptor);
        }
        static int service() {
            try {
                MemorySegment here=(MemorySegment)current.invokeExact(),original=(MemorySegment)main.invokeExact();
                require(here.address()!=0&&here.address()==original.address()&&mode.address()!=0,"Runloop service is not on the actual Cocoa main thread");
                int result=(int)run.invokeExact(mode,0.002,(byte)0);require(result>=1&&result<=4,"Unknown native runloop result");
                runloopCalls++;runloopResults[result]++;return result;
            }catch(Throwable failure){throw new IllegalStateException("Bounded test Cocoa runloop service failed",failure);}
        }
    }
    private static final class Cgl {
        static final int SURFACE_ORDER=235;
        static final Arena libraryArena=Arena.ofAuto();
        static final SymbolLookup symbols=SymbolLookup.libraryLookup("/System/Library/Frameworks/OpenGL.framework/OpenGL",libraryArena);
        static final Linker linker=Linker.nativeLinker();
        static final MethodHandle current=function("CGLGetCurrentContext",FunctionDescriptor.of(ValueLayout.ADDRESS));
        static final MethodHandle get=function("CGLGetParameter",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
        static final MethodHandle set=function("CGLSetParameter",FunctionDescriptor.of(ValueLayout.JAVA_INT,ValueLayout.ADDRESS,ValueLayout.JAVA_INT,ValueLayout.ADDRESS));
        static MethodHandle function(String name,FunctionDescriptor descriptor) {
            return linker.downcallHandle(symbols.find(name).orElseThrow(()->new UnsupportedOperationException("Required CGL test symbol is absent: "+name)),descriptor);
        }
        static long current() {
            try { return ((MemorySegment)current.invokeExact()).address(); }
            catch(Throwable error){throw new IllegalStateException("Current CGL test context is unavailable",error);}
        }
        static int get(long context) {
            try(Arena arena=Arena.ofConfined()) {
                MemorySegment value=arena.allocate(ValueLayout.JAVA_INT);
                int code=(int)get.invokeExact(MemorySegment.ofAddress(context),SURFACE_ORDER,value);
                require(code==0,"CGL test parameter read failed");return value.get(ValueLayout.JAVA_INT,0);
            } catch(Throwable error){throw new IllegalStateException("CGL test parameter read failed",error);}
        }
        static int set(long context,int value) {
            try(Arena arena=Arena.ofConfined()) {
                MemorySegment input=arena.allocate(ValueLayout.JAVA_INT);input.set(ValueLayout.JAVA_INT,0,value);
                return (int)set.invokeExact(MemorySegment.ofAddress(context),SURFACE_ORDER,input);
            } catch(Throwable error){throw new IllegalStateException("CGL test parameter write failed",error);}
        }
    }

    private static final class OriginalSurface implements GpuSurface {
        final GlSurface delegate=new GlSurface(new PixelFormat());
        MetalCompositeSurface owner;
        long window,context,cglContext;
        int savedSurfaceOrder,requestedSurfaceOrder;
        boolean surfaceOrderChanged;
        Map<String,Object> report;
        int creates,swaps,destroys;
        @Override public long createWindow(String title,int width,int height,boolean resizable) {
            require(++creates==1,"Original window was created more than once");
            window=delegate.createWindow(title,width,height,resizable);context=SDL_GL_GetCurrentContext();
            require(context!=0,"Original SDL GL context is absent");return window;
        }
        @Override public void makeCurrent() { delegate.makeCurrent(); }
        @Override public void releaseCurrent() { delegate.releaseCurrent(); }
        @Override public void swapBuffers() {
            restoreSurfaceOrder();
            require(owner==null||owner.nativeCounter(ATTACHED)==0||owner.nativeCounter(HIDDEN)==1,
                    "OpenGL swap preceded confirmed child-layer hiding");
            swaps++;sameWindow();delegate.swapBuffers();
        }
        @Override public void setVSyncEnabled(boolean enabled) { delegate.setVSyncEnabled(enabled); }
        @Override public void destroy() {
            require(owner==null||owner.nativeCounter(ATTACHED)==0,"SDL destruction preceded native child detachment");
            try { restoreSurfaceOrder(); } finally { destroys++;delegate.destroy(); }
        }
        void sameWindow() {
            require(display.getHandle()==window&&SDL_GL_GetCurrentWindow()==window,"The original SDL window changed");
            require(SDL_GL_GetCurrentContext()==context,"The original GL context changed");
            require((SDL_GetWindowFlags(window)&SDL_WINDOW_OPENGL)!=0,"The SDL window lost OpenGL ownership");
            require((SDL_GetWindowFlags(window)&SDL_WINDOW_METAL)==0,"The live SDL window was converted to Metal");
            if(cglContext!=0)require(Cgl.current()==cglContext,"The original CGL context changed");
        }
        int surfaceOrder() {
            require(cglContext!=0&&Cgl.current()==cglContext,"Surface order requires the original current CGL context");
            return Cgl.get(cglContext);
        }
        void configureSurfaceOrder(int value,Map<String,Object> report) {
            this.report=report;cglContext=Cgl.current();savedSurfaceOrder=surfaceOrder();
            require(savedSurfaceOrder==0,"Fresh control CGL surface order is not zero");requestedSurfaceOrder=value;
            report.put("originalSurfaceOrder",savedSurfaceOrder);report.put("requestedSurfaceOrder",value);
            if(value!=savedSurfaceOrder) {
                surfaceOrderChanged=true;
                require(Cgl.set(cglContext,value)==0,"CGL surface order setup failed");
            }
            require(surfaceOrder()==value,"The selected CGL surface order did not apply");
        }
        void restoreSurfaceOrder() {
            if(!surfaceOrderChanged)return;
            require(Cgl.current()==cglContext,"Cannot restore a foreign or released CGL context");
            require(Cgl.set(cglContext,savedSurfaceOrder)==0,
                    "Original CGL surface order restoration failed");
            require(surfaceOrder()==savedSurfaceOrder,"Original CGL surface order was not restored");
            surfaceOrderChanged=false;report.put("surfaceOrderRestoredBeforeFallbackAndClose",true);
        }
    }

    public static void main(String[] args) throws Exception {
        if((args.length!=4&&args.length!=5)||!List.of("transport","transport-sync0","transport-sync1","fallback","transport-runloop","transport-sync0-runloop").contains(args[3])
                ||args.length==5&&!List.of("0","-1").contains(args[4]))
            throw new IllegalArgumentException("Pass library, reference Metal shader, owned report path and transport[-sync0|-sync1]|fallback");
        serviceCocoaLoop=args[3].endsWith("-runloop");
        if(serviceCocoaLoop&&args.length==5&&!args[4].equals("0"))throw new IllegalArgumentException("The runloop comparison uses the unchanged original surface order");
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")) {
            System.out.println("SKIP: Real SDL/Metal presentation requires macOS");System.exit(77);return;
        }
        require(!MainThread.DISPATCH,"Use -XstartOnFirstThread for this explicit main-thread SDL probe");
        Path output=Path.of(args[2]);Map<String,Object> report=new LinkedHashMap<>();
        report.put("case",args[3]);
        report.put("pumpStyle",serviceCocoaLoop?"SDL-poll-plus-2ms-Cocoa-runloop":"SDL-poll-plus-2ms-sleep");
        report.put("currentDisplayedTimeoutNanoseconds",5_000_000_000L);
        report.put("expectedHostedOwnership",Boolean.getBoolean("arcadium.present072.expectedHostedLayer"));
        report.put("minecraft",false);report.put("skikoOrCeraQualified",false);report.put("performanceClaim",false);
        report.put("detailedObservationAfterDisplayDecisionOnly",true);
        Throwable failure=null;
        try {
            if(args[3].startsWith("transport")) {
                String pacingCase=serviceCocoaLoop?args[3].substring(0,args[3].length()-"-runloop".length()):args[3];
                stage="current-frame-window";currentFrames(Path.of(args[0]),Path.of(args[1]),report,pacingCase,args.length==5?Integer.parseInt(args[4]):0);
            } else {
                stage="unsupported-color-fallback";fallback(Path.of(args[0]),Path.of(args[1]),report,args.length==5?Integer.parseInt(args[4]):0);
            }
            report.put("passed",true);stage="complete";
        } catch(Throwable caught) {
            failure=caught;report.put("passed",false);report.put("failureClass",caught.getClass().getName());
            report.put("failure",String.valueOf(caught.getMessage()));
        } finally {
            report.put("stage",stage);report.put("frames",frames);report.put("mainDispatch",MainThread.DISPATCH);
            report.put("cocoaRunloopServiceCalls",runloopCalls);report.put("cocoaRunloopResults",List.of(runloopResults[1],runloopResults[2],runloopResults[3],runloopResults[4]));
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
        require((original.owner.nativeCounter(72)==1)==Boolean.getBoolean("arcadium.present072.expectedHostedLayer"),
                "The actual child-layer ownership differs from the selected experiment");
    }
    private static void currentFrames(Path library,Path shader,Map<String,Object> report,String pacingCase,int surfaceOrder) throws Exception {
        MetalResidentSceneBackend backend=scene(library,shader);OriginalSurface original=new OriginalSurface();
        try {
            create(backend,original,MetalCompositeSurface.RGBA8_LINEAR);
            original.configureSurfaceOrder(surfaceOrder,report);
            int[] widths={64,64,96,112,80,96},heights={64,64,80,80,96,80};
            for(int frame=1;frame<=widths.length;frame++) {
                stage="current-frame-"+frame;
                if(frame>=3) {
                    int width=widths[frame-1],height=heights[frame-1];
                    MainThread.run(()->{
                        require(SDL_SetWindowSize(original.window,width,height),"SDL resize was rejected");
                        require(SDL_SyncWindow(original.window),"The asynchronous SDL resize did not finalize");
                    });
                    long limit=System.nanoTime()+5_000_000_000L;
                    while((display.getWindowWidth()!=width||display.getWindowHeight()!=height)&&System.nanoTime()<limit)pump();
                    require(display.getWindowWidth()==width&&display.getWindowHeight()==height,"SDL resize events did not settle");
                    original.owner.makeCurrent();
                }
                Map<String,Object> before=geometry(original);before.put("frame",frame);before.put("stage","before-publish");frames.add(before);
                require(original.surfaceOrder()==surfaceOrder,"CGL surface order changed before current-frame submission");
                boolean synced=pacingCase.equals("transport-sync1")||pacingCase.equals("transport")&&(frame&1)==0;
                display.setVSyncEnabled(synced);draw(frame);original.owner.swapBuffers();
                awaitDisplayed(original,frame);original.sameWindow();
                Map<String,Object> pacing=pacing(original);pacing.put("frame",frame);
                report.put("pacing"+frame,pacing);
                require(original.swaps==0&&original.owner.submittedFrames()==frame&&original.owner.fallbackFrames()==0,
                        "The current-frame Metal path unexpectedly used OpenGL fallback: "+original.owner.status());
                require(original.owner.nativeCounter(PREPARED)==frame&&original.owner.nativeCounter(SUBMITTED)==frame,
                        "Current submitted frame IDs do not match");
                require(original.owner.nativeCounter(24)==1,"The current drawable was not published in its view transaction");
                require(original.owner.nativeCounter(BYTES)>0&&original.owner.nativeCounter(BYTES)<=256L*1024*1024,
                        "Shared image ownership is absent or over budget");
                require(GL11.glGetError()==GL11.GL_NO_ERROR,"GL state error after Metal presentation");
            }
            report.put("finalLayerAudit",layerState(original));
            stage="backend-close-with-live-sdl-window";
            original.restoreSurfaceOrder();require(original.surfaceOrder()==original.savedSurfaceOrder,"Surface order changed before backend close");
            original.owner.releaseCurrent();require(SDL_GL_GetCurrentContext()==0,"Original context release failed");
            backend.close();require(SDL_GL_GetCurrentContext()==0,"Backend cleanup did not restore the released context state");
            require(original.owner.nativeCounter(ATTACHED)==0,"Backend close retained its child view");
            original.owner.makeCurrent();draw(7);original.owner.swapBuffers();original.sameWindow();
            require(original.swaps==1&&original.owner.fallbackFrames()==1,"The live original window did not resume GL swaps");
            report.put("displayedCurrentFrames",6);report.put("resizeTransitions",4);report.put("resize",true);report.put("nativeResizeBarrier",true);
            report.put("originalWindowAndContextRetained",true);
            report.put("backendCloseRestoresReleasedContext",true);report.put("glFallbackAfterSuccessfulMetal",true);
        } finally {
            try { original.restoreSurfaceOrder(); } finally { if(display.isCreated())display.destroy();backend.close(); }
            require(original.destroys==1&&!display.isCreated(),"Original SDL window was not destroyed exactly once");
        }
    }
    private static void fallback(Path library,Path shader,Map<String,Object> report,int surfaceOrder) throws Exception {
        MetalResidentSceneBackend backend=scene(library,shader);OriginalSurface original=new OriginalSurface();
        try {
            create(backend,original,MetalCompositeSurface.RGBA8_SRGB);
            original.configureSurfaceOrder(surfaceOrder,report);
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
            try { original.restoreSurfaceOrder(); } finally { if(display.isCreated())display.destroy();backend.close(); }
            require(original.destroys==1&&!display.isCreated(),"Fallback SDL window was not destroyed exactly once");
        }
    }
    private static void awaitDisplayed(OriginalSurface original,int frame) throws Exception {
        long limit=System.nanoTime()+5_000_000_000L;
        while((original.owner.nativeCounter(COMPLETED)!=frame||original.owner.nativeCounter(DISPLAYED)!=frame)
                &&System.nanoTime()<limit)pump();
        boolean displayed=original.owner.nativeCounter(COMPLETED)==frame&&original.owner.nativeCounter(DISPLAYED)==frame;
        Map<String,Object> state=new LinkedHashMap<>();state.put("frame",frame);state.put("width",display.getWidth());
        state.put("height",display.getHeight());state.put("prepared",original.owner.nativeCounter(PREPARED));
        state.put("submitted",original.owner.nativeCounter(SUBMITTED));state.put("completed",original.owner.nativeCounter(COMPLETED));
        state.put("displayed",original.owner.nativeCounter(DISPLAYED));state.put("status",original.owner.status());
        state.put("sdlVisible",display.isVisible());state.put("sdlFlags",SDL_GetWindowFlags(original.window));frames.add(state);
        state.put("geometry",geometry(original));
        Map<String,Object> texture=new LinkedHashMap<>();
        String[] textureNames={"frame","width","height","arrayLength","textureType","sampleCount","pixelFormat"};
        for(int i=0;i<textureNames.length;i++)texture.put(textureNames[i],original.owner.nativeCounter(73+i));
        state.put("publishedTextureBeforePresent",texture);
        if(!displayed) {
            try {state.put("layerAuditAfterFailedDecision",layerState(original));}
            catch(Throwable error){state.put("layerAuditFailure",error.getClass().getName());}
            state.put("displayedAfterObservation",original.owner.nativeCounter(DISPLAYED));
        }
        require(displayed,"Actual SDL Metal presentation did not report its current displayed frame");
    }
    private static Map<String,Object> geometry(OriginalSurface original) {
        Map<String,Object> value=new LinkedHashMap<>();
        String[] realNames={"parentWidth","parentHeight","viewBoundsWidth","viewBoundsHeight","viewFrameWidth","viewFrameHeight"};
        for(int i=0;i<realNames.length;i++)value.put(realNames[i],Double.longBitsToDouble(original.owner.nativeCounter(11+i)));
        value.put("drawableWidth",original.owner.nativeCounter(17));value.put("drawableHeight",original.owner.nativeCounter(18));
        value.put("contentsScale",Double.longBitsToDouble(original.owner.nativeCounter(19)));
        value.put("windowBackingScale",Double.longBitsToDouble(original.owner.nativeCounter(20)));
        value.put("parentAttached",original.owner.nativeCounter(21)==1);value.put("windowVisible",original.owner.nativeCounter(22)==1);
        value.put("windowOcclusionVisible",original.owner.nativeCounter(23)==1);
        value.put("transactionalPresentation",original.owner.nativeCounter(24)==1);
        value.put("hostedOwnership",original.owner.nativeCounter(72)==1);
        return value;
    }
    private static Map<String,Object> layerState(OriginalSurface original) {
        Map<String,Object> value=geometry(original);
        List<Long> before=new ArrayList<>();
        for(int i=64;i<72;i++)before.add(original.owner.nativeCounter(i));
        value.put("layerChainBeforeParentLayerRead",before);
        String[] names={"childWindowMatches","parentViewId","childSuperviewId","childWindowId","parentLayerId",
                "childLayerId","childSuperlayerId","childLayerIndexPlusOne","parentLayerChildCount","hiddenAncestor",
                "childLayerHidden","childLayerOpacity","parentLayerHidden","parentLayerOpacity","layerContentsPresent",
                "viewWantsUpdateLayer","viewNeedsDisplay","layerDelegateId","presentationLayerId","preparedCglSwapInterval",
                "layerDisplaySyncEnabled","visibleWidth","visibleHeight","layerNeedsDisplay","viewOpaque",
                "currentGlViewLayerId","currentGlViewId","currentGlContextMatches"};
        for(int i=0;i<names.length;i++) {
            long raw=original.owner.nativeCounter(25+i);
            switch(i) {
                case 0,9,10,12,14,15,16,20,23,24,27 -> value.put(names[i],raw!=0);
                case 11,13,21,22 -> value.put(names[i],Double.longBitsToDouble(raw));
                default -> value.put(names[i],raw);
            }
        }
        List<Long> ancestors=new ArrayList<>();
        for(int i=64;i<72;i++)ancestors.add(original.owner.nativeCounter(i));
        value.put("layerChain",ancestors);
        value.put("originalGlSurfaceOrder",original.owner.nativeCounter(55));
        value.put("parentLayerZ",Double.longBitsToDouble(original.owner.nativeCounter(56)));
        value.put("childLayerZ",Double.longBitsToDouble(original.owner.nativeCounter(57)));
        String[] published={"publishedLayerId","publishedSuperlayerId","publishedDrawableLayerId","publishedViewId","publishedContentViewId","publishedDrawableId"};
        for(int i=0;i<published.length;i++)value.put(published[i],original.owner.nativeCounter(58+i));
        List<Map<String,Object>> siblings=new ArrayList<>();
        for(int i=0;i<4;i++) {
            int key=128+i*12;long id=original.owner.nativeCounter(key);if(id==0)break;
            Map<String,Object> entry=new LinkedHashMap<>();entry.put("index",i);entry.put("id",id);
            entry.put("kindFlags",original.owner.nativeCounter(key+1));entry.put("z",Double.longBitsToDouble(original.owner.nativeCounter(key+2)));
            entry.put("hidden",original.owner.nativeCounter(key+3)!=0);entry.put("opacity",Double.longBitsToDouble(original.owner.nativeCounter(key+4)));
            entry.put("contentsPresent",original.owner.nativeCounter(key+5)!=0);
            for(int j=6;j<=9;j++)entry.put(new String[]{"boundsWidth","boundsHeight","frameWidth","frameHeight"}[j-6],Double.longBitsToDouble(original.owner.nativeCounter(key+j)));
            entry.put("superlayerId",original.owner.nativeCounter(key+10));entry.put("delegateId",original.owner.nativeCounter(key+11));siblings.add(entry);
        }
        value.put("siblings",siblings);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var interval=stack.mallocInt(1);require(SDL_GL_GetSwapInterval(interval),"SDL swap interval observation failed");
            value.put("sdlSwapInterval",interval.get(0));
        }
        return value;
    }
    private static Map<String,Object> pacing(OriginalSurface original) {
        int interval;
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var value=stack.mallocInt(1);require(SDL_GL_GetSwapInterval(value),"Current SDL pacing is unavailable");interval=value.get(0);
        }
        require(interval==0||interval==1,"Unsupported logical SDL interval");
        long configured=original.owner.nativeCounter(53),prepared=original.owner.nativeCounter(54);
        boolean enabled=original.owner.nativeCounter(45)!=0;
        require(configured==interval&&prepared==interval&&enabled==(interval!=0),"SDL pacing was not mirrored to the actual current Metal layer");
        Map<String,Object> value=new LinkedHashMap<>();value.put("sdl",interval);value.put("configured",configured);
        value.put("prepared",prepared);value.put("layerDisplaySyncEnabled",enabled);return value;
    }
    private static void pump() throws Exception {
        display.processMessages();
        if(!serviceCocoaLoop){Thread.sleep(2);return;}
        long window=SDL_GL_GetCurrentWindow(),context=SDL_GL_GetCurrentContext();
        int outcome=MainThread.call(CocoaLoop::service);
        require(SDL_GL_GetCurrentWindow()==window&&SDL_GL_GetCurrentContext()==context,"Test servicing changed the original current window/context");
        if(outcome==1||outcome==2)Thread.sleep(2);
    }
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
