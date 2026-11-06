package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;

public final class ActorScene073PerformanceEvidence {
    private static final boolean ENABLED=Boolean.getBoolean("arcadium.actorScene073.performance");
    private static Map<String,Object> before;
    private static String failure;
    private ActorScene073PerformanceEvidence(){}
    public static void begin(){
        if(!ENABLED)return;
        try{owned();require(!PlayerPipeline072Qa.timingActive(),"Counter snapshots must precede the sampling window");before=snapshot();if(on())require(ActorSceneGpu.dispatches>0&&ActorSceneGpu.resolvedActorRows>0,"The actual actor-scene GPU path did not warm before sampling");require(GpuModelTransforms.dispatches>0,"Native GPU rendering did not warm before sampling");}
        catch(Throwable error){failure=error.toString();}
    }
    public static void finish(Throwable parentFailure){
        if(!ENABLED)return;
        var result=new LinkedHashMap<String,Object>();
        try{
            owned();require(!PlayerPipeline072Qa.timingActive(),"Post-run metadata was invoked inside the sampling window");require(parentFailure==null&&failure==null,"The performance fixture failed: "+(parentFailure==null?failure:parentFailure));require(before!=null,"The before-window counters are absent");Map<String,Object> after=snapshot(),delta=difference(before,after);Map<?,?> report=(Map<?,?>)field(PlayerPipeline072Qa.class,"report"),performance=(Map<?,?>)report.get("performance");require(performance!=null,"An unprofiled performance result is absent");long samples=((Number)performance.get("frames")).longValue();require(samples>100,"The measured frame count is incomplete");Map<?,?> common=(Map<?,?>)delta.get("render");long compact=((Number)common.get("compactPlayers")).longValue(),nativeParts=((Number)common.get("nativeBodyParts")).longValue();require(compact>0||nativeParts>0,"The native renderer did not process actor bodies during sampling");
            if(on()){
                Map<?,?> actor=(Map<?,?>)delta.get("actor"),scene=(Map<?,?>)delta.get("scene"),materials=(Map<?,?>)delta.get("materials");require(((Number)actor.get("dispatches")).longValue()>0&&((Number)actor.get("resolvedRows")).longValue()>0&&((Number)materials.get("checks")).longValue()>0,"The new actor scene had no actual GPU/material work in the sampling window");require(((Number)scene.get("gpuOnlyCaptures")).longValue()>0,"The supported GPU-only producer was unused");
            }
            result.put("passed",true);result.put("before",before);result.put("after",after);result.put("delta",delta);result.put("declaredActors",Integer.getInteger("arcadium.playerPipeline072.players"));result.put("actualCompactCapturesPerMeasuredInterval",compact/(double)samples);result.put("actualNativeBodyPartsPerMeasuredInterval",nativeParts/(double)samples);result.put("actualVisibilityRecordedNotForced",true);result.put("performance",performance);
        }catch(Throwable error){result.put("passed",false);result.put("failure",error.toString());result.put("before",before);}
        result.put("mode",on()?"on":"off");result.put("snapshotsOnlyAtBoundaries",true);result.put("frameReadbacks",0);result.put("perFrameMaterialOrPlayerObservers",0);result.put("jfr",false);result.put("sameFrameQualityClaim",false);result.put("scope","Owned generated-skin actor workload; actual visible/rendered counts are reported separately from the declared roster");
        try{Path output=Path.of(System.getProperty("arcadium.actorScene073.performanceOutput"));Files.createDirectories(output.getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){throw new IllegalStateException("Performance evidence export failed",error);}
    }
    private static boolean on(){return "on".equals(System.getProperty("arcadium.actorScene073.performanceExpected"));}
    private static void owned(){
        var mc=Minecraft.getInstance();require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"The actor performance case requires its owned offline identity");require("performance".equals(System.getProperty("arcadium.playerPipeline072.mode"))&&"default".equals(System.getProperty("arcadium.playerPipeline072.scene")),"Only the declared default dynamic actor workload is certified here");require(Set.of("off","on").contains(System.getProperty("arcadium.actorScene073.performanceExpected"))&&GpuPlayerPackets.actorSceneEnabled()==on(),"The requested actor-scene variant differs from actual production state");require(Set.of(3,64).contains(Integer.getInteger("arcadium.playerPipeline072.players"))&&Integer.getInteger("arcadium.playerPipeline072.warmup")==25&&Integer.getInteger("arcadium.playerPipeline072.seconds")==30,"The declared fixed3/64,25s/30s workload changed");require(!Boolean.getBoolean("arcadium.playerPipeline072.profiling")&&System.getProperty("arcadium.actorScene073.minecraft")==null&&System.getProperty("arcadium.actorPacket072.proof")==null,"Profiling and per-frame correctness observers must be absent");require(System.getProperty("arcadium.residentBodies072.mode","off").equals("off"),"The separately unadopted body experiment must remain off");require(mc.width==1920&&mc.height==1080&&mc.options.viewDistance==8,"The full-size D8 workload changed");
    }
    private static Map<String,Object> snapshot()throws Exception{
        var mc=Minecraft.getInstance();var data=new LinkedHashMap<String,Object>();data.put("render",Map.of("compactPlayers",GpuPlayerPipeline.playersDrawn,"nativeBodyParts",NativePlayerPosePipeline.bodyPartsQueued,"modelDispatches",GpuModelTransforms.dispatches,"playerAssignments",GpuPlayerPackets.actorAssignments));data.put("observed",Map.of("visiblePlayerBudget",GpuModelTransforms.visiblePlayers(),"residentActorSlots",GpuPlayerPackets.residentActorCount(),"worldPlayerRoster",mc.world.players.size(),"worldEntityRoster",mc.world.entities.size(),"entityRendererDebug",mc.worldRenderer.getEntityDebugInfo()));data.put("residentDependency",Map.of("enabled",GpuResidentPlayerState.enabled,"binds",GpuResidentPlayerState.binds,"busyFallbacks",GpuResidentPlayerState.busyFallbacks,"mappingFailures",GpuResidentPlayerState.mappingFailures,"wordsWritten",GpuResidentPlayerState.wordsWritten,"wordsReused",GpuResidentPlayerState.wordsReused));
        if(on()){
            var actor=new LinkedHashMap<String,Object>();actor.put("dispatches",ActorSceneGpu.dispatches);actor.put("reuses",ActorSceneGpu.reuses);actor.put("resolvedRows",ActorSceneGpu.resolvedActorRows);actor.put("streamRows",ActorSceneGpu.streamRows+GpuPlayerPackets.actorSceneStreamRows);actor.put("busyFallbacks",ActorSceneGpu.busyFallbacks);actor.put("failures",ActorSceneGpu.failures);actor.put("contextRefusals",ActorSceneGpu.contextRefusals);actor.put("tickUploads",ActorSceneGpu.tickUploads);actor.put("tickUploadWords",ActorSceneGpu.tickUploadWords);actor.put("frameUploadWords",ActorSceneGpu.frameUploadWords);actor.put("ownedBytes",ActorSceneGpu.bytes());data.put("actor",actor);
            ActorSceneData scene=(ActorSceneData)field(GpuPlayerPackets.class,"scene");ActorSceneMaterials materials=(ActorSceneMaterials)field(GpuPlayerPackets.class,"materials");data.put("scene",Map.of("captures",scene.frameCaptures,"gpuOnlyCaptures",scene.gpuOnlyCaptures,"tickWrites",scene.tickWrites,"tickReuses",scene.tickReuses,"lazyFullCpuRows",scene.cpuInterpolationRows,"scalarOverrideWords",scene.overrideInterpolationWords,"cachedOverrideWords",scene.constantOverrideWords,"poseMismatches",scene.poseMismatches));data.put("materials",Map.of("checks",materials.checks,"created",materials.created,"reuses",materials.reuses,"fallbacks",materials.fallbacks));
        }
        else data.put("actorInspection","Skipped to retain the ordinary OFF kernel initialization boundary");return data;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> difference(Map<String,Object> a,Map<String,Object> b){var result=new LinkedHashMap<String,Object>();for(String key:b.keySet()){Object left=a.get(key),right=b.get(key);if(left instanceof Number x&&right instanceof Number y)result.put(key,y.longValue()-x.longValue());else if(left instanceof Map<?,?> x&&right instanceof Map<?,?> y)result.put(key,difference((Map<String,Object>)x,(Map<String,Object>)y));}return result;}
    private static Object field(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(null);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
