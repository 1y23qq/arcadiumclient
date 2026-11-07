// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.runtime.qa.ActorFused073PerformancePolicy;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import org.lwjgl.opengl.Display;
import org.polyfrost.oneconfig.api.config.v1.Property;

public final class ActorFused073PerformanceEvidence {
    private static final boolean ENABLED=Boolean.getBoolean("arcadium.actorFused073.performance");
    private static final Thread OWNER=Thread.currentThread();
    private static Snapshot before;private static boolean finished;
    private static com.google.gson.JsonObject domain;
    private record Snapshot(Map<String,Object> data,GpuPlayerPackets.FusedActorMetrics metrics,long compact,long nativeParts,long dispatches,long sorts,Set<String> rendered){}
    private ActorFused073PerformanceEvidence(){}
    public static void begin(){
        if(!ENABLED)return;
        try{owned();require(!PlayerPipeline072Qa.timingActive(),"Before metadata entered the measured interval");require(before==null,"Fused performance counters were armed twice");before=snapshot();ActorFused073PerformancePolicy.metrics(before.metrics,on(),true);require(before.dispatches>0&&!before.rendered.isEmpty(),"Actual native bodies did not warm");}
        catch(Exception failure){throw new IllegalStateException("Fused boundary eligibility failed before measurement",failure);}
    }
    public static void finish(Throwable parentFailure){
        if(!ENABLED||finished)return;finished=true;var result=new LinkedHashMap<String,Object>();
        try{
            owned();require(!PlayerPipeline072Qa.timingActive(),"After metadata entered the measured interval");require(parentFailure==null,"Parent fixture failed: "+parentFailure);require(before!=null,"The actual before boundary is absent");Snapshot after=snapshot();
            Map<?,?> parent=(Map<?,?>)field(PlayerPipeline072Qa.class,null,"report");Map<?,?> perf=(Map<?,?>)parent.get("performance");require(perf!=null&&parent.get("quietWindow") instanceof Map<?,?>,"Unprofiled Root quiet sample is absent");long frames=((Number)perf.get("frames")).longValue();
            require(before.rendered.equals(after.rendered),"Native subject set changed at the sampling boundaries");require(before.data.get("configuration").equals(after.data.get("configuration")),"Video/feature configuration changed during timing");
            Map<String,Object> fusedDelta=ActorFused073PerformancePolicy.delta(before.metrics,after.metrics,on());
            long nativeComputes=(after.dispatches-before.dispatches)-(after.sorts-before.sorts);require(nativeComputes>0,"No actual native model compute submissions");
            if(on()){require(((Number)fusedDelta.get("dispatches")).longValue()==nativeComputes&&((Number)fusedDelta.get("ordinaryDispatches")).longValue()==0,"Measured model submissions did not all use the fused reader");
                require(((Number)fusedDelta.get("actorRowReads")).longValue()==nativeComputes*players(),"Measured tagged source-row count differs from the exact actor domain");
                require(((Number)fusedDelta.get("cpuInterpolationRows")).longValue()==0&&((Number)fusedDelta.get("validationRows")).longValue()==0,"CPU interpolation or correctness observer entered the sample");}
            result.put("nativeModelComputeSubmissions",nativeComputes);result.put("consumptionScope","Aggregate fenced native submissions/row counts, not per-frame per-actor GPU execution or completion");Map<String,Object> workload=ActorFused073PerformancePolicy.workload(after.compact-before.compact,after.nativeParts-before.nativeParts,after.dispatches-before.dispatches,frames);
            var rates=new LinkedHashMap<String,Object>();for(String key:List.of("dispatches","actorRowReads","ordinaryDispatches","ordinaryRowReads","busyFallbacks","uploadWords","inputReuses","gpuOnlyCaptures","cpuInterpolationRows"))rates.put(key+"PerSampledInterval",((Number)fusedDelta.get(key)).longValue()/(double)frames);
            result.put("passed",true);result.put("before",before.data);result.put("after",after.data);result.put("fusedDelta",fusedDelta);result.put("fusedPerInterval",rates);result.put("workload",workload);result.put("performance",perf);result.put("quietWindow",parent.get("quietWindow"));result.put("declaredActors",players());result.put("actualBoundaryNativeSubjectCount",after.rendered.size());result.put("actualBoundaryNativeSubjectIds",after.rendered);result.put("actualVisibilityObservedNotForced",true);
        }catch(Throwable failure){result.put("passed",false);result.put("failure",failure.toString());if(before!=null)result.put("before",before.data);}
        result.put("domainId",System.getProperty("arcadium.actorFused073.domain"));result.put("bindingSha256",System.getProperty("arcadium.actorFused073.bindingSha256"));result.put("schema","arcadium.fused073.performance-evidence.v4");result.put("producerWitnessUsesRetainedSource",true);result.put("nativeVisibilityOriginEpochKnown",false);result.put("gpuExecutionOrCompletionClaim",false);result.put("variant",on()?"on":"off");result.put("profile",System.getProperty("arcadium.actorFused073.profile",""));result.put("snapshotsOnlyBeforeAfter",true);result.put("perFrameObserverHooks",0);result.put("gpuBufferOrPixelReadbacks",0);result.put("jfr",false);result.put("rootOperationAuditRequired",true);result.put("performanceCertified",false);result.put("adopted",false);result.put("scope","Exact bound generated-skin domain; counters are actual native submissions and row reads, not distinct actor counts or real Hypixel performance.");
        try{Path path=Path.of(System.getProperty("arcadium.actorFused073.output"));Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception failure){throw new IllegalStateException("Fused performance export failed",failure);}
    }
    private static com.google.gson.JsonObject domain()throws Exception{
        if(domain==null){
            Path path=Path.of(System.getProperty("arcadium.actorFused073.domainBinding"));byte[] bytes=Files.readAllBytes(path);
            require(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).equals(System.getProperty("arcadium.actorFused073.bindingSha256")),"Domain binding bytes differ");
            domain=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            require(domain.get("id").getAsString().equals(System.getProperty("arcadium.actorFused073.domain")),"Domain identity differs");
        }
        return domain;
    }
    private static boolean on(){return "on".equals(System.getProperty("arcadium.actorFused073.expected"));}
    private static int players(){return Integer.getInteger("arcadium.playerPipeline072.players",0);}
    private static void owned()throws Exception{
        var mc=Minecraft.getInstance();require(Thread.currentThread()==OWNER,"Boundary metadata left its native client owner");require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null&&mc.world!=null&&mc.player!=null,"Only the owned offline world is permitted");
        require(Set.of("off","on").contains(System.getProperty("arcadium.actorFused073.expected"))&&GpuPlayerPackets.fusedActorInputEnabled()==on(),"Requested fused variant differs from actual Main");
        ActorFused073PerformancePolicy.mode(on(),Boolean.getBoolean("arcadium.actorScene073.enabled"),Boolean.getBoolean("arcadium.performance073.quietWindow"),players(),Integer.getInteger("arcadium.playerPipeline072.warmup",0),Integer.getInteger("arcadium.playerPipeline072.seconds",0),domain().get("actors").getAsInt());
        require(Boolean.getBoolean("arcadium.actorScene073.performance")&&"performance".equals(System.getProperty("arcadium.playerPipeline072.mode"))&&"default".equals(System.getProperty("arcadium.playerPipeline072.scene")),"Common dynamic3/64 scheduling contract differs");
        require(domain().get("profile").getAsString().equals(System.getProperty("arcadium.actorFused073.profile")),"The qualified Beta profile differs");
        require(domain().get("actors").getAsInt()==players(),"The qualified actor count differs");
        require(new GsonBuilder().create().toJsonTree(List.of(mc.player.x,mc.player.y,mc.player.z,mc.player.yaw,mc.player.pitch,mc.options.fov)).equals(domain().get("camera")),"The qualified camera differs");
        require(!Boolean.getBoolean("arcadium.playerPipeline072.profiling")&&System.getProperty("arcadium.actorScene073.minecraft")==null&&!Boolean.getBoolean("arcadium.actorPacket072.proof")&&!Boolean.getBoolean("arcadium.nameReset072.proof")&&!Boolean.getBoolean("arcadium.residentMaterial072.proof"),"A profiler or correctness observer is enabled");
        require("off".equals(System.getProperty("arcadium.residentBodies072.mode","off"))&&!Boolean.getBoolean("arcadium.residentBodies072.performanceQualified"),"Unqualified whole-body experiment must remain off");
        require(mc.width==1920&&mc.height==1080&&Display.getWidth()==1920&&Display.getHeight()==1080&&mc.options.viewDistance==8&&mc.options.fpsLimit==260&&!mc.options.vsync&&mc.options.perspective==0&&Display.isActive(),"Full-resolution unlimited D8 native video contract changed");
    }
    @SuppressWarnings("unchecked") private static Snapshot snapshot()throws Exception{
        long metadataStart=System.nanoTime();var mc=Minecraft.getInstance();var data=new LinkedHashMap<String,Object>();data.put("metadataStartedNano",metadataStart);var metrics=GpuPlayerPackets.fusedActorMetrics();data.put("fused",metrics);data.put("render",Map.of("compactCaptures",GpuPlayerPipeline.playersDrawn,"nativeBodyParts",NativePlayerPosePipeline.bodyPartsQueued,"nativeModelDispatches",GpuModelTransforms.dispatches,"nativeSortDispatches",GpuModelTransforms.sortedBatches,"nativePlayerAssignments",GpuPlayerPackets.actorAssignments));
        Object visible=field(GpuModelTransforms.class,null,"visibility");Set<Object> observed=(Set<Object>)field(visible.getClass(),visible,"observed");List<?> roster=(List<?>)field(PlayerPipeline072Qa.class,null,"actors");require(roster.size()==players(),"Declared actor roster differs");
        var rendered=new TreeSet<String>();var subjects=new ArrayList<Map<String,Object>>();
        ActorFused073ProducerWitness.Evidence witness=on()?ActorFused073ProducerWitness.captureRuntime(mc.world,roster):null;
        for(int i=0;i<roster.size();i++){
            var actor=(ClientPlayerEntity)roster.get(i);require(!actor.removed&&mc.world.entities.contains(actor)&&mc.world.players.contains(actor)&&mc.world.getEntity(72000+i)==actor,"A native subject left its authoritative world/ID membership");boolean seen=observed.contains(actor);var row=new LinkedHashMap<String,Object>();row.put("id",72000+i);row.put("name",actor.getName());row.put("nativeEntryObservedUnversioned",seen);row.put("position",List.of(actor.x,actor.y,actor.z));row.put("chunk",List.of(actor.chunkX,actor.chunkY,actor.chunkZ));row.put("inChunk",actor.inChunk);row.put("invisible",actor.isInvisible());
            var shape=actor.getShape();row.put("bounds",List.of(shape.minX,shape.minY,shape.minZ,shape.maxX,shape.maxY,shape.maxZ));row.put("skinMask",actor.getSyncedData().getByte(10));row.put("spectator",actor.isSpectator());row.put("sleeping",actor.isSleeping());row.put("riding",actor.isRiding());
            var expected=domain().getAsJsonObject("geometry").getAsJsonArray("subjects").get(i).getAsJsonObject();
            var actualJson=new GsonBuilder().create().toJsonTree(row).getAsJsonObject();for(String key:List.of("id","name","position","bounds","skinMask","invisible","spectator","sleeping","riding"))require(actualJson.get(key).equals(expected.get(key)),"Qualified native geometry differs: "+key);
            require(seen,"A qualified subject is absent from the unversioned native entry set: "+actor.getName());rendered.add((72000+i)+":"+actor.getName());
            if(on()){var producer=witness.rows().get(i);require(producer.subjectIndex()==i,"Producer witness order differs");row.put("actorSlot",producer.primaryJob());row.put("actorHandle",producer.handle());row.put("gpuOnlyCaptureWitness",producer);row.put("producerWitnessClassification",witness.classification());}
            subjects.add(row);
        }
        require(!rendered.isEmpty()&&rendered.size()<=players(),"No actual native subjects were observed");for(Object entity:observed)require(entity==mc.player||roster.contains(entity),"An uncontrolled player reached the native renderer");
        data.put("subjects",subjects);data.put("producerWitness",witness);data.put("nativeVisibilityOriginEpochKnown",false);data.put("observed",Map.of("nativeSubjectsInUnversionedSetAtRead",rendered.size(),"visibleBudgetPreviousFrame",GpuModelTransforms.visiblePlayers(),"residentActorSlots",GpuPlayerPackets.residentActorCount(),"packetCount",GpuPlayerPackets.count(),"nativeBatchEpoch",GpuModelTransforms.batchEpoch(),"worldPlayerRoster",mc.world.players.size(),"worldEntityRoster",mc.world.entities.size(),"rendererDebug",mc.worldRenderer.getEntityDebugInfo()));
        data.put("residentDependency",Map.of("enabled",GpuResidentPlayerState.enabled,"binds",GpuResidentPlayerState.binds,"busyFallbacks",GpuResidentPlayerState.busyFallbacks,"mappingFailures",GpuResidentPlayerState.mappingFailures,"writtenWords",GpuResidentPlayerState.wordsWritten,"reusedWords",GpuResidentPlayerState.wordsReused));
        data.put("configuration",configuration(mc));data.put("domainId",domain().get("id").getAsString());data.put("bindingSha256",System.getProperty("arcadium.actorFused073.bindingSha256"));
        var terrain=(Map<?,?>)call(PlayerPipeline072Qa.class,"terrainState",new Class<?>[]{Minecraft.class},mc);require(Boolean.TRUE.equals(terrain.get("ready")),"Boundary dataset is not complete");
        require(new GsonBuilder().create().toJsonTree(terrain.get("inventory")).equals(domain().get("dataset")),"Boundary loaded geometry dataset differs");
        data.put("dataset",terrain.get("inventory"));data.put("metadataEndedNano",System.nanoTime());return new Snapshot(data,metrics,GpuPlayerPipeline.playersDrawn,NativePlayerPosePipeline.bodyPartsQueued,GpuModelTransforms.dispatches,GpuModelTransforms.sortedBatches,Set.copyOf(rendered));
    }
    private static Map<String,Object> configuration(Minecraft mc)throws Exception{
        var result=new LinkedHashMap<String,Object>();Object live=call(PlayerPipeline072Qa.class,"settings",new Class<?>[]{Minecraft.class},mc);result.put("fullParentSettings",live);Map<?,?> settings=(Map<?,?>)live;require(settings.get("beta") instanceof Map<?,?> beta&&beta.size()==25,"All25 Beta values must be present");var requested=JsonParser.parseString(Files.readString(Path.of(System.getProperty("arcadium.playerPipeline072.settings")))).getAsJsonObject().get("beta");require(new GsonBuilder().create().toJsonTree(settings.get("beta")).equals(requested),"The actual all25 Beta values differ before measurement");
        require(new GsonBuilder().create().toJsonTree(settings.get("beta")).equals(domain().get("betaAll25")),"All25 Beta values differ from native-qualified domain");
        result.put("video",Map.of("framebufferWidth",mc.width,"framebufferHeight",mc.height,"windowWidth",Display.getWidth(),"windowHeight",Display.getHeight(),"fullscreen",Display.isFullscreen(),"publicGameOptions",publicOptions(mc.options)));
        var features=new TreeMap<String,Object>();for(String name:List.of("Skin3DConfig","AnimationsConfig","CapeConfig","NickHiderConfig","StarHider")){Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client.features."+name);Object instance=type.getField("INSTANCE").get(null),tree=type.getMethod("getTree").invoke(instance);features.put(name,tree(tree));}result.put("featureTrees",features);
        var privateFlags=new TreeMap<String,Object>();for(String key:List.of("arcadium.residentBodies072.mode","arcadium.residentBodies072.sharedPalette","arcadium.residentBodies072.selectedMaterial","arcadium.terrainResident072.enabled","arcadium.namePreparation073.lowerEntity"))privateFlags.put(key,System.getProperty(key,"<absent>"));result.put("otherPrivateFlags",privateFlags);return result;
    }
    private static Map<String,Object> publicOptions(Object options)throws Exception{var values=new TreeMap<String,Object>();for(Field field:options.getClass().getFields()){if(Modifier.isStatic(field.getModifiers()))continue;Object value=field.get(options);if(value==null||value instanceof Number||value instanceof Boolean||value instanceof Enum<?>||value instanceof String)values.put(field.getName(),value==null?"<null>":value instanceof Enum<?> e?e.name():value);}return values;}
    private static Map<String,Object> tree(Object tree)throws Exception{var result=new TreeMap<String,Object>();for(var entry:((Map<?,?>)tree.getClass().getField("map").get(tree)).entrySet())if(entry.getValue() instanceof Property<?> property){Object value=property.get();require(value==null||value instanceof Number||value instanceof Boolean||value instanceof String||value instanceof Enum<?>,"Unrecorded non-scalar feature property: "+entry.getKey());result.put(entry.getKey().toString(),value==null?"<null>":value instanceof Enum<?> e?e.name():value);}return result;}
    private static Object field(Class<?> type,Object instance,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(instance);}
    private static Object call(Class<?> type,String name,Class<?>[]parameters,Object...args)throws Exception{Method method=type.getDeclaredMethod(name,parameters);method.setAccessible(true);return method.invoke(null,args);}
    private static void require(boolean value,String message){ActorFused073PerformancePolicy.require(value,message);}
}
