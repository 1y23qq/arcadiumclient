// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.jetbrains.skia.*;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.PixelFormat;
import pl.tomgirl.pylon.window.*;
import static java.lang.foreign.ValueLayout.*;
import static org.lwjgl.sdl.SDLVideo.*;

public final class MetalSceneHandoffProbe {
    private static final DisplaySdl display=DisplaySdl.instance();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<Map<String,Object>> observations=new ArrayList<>();
    private static final List<String> displayFailures=new ArrayList<>();
    private static final float[] QUAD={-1,-1,0,0,0,1,-1,0,1,0,1,1,0,1,1,-1,-1,0,0,0,1,1,0,1,1,-1,1,0,0,1};
    private static final byte[] MATERIAL={(byte)255,0,0,(byte)255,0,(byte)255,0,(byte)255,0,0,(byte)255,(byte)255,(byte)255,(byte)255,0,(byte)255};
    private static final class NativeCalls implements CurrentFrameCalls {
        final MemorySegment scene;MemorySegment bridge=MemorySegment.NULL;Object owner;
        final MethodHandle create,interval,prepare,prepareScene,publish,hide,release,destroy,query,readPixels,sceneQuery,errorText;
        NativeCalls(MetalResidentSceneBackend backend) {
            scene=backend.presentationContext();SymbolLookup symbols=backend.presentationSymbols();Linker l=Linker.nativeLinker();
            try{require((int)fn(l,symbols,"arc_present_abi",FunctionDescriptor.of(JAVA_INT)).invokeExact()==1&&(int)fn(l,symbols,"arc_present_scene_abi",FunctionDescriptor.of(JAVA_INT)).invokeExact()==1,"Presentation ABI differs");}catch(Throwable failure){throw error(failure);}
            create=fn(l,symbols,"arc_present_create",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS,ADDRESS));
            interval=fn(l,symbols,"arc_present_set_swap_interval",FunctionDescriptor.of(JAVA_INT,ADDRESS,JAVA_INT));
            prepare=fn(l,symbols,"arc_present_prepare",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS));
            prepareScene=fn(l,symbols,"arc_present_prepare_scene",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS,ADDRESS));
            publish=fn(l,symbols,"arc_present_publish",FunctionDescriptor.of(JAVA_INT,ADDRESS,JAVA_LONG));
            hide=fn(l,symbols,"arc_present_hide",FunctionDescriptor.of(JAVA_INT,ADDRESS));
            release=fn(l,symbols,"arc_present_release_gl",FunctionDescriptor.of(JAVA_INT,ADDRESS));
            destroy=fn(l,symbols,"arc_present_destroy",FunctionDescriptor.of(JAVA_INT,ADDRESS));
            query=fn(l,symbols,"arc_present_query",FunctionDescriptor.of(JAVA_LONG,ADDRESS,JAVA_INT));
            sceneQuery=fn(l,symbols,"arc_scene_query",FunctionDescriptor.of(JAVA_LONG,ADDRESS,JAVA_INT));
            errorText=fn(l,symbols,"arc_present_error",FunctionDescriptor.of(JAVA_INT,ADDRESS,ADDRESS,JAVA_LONG));
            readPixels=symbols.find("arc_present_testing_read_rgba").map(p->l.downcallHandle(p,FunctionDescriptor.of(JAVA_INT,ADDRESS,JAVA_LONG,ADDRESS,JAVA_LONG))).orElse(null);
        }
        static MethodHandle fn(Linker l,SymbolLookup s,String n,FunctionDescriptor d){return l.downcallHandle(s.find(n).orElseThrow(),d);}
        static RuntimeException error(Throwable e){return new IllegalStateException("Owned native handoff call failed",e);}
        @Override public boolean supportsSwapInterval(){return true;}
        @Override public boolean supportsSceneInput(){return true;}
        @Override public boolean reserveSceneOwner(Object value){if(owner!=null)return false;owner=value;return true;}
        @Override public void releaseSceneOwner(Object value){require(owner==value,"Ticket owner mismatch");owner=null;}
        @Override public int create(long window){try(Arena a=Arena.ofConfined()){MemorySegment out=a.allocate(ADDRESS);out.set(ADDRESS,0,MemorySegment.NULL);int code=(int)create.invokeExact(scene,MemorySegment.ofAddress(window),out);bridge=out.get(ADDRESS,0);return code;}catch(Throwable e){throw error(e);}}
        @Override public int setSwapInterval(int value){try{return(int)interval.invokeExact(bridge,value);}catch(Throwable e){throw error(e);}}
        @Override public int prepare(MemorySegment frame){try{return(int)prepare.invokeExact(bridge,frame);}catch(Throwable e){throw error(e);}}
        @Override public int prepareScene(long target,MemorySegment frame){try{return(int)prepareScene.invokeExact(bridge,MemorySegment.ofAddress(target),frame);}catch(Throwable e){throw error(e);}}
        @Override public int publish(long frame){try{return(int)publish.invokeExact(bridge,frame);}catch(Throwable e){throw error(e);}}
        @Override public int hide(){try{return(int)hide.invokeExact(bridge);}catch(Throwable e){throw error(e);}}
        @Override public int releaseGl(){try{return(int)release.invokeExact(bridge);}catch(Throwable e){throw error(e);}}
        @Override public int destroy(){try{int result=(int)destroy.invokeExact(bridge);if(result==0)bridge=MemorySegment.NULL;return result;}catch(Throwable e){throw error(e);}}
        @Override public boolean attached(){return bridge.address()!=0;}
        @Override public String error(){try(Arena a=Arena.ofConfined()){MemorySegment out=a.allocate(1024);out.fill((byte)0);return(int)errorText.invokeExact(bridge,out,1024L)==0?out.getString(0):"Native error unavailable";}catch(Throwable failure){return failure.getClass().getName();}}
        @Override public long query(int key){try{return(long)query.invokeExact(bridge,key);}catch(Throwable e){throw error(e);}}
        long scene(int key){try{return(long)sceneQuery.invokeExact(scene,key);}catch(Throwable e){throw error(e);}}
        ByteBuffer drawable(long frame,int bytes){ByteBuffer out=ByteBuffer.allocateDirect(bytes);try{require(readPixels!=null&&(int)readPixels.invokeExact(bridge,frame,MemorySegment.ofBuffer(out),(long)bytes)==0,"Actual drawable readback is unavailable");return out;}catch(Throwable e){throw error(e);}}
    }
    private static final class Original implements GpuSurface {
        final GlSurface delegate=new GlSurface(new PixelFormat());long window,context;int creates,swaps,destroys;NativeCalls calls;
        @Override public long createWindow(String title,int w,int h,boolean resize){require(++creates==1,"A second window was created");window=delegate.createWindow(title,w,h,resize);context=SDL_GL_GetCurrentContext();require(context!=0,"Original GL context absent");return window;}
        @Override public void makeCurrent(){delegate.makeCurrent();same();}
        @Override public void releaseCurrent(){delegate.releaseCurrent();}
        @Override public void setVSyncEnabled(boolean enabled){delegate.setVSyncEnabled(enabled);}
        @Override public void swapBuffers(){require(calls==null||!calls.attached()||calls.query(7)==1,"GL swapped before hiding Metal");same();swaps++;delegate.swapBuffers();}
        @Override public void destroy(){require(calls==null||!calls.attached(),"Original window destroyed before child detach");destroys++;delegate.destroy();}
        void same(){require(display.getHandle()==window&&SDL_GL_GetCurrentWindow()==window&&SDL_GL_GetCurrentContext()==context,"Original window/context changed");long f=SDL_GetWindowFlags(window);require((f&SDL_WINDOW_OPENGL)!=0&&(f&SDL_WINDOW_METAL)==0,"SDL ownership changed");}
    }
    public static void main(String[] args)throws Exception {
        require(args.length==6,"Pass library/hash, shader, font, report and ticket|pixels cohort");
        Path output=Path.of(args[4]);boolean passed=false;MetalResidentSceneBackend backend=null;NativeCalls calls=null;MetalSceneTicket ticket=null;MetalDeviceLease device=null;Original original=new Original();
        try {
            require(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac")&&!MainThread.DISPATCH,"Use Mac first-thread JVM");
            Path library=Path.of(args[0]);require(hash(library).equals(args[1]),"Native source library hash differs");boolean pixels=args[5].equals("pixels");require(pixels||args[5].equals("ticket"),"Unknown cohort");
            String shader=Files.readString(Path.of(args[2]));backend=scene(library,shader);calls=new NativeCalls(backend);original.calls=calls;
            require((calls.readPixels!=null)==pixels,"Production and instrumented cohorts must use separate matching libraries/JVMs");
            display.setTitle("Arcadium scene handoff QA");display.setDisplayMode(new DisplayMode(192,128));display.setResizable(true);display.setHighPixelDensity(false);display.setSurface(original);display.create(original);original.same();
            var host=new MetalCompositeSurface.PylonHost();NativeCalls selected=calls;
            require(host.main(()->selected.create(host.cocoaWindow(original.window)))==0,"Child bridge creation failed");
            original.setVSyncEnabled(true);int actual=interval();require(host.main(()->selected.setSwapInterval(actual))==0,"Actual SDL pacing rejected");
            ticket=new MetalSceneTicket(calls,operation->host.main(operation::getAsInt));device=backend.metalDeviceLease();
            DirectContext skia=device.createResource((d,q)->DirectContext.Companion.makeMetal(d,q));skia.setResourceCacheLimit(16L*1024*1024);
            Typeface face=device.createResource((d,q)->FontMgr.Companion.getDefault().makeFromFile(args[3],0));Font font=device.createResource((d,q)->new Font(face,18));
            Paint text=device.createResource((d,q)->new Paint());text.setColor(0xffffffff);text.setAntiAlias(true);
            Paint green=device.createResource((d,q)->new Paint());green.setColor(0xff00ff00);
            ImageFilter blur=device.createResource((d,q)->ImageFilter.Companion.makeBlur(3,3,FilterTileMode.DECAL,null,null));Paint glow=device.createResource((d,q)->new Paint());glow.setColor(0xffff0000);glow.setImageFilter(blur);
            foreignScene(library,shader,ticket);
            long frame=render(backend,192,128);boolean staleRejected=false;MetalDeviceLease.Target stale=null;try{stale=device.target(frame+1);}catch(IllegalStateException expected){staleRejected=true;}finally{if(stale!=null)stale.close();}
            require(staleRejected,"Unsubmitted target frame was accepted");report.put("unsubmittedTargetFrameRejected",true);
            try(var target=device.target(frame)){boolean[] wrongOwner={false};Thread thread=new Thread(()->{try{target.frame();}catch(IllegalStateException expected){wrongOwner[0]=true;}},"owned-lease-rejection");thread.start();thread.join();require(wrongOwner[0],"Wrong-thread target access was accepted");report.put("wrongThreadTargetRejected",true);Surface surface=surface(target,skia);draw(surface,font,text,green,glow,"cancel");require(ticket.prepare(target,()->surface.flushAndSubmit(false)),"Cancel preparation failed");require(calls.query(83)==1&&calls.scene(11)==1,"Prepared claim was not retained");ticket.cancel();}
            backend.awaitFrame();require(calls.query(83)==0&&calls.scene(11)==0,"Cancelled claim leaked");report.put("actualPreparedClaimCancelled",true);
            frame=render(backend,192,128);try(var target=device.target(frame)){Surface surface=surface(target,skia);draw(surface,font,text,green,glow,"failed");boolean rejected=false;int[] submissions={0};IllegalStateException injected=new IllegalStateException("Owned post-submit failure");try{ticket.prepare(target,()->{surface.flushAndSubmit(false);submissions[0]++;throw injected;});}catch(IllegalStateException expected){require(expected==injected,"A different failure replaced the submission fault");rejected=true;}require(rejected&&submissions[0]==1,"Injected submission failure escaped");ticket.cancel();}
            backend.awaitFrame();require(calls.query(83)==0&&calls.scene(11)==0,"Failed-submission ownership leaked");report.put("realGpuSubmitThenThrowCleaned",true);
            int[] widths={192,224,208},heights={128,160,144};long generation=0;
            for(int i=0;i<3;i++) {
                if(i>0){final int w=widths[i],h=heights[i];MainThread.run(()->{require(SDL_SetWindowSize(original.window,w,h)&&SDL_SyncWindow(original.window),"Owned resize failed");});long limit=System.nanoTime()+5_000_000_000L;while((display.getWindowWidth()!=w||display.getWindowHeight()!=h)&&System.nanoTime()<limit)pump();require(display.getWindowWidth()==w&&display.getWindowHeight()==h,"Original resize events did not settle");original.makeCurrent();}
                int w=display.getWidth(),h=display.getHeight();require(w==widths[i]&&h==heights[i],"Actual pixel dimensions differ");
                frame=render(backend,w,h);long currentFrame=frame;
                try(var target=device.target(frame)) {
                    require(target.generation()>=generation,"Target generation moved backwards");if(i>0)require(target.generation()>generation,"Resize reused a stale generation");generation=target.generation();
                    Surface surface=surface(target,skia);draw(surface,font,text,green,glow,"Scene "+frame);
                    require(ticket.prepare(target,()->surface.flushAndSubmit(false)),"Scene target was rejected");
                    require(calls.query(80)==1&&calls.query(81)==frame&&calls.query(82)==generation&&calls.query(83)==1,"Wrong prepared source provenance");
                    require(ticket.publish(),"Scene publish failed");
                }
                Map<String,Object> row=await(calls,original,frame,"scene",w,h);observations.add(row);backend.awaitFrame();ByteBuffer source=ByteBuffer.allocateDirect(w*h*4);backend.readRgba(source);
                require(source.get(2)==(byte)255&&source.get((h-1)*w*4)==(byte)255,"Asymmetric textured mesh is absent");
                composition(source,w,row);
                require(calls.scene(11)==0&&calls.query(83)==0,"Published claim did not retire");row.put("sceneGeneration",generation);row.put("sourceRgbaSha256",hash(source));
                if(pixels){ByteBuffer shown=calls.drawable(currentFrame,w*h*4);images(source,shown,w,h,output,"scene-"+frame,row);exact(source,shown,w,h,row);}
                if(!Boolean.TRUE.equals(row.get("exactCurrentDisplayed")))ticket.cancel();
            }
            ticket.close();ticket=null;long glFrame=frame+1;ByteBuffer glSource=glPattern(display.getWidth(),display.getHeight());
            try(Arena memory=Arena.ofConfined()){MemorySegment descriptor=memory.allocate(32,8);MetalPresentFrame.write(descriptor,glFrame,display.getWidth(),display.getHeight(),1);require(calls.prepare(descriptor)==0&&host.main(()->selected.publish(glFrame))==0,"Legacy GL-source control failed");}
            Map<String,Object> glRow=await(calls,original,glFrame,"legacy-gl-source",display.getWidth(),display.getHeight());observations.add(glRow);require(calls.query(80)==0,"Legacy source retained a Metal alias");if(pixels){ByteBuffer shown=calls.drawable(glFrame,glSource.remaining());images(glSource,shown,display.getWidth(),display.getHeight(),output,"legacy-gl-"+glFrame,glRow);exact(glSource,shown,display.getWidth(),display.getHeight(),glRow);}
            require(host.main(calls::hide)==0&&calls.query(7)==1,"Fallback did not hide child");glPattern(display.getWidth(),display.getHeight());original.swapBuffers();require(original.swaps==1,"Original fallback swap missing");report.put("manualGlFallbackAfterHide",true);
            device.close();device=null;backend.awaitFrame();require(calls.scene(10)==0&&calls.scene(11)==0,"Lease ledgers remain live");report.put("nativeLeaseCountsZero",true);
            passed=displayFailures.isEmpty();
            report.put("librarySha256",args[1]);report.put("cohort",args[5]);report.put("actualSkikoVersion","0.154.0");report.put("twoActualResizes",true);report.put("texturedGeometryAndMaterial",true);report.put("actualSkikoTextAndBlur",true);
        }catch(Throwable failure){report.put("failureClass",failure.getClass().getName());report.put("failure",String.valueOf(failure.getMessage()));if(calls!=null&&calls.attached())report.put("nativeError",calls.error());failure.printStackTrace();passed=false;}
        finally {
            try {
                if(ticket!=null)ticket.close();if(device!=null)device.close();
                if(calls!=null&&calls.attached()){require(calls.releaseGl()==0,"GL ownership release failed");NativeCalls owned=calls;require(MainThread.call(owned::destroy)==0,"Child detach failed");}
                if(backend!=null)backend.close();if(display.isCreated())display.destroy();
                require(original.creates==1&&original.destroys==1,"Window lifetime counts differ");report.put("normalOwnedTeardown",true);
            }catch(Throwable cleanup){report.put("cleanupFailure",cleanup.toString());passed=false;}
            report.put("passed",passed);report.put("observations",observations);report.put("displayFailures",displayFailures);report.put("ownedProcessId",ProcessHandle.current().pid());
            report.put("displayedNeverSubstitutedByCompleted",true);report.put("sceneCpuReadbackBeforeSubmission",false);report.put("sceneDiagnosticReadbacksAfterSubmission",true);report.put("legacyGlControlReadbackBeforePrepare",true);report.put("sameFrameMinecraftGlReplayQualified",false);report.put("minecraftWorldOrCeraQualified",false);report.put("clientUiSelected",false);report.put("performanceClaim",false);
            Files.writeString(output,json(report)+"\n");
        }
        if(!passed)throw new IllegalStateException("Private actual scene handoff unit failed; preserve every recorded gate");
    }
    private static MetalResidentSceneBackend scene(Path library,String shader) {
        var scene=MetalResidentSceneBackend.open(library,new ResidentSceneBackend.Layout(1,80,20,256),shader,"passVertex","passFragment");
        ByteBuffer mesh=ByteBuffer.allocateDirect(QUAD.length*4).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<QUAD.length;i++)mesh.putFloat(i*4,QUAD[i]);scene.putMesh(1,mesh);
        ByteBuffer material=ByteBuffer.allocateDirect(MATERIAL.length);material.put(MATERIAL).flip();scene.putMaterial(1,2,2,material);
        ByteBuffer actor=ByteBuffer.allocateDirect(80).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<16;i++)actor.putFloat(i*4,i%5==0?1:0);for(int i=16;i<20;i++)actor.putFloat(i*4,1);scene.writeActors(0,actor);
        scene.putPipeline(1,shader,"passVertex","passFragment",new ScenePasses.Pipeline(3,ScenePasses.LEQUAL,0,0,1,0,1,0,0,0,15));return scene;
    }
    private static long render(MetalResidentSceneBackend scene,int w,int h) {
        ByteBuffer uniforms=ByteBuffer.allocateDirect(256).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<16;i++)uniforms.putFloat(i*4,i%5==0?1:0);uniforms.putFloat(64,-1);
        ByteBuffer commands=ByteBuffer.allocateDirect(24).order(ByteOrder.LITTLE_ENDIAN);commands.putInt(1).putInt(1).putInt(0).putInt(6).putInt(0).putInt(1).flip();
        ByteBuffer passes=ByteBuffer.allocateDirect(96);ScenePasses.write(passes,List.of(new ScenePasses.Pass(1,0,1,0,80,5,3,0,0,0,w,h,0,0,0,0,0,0,0,1,1,0,1)));
        require(scene.submitPasses(w,h,uniforms,passes,1,commands,1),"Scene slot busy");return scene.statistics().submittedFrames();
    }
    private static Surface surface(MetalDeviceLease.Target target,DirectContext context) {
        BackendRenderTarget brt=target.createResource((texture,w,h)->BackendRenderTarget.Companion.makeMetal(w,h,texture));
        return target.createResource((texture,w,h)->Surface.Companion.makeFromBackendRenderTarget(context,brt,SurfaceOrigin.TOP_LEFT,SurfaceColorFormat.BGRA_8888,ColorSpace.Companion.getSRGB(),null));
    }
    private static void draw(Surface surface,Font font,Paint text,Paint green,Paint blur,String value){Canvas c=surface.getCanvas();c.drawRect(4,4,22,22,green);c.drawString(value,6,66,font,text);c.drawRect(80,14,100,34,blur);}
    private static void foreignScene(Path library,String shader,MetalSceneTicket ticket) {
        try(var other=scene(library,shader);var device=other.metalDeviceLease()){long frame=render(other,192,128);try(var target=device.target(frame)){require(!ticket.prepare(target,()->{}),"A foreign scene/queue was accepted");}other.awaitFrame();}
        report.put("foreignSceneQueueRejected",true);
    }
    private static Map<String,Object> await(NativeCalls calls,Original original,long frame,String source,int w,int h)throws Exception {
        long started=System.nanoTime(),deadline=started+5_000_000_000L;while((calls.query(3)!=frame||calls.query(4)!=frame)&&System.nanoTime()<deadline)pump();
        long prepared=calls.query(1),submitted=calls.query(2),completed=calls.query(3),displayed=calls.query(4),gpuFailure=calls.query(84),decided=System.nanoTime();original.same();boolean shown=decided<=deadline&&prepared==frame&&submitted==frame&&completed==frame&&displayed==frame&&gpuFailure==0;
        Map<String,Object> row=new LinkedHashMap<>();row.put("source",source);row.put("frame",frame);row.put("width",w);row.put("height",h);row.put("prepared",prepared);row.put("submitted",submitted);row.put("completed",completed);row.put("displayed",displayed);row.put("gpuFailure",gpuFailure);row.put("exactCurrentDisplayed",shown);row.put("waitElapsedNanoseconds",decided-started);row.put("displayDeadlineNanoseconds",5_000_000_000L);
        if(!shown)displayFailures.add(source+" target"+frame+" displayed"+displayed);
        row.put("sdlVisible",display.isVisible());row.put("sdlActive",display.isActive());row.put("windowVisible",calls.query(22)==1);row.put("windowOcclusionVisible",calls.query(23)==1);row.put("childWindowMatches",calls.query(25)==1);row.put("drawableWidth",calls.query(17));row.put("drawableHeight",calls.query(18));row.put("actualSdlInterval",interval());row.put("nativeConfiguredInterval",calls.query(53));row.put("nativePreparedInterval",calls.query(54));row.put("layerDisplaySync",calls.query(45));row.put("publishedTextureFrame",calls.query(73));row.put("publishedTextureWidth",calls.query(74));row.put("publishedTextureHeight",calls.query(75));
        observations.add(row);
        require(interval()==1&&calls.query(53)==1&&calls.query(54)==1&&calls.query(45)==1,"Actual SDL/native/layer pacing differs");
        require(display.isVisible()&&display.isActive()&&calls.query(22)==1&&calls.query(23)==1&&calls.query(25)==1&&calls.query(52)==1&&calls.query(24)==1,"Current original window is not active/visible/attached");
        require(calls.query(17)==w&&calls.query(18)==h&&calls.query(73)==frame&&calls.query(74)==w&&calls.query(75)==h,"Published current texture dimensions differ");
        observations.removeLast();
        return row;
    }
    private static void composition(ByteBuffer source,int width,Map<String,Object> row) {
        int rectangle=(8*width+8)*4;require((source.get(rectangle)&255)==0&&(source.get(rectangle+1)&255)==255&&(source.get(rectangle+2)&255)==0,"Actual Skiko rectangle is absent");
        int glyphs=0,blur=0;for(int y=43;y<68;y++)for(int x=6;x<90;x++){int p=(y*width+x)*4;if((source.get(p)&255)>128&&(source.get(p+1)&255)>128&&(source.get(p+2)&255)>128)glyphs++;}
        for(int y=16;y<32;y++)for(int x=76;x<80;x++)if((source.get((y*width+x)*4)&255)>0)blur++;
        row.put("actualSkikoGlyphPixels",glyphs);row.put("actualSkikoBlurOutsideRectPixels",blur);require(glyphs>20&&blur>10,"Actual Skiko text/blur is absent");
    }
    private static void exact(ByteBuffer source,ByteBuffer shown,int w,int h,Map<String,Object> row) {
        long different=0,verticalDifferent=0;for(int y=0;y<h;y++)for(int x=0;x<w*4;x++){int p=y*w*4+x;if(source.get(p)!=shown.get(p))different++;if(source.get((h-1-y)*w*4+x)!=shown.get(p))verticalDifferent++;}
        row.put("actualDrawableComparedBytes",source.remaining());row.put("actualDrawableDifferentBytes",different);row.put("verticalFlipNegativeControlDifferentBytes",verticalDifferent);require(different==0&&verticalDifferent>0,"Actual drawable pixels/orientation differ");
    }
    private static void images(ByteBuffer source,ByteBuffer shown,int width,int height,Path output,String name,Map<String,Object> row)throws Exception {
        row.put("actualDrawableRgbaSha256",hash(shown));
        for(int i=0;i<2;i++){ByteBuffer pixels=i==0?source:shown;BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int p=(y*width+x)*4;image.setRGB(x,y,((pixels.get(p+3)&255)<<24)|((pixels.get(p)&255)<<16)|((pixels.get(p+1)&255)<<8)|(pixels.get(p+2)&255));}Path file=output.resolveSibling("handoff-"+name+(i==0?"-source.png":"-drawable.png"));require(ImageIO.write(image,"png",file.toFile()),"PNG evidence writer unavailable");row.put(i==0?"sourcePng":"actualDrawablePng",file.getFileName().toString());row.put(i==0?"sourcePngSha256":"actualDrawablePngSha256",hash(file));}
    }
    private static ByteBuffer glPattern(int w,int h) {
        GL11.glViewport(0,0,w,h);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_DITHER);GL11.glEnable(GL11.GL_SCISSOR_TEST);
        for(int i=0;i<4;i++){GL11.glScissor((i&1)*w/2,(i>>>1)*h/2,w/2,h/2);GL11.glClearColor((i&1)!=0?1:0,(i>>>1)!=0?1:0,.25f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);}
        ByteBuffer raw=ByteBuffer.allocateDirect(w*h*4);int oldRead=GL11.glGetInteger(GL11.GL_READ_BUFFER),oldPack=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try{GL11.glReadBuffer(GL11.GL_BACK);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,raw);}finally{GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,oldPack);GL11.glReadBuffer(oldRead);}
        ByteBuffer top=ByteBuffer.allocateDirect(raw.capacity());for(int y=0;y<h;y++)for(int x=0;x<w*4;x++)top.put(y*w*4+x,raw.get((h-1-y)*w*4+x));return top;
    }
    private static int interval(){try(var stack=org.lwjgl.system.MemoryStack.stackPush()){IntBuffer value=stack.mallocInt(1);require(SDL_GL_GetSwapInterval(value),"SDL pacing getter failed");return value.get(0);}}
    private static void pump()throws Exception{display.processMessages();Thread.sleep(2);}
    private static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static String hash(ByteBuffer b)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(b.duplicate());return HexFormat.of().formatHex(d.digest());}
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    private static String json(Object value){if(value==null)return"null";if(value instanceof Number||value instanceof Boolean)return value.toString();if(value instanceof Map<?,?>m){List<String>parts=new ArrayList<>();m.forEach((k,v)->parts.add(json(k.toString())+":"+json(v)));return"{"+String.join(",",parts)+"}";}if(value instanceof Iterable<?>it){List<String>parts=new ArrayList<>();it.forEach(v->parts.add(json(v)));return"["+String.join(",",parts)+"]";}return"\""+value.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
}
