// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainScene073GpuEvidence;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainOracleGeometry073;
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
    private static final int WIDTH=1920,HEIGHT=1080;
    private static final TerrainResidentSceneBlueprint.Plan plan=TerrainResidentSceneBlueprint.create();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<Map<String,Object>> groups=new ArrayList<>();
    private static final List<Sample> samples=new ArrayList<>();
    private static final List<String> captureErrors=new ArrayList<>();
    private static final List<Map<String,Object>> events=new ArrayList<>();
    private static final ByteBuffer readback=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);
    private static boolean running,complete,failed,sourceComplete;
    private static int stage=-1,prelude,groupTick,sourceTick;
    private static long originalCalls,preludeCalls,preludeTerrainReturns,nestedOriginalCalls,sourceCalls;
    private static int[] terrainReturns=new int[5],blurCalls=new int[5],capturePoints=new int[5],blurExits=new int[5];
    private static int eventSerial;
    private static Field gpuOption;
    private static Map<String,Object> gpuValidation,sourceValidation;
    private static Map<String,Object> group=new LinkedHashMap<>();
    private record Sample(String name,byte[] rgba,byte[] depth,byte[] integerDepth,Map<String,Object> metadata) { }

    private TerrainFullFrameOracle073(){ }
    public static boolean enabled(){return System.getProperty("arcadium.terrainOracle073.output")!=null;}
    public static boolean allViews(){return enabled()&&Boolean.getBoolean("arcadium.terrainOracle073.allViews");}
    private static int view(){return allViews()?TerrainScene073Qa.oracleViewIndex():0;}
    private static boolean rapid(){return allViews()&&TerrainOracleViews073.rapid(view());}
    private static String stageName(){return stage==4?"source-gpu":TerrainOracleViews073.names(view()).get(stage);}
    private static TerrainResidentSceneBlueprint.Camera expectedCamera(){return stage==4?TerrainOracleViews073.source(plan,view()):TerrainOracleViews073.capture(plan,view());}
    public static boolean controlling(){return enabled()&&running;}
    public static boolean gpuStage(){return controlling()&&stage>=0&&(stage==4||TerrainOracleViews073.gpu(view(),stage));}
    public static boolean completed(){return complete;}
    public static Path gpuPng(){return allViews()?output().getParent().resolve("08-rapid-yaw-gpu-first/gpu.pre-polyblur.png"):output().resolveSibling("gpu.pre-polyblur.png");}
    public static void terrainReturn(){if(controlling()){if(stage>=0)terrainReturns[stage]++;else preludeTerrainReturns++;}}
    public static void gpuValidated(Map<String,Object> value){
        if(!gpuStage())return;
        try{var current=new LinkedHashMap<>(value);if(rapid())current.put("actualIndirectRecipes",TerrainOracleGeometry073.capture());if(stage==4)sourceValidation=current;else gpuValidation=current;}
        catch(Exception failure){error("Actual indirect recipe observer failed: "+failure);}
    }
    public static void blurRender(){if(controlling()&&stage>=0){event("blur-entry");if(capturePoints[stage]!=1)error("Stage"+stage+" world blur preceded/duplicated the passive capture");blurCalls[stage]++;}}
    public static void blurReturned(){if(controlling()&&stage>=0){blurExits[stage]++;event("blur-exit");}}

    public static void render(float delta,long deadline,Operation<Void> original){
        if(!enabled()||complete||failed||!TerrainScene073Qa.oracleReady()){original.call(delta,deadline);return;}
        if(running){nestedOriginalCalls++;original.call(delta,deadline);return;}
        Minecraft mc=Minecraft.getInstance();boolean oldGpu=BetaOptimizationsConfig.getGpuTerrainCommands(),oldBackend=GpuTerrainCommands.enabled;Throwable problem=null;boolean succeeded=false,rotated=false;
        try{
            stage=4;guard(mc);stage=-1;
            if(gpuOption==null){gpuOption=BetaOptimizationsConfig.class.getDeclaredField("gpuTerrainCommands");gpuOption.setAccessible(true);}
            running=true;
            if(prelude++<8){mode(false);preludeCalls++;original.call(.5f,deadline);}
            else if(rapid()&&!sourceComplete){
                sourceTick=TerrainScene073Qa.oracleTick();group.put("sourceNativeReadiness",TerrainScene073Qa.oracleFreshReadiness());stage=4;mode(true);guard(mc);event("world-entry");sourceCalls++;original.call(.5f,deadline);event("world-return");validateStage(4);
                require(sourceValidation!=null&&sourceCalls==1,"A real source-orientation GPU pass is required before yaw reversal");sourceComplete=true;
            }else{
                groupTick=TerrainScene073Qa.oracleTick();group.put("viewIndex",view());group.put("kind",rapid()?"gpu-first same-frame yaw reversal":"original steady camera");group.put("cameraName",TerrainOracleViews073.capture(plan,view()).name());group.put("nativeReadiness",TerrainScene073Qa.oracleFreshReadiness());
                group.put("scope","Full uncropped WORLD before PolyBlur world postprocessing and hand depth clear");group.put("captureBoundary","GameRenderer.render(IFJ)V BEFORE Profiler.swap(\"hand\"); actual PolyBlur follows that call");group.put("partialTick",.5f);group.put("clientTick",groupTick);group.put("nativePreludeOriginalCalls",preludeCalls);group.put("actualNativePreludeTerrainReturns",preludeTerrainReturns);group.put("blurState",blurState());
                require(Boolean.TRUE.equals(((Map<?,?>)group.get("blurState")).get("enabled"))&&GLX.usePostProcess,"Unsupported/disabled real blur stage");
                if(rapid()){
                    require(sourceComplete&&sourceValidation!=null&&sourceCalls==1,"Missing observed GPU source frame");group.put("sourceGpuClientTick",sourceTick);group.put("sourceCamera",List.of(mc.player.x,mc.player.y,mc.player.z,mc.player.yaw,mc.player.pitch,mc.options.fov));
                    TerrainResidentSceneWorld.freezeClientCamera(mc,TerrainOracleViews073.capture(plan,view()));rotated=true;
                    group.put("rotationAtActualWrapperEntry",true);group.put("sourceGpuNativeGuardValidation",sourceValidation);
                }
                int nativeBeforeFirstRotatedGpu=0;boolean rotatedGpuObserved=false;
                for(stage=0;stage<4;stage++){
                    boolean gpu=TerrainOracleViews073.gpu(view(),stage);if(rotated&&!rotatedGpuObserved){if(gpu){group.put("nativeOperationsAfterRotationBeforeGpu",nativeBeforeFirstRotatedGpu);require(nativeBeforeFirstRotatedGpu==0,"A native render preceded the rapid GPU operation");rotatedGpuObserved=true;}else nativeBeforeFirstRotatedGpu++;}
                    mode(gpu);guard(mc);require(TerrainScene073Qa.oracleTick()==groupTick,"Client tick advanced inside comparison group");event("world-entry");originalCalls++;original.call(.5f,deadline);event("world-return");validateStage(stage);
                }
                require(TerrainScene073Qa.oracleTick()==groupTick&&gpuValidation!=null&&nestedOriginalCalls==0,"GPU guards missing or nested world operations unsupported");
                var comparisons=new ArrayList<Map<String,Object>>();boolean exact=true;
                for(String[] pair:new String[][]{{"native-a","native-b"},{"native-a","native-c"},{"native-b","native-c"},{"native-a","gpu"}}){var comparison=compare(sample(pair[0]),sample(pair[1]));comparisons.add(comparison);exact&=Boolean.TRUE.equals(comparison.get("passed"));}
                if(rapid()){
                    var before=map(sourceValidation.get("actualIndirectRecipes"));var after=map(gpuValidation.get("actualIndirectRecipes"));var newlyExposed=TerrainOracleGeometry073.difference(before,after);
                    group.put("actualNewlyExposedGeometry",newlyExposed);require(((Number)newlyExposed.get("newlyExposedCommands")).intValue()>0,"Rapid view exposed no new actual native geometry");
                    require(((Number)after.get("computeDispatches")).longValue()>((Number)before.get("computeDispatches")).longValue(),"Same-frame yaw did not dispatch current GPU culling");
                    var sourceShader=map(sourceValidation.get("shader"));var targetShader=map(gpuValidation.get("shader"));require(!Arrays.equals((float[])sourceShader.get("modelView"),(float[])targetShader.get("modelView")),"Actual native view matrix did not change after rotation");
                    var sourceResident=map(sourceValidation.get("resident"));var targetResident=map(gpuValidation.get("resident"));require(((Number)targetResident.get("passSerial")).longValue()>((Number)sourceResident.get("passSerial")).longValue(),"Rapid GPU pass reused an earlier pass serial");
                    require(!Arrays.equals(sample("source-gpu").rgba,sample("gpu").rgba),"Yaw reversal produced the same full world image");
                }
                Object worldTime=sample("native-a").metadata.get("worldClientTime");for(Sample current:samples)if(!current.name.equals("source-gpu"))require(Objects.equals(worldTime,current.metadata.get("worldClientTime")),"Client world time changed inside the comparison group");
                Map<String,Object> stable=new TreeMap<>(map(samples.getFirst().metadata.get("betaSettings")));stable.remove("gpuTerrainCommands");stable.remove("effectiveGpuTerrain");
                for(Sample sample:samples){var current=new TreeMap<>(map(sample.metadata.get("betaSettings")));current.remove("gpuTerrainCommands");current.remove("effectiveGpuTerrain");require(current.equals(stable),"Public Beta settings changed within the comparison group");}
                var captures=new ArrayList<Map<String,Object>>();for(Sample sample:samples)captures.add(save(sample));
                group.put("captures",captures);group.put("comparisons",comparisons);group.put("actualEvents",List.copyOf(events));group.put("actualTerrainReturns",Arrays.copyOf(terrainReturns,4));group.put("actualWorldBlurCallsAfterCapture",Arrays.copyOf(blurCalls,4));group.put("actualWorldBlurExits",Arrays.copyOf(blurExits,4));group.put("actualGroupedOriginalWorldOperations",originalCalls);group.put("actualControlledWorldOperations",originalCalls+preludeCalls+sourceCalls);group.put("actualNestedWorldOperations",nestedOriginalCalls);group.put("actualSourceGpuOriginalWorldOperations",sourceCalls);group.put("sourceGpuTerrainReturns",terrainReturns[4]);group.put("sourceGpuBlurEntries",blurCalls[4]);group.put("sourceGpuBlurExits",blurExits[4]);group.put("gpuNativeGuardValidation",gpuValidation);group.put("passed",exact);group.put("zeroTolerance",true);group.put("historyUsedForComparison",false);group.put("finalFrameSubstitution",false);group.put("performanceClaim",false);
                require(exact,"Strict native repeats or GPU world RGBA/F32/U32 comparison failed");succeeded=true;
            }
        }catch(Throwable failure){problem=failure;failed=true;group.put("passed",false);group.put("error",stack(failure));group.put("actualEvents",List.copyOf(events));group.put("passiveCaptureErrors",List.copyOf(captureErrors));group.put("actualTerrainReturns",terrainReturns.clone());try{var saved=new ArrayList<Map<String,Object>>();for(var sample:samples)saved.add(save(sample));group.put("captures",saved);write(false);}catch(Exception io){failure.addSuppressed(io);}
        }finally{
            stage=-1;running=false;
            try{if(rotated)TerrainResidentSceneWorld.freezeClientCamera(mc,TerrainOracleViews073.source(plan,view()));if(gpuOption!=null)gpuOption.setBoolean(null,oldGpu);GpuTerrainCommands.enabled=oldBackend;}
            catch(Exception failure){problem=failure;succeeded=false;complete=false;}
        }
        if(problem!=null)TerrainScene073Qa.oracleFailed(problem);
        else if(succeeded){
            try{
                stage=4;guard(mc);stage=-1;boolean restored=BetaOptimizationsConfig.getGpuTerrainCommands()==oldGpu&&GpuTerrainCommands.enabled==oldBackend;require(restored,"Controlled flags were not restored");group.put("modeBeforeAndRestored",Map.of("publicGpuTerrain",oldGpu,"effectiveGpuTerrain",oldBackend));group.put("controlledGpuFlagRestored",true);group.put("ownedCameraRestored",true);groups.add(group);require(groups.size()<=(allViews()?TerrainOracleViews073.GROUPS:1),"Unbounded comparison groups");
                complete=!allViews()||groups.size()==TerrainOracleViews073.GROUPS;write(complete);samples.clear();
                if(complete)TerrainScene073Qa.oracleComplete(report);else{resetGroup();TerrainScene073Qa.oracleViewComplete();}
            }catch(Exception failure){failed=true;TerrainScene073Qa.oracleFailed(failure);}
        }
    }
    private static void validateStage(int current){
        require(capturePoints[current]==1&&captureErrors.isEmpty(),"Passive capture failed/unsupported: "+captureErrors);require(terrainReturns[current]==1,"Actual solid terrain-return count must be exactly one");require(blurCalls[current]==1&&blurExits[current]==1,"Actual Hybrid blur must enter/exit exactly once");
        require(validOrder(events.stream().filter(e->Integer.valueOf(current).equals(e.get("stage"))).map(e->(String)e.get("kind")).toList()),"Actual world/capture/blur event order differs");
        require(samples.stream().filter(s->s.name.equals(current==4?"source-gpu":TerrainOracleViews073.names(view()).get(current))).count()==1,"Actual sample missing/duplicated");
    }
    private static Sample sample(String name){return samples.stream().filter(s->s.name.equals(name)).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static void resetGroup(){samples.clear();events.clear();captureErrors.clear();group=new LinkedHashMap<>();terrainReturns=new int[5];blurCalls=new int[5];capturePoints=new int[5];blurExits=new int[5];prelude=eventSerial=0;originalCalls=preludeCalls=preludeTerrainReturns=nestedOriginalCalls=sourceCalls=0;gpuValidation=sourceValidation=null;sourceComplete=false;}
    private static void error(String message){if(captureErrors.size()<32)captureErrors.add(message);}
    public static void captureWorld(int pass,float partial){
        if(!controlling()||stage<0)return;
        try{
            require(pass==2&&Float.floatToRawIntBits(partial)==Float.floatToRawIntBits(.5f),"Unsupported world pass/partial tick");require(++capturePoints[stage]==1&&blurCalls[stage]==0,"World capture was duplicated or followed a blur invocation");
            event("pre-capture");
            Minecraft mc=Minecraft.getInstance();guard(mc);int tick=stage==4?sourceTick:groupTick;require(TerrainScene073Qa.oracleTick()==tick,"Passive world capture changed client tick");
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
            var metadata=new LinkedHashMap<String,Object>();metadata.put("width",WIDTH);metadata.put("height",HEIGHT);metadata.put("fullUncropped",true);metadata.put("drawFramebuffer",draw);metadata.put("mainFramebuffer",mc.getRenderTarget().frameBufferId);metadata.put("partialTick",partial);metadata.put("clientTick",tick);metadata.put("viewIndex",view());metadata.put("sourceGpuSetup",stage==4);metadata.put("depthByteOrder",ByteOrder.nativeOrder().toString());metadata.put("nonClearDepthPixels",nonClear);metadata.put("perPassPhysicalProof",TerrainScene073GpuEvidence.passProof(GpuTerrainCommands.enabled));metadata.put("betaSettings",settings());metadata.put("effectiveGpuTerrain",GpuTerrainCommands.enabled);metadata.put("camera",List.of(mc.player.x,mc.player.y,mc.player.z,mc.player.yaw,mc.player.pitch,mc.options.fov));metadata.put("blurCallsBeforeCapture",blurCalls[stage]);metadata.put("worldClientTime",mc.world.getTime());
            metadata.put("readbackGlError",glError);metadata.put("captureStage","pre-PolyBlur Profiler.hand BEFORE");require(samples.size()<5,"Per-view raw sample bound exceeded");samples.add(new Sample(stageName(),rgba,depth,integerDepth,metadata));event("capture-complete");
            if(nonClear<=100)error("Stage"+stage+" native depth is trivial/unsupported");if(glError!=GL11.GL_NO_ERROR)error("Stage"+stage+" native readback GL error"+glError);
        }catch(Throwable failure){error("Stage"+stage+" passive pre-PolyBlur capture failed: "+failure);}
    }
    private static void mode(boolean value)throws Exception{gpuOption.setBoolean(null,value);GpuTerrainCommands.enabled=value;}
    private static void event(String kind){require(events.size()<32,"Per-view event bound exceeded");events.add(Map.of("sequence",++eventSerial,"stage",stage,"name",stageName(),"kind",kind,"clientTick",TerrainScene073Qa.oracleTick()));}
    private static boolean validOrder(List<String> kinds){return kinds.equals(List.of("world-entry","pre-capture","capture-complete","blur-entry","blur-exit","world-return"));}
    private static void guard(Minecraft mc){require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getServer()!=null&&mc.world!=null&&mc.player!=null&&mc.getCurrentServerEntry()==null,"Only the owned offline fixture can run the oracle");require(mc.width==WIDTH&&mc.height==HEIGHT&&mc.screen==null&&mc.player.x==expectedCamera().x()&&mc.player.y==expectedCamera().y()&&mc.player.z==expectedCamera().z()&&mc.player.yaw==expectedCamera().yaw()&&mc.player.pitch==expectedCamera().pitch()&&mc.options.fov==expectedCamera().fov(),"Owned oracle camera moved or resized");require(!BetaOptimizationsConfig.getWorldRenderScale()&&!mc.options.anaglyph,"World scale/anaglyph must remain disabled as in the source proof");}
    private static byte[] pixels(int format,int type){readback.clear();GL11.glReadPixels(0,0,WIDTH,HEIGHT,format,type,readback);byte[] bytes=new byte[readback.capacity()];readback.get(bytes);return bytes;}
    private static Map<String,Object> compare(Sample a,Sample b){Map<String,Object> result=new LinkedHashMap<>();result.put("first",a.name);result.put("second",b.name);boolean exact=true;for(int i=0;i<3;i++){byte[] x=i==0?a.rgba:i==1?a.depth:a.integerDepth,y=i==0?b.rgba:i==1?b.depth:b.integerDepth;int count=0;for(int at=0;at<x.length;at++)if(x[at]!=y[at])count++;result.put(i==0?"rgbaDifferentBytes":i==1?"floatDepthDifferentBytes":"integerDepthDifferentBytes",count);exact&=count==0;}result.put("passed",exact);return result;}
    private static Map<String,Object> save(Sample s)throws Exception{Files.createDirectories(captureDirectory());var value=new LinkedHashMap<String,Object>(s.metadata);value.put("name",s.name);value.put("rgba",gzip(s.rgba,s.name+".rgba.gz"));value.put("floatDepth",gzip(s.depth,s.name+".depth-f32.gz"));value.put("integerDepth",gzip(s.integerDepth,s.name+".depth-u32.gz"));BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int at=((HEIGHT-1-y)*WIDTH+x)*4;image.setRGB(x,y,(s.rgba[at+3]&255)<<24|(s.rgba[at]&255)<<16|(s.rgba[at+1]&255)<<8|s.rgba[at+2]&255);}Path png=captureDirectory().resolve(s.name+".pre-polyblur.png");ImageIO.write(image,"png",png.toFile());value.put("png",png.toString());return value;}
    private static Map<String,Object> gzip(byte[] data,String name)throws Exception{Path file=captureDirectory().resolve(name);try(var stream=new GZIPOutputStream(Files.newOutputStream(file))){stream.write(data);}return Map.of("path",file.toString(),"bytes",data.length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)),"encoding","gzip lossless");}
    private static Map<String,Object> settings(){var values=new TreeMap<String,Object>();for(var e:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(e.getValue() instanceof Property<?> p){Object v=p.get();if(v instanceof Boolean||v instanceof Number||v instanceof String)values.put(e.getKey(),v);}values.put("effectiveGpuTerrain",GpuTerrainCommands.enabled);return values;}
    private static Map<String,Object> blurState()throws Exception{Class<?> type=Class.forName("org.polyfrost.polyblur.client.PolyBlurConfig");Object config=type.getField("INSTANCE").get(null);var values=new LinkedHashMap<String,Object>();for(String[] v:new String[][]{{"enabled","isEnabled"},{"type","getBlurType"},{"strength","getStrength"},{"handStrength","getHandBlurStrength"},{"samples","getMotionBlurSamples"},{"blurHand","getBlurHand"},{"phosphorMode","getPhosphorMode"},{"velocityBuffer","getVelocityBuffer"},{"translationParallax","getTranslationParallax"}})values.put(v[0],type.getMethod(v[1]).invoke(config));values.put("nativePostProcessSupported",GLX.usePostProcess);require(Integer.valueOf(2).equals(values.get("type")),"Unsupported world blur recipe; do not skip temporal controls");return values;}
    private static Path output(){return Path.of(System.getProperty("arcadium.terrainOracle073.output"));}
    private static Path captureDirectory(){return allViews()?output().getParent().resolve(String.format(java.util.Locale.ROOT,"%02d-%s",view(),TerrainOracleViews073.capture(plan,view()).name())):output().getParent();}
    private static void write(boolean passed)throws Exception{
        Files.createDirectories(output().getParent());report.clear();
        if(!allViews())report.putAll(group);else{report.put("scope","All eight original steady cameras plus separately labeled same-frame GPU-first yaw reversal; full WORLD PRE-PolyBlur, not final composited frames");report.put("groups",List.copyOf(groups));if(failed)report.put("failedGroup",group);report.put("ordinaryNativeViewPreparation",TerrainOraclePreparation073.evidence());}
        report.put("passed",passed&&!failed);report.put("zeroTolerance",true);report.put("historyUsedForComparison",false);report.put("finalFrameSubstitution",false);report.put("all8CameraQualified",allViews()&&passed);report.put("all8SteadyPrePolyBlurQualified",allViews()&&passed);report.put("rapidYawSameFrameQualified",allViews()&&passed);report.put("oneCameraPrePolyBlurParityQualified",!allViews()&&passed);report.put("blockPublicationStressQualified",false);report.put("fullSceneVisualParityQualified",false);report.put("publicationQualified",false);report.put("performanceClaim",false);report.put("memoryBounds",Map.of("maximumRawSamplesPerView",5,"maximumRawHeapBytes",5L*WIDTH*HEIGHT*4*3,"reusedDirectReadbackBytes",WIDTH*HEIGHT*4,"maximumMetadataGroups",9,"maximumEventsPerView",32,"rawSamplesReleasedAfterEachView",true));
        Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static String stack(Throwable failure){var text=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(text));return text.toString();}
}
