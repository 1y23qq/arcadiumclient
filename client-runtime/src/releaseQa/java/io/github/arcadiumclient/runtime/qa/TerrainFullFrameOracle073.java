// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.config.v1.Property;

public final class TerrainFullFrameOracle073 {
    private static final String[] NAMES={"native-a","native-b","gpu","native-c"};
    private static final int WIDTH=1920,HEIGHT=1080;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<Sample> samples=new ArrayList<>();
    private static final List<String> captureErrors=new ArrayList<>();
    private static final List<Map<String,Object>> events=new ArrayList<>();
    private static boolean running,complete,failed;
    private static int stage=-1,prelude,groupTick;
    private static long originalCalls,preludeCalls,preludeTerrainReturns,nestedOriginalCalls;
    private static int[] terrainReturns=new int[4],blurCalls=new int[4],capturePoints=new int[4];
    private static int[] blurExits=new int[4];
    private static int eventSerial;
    private static Field gpuOption;
    private static Map<String,Object> gpuValidation;
    private record Sample(String name,byte[] rgba,byte[] depth,byte[] integerDepth,Map<String,Object> metadata) { }

    private TerrainFullFrameOracle073() { }
    public static boolean enabled(){return System.getProperty("arcadium.terrainOracle073.output")!=null;}
    public static boolean controlling(){return enabled()&&running;}
    public static boolean gpuStage(){return controlling()&&stage==2;}
    public static boolean completed(){return complete;}
    public static Path gpuPng(){return output().resolveSibling("gpu.pre-polyblur.png");}
    public static void terrainReturn(){if(controlling()){if(stage>=0)terrainReturns[stage]++;else preludeTerrainReturns++;}}
    public static void gpuValidated(Map<String,Object> value){if(gpuStage())gpuValidation=value;}
    public static void blurRender(){if(controlling()&&stage>=0){event("blur-entry");if(capturePoints[stage]!=1)captureErrors.add("Stage"+stage+" world blur preceded/duplicated the passive capture");blurCalls[stage]++;}}
    public static void blurReturned(){if(controlling()&&stage>=0){blurExits[stage]++;event("blur-exit");}}

