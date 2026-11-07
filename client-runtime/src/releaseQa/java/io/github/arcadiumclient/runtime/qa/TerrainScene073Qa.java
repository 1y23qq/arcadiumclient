// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuTerrainCommands;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainResidentScene;
import io.github.arcadiumclient.runtime.client.optimizations.TerrainScene073GpuEvidence;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.*;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.polyfrost.oneconfig.api.config.v1.Property;
import pl.tomgirl.pylon.window.DisplaySdl;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;

public final class TerrainScene073Qa {
    private static final TerrainResidentSceneBlueprint.Plan plan=TerrainResidentSceneBlueprint.create();
    private static final TerrainSceneNativeReadiness readiness=new TerrainSceneNativeReadiness();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static Future<Map<String,Object>> building,moving;
    private static int ticks,phase,eligibleFrames,lastPhase=-1;
    private static boolean capture,done,foregroundAcquired;
    private static int foregroundWaitTicks;
    private static long readyDraws,nativeFrames,cameraBaseFrames;
    private static int warmIndex,oracleViewIndex;
    private static boolean warming=true;
    private static TerrainResidentSceneBlueprint.Camera camera=plan.cameras().getFirst();
    private static final List<Map<String,Object>> warmup=new ArrayList<>();
    private static final List<Map<String,Object>> interiorSurveys=new ArrayList<>();
    private static TerrainResidentSceneBlueprint.Camera surveyTarget;
    private static TerrainTrackingInteriorSurvey073.Survey survey;
    private static boolean surveying,returningFromSurvey;
    private static Future<Map<String,Object>> surveyStartProof,surveyEndProof,surveyReturnProof;
    private static Map<String,Object> completedSurvey;
    private static boolean roofSurveyStarted,roofSurveyActive,roofSurveyRestoring;
    private static TerrainResidentSceneBlueprint.Camera roofAnchor;
    private static List<TerrainRoofExposure073.Aim> roofAims=List.of();
    private static int roofAimIndex;
    private static Object roofManagerIdentity,roofBootstrapChanges;
    private static final List<Map<String,Object>> roofEvidence=new ArrayList<>();
    private static final List<Map<String,Object>> progressHistory=new ArrayList<>();
    private static int progressTick=-100,progressPhase=-1,progressWarmIndex=-1,progressWrites,progressDropped;
    private static Map<String,Object> shader=Map.of();
    private TerrainScene073Qa(){}
    public static boolean enabled(){return System.getProperty("arcadium.terrainWorld073.output")!=null;}
    public static boolean oracleReady(){return phase==5&&!done&&foregroundAcquired;}
    public static int oracleTick(){return ticks;}
    public static int oracleViewIndex(){return oracleViewIndex;}
    public static TerrainResidentSceneBlueprint.Camera oracleCamera(){return camera;}
    public static long oracleNativeFrames(){return nativeFrames;}
    @SuppressWarnings("unchecked") public static Map<String,Object> oracleLastReadiness(){return (Map<String,Object>)report.getOrDefault("nativeReadiness",Map.of());}
    public static Map<String,Object> oracleFreshReadiness()throws Exception{var state=readiness.sample(Minecraft.getInstance(),plan);report.put("nativeReadiness",state);require(Boolean.TRUE.equals(state.get("ready")),"Full native readiness changed before the actual comparison group");return state;}
    public static void oracleMoveNative(Minecraft mc,TerrainResidentSceneBlueprint.Camera target){require(TerrainFullFrameOracle073.allViews()&&!warming,"Only the opt-in owned comparison may move between mature views");BetaOptimizationsConfig.setGpuTerrainCommands(false);GpuTerrainCommands.enabled=false;camera=target;moving=TerrainResidentSceneWorld.move(mc,target);phase=3;}
    public static void oracleViewComplete(){require(TerrainFullFrameOracle073.allViews()&&phase==5&&oracleViewIndex<8,"Unexpected comparison view transition");TerrainOraclePreparation073.begin(++oracleViewIndex,Minecraft.getInstance());}
    public static void oracleComplete(Map<String,Object> oracle){require(phase==5&&Boolean.TRUE.equals(oracle.get("passed")),"A failed/unarmed oracle cannot advance terrain QA");report.put(TerrainFullFrameOracle073.allViews()?"multiViewPrePolyBlurOracle":"oneCameraPrePolyBlurOracle",oracle);capture=true;phase=6;}
    public static void oracleFailed(Throwable error){if(done)return;report.put("oracleFailure",error.toString());try{BetaOptimizationsConfig.setGpuTerrainCommands(false);GpuTerrainCommands.enabled=false;GpuTerrainCommands.reset();report.put("oracleFailureCleanup",TerrainScene073GpuEvidence.released());}catch(Throwable cleanup){error.addSuppressed(cleanup);}finish(error);}
    private static Path capturedFrame(Minecraft mc){return TerrainFullFrameOracle073.enabled()?TerrainFullFrameOracle073.gpuPng():mc.gameDir.toPath().resolve("screenshots/terrain073-resident-positive.png");}
    private static Path output(){return Path.of(System.getProperty("arcadium.terrainWorld073.output"));}
    public static void tick(){
        if(!enabled()||done)return;var mc=Minecraft.getInstance();
        try{
            if(++ticks>6000)throw new IllegalStateException("Owned terrain073 positive proof timed out atphase "+phase);
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"Use only the owned offline terrain073 fixture");
            require(GpuTerrainCommands.residentBootRequested(),"The private resident mixins must be selected at JVM startup");
            if(phase==0&&ticks>60&&mc.screen!=null&&mc.world==null){
                ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);mc.options.viewDistance=8;mc.options.fpsLimit=260;mc.options.cloudRenderMode=0;mc.options.perspective=0;mc.options.viewBobbing=false;mc.options.hideGui=true;mc.options.fov=75;mc.options.difficulty=Difficulty.PEACEFUL;org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                var settings=new TreeMap<String,Object>();
                for(var entry:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(entry.getValue() instanceof Property<?> property&&property.get() instanceof Boolean){boolean value=!entry.getKey().equals("worldRenderScale");property.setAs(value&&!entry.getKey().equals("gpuTerrainCommands"));settings.put(entry.getKey(),value);}
                require(settings.size()==21&&Boolean.TRUE.equals(settings.get("gpuTerrainCommands")),"Unexpected public Beta inventory");
                BetaOptimizationsConfig.setMinimumPlayers(1);BetaOptimizationsConfig.setMinimumGpuLabels(1);BetaOptimizationsConfig.setMinimumGpuParticles(1);BetaOptimizationsConfig.setWorldResolution(75);GpuTerrainCommands.enabled=false;
                settings.put("minimumPlayers",1);settings.put("minimumGpuLabels",1);settings.put("minimumGpuParticles",1);settings.put("worldResolution",75);report.put("betaSettings",settings);var nativeSettings=new TreeMap<>(settings);nativeSettings.put("gpuTerrainCommands",false);report.put("warmupBetaSettings",nativeSettings);
                mc.startGame("arcadium-terrain073-positive","Owned terrain073 positive",new WorldSettings(720912,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;
            }else if(phase==1&&mc.world!=null&&mc.player!=null){mc.openScreen(null);building=TerrainResidentSceneWorld.build(mc,plan);phase=2;}
            else if(phase==2&&building.isDone()){report.put("worldBuild",building.get());moveWarmupTarget(mc,camera);}
            else if(phase==3&&moving.isDone()){
                report.put("serverCamera",moving.get());cameraBaseFrames=nativeFrames;
                if(surveying&&surveyStartProof==null)surveyStartProof=serverSurveyProof(mc);
                if(returningFromSurvey&&surveyReturnProof==null)surveyReturnProof=serverSurveyProof(mc);
                phase=4;
            }
            if(phase!=lastPhase){lastPhase=phase;System.out.println("TERRAIN073_PHASE "+phase);}
            if(phase>=4){
                TerrainResidentSceneWorld.freezeClientCamera(mc,camera);require(mc.screen==null,"Unexpected GUI in terrain073 source fixture");
                if(!Display.isActive()){
                    if(foregroundAcquired){report.put("focusFailure",focusState());throw new IllegalStateException("Owned terrain073 window lost foreground after acquisition");}
                    report.put("foregroundAcquisition",Map.of("acquired",false,"waitingTicks",++foregroundWaitTicks));
                    require(foregroundWaitTicks<=1200,"Owned terrain073 window was never focused");progress(false);return;
                }
                if(!foregroundAcquired)report.put("focusAtAcquisition",focusState());foregroundAcquired=true;report.put("foregroundAcquisition",Map.of("acquired",true,"waitingTicks",foregroundWaitTicks));
                if(phase==4){
                    var state=readiness.sample(mc,plan);report.put("nativeReadiness",state);
                    if(roofSurveyActive){advanceRoofSurvey(mc,state);}
                    else if(warming&&!roofSurveyStarted&&camera.name().equals("roof")&&Boolean.TRUE.equals(state.get("datasetReady"))&&nativeFrames>cameraBaseFrames&&startRoofSurvey(mc,state)){}
                    else if(surveying){advanceInteriorSurvey(mc,state);}
                    else if(warming&&Boolean.TRUE.equals(state.get("datasetReady"))&&nativeFrames>cameraBaseFrames){
                        require(TerrainResidentScene.regionDraws==0&&!GpuTerrainCommands.enabled,"Warmup must use the unchanged native terrain renderer");
                        if(roofSurveyRestoring){
                            require(camera==roofAnchor&&roofManagerIdentity.equals(state.get("nativeRenderManagerIdentity"))&&roofBootstrapChanges.equals(state.get("bootstrapDistanceChanges")),"Roof exposure changed the target camera/render manager/distance");
                            report.put("restoredOriginalRoof",Map.of("camera",camera.name(),"position",List.of(camera.x(),camera.y(),camera.z()),"yaw",camera.yaw(),"pitch",camera.pitch(),"nativeFrames",nativeFrames-cameraBaseFrames));roofSurveyRestoring=false;
                        }
                        if(returningFromSurvey){
                            if(surveyReturnProof==null||!surveyReturnProof.isDone()){progress(false);return;}
                            var returned=surveyReturnProof.get();verifySurveyProof(returned,false);verifySurveyDataset(mc,state);
                            require(completedSurvey.get("nativeRenderManagerIdentity").equals(state.get("nativeRenderManagerIdentity")),"Interior survey reset the native render manager");
                            require(completedSurvey.get("bootstrapDistanceChanges").equals(state.get("bootstrapDistanceChanges")),"Interior survey changed client render distance");
                            completedSurvey.put("returnedTargetReadiness",state);completedSurvey.put("returnedServerProof",returned);completedSurvey.put("actualTargetNativeFrames",nativeFrames-cameraBaseFrames);completedSurvey.put("returnedToExactTarget",camera==surveyTarget);
                            require(camera==surveyTarget,"Interior camera was counted as the target view");returningFromSurvey=false;
                        }
                        warmup.add(Map.of("camera",camera.name(),"readiness",state,"nativeTerrainFrames",nativeFrames-cameraBaseFrames));report.put("nativeViewWarmup",List.copyOf(warmup));
                        if(++warmIndex<plan.cameras().size())moveWarmupTarget(mc,plan.cameras().get(warmIndex));else{require(warmup.size()==plan.cameras().size(),"A native target warmup entry was omitted");for(int i=0;i<warmup.size();i++)require(warmup.get(i).get("camera").equals(plan.cameras().get(i).name()),"An interior survey replaced a target view");warming=false;if(TerrainFullFrameOracle073.allViews())TerrainOraclePreparation073.begin(0,mc);else{camera=plan.cameras().getFirst();moving=TerrainResidentSceneWorld.move(mc,camera);phase=3;}}
                    }else if(!warming&&(TerrainFullFrameOracle073.allViews()?TerrainOraclePreparation073.tick(mc,state):Boolean.TRUE.equals(state.get("ready")))){
                        if(TerrainFullFrameOracle073.allViews())report.put("oracleNativePreparation",TerrainOraclePreparation073.evidence());
                        BetaOptimizationsConfig.setGpuTerrainCommands(true);GpuTerrainCommands.enabled=true;readyDraws=TerrainResidentScene.regionDraws;eligibleFrames=0;phase=5;
                    }
                }
                else if(phase==7&&pngComplete(capturedFrame(mc))){
                    Path path=capturedFrame(mc);var image=ImageIO.read(path.toFile());require(image!=null&&image.getWidth()==1920&&image.getHeight()==1080,"Incomplete native terrain073 screenshot");
                    var colors=new HashSet<Integer>();for(int y=0;y<1080;y+=8)for(int x=0;x<1920;x+=8)colors.add(image.getRGB(x,y));require(colors.size()>=32,"Blank terrain073 capture");
                    report.put("frame",Map.of("file",path.getFileName().toString(),"width",1920,"height",1080,"sampledColors",colors.size(),"fullUncropped",true,"captureStage",TerrainFullFrameOracle073.enabled()?"pre-PolyBlur world":"normal final framebuffer"));
                    BetaOptimizationsConfig.setGpuTerrainCommands(false);GpuTerrainCommands.enabled=false;GpuTerrainCommands.reset();var released=TerrainScene073GpuEvidence.released();report.put("cleanup",released);
                    require(Boolean.TRUE.equals(released.get("ownedObjectsDeleted"))&&Long.valueOf(0).equals(released.get("budgetBytes"))&&Integer.valueOf(0).equals(released.get("budgetOwners"))&&Long.valueOf(0).equals(released.get("residentBytes")),"Resident terrain cleanup was incomplete");finish(null);
                }
            }
            if(!done)progress(false);
        }catch(Throwable failure){finish(failure);}
    }
    private static boolean startRoofSurvey(Minecraft mc,Map<String,Object> state){
        require(!GpuTerrainCommands.enabled&&TerrainResidentScene.regionDraws==0&&mc.options.viewDistance==8,"Only native owned D8 preparation may expose roof targets");
        roofSurveyStarted=true;roofAnchor=camera;Set<TerrainRoofExposure073.Section> finalTargets=new HashSet<>();var finalCamera=plan.cameras().getFirst();int fx=TerrainSceneNativeReadiness.trackingChunk(finalCamera.x()),fz=TerrainSceneNativeReadiness.trackingChunk(finalCamera.z());
        for(var block:plan.blocks())if(Math.abs((block.x()>>4)-fx)<=8&&Math.abs((block.z()>>4)-fz)<=8)finalTargets.add(new TerrainRoofExposure073.Section(block.x()>>4,block.y()>>4,block.z()>>4));
        var unbuilt=new ArrayList<TerrainRoofExposure073.Section>();for(Object item:(List<?>)state.get("unreadyTargets")){var row=(Map<?,?>)item;var p=(List<?>)row.get("section");unbuilt.add(new TerrainRoofExposure073.Section(((Number)p.get(0)).intValue(),((Number)p.get(1)).intValue(),((Number)p.get(2)).intValue()));}
        roofAims=TerrainRoofExposure073.plan(roofAnchor.x(),roofAnchor.y(),roofAnchor.z(),finalTargets,unbuilt);roofManagerIdentity=state.get("nativeRenderManagerIdentity");roofBootstrapChanges=state.get("bootstrapDistanceChanges");
        report.put("roofExposurePlan",Map.of("finalTargetSections",finalTargets.size(),"selectedUnbuiltFinalTargets",roofAims.stream().map(a->Map.of("section",List.of(a.target().x(),a.target().y(),a.target().z()),"yaw",a.yaw(),"pitch",a.pitch())).toList(),"positionUnchanged",true,"allFinalTargetColumnsRetained",true,"countedAsTargetView",false));
        if(roofAims.isEmpty())return false;roofSurveyActive=true;roofAimIndex=0;moveRoofAim(mc);return true;
    }
    private static void moveRoofAim(Minecraft mc){var aim=roofAims.get(roofAimIndex);camera=new TerrainResidentSceneBlueprint.Camera(roofAnchor.name()+"/native-exposure-"+roofAimIndex,roofAnchor.x(),roofAnchor.y(),roofAnchor.z(),aim.yaw(),aim.pitch(),roofAnchor.fov());moving=TerrainResidentSceneWorld.move(mc,camera);phase=3;}
    private static void advanceRoofSurvey(Minecraft mc,Map<String,Object> state){
        require(warming&&!GpuTerrainCommands.enabled&&TerrainResidentScene.regionDraws==0&&mc.options.viewDistance==8,"Resident terrain or distance changed during roof exposure");
        require(camera.x()==roofAnchor.x()&&camera.y()==roofAnchor.y()&&camera.z()==roofAnchor.z(),"Roof exposure moved its retention anchor");
        if(!Boolean.TRUE.equals(state.get("datasetReady"))||nativeFrames<=cameraBaseFrames)return;
        require(roofManagerIdentity.equals(state.get("nativeRenderManagerIdentity"))&&roofBootstrapChanges.equals(state.get("bootstrapDistanceChanges")),"Roof exposure reset native meshes");
        var aim=roofAims.get(roofAimIndex);var position=List.of(aim.target().x(),aim.target().y(),aim.target().z());
        boolean ready=true;for(Object value:(List<?>)state.get("unreadyTargets"))if(((Map<?,?>)value).get("section").equals(position)){ready=false;break;}
        if(!ready)return;
        roofEvidence.add(Map.of("section",position,"yaw",camera.yaw(),"pitch",camera.pitch(),"nativeFrames",nativeFrames-cameraBaseFrames,"nativeReady",true,"nativeReadiness",state,"actualScheduler",TerrainBuildEligibility073.target(aim.target().x(),aim.target().y(),aim.target().z()),"countedAsTargetView",false));report.put("nativeRoofExposures",List.copyOf(roofEvidence));
        if(++roofAimIndex<roofAims.size()){moveRoofAim(mc);return;}
        roofSurveyActive=false;roofSurveyRestoring=true;camera=roofAnchor;moving=TerrainResidentSceneWorld.move(mc,camera);phase=3;
    }
    private static void moveWarmupTarget(Minecraft mc,TerrainResidentSceneBlueprint.Camera target){
        require(warming&&warmIndex<plan.cameras().size(),"Interior surveys are limited to owned native warmup");
        require(!GpuTerrainCommands.enabled&&TerrainResidentScene.regionDraws==0,"No resident draws may run during the native survey");
        surveyTarget=target;survey=TerrainTrackingInteriorSurvey073.forTarget(target.x(),target.z());surveying=survey!=null;returningFromSurvey=false;surveyStartProof=surveyEndProof=surveyReturnProof=null;
        if(surveying){
            require(mc.options.viewDistance==8,"Interior survey requires the unchanged client D8");
            camera=new TerrainResidentSceneBlueprint.Camera(target.name()+"/tracking-interior",survey.x(),target.y(),survey.z(),target.yaw(),target.pitch(),target.fov());
            report.put("activeInteriorSurvey",Map.of("target",target.name(),"trackingCenter",List.of(survey.trackingX(),survey.trackingZ()),"targetTickCenter",List.of(survey.targetTickX(),survey.targetTickZ()),"surveyPosition",List.of(camera.x(),camera.y(),camera.z()),"countedAsTargetView",false));
        }else camera=target;
        moving=TerrainResidentSceneWorld.move(mc,camera);phase=3;
    }
    private static void advanceInteriorSurvey(Minecraft mc,Map<String,Object> state)throws Exception{
        require(warming&&survey!=null&&!GpuTerrainCommands.enabled&&TerrainResidentScene.regionDraws==0,"Unexpected survey scope");
        if(!Boolean.TRUE.equals(state.get("datasetReady"))||nativeFrames<=cameraBaseFrames||surveyStartProof==null||!surveyStartProof.isDone())return;
        verifySurveyDataset(mc,state);var start=surveyStartProof.get();
        if(!surveyProofMatches(start,true)){report.put("interiorSurveyAwaitingNativeTracking",start);surveyStartProof=serverSurveyProof(mc);surveyEndProof=null;return;}
        if(surveyEndProof==null){surveyEndProof=serverSurveyProof(mc);return;}
        if(!surveyEndProof.isDone())return;var end=surveyEndProof.get();verifySurveyProof(end,true);
        if(((Number)end.get("worldTick")).longValue()<=((Number)start.get("worldTick")).longValue()){surveyEndProof=serverSurveyProof(mc);return;}
        completedSurvey=new LinkedHashMap<>();completedSurvey.put("target",surveyTarget.name());completedSurvey.put("survey",camera.name());completedSurvey.put("startServerProof",start);completedSurvey.put("endServerProof",end);completedSurvey.put("nativeReadiness",state);completedSurvey.put("nativeFrames",nativeFrames-cameraBaseFrames);completedSurvey.put("nativeRenderManagerIdentity",state.get("nativeRenderManagerIdentity"));completedSurvey.put("bootstrapDistanceChanges",state.get("bootstrapDistanceChanges"));completedSurvey.put("manualChunkTicks",false);completedSurvey.put("readinessFlagsWritten",false);completedSurvey.put("viewDistanceChanged",false);completedSurvey.put("countedAsTargetView",false);
        interiorSurveys.add(completedSurvey);report.put("nativeInteriorSurveys",interiorSurveys);
        surveying=false;returningFromSurvey=true;camera=surveyTarget;moving=TerrainResidentSceneWorld.move(mc,camera);phase=3;
    }
    private static void verifySurveyDataset(Minecraft mc,Map<String,Object> state){
        require(mc.options.viewDistance==8&&Integer.valueOf(289).equals(state.get("loadedColumns"))&&Integer.valueOf(4624).equals(state.get("sectionCount"))&&Integer.valueOf(0).equals(state.get("missingSections"))&&Boolean.TRUE.equals(state.get("builderIdle")),"Survey must retain full native D8 data and idle builders");
        require(state.get("cameraChunk").equals(List.of(survey.trackingX(),survey.trackingZ())),"Survey changed the measured tracking chunk");
    }
    private static void verifySurveyProof(Map<String,Object> proof,boolean interior){
        require(surveyProofMatches(proof,interior),"Native D8 survey centers/radii differ from their actual owned position: "+proof);
    }
    private static boolean surveyProofMatches(Map<String,Object> proof,boolean interior){return Integer.valueOf(8).equals(proof.get("trackingRadius"))&&Integer.valueOf(8).equals(proof.get("tickRadius"))&&proof.get("trackingCenter").equals(List.of(survey.trackingX(),survey.trackingZ()))&&proof.get("storedTrackingCenter").equals(List.of(survey.trackingX(),survey.trackingZ()))&&proof.get("tickCenter").equals(interior?List.of(survey.trackingX(),survey.trackingZ()):List.of(survey.targetTickX(),survey.targetTickZ()));}
    private static Future<Map<String,Object>> serverSurveyProof(Minecraft mc){
        require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null&&mc.getServer()!=null,"Survey cannot inspect a human or remote world");
        var owner=mc.getServer();return owner.executeTask(()->{
            var world=owner.getWorld(0);require(world.players.size()==1&&world.players.getFirst() instanceof net.minecraft.server.entity.living.player.ServerPlayerEntity,"Survey requires one owned server player");
            var player=(net.minecraft.server.entity.living.player.ServerPlayerEntity)world.players.getFirst();require("ArcadiumTest".equals(player.getName()),"Unexpected server player in survey");
            var proof=new LinkedHashMap<String,Object>();proof.put("worldTick",world.getTime());proof.put("position",List.of(player.x,player.y,player.z));proof.put("trackingCenter",List.of(TerrainTrackingInteriorSurvey073.trackingChunk(player.x),TerrainTrackingInteriorSurvey073.trackingChunk(player.z)));proof.put("storedTrackingCenter",List.of(TerrainTrackingInteriorSurvey073.trackingChunk(player.trackedX),TerrainTrackingInteriorSurvey073.trackingChunk(player.trackedZ)));proof.put("tickCenter",List.of(TerrainTrackingInteriorSurvey073.tickingChunk(player.x),TerrainTrackingInteriorSurvey073.tickingChunk(player.z)));proof.put("trackingRadius",((io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunkMapAccess)world.getChunkMap()).player072$trackingRadius());proof.put("tickRadius",owner.getPlayerManager().getChunkViewDistance());return proof;
        });
    }
    public static void configured(Object renderer,String pass,ChunkRenderMatrices matrices,CameraTransform face,CameraTransform render){
        if(!enabled()||done)return;
        try{var state=TerrainScene073GpuEvidence.configuredPass(renderer,pass,matrices,face,render);if(pass.equals("solid")){shader=state;if(phase>=4)report.put("latestNativeTerrainState",shader);}}catch(Throwable error){finish(error);}
    }
    public static void terrainReturned(String pass){
        if(!enabled()||done)return;try{TerrainScene073GpuEvidence.returnedPass(pass);}catch(Throwable error){finish(error);return;}if(!pass.equals("solid"))return;nativeFrames++;if(phase!=5)return;
        if(TerrainFullFrameOracle073.enabled()){if(TerrainFullFrameOracle073.controlling())TerrainFullFrameOracle073.terrainReturn();if(!TerrainFullFrameOracle073.gpuStage())return;}
        try{
            Map<String,Object> evidence=TerrainResidentScene.evidence();report.put("resident",evidence);report.put("shader",shader);
            if(!Boolean.TRUE.equals(evidence.get("ready"))||TerrainResidentScene.regionDraws<=readyDraws){if(++eligibleFrames>120)throw new IllegalStateException("Resident terrain never submitted current full-world geometry: "+evidence.get("status"));return;}
            require(evidence.get("preparedPublicationEpoch").equals(evidence.get("submittedPublicationEpoch")),"The native submitted epoch differs from the prepared snapshot");
            require(Long.valueOf(0).equals(evidence.get("errors")),"Resident preflight reported a runtime error");
            require(Boolean.TRUE.equals(shader.get("physicalProgramMatches"))&&shader.containsKey("compiledFact"),"Actual compiled native shader provenance missing");
            var counts=TerrainScene073GpuEvidence.currentCounts();require(((Number)counts.get("gpuCommandCount")).longValue()>0&&((Number)counts.get("positiveRegions")).intValue()>0,"Resident indirect counters contain no geometry");report.put("gpuCounts",counts);
            report.put("camera",Map.of("position",List.of(Minecraft.getInstance().player.x,Minecraft.getInstance().player.y,Minecraft.getInstance().player.z),"yaw",Minecraft.getInstance().player.yaw,"pitch",Minecraft.getInstance().player.pitch,"fovDegrees",Minecraft.getInstance().options.fov));
            if(TerrainFullFrameOracle073.enabled()){TerrainFullFrameOracle073.gpuValidated(Map.of("resident",evidence,"shader",shader,"counts",counts));return;}
            capture=true;phase=6;
        }catch(Throwable failure){finish(failure);}
    }
    public static void frame(){
        if(!enabled()||done||!capture)return;capture=false;var mc=Minecraft.getInstance();
        try{require(mc.width==1920&&mc.height==1080&&GL11.glGetError()==GL11.GL_NO_ERROR,"Unexpected terrain073 source capture state");if(TerrainFullFrameOracle073.enabled()){require(TerrainFullFrameOracle073.completed()&&pngComplete(TerrainFullFrameOracle073.gpuPng()),"Oracle must complete all real operations before phase7");}else Screenshot.take(mc.gameDir,"terrain073-resident-positive.png",mc.width,mc.height,mc.getRenderTarget());phase=7;}
        catch(Throwable failure){finish(failure);}
    }
    private static boolean pngComplete(Path path)throws Exception{
        if(!Files.isRegularFile(path)||Files.size(path)<12)return false;try(var channel=Files.newByteChannel(path)){var tail=ByteBuffer.allocate(12);channel.position(channel.size()-12);while(tail.hasRemaining()&&channel.read(tail)>0){}return tail.position()==12&&tail.getInt(0)==0&&tail.getInt(4)==0x49454e44;}
    }
    private static void progress(boolean force)throws Exception{
        if(!force&&ticks-progressTick<100&&progressPhase==phase&&progressWarmIndex==warmIndex)return;
        progressTick=ticks;progressPhase=phase;progressWarmIndex=warmIndex;progressWrites++;
        var snapshot=new LinkedHashMap<String,Object>();snapshot.put("utc",java.time.Instant.now().toString());snapshot.put("nanoTime",System.nanoTime());snapshot.put("ticks",ticks);snapshot.put("phase",phase);snapshot.put("camera",camera.name());snapshot.put("warmIndex",warmIndex);snapshot.put("warming",warming);snapshot.put("roofExposureActive",roofSurveyActive);snapshot.put("roofExposureIndex",roofAimIndex);snapshot.put("roofExposureTargets",roofAims.size());snapshot.put("roofRestoringOriginalView",roofSurveyRestoring);snapshot.put("interiorSurveyActive",surveying);snapshot.put("returningFromInteriorSurvey",returningFromSurvey);snapshot.put("interiorSurveyCount",interiorSurveys.size());snapshot.put("actualWarmupTarget",warming&&surveyTarget!=null?surveyTarget.name():camera.name());snapshot.put("oracleViewIndex",oracleViewIndex);if(TerrainFullFrameOracle073.allViews())snapshot.put("oraclePreparation",TerrainOraclePreparation073.progress());snapshot.put("nativeTerrainFrames",nativeFrames);snapshot.put("gpuTerrainEnabled",GpuTerrainCommands.enabled);snapshot.put("focus",focusState());
        var compact=new LinkedHashMap<String,Object>();Object raw=report.get("nativeReadiness");
        if(raw instanceof Map<?,?> state){for(String key:List.of("ready","cameraChunk","haloStage","bootstrapDistanceChanges","server","datasetReady","loadedColumns","expectedColumns","missingSections","sectionCount","expectedSectionCount","targetSections","targetsReady","builderIdle","schedulerEligibility"))if(state.containsKey(key))compact.put(key,state.get(key));Object unbuilt=state.get("unreadyTargets");if(unbuilt instanceof List<?> list){compact.put("unreadyTargetsTotal",list.size());compact.put("unreadyTargetsSample",list.subList(0,Math.min(16,list.size())));}}
        snapshot.put("nativeReadiness",compact);if(progressHistory.size()==64){progressHistory.removeFirst();progressDropped++;}progressHistory.add(snapshot);
        var payload=new LinkedHashMap<String,Object>();payload.put("correctnessOnly",true);payload.put("performanceClaim",false);payload.put("snapshotWrites",progressWrites);payload.put("historyLimit",64);payload.put("droppedOldSnapshots",progressDropped);payload.put("latest",snapshot);payload.put("history",List.copyOf(progressHistory));
        Path path=output().resolveSibling("progress.json");Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(payload));
    }
    private static Map<String,Object> focusState(){
        var state=new LinkedHashMap<String,Object>();state.put("ticks",ticks);state.put("phase",phase);state.put("camera",camera.name());state.put("warmIndex",warmIndex);state.put("nativeFrames",nativeFrames);state.put("displayActive",Display.isActive());
        try{
            long window=DisplaySdl.instance().getHandle(),flags=SDLVideo.SDL_GetWindowFlags(window),keyboard=SDLKeyboard.SDL_GetKeyboardFocus(),mouse=SDLMouse.SDL_GetMouseFocus();
            state.put("ownedSdlWindow",window);state.put("ownedSdlWindowId",SDLVideo.SDL_GetWindowID(window));state.put("sdlFlags",flags);state.put("sdlInputFocus",(flags&SDLVideo.SDL_WINDOW_INPUT_FOCUS)!=0);state.put("sdlMouseFocus",(flags&SDLVideo.SDL_WINDOW_MOUSE_FOCUS)!=0);state.put("sdlMinimized",(flags&SDLVideo.SDL_WINDOW_MINIMIZED)!=0);state.put("sdlHidden",(flags&SDLVideo.SDL_WINDOW_HIDDEN)!=0);state.put("sdlKeyboardIsOwned",keyboard==window);state.put("sdlMouseIsOwned",mouse==window);
        }catch(Throwable error){state.put("diagnosticError",error.toString());}
        return state;
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void finish(Throwable error){
        if(done)return;try{progress(true);}catch(Exception diagnostic){report.put("progressWriteError",diagnostic.toString());}done=true;report.put("passed",error==null);report.put("blueprintSha256",plan.sha256());report.put("phase",phase);report.put("correctnessOnly",true);report.put("fullSceneVisualParityQualified",false);report.put("publicationQualified",false);report.put("performanceClaim",false);report.put("finalResident",TerrainResidentScene.evidence());
        if(error!=null){report.put("error",error.toString());error.printStackTrace();}
        report.put("environment",Map.of("renderer",GL11.glGetString(GL11.GL_RENDERER),"openGl",GL11.glGetString(GL11.GL_VERSION),"java",System.getProperty("java.version")));
        try{Files.createDirectories(output().toAbsolutePath().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failed){failed.printStackTrace();}Minecraft.getInstance().stop();
    }
}
