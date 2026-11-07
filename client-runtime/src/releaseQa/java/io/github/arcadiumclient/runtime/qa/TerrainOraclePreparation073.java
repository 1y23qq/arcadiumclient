// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunkMapAccess;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;

public final class TerrainOraclePreparation073 {
    private static final TerrainResidentSceneBlueprint.Plan plan=TerrainResidentSceneBlueprint.create();
    private static final List<Map<String,Object>> completed=new ArrayList<>();
    private static final List<Map<String,Object>> aims=new ArrayList<>();
    private static final Set<TerrainRoofExposure073.Section> exposed=new HashSet<>();
    private static TerrainResidentSceneBlueprint.Camera goal;
    private static TerrainTrackingInteriorSurvey073.Survey interior;
    private static List<TerrainRoofExposure073.Aim> batch=List.of();
    private static Map<String,Object> row;
    private static Future<Map<String,Object>> proof;
    private static Map<String,Object> firstProof;
    private static String role="none";
    private static int index,aimIndex,batches;
    private static long baseDraws,baseFrames;
    private static Object manager,distanceChanges;
    private TerrainOraclePreparation073(){ }
    public static void begin(int next,Minecraft mc){
        require(next==completed.size()&&next<TerrainOracleViews073.GROUPS,"Preparation view order changed");index=next;goal=TerrainOracleViews073.source(plan,index);baseDraws=TerrainResidentScene.regionDraws;
        aims.clear();exposed.clear();batches=0;row=new LinkedHashMap<>();row.put("viewIndex",index);row.put("sourceCamera",goal.name());row.put("gpuDrawsBeforePreparation",baseDraws);row.put("readinessFlagsWritten",false);row.put("nativeChunkTicksInvoked",false);row.put("viewDistanceChanged",false);row.put("countedAsComparisonView",false);
        var state=TerrainScene073Qa.oracleLastReadiness();manager=state.get("nativeRenderManagerIdentity");distanceChanges=state.get("bootstrapDistanceChanges");require(manager!=null&&distanceChanges!=null,"The original native renderer/distance must already exist");row.put("expectedTargetSections",TerrainOracleViews073.targets(plan,goal).size());toGoal(mc);
    }
    public static boolean tick(Minecraft mc,Map<String,Object> state)throws Exception{
        require(row!=null&&!role.equals("done")&&!role.equals("none"),"Unarmed view preparation");
        require(!GpuTerrainCommands.enabled&&!BetaOptimizationsConfig.getGpuTerrainCommands()&&TerrainResidentScene.regionDraws==baseDraws&&mc.options.viewDistance==8,"Only normal native D8 preparation is allowed");
        require(Objects.equals(manager,state.get("nativeRenderManagerIdentity"))&&Objects.equals(distanceChanges,state.get("bootstrapDistanceChanges")),"Preparation reset native renderer/distance");
        if(!Boolean.TRUE.equals(state.get("datasetReady"))||TerrainScene073Qa.oracleNativeFrames()<=baseFrames)return false;
        require(Integer.valueOf(289).equals(state.get("loadedColumns"))&&Integer.valueOf(4624).equals(state.get("sectionCount"))&&Integer.valueOf(0).equals(state.get("missingSections"))&&Boolean.TRUE.equals(state.get("builderIdle")),"Preparation lost full native dataset gates");
        if(role.equals("interior")){
            if(proof==null){proof=serverProof(mc);return false;}if(!proof.isDone())return false;var current=proof.get();proof=null;
            boolean matches=current.get("trackingCenter").equals(List.of(interior.trackingX(),interior.trackingZ()))&&current.get("storedTrackingCenter").equals(List.of(interior.trackingX(),interior.trackingZ()))&&current.get("tickCenter").equals(List.of(interior.trackingX(),interior.trackingZ()));
            if(!matches){firstProof=null;return false;}require(Integer.valueOf(8).equals(current.get("trackingRadius"))&&Integer.valueOf(8).equals(current.get("tickRadius")),"Interior changed native server radius");
            if(firstProof==null){firstProof=current;return false;}if(((Number)current.get("worldTick")).longValue()<=((Number)firstProof.get("worldTick")).longValue())return false;
            row.put("latestInteriorSurvey",Map.of("startServerProof",firstProof,"endServerProof",current,"nativeFrames",TerrainScene073Qa.oracleNativeFrames()-baseFrames,"position",List.of(interior.x(),goal.y(),interior.z()),"targetCounted",false));move(mc,goal,"target");return false;
        }
        if(role.equals("roof")){
            var aim=batch.get(aimIndex);var position=List.of(aim.target().x(),aim.target().y(),aim.target().z());
            for(Object object:(List<?>)state.get("unreadyTargets"))if(((Map<?,?>)object).get("section").equals(position))return false;
            require(exposed.add(aim.target())&&exposed.size()<=TerrainOracleViews073.MAX_AIMS,"Repeated/unbounded roof exposure");
            aims.add(Map.of("section",position,"batch",batches,"yaw",TerrainScene073Qa.oracleCamera().yaw(),"pitch",TerrainScene073Qa.oracleCamera().pitch(),"actualNativeFrames",TerrainScene073Qa.oracleNativeFrames()-baseFrames,"nativeCollectorEvidence",TerrainBuildEligibility073.target(aim.target().x(),aim.target().y(),aim.target().z()),"datasetReady",true,"targetBuilt",true));
            if(++aimIndex<batch.size())moveAim(mc);else toGoal(mc);return false;
        }
        require(role.equals("target")&&TerrainScene073Qa.oracleCamera()==goal,"Preparation did not restore exact target camera");
        if(Boolean.TRUE.equals(state.get("ready"))){
            require(Boolean.TRUE.equals(state.get("targetsReady"))&&Objects.equals(row.get("expectedTargetSections"),state.get("targetSections")),"Ready target omitted native target gates");row.put("ready",state);row.put("nativeRoofExposureAims",List.copyOf(aims));row.put("roofBatches",batches);row.put("managerIdentityRetained",true);row.put("gpuDrawsAfterPreparation",TerrainResidentScene.regionDraws);completed.add(row);require(completed.size()<=TerrainOracleViews073.GROUPS,"Unbounded preparation views");role="done";return true;
        }
        var unbuilt=new ArrayList<TerrainRoofExposure073.Section>();for(Object item:(List<?>)state.get("unreadyTargets")){var section=(List<?>)((Map<?,?>)item).get("section");unbuilt.add(new TerrainRoofExposure073.Section(((Number)section.get(0)).intValue(),((Number)section.get(1)).intValue(),((Number)section.get(2)).intValue()));}
        if(unbuilt.isEmpty())return false;
        batch=TerrainOracleViews073.batch(plan,goal,unbuilt,exposed);require(!batch.isEmpty()&&++batches<=TerrainOracleViews073.MAX_BATCHES,"Native exposure could not prepare full target gates within bounds");aimIndex=0;moveAim(mc);return false;
    }
    private static void toGoal(Minecraft mc){
        interior=TerrainTrackingInteriorSurvey073.forTarget(goal.x(),goal.z());proof=null;firstProof=null;
        if(interior==null)move(mc,goal,"target");else move(mc,new TerrainResidentSceneBlueprint.Camera(goal.name()+"/oracle-tracking-interior",interior.x(),goal.y(),interior.z(),goal.yaw(),goal.pitch(),goal.fov()),"interior");
    }
    private static void moveAim(Minecraft mc){var roof=plan.cameras().get(5);var aim=batch.get(aimIndex);move(mc,new TerrainResidentSceneBlueprint.Camera(goal.name()+"/oracle-roof-"+batches+"-"+aimIndex,roof.x(),roof.y(),roof.z(),aim.yaw(),aim.pitch(),roof.fov()),"roof");}
    private static void move(Minecraft mc,TerrainResidentSceneBlueprint.Camera camera,String next){role=next;baseFrames=TerrainScene073Qa.oracleNativeFrames();TerrainScene073Qa.oracleMoveNative(mc,camera);}
    private static Future<Map<String,Object>> serverProof(Minecraft mc){var owner=mc.getServer();return owner.executeTask(()->{
        var world=owner.getWorld(0);require(world.players.size()==1&&world.players.getFirst() instanceof ServerPlayerEntity,"Unexpected owned server player");var player=(ServerPlayerEntity)world.players.getFirst();require(player.getName().equals("ArcadiumTest"),"Unexpected fixture identity");var values=new LinkedHashMap<String,Object>();values.put("worldTick",world.getTime());values.put("trackingCenter",List.of(TerrainTrackingInteriorSurvey073.trackingChunk(player.x),TerrainTrackingInteriorSurvey073.trackingChunk(player.z)));values.put("storedTrackingCenter",List.of(TerrainTrackingInteriorSurvey073.trackingChunk(player.trackedX),TerrainTrackingInteriorSurvey073.trackingChunk(player.trackedZ)));values.put("tickCenter",List.of(TerrainTrackingInteriorSurvey073.tickingChunk(player.x),TerrainTrackingInteriorSurvey073.tickingChunk(player.z)));values.put("trackingRadius",((PlayerPipeline072ChunkMapAccess)world.getChunkMap()).player072$trackingRadius());values.put("tickRadius",owner.getPlayerManager().getChunkViewDistance());return values;
    });}
    public static List<Map<String,Object>> evidence(){return List.copyOf(completed);}
    public static Map<String,Object> progress(){return Map.of("viewIndex",index,"role",role,"roofBatches",batches,"aimIndex",aimIndex,"batchAims",batch.size(),"completedAims",exposed.size(),"maxAimsPerView",TerrainOracleViews073.MAX_AIMS,"maxBatchesPerView",TerrainOracleViews073.MAX_BATCHES);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