    public static void render(float delta,long deadline,Operation<Void> original){
        if(!enabled()||complete||failed||!TerrainScene073Qa.oracleReady()){original.call(delta,deadline);return;}
        if(running){nestedOriginalCalls++;original.call(delta,deadline);return;}
        Minecraft mc=Minecraft.getInstance();boolean oldGpu=BetaOptimizationsConfig.getGpuTerrainCommands(),oldBackend=GpuTerrainCommands.enabled;Throwable problem=null;
        try{
            guard(mc);if(gpuOption==null){gpuOption=BetaOptimizationsConfig.class.getDeclaredField("gpuTerrainCommands");gpuOption.setAccessible(true);}
            running=true;
            if(prelude++<8){stage=-1;mode(false);preludeCalls++;original.call(.5f,deadline);}
            else{
            groupTick=TerrainScene073Qa.oracleTick();report.put("scope","ONE camera, full uncropped WORLD before PolyBlur world postprocessing and hand depth clear");report.put("captureBoundary","GameRenderer.render(IFJ)V BEFORE Profiler.swap(\"hand\"); pinned PolyBlur injects AFTER that call");report.put("partialTick",.5f);report.put("clientTick",groupTick);report.put("nativePreludeOriginalCalls",preludeCalls);report.put("actualNativePreludeTerrainReturns",preludeTerrainReturns);report.put("blurState",blurState());
            require(Boolean.TRUE.equals(((Map<?,?>)report.get("blurState")).get("enabled"))&&GLX.usePostProcess,"Unsupported/disabled real blur stage; do not silently skip it");
            for(stage=0;stage<4;stage++){
                mode(stage==2);guard(mc);require(TerrainScene073Qa.oracleTick()==groupTick,"Client tick advanced inside the render group");event("world-entry");originalCalls++;original.call(.5f,deadline);event("world-return");
                require(capturePoints[stage]==1&&samples.size()==stage+1&&captureErrors.isEmpty(),"Passive pre-blur capture failed/unsupported: "+captureErrors);require(terrainReturns[stage]>0,"No actual solid terrain-return callback");require(blurCalls[stage]==1&&blurExits[stage]==1,"The unchanged Hybrid world blur did not enter/exit exactly once after the capture");
                final int currentStage=stage;require(validOrder(events.stream().filter(e->Integer.valueOf(currentStage).equals(e.get("stage"))).map(e->(String)e.get("kind")).toList()),"Actual world/capture/blur event order differs");
            }
            require(TerrainScene073Qa.oracleTick()==groupTick&&gpuValidation!=null&&nestedOriginalCalls==0,"GPU guards missing or nested world operations unsupported");
            var comparisons=new ArrayList<Map<String,Object>>();boolean exact=true;
            for(int[] pair:new int[][]{{0,1},{0,3},{1,3},{0,2}}){var comparison=compare(samples.get(pair[0]),samples.get(pair[1]));comparisons.add(comparison);exact&=Boolean.TRUE.equals(comparison.get("passed"));}
            var captures=new ArrayList<Map<String,Object>>();for(Sample sample:samples)captures.add(save(sample));
            report.put("captures",captures);report.put("comparisons",comparisons);report.put("actualEvents",events);report.put("actualTerrainReturns",terrainReturns);report.put("actualWorldBlurCallsAfterCapture",blurCalls);report.put("actualWorldBlurExits",blurExits);report.put("actualGroupedOriginalWorldOperations",originalCalls);report.put("actualControlledWorldOperations",originalCalls+preludeCalls);report.put("actualNestedWorldOperations",nestedOriginalCalls);report.put("gpuNativeGuardValidation",gpuValidation);report.put("passed",exact);report.put("zeroTolerance",true);report.put("historyUsedForComparison",false);report.put("finalFrameSubstitution",false);report.put("oneCameraPrePolyBlurParityQualified",exact);report.put("all8CameraQualified",false);report.put("publicationQualified",false);report.put("performanceClaim",false);write();
            require(exact,"Strict native repeats or GPU world RGBA/F32/U32 comparison failed");complete=true;
            }
        }catch(Throwable failure){problem=failure;failed=true;report.put("passed",false);report.put("error",stack(failure));report.put("actualEvents",events);report.put("passiveCaptureErrors",captureErrors);report.put("actualTerrainReturns",terrainReturns);report.put("actualWorldBlurCallsAfterCapture",blurCalls);report.put("actualWorldBlurExits",blurExits);report.put("actualGroupedOriginalWorldOperations",originalCalls);report.put("actualControlledWorldOperations",originalCalls+preludeCalls);report.put("performanceClaim",false);try{var saved=new ArrayList<Map<String,Object>>();for(var sample:samples)saved.add(save(sample));report.put("captures",saved);write();}catch(Exception io){failure.addSuppressed(io);}
        }finally{
            stage=-1;running=false;
            try{if(gpuOption!=null)gpuOption.setBoolean(null,oldGpu);GpuTerrainCommands.enabled=oldBackend;}catch(Exception failure){problem=failure;complete=false;}
        }
        if(problem!=null)TerrainScene073Qa.oracleFailed(problem);
        else if(complete){report.put("controlledGpuFlagRestored",BetaOptimizationsConfig.getGpuTerrainCommands()==oldGpu&&GpuTerrainCommands.enabled==oldBackend);try{write();TerrainScene073Qa.oracleComplete(report);}catch(Exception failure){TerrainScene073Qa.oracleFailed(failure);}}
    }

    public static void captureWorld(int pass,float partial){
        if(!controlling()||stage<0)return;
        try{
            require(pass==2&&Float.floatToRawIntBits(partial)==Float.floatToRawIntBits(.5f),"Unsupported world pass/partial tick");require(++capturePoints[stage]==1&&blurCalls[stage]==0,"World capture was duplicated or followed a blur invocation");
            event("pre-capture");
            Minecraft mc=Minecraft.getInstance();guard(mc);require(TerrainScene073Qa.oracleTick()==groupTick,"Passive world capture changed client tick");
            int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            require(pack==0,"Unsupported pixel-pack buffer; preserve native state and fail");IntBuffer viewport=BufferUtils.createIntBuffer(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);require(viewport.get(0)==0&&viewport.get(1)==0&&viewport.get(2)==WIDTH&&viewport.get(3)==HEIGHT,"World viewport is not full uncropped1080p");
            int align=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT),row=GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH),skipRows=GL11.glGetInteger(GL12.GL_PACK_SKIP_ROWS),skipPixels=GL11.glGetInteger(GL12.GL_PACK_SKIP_PIXELS),swap=GL11.glGetInteger(GL11.GL_PACK_SWAP_BYTES),lsb=GL11.glGetInteger(GL11.GL_PACK_LSB_FIRST),readBuffer=0;boolean readBufferKnown=false;
            byte[] rgba,depth,integerDepth;
            try{
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);require(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"Native world framebuffer is incomplete");readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);readBufferKnown=true;GL11.glReadBuffer(draw==0?GL11.GL_BACK:GL30.GL_COLOR_ATTACHMENT0);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,4);GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS,0);GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES,0);GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST,0);
                rgba=pixels(GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE);depth=pixels(GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT);integerDepth=pixels(GL11.GL_DEPTH_COMPONENT,GL11.GL_UNSIGNED_INT);
            }finally{if(readBufferKnown)GL11.glReadBuffer(readBuffer);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,align);GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH,row);GL11.glPixelStorei(GL12.GL_PACK_SKIP_ROWS,skipRows);GL11.glPixelStorei(GL12.GL_PACK_SKIP_PIXELS,skipPixels);GL11.glPixelStorei(GL11.GL_PACK_SWAP_BYTES,swap);GL11.glPixelStorei(GL11.GL_PACK_LSB_FIRST,lsb);}
            int nonClear=0;ByteBuffer values=ByteBuffer.wrap(integerDepth).order(ByteOrder.nativeOrder());for(int at=0;at<integerDepth.length;at+=4)if(values.getInt(at)!=-1)nonClear++;
            int glError=GL11.glGetError();
            var metadata=new LinkedHashMap<String,Object>();metadata.put("width",WIDTH);metadata.put("height",HEIGHT);metadata.put("fullUncropped",true);metadata.put("drawFramebuffer",draw);metadata.put("mainFramebuffer",mc.getRenderTarget().frameBufferId);metadata.put("partialTick",partial);metadata.put("clientTick",groupTick);metadata.put("depthByteOrder",ByteOrder.nativeOrder().toString());metadata.put("nonClearDepthPixels",nonClear);metadata.put("betaSettings",settings());metadata.put("effectiveGpuTerrain",GpuTerrainCommands.enabled);metadata.put("camera",List.of(mc.player.x,mc.player.y,mc.player.z,mc.player.yaw,mc.player.pitch,mc.options.fov));metadata.put("blurCallsBeforeCapture",blurCalls[stage]);metadata.put("worldClientTime",mc.world.getTime());
            metadata.put("readbackGlError",glError);metadata.put("captureStage","pre-PolyBlur Profiler.hand BEFORE");samples.add(new Sample(NAMES[stage],rgba,depth,integerDepth,metadata));event("capture-complete");
            if(nonClear<=100)captureErrors.add("Stage"+stage+" native depth is trivial/unsupported");if(glError!=GL11.GL_NO_ERROR)captureErrors.add("Stage"+stage+" native readback GL error"+glError);
        }catch(Throwable failure){captureErrors.add("Stage"+stage+" passive pre-PolyBlur capture failed: "+failure);}
    }
    private static void mode(boolean value)throws Exception{gpuOption.setBoolean(null,value);GpuTerrainCommands.enabled=value;}
    private static void event(String kind){events.add(Map.of("sequence",++eventSerial,"stage",stage,"name",NAMES[stage],"kind",kind,"clientTick",TerrainScene073Qa.oracleTick()));}
    private static boolean validOrder(List<String> kinds){return kinds.equals(List.of("world-entry","pre-capture","capture-complete","blur-entry","blur-exit","world-return"));}
    private static void guard(Minecraft mc){require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getServer()!=null&&mc.world!=null&&mc.player!=null&&mc.getCurrentServerEntry()==null,"Only the owned offline fixture can run the oracle");require(mc.width==WIDTH&&mc.height==HEIGHT&&mc.screen==null&&mc.player.x==0&&mc.player.y==14&&mc.player.z==-28&&mc.player.yaw==0&&mc.player.pitch==10&&mc.options.fov==75,"One-camera oracle moved or resized");require(!BetaOptimizationsConfig.getWorldRenderScale()&&!mc.options.anaglyph,"World scale/anaglyph must remain disabled as in the source proof");}
    private static byte[] pixels(int format,int type){ByteBuffer buffer=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);GL11.glReadPixels(0,0,WIDTH,HEIGHT,format,type,buffer);byte[] bytes=new byte[buffer.capacity()];buffer.get(bytes);return bytes;}
    private static Map<String,Object> compare(Sample a,Sample b){Map<String,Object> result=new LinkedHashMap<>();result.put("first",a.name);result.put("second",b.name);boolean exact=true;for(int i=0;i<3;i++){byte[] x=i==0?a.rgba:i==1?a.depth:a.integerDepth,y=i==0?b.rgba:i==1?b.depth:b.integerDepth;int count=0;for(int at=0;at<x.length;at++)if(x[at]!=y[at])count++;result.put(i==0?"rgbaDifferentBytes":i==1?"floatDepthDifferentBytes":"integerDepthDifferentBytes",count);exact&=count==0;}result.put("passed",exact);return result;}
    private static Map<String,Object> save(Sample s)throws Exception{Files.createDirectories(output().getParent());var value=new LinkedHashMap<String,Object>(s.metadata);value.put("name",s.name);value.put("rgba",gzip(s.rgba,s.name+".rgba.gz"));value.put("floatDepth",gzip(s.depth,s.name+".depth-f32.gz"));value.put("integerDepth",gzip(s.integerDepth,s.name+".depth-u32.gz"));BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int at=((HEIGHT-1-y)*WIDTH+x)*4;image.setRGB(x,y,(s.rgba[at+3]&255)<<24|(s.rgba[at]&255)<<16|(s.rgba[at+1]&255)<<8|s.rgba[at+2]&255);}Path png=output().resolveSibling(s.name+".pre-polyblur.png");ImageIO.write(image,"png",png.toFile());value.put("png",png.toString());return value;}
    private static Map<String,Object> gzip(byte[] data,String name)throws Exception{Path file=output().resolveSibling(name);try(var stream=new GZIPOutputStream(Files.newOutputStream(file))){stream.write(data);}return Map.of("path",file.toString(),"bytes",data.length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)),"encoding","gzip lossless");}
    private static Map<String,Object> settings(){var values=new TreeMap<String,Object>();for(var e:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(e.getValue() instanceof Property<?> p){Object v=p.get();if(v instanceof Boolean||v instanceof Number||v instanceof String)values.put(e.getKey(),v);}values.put("effectiveGpuTerrain",GpuTerrainCommands.enabled);return values;}
    private static Map<String,Object> blurState()throws Exception{Class<?> type=Class.forName("org.polyfrost.polyblur.client.PolyBlurConfig");Object config=type.getField("INSTANCE").get(null);var values=new LinkedHashMap<String,Object>();for(String[] v:new String[][]{{"enabled","isEnabled"},{"type","getBlurType"},{"strength","getStrength"},{"handStrength","getHandBlurStrength"},{"samples","getMotionBlurSamples"},{"blurHand","getBlurHand"},{"phosphorMode","getPhosphorMode"},{"velocityBuffer","getVelocityBuffer"},{"translationParallax","getTranslationParallax"}})values.put(v[0],type.getMethod(v[1]).invoke(config));values.put("nativePostProcessSupported",GLX.usePostProcess);require(Integer.valueOf(2).equals(values.get("type")),"Unsupported world blur recipe; do not skip temporal controls");return values;}
    private static Path output(){return Path.of(System.getProperty("arcadium.terrainOracle073.output"));}
    private static void write()throws Exception{Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static String stack(Throwable failure){var text=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(text));return text.toString();}
}
