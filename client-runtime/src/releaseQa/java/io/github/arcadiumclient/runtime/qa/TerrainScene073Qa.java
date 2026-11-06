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
    private static int warmIndex;
    private static boolean warming=true;
    private static TerrainResidentSceneBlueprint.Camera camera=plan.cameras().getFirst();
    private static final List<Map<String,Object>> warmup=new ArrayList<>();
    private static Map<String,Object> shader=Map.of();
    private TerrainScene073Qa(){}
    public static boolean enabled(){return System.getProperty("arcadium.terrainWorld073.output")!=null;}
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
            else if(phase==2&&building.isDone()){report.put("worldBuild",building.get());moving=TerrainResidentSceneWorld.move(mc,camera);phase=3;}
            else if(phase==3&&moving.isDone()){report.put("serverCamera",moving.get());cameraBaseFrames=nativeFrames;phase=4;}
            if(phase!=lastPhase){lastPhase=phase;System.out.println("TERRAIN073_PHASE "+phase);}
            if(phase>=4){
                TerrainResidentSceneWorld.freezeClientCamera(mc,camera);require(mc.screen==null,"Unexpected GUI in terrain073 source fixture");
                if(!Display.isActive()){
                    if(foregroundAcquired){report.put("focusFailure",focusState());throw new IllegalStateException("Owned terrain073 window lost foreground after acquisition");}
                    report.put("foregroundAcquisition",Map.of("acquired",false,"waitingTicks",++foregroundWaitTicks));
                    require(foregroundWaitTicks<=1200,"Owned terrain073 window was never focused");return;
                }
                if(!foregroundAcquired)report.put("focusAtAcquisition",focusState());foregroundAcquired=true;report.put("foregroundAcquisition",Map.of("acquired",true,"waitingTicks",foregroundWaitTicks));
                if(phase==4){
                    var state=readiness.sample(mc,plan);report.put("nativeReadiness",state);
                    if(warming&&Boolean.TRUE.equals(state.get("datasetReady"))&&nativeFrames>cameraBaseFrames){
                        require(TerrainResidentScene.regionDraws==0&&!GpuTerrainCommands.enabled,"Warmup must use the unchanged native terrain renderer");
                        warmup.add(Map.of("camera",camera.name(),"readiness",state,"nativeTerrainFrames",nativeFrames-cameraBaseFrames));report.put("nativeViewWarmup",List.copyOf(warmup));
                        if(++warmIndex<plan.cameras().size())camera=plan.cameras().get(warmIndex);else{warming=false;camera=plan.cameras().getFirst();}
                        moving=TerrainResidentSceneWorld.move(mc,camera);phase=3;
                    }else if(!warming&&Boolean.TRUE.equals(state.get("ready"))){
                        BetaOptimizationsConfig.setGpuTerrainCommands(true);GpuTerrainCommands.enabled=true;readyDraws=TerrainResidentScene.regionDraws;phase=5;
                    }
                }
                else if(phase==7&&pngComplete(mc.gameDir.toPath().resolve("screenshots/terrain073-resident-positive.png"))){
                    Path path=mc.gameDir.toPath().resolve("screenshots/terrain073-resident-positive.png");var image=ImageIO.read(path.toFile());require(image!=null&&image.getWidth()==1920&&image.getHeight()==1080,"Incomplete native terrain073 screenshot");
                    var colors=new HashSet<Integer>();for(int y=0;y<1080;y+=8)for(int x=0;x<1920;x+=8)colors.add(image.getRGB(x,y));require(colors.size()>=32,"Blank terrain073 capture");
                    report.put("frame",Map.of("file",path.getFileName().toString(),"width",1920,"height",1080,"sampledColors",colors.size(),"fullUncropped",true));
                    BetaOptimizationsConfig.setGpuTerrainCommands(false);GpuTerrainCommands.enabled=false;GpuTerrainCommands.reset();var released=TerrainScene073GpuEvidence.released();report.put("cleanup",released);
                    require(Boolean.TRUE.equals(released.get("ownedObjectsDeleted"))&&Long.valueOf(0).equals(released.get("budgetBytes"))&&Integer.valueOf(0).equals(released.get("budgetOwners"))&&Long.valueOf(0).equals(released.get("residentBytes")),"Resident terrain cleanup was incomplete");finish(null);
                }
            }
        }catch(Throwable failure){finish(failure);}
    }
    public static void configured(Object renderer,String pass,ChunkRenderMatrices matrices,CameraTransform face,CameraTransform render){
        if(!enabled()||done||!pass.equals("solid"))return;
        try{shader=TerrainScene073GpuEvidence.configured(renderer,matrices,face,render);if(phase>=4)report.put("latestNativeTerrainState",shader);}catch(Throwable error){finish(error);}
    }
    public static void terrainReturned(String pass){
        if(!enabled()||done||!pass.equals("solid"))return;nativeFrames++;if(phase!=5)return;
        try{
            Map<String,Object> evidence=TerrainResidentScene.evidence();report.put("resident",evidence);report.put("shader",shader);
            if(!Boolean.TRUE.equals(evidence.get("ready"))||TerrainResidentScene.regionDraws<=readyDraws){if(++eligibleFrames>120)throw new IllegalStateException("Resident terrain never submitted current full-world geometry: "+evidence.get("status"));return;}
            require(evidence.get("preparedPublicationEpoch").equals(evidence.get("submittedPublicationEpoch")),"The native submitted epoch differs from the prepared snapshot");
            require(Long.valueOf(0).equals(evidence.get("errors")),"Resident preflight reported a runtime error");
            require(Boolean.TRUE.equals(shader.get("physicalProgramMatches"))&&shader.containsKey("compiledFact"),"Actual compiled native shader provenance missing");
            var counts=TerrainScene073GpuEvidence.currentCounts();require(((Number)counts.get("gpuCommandCount")).longValue()>0&&((Number)counts.get("positiveRegions")).intValue()>0,"Resident indirect counters contain no geometry");report.put("gpuCounts",counts);
            report.put("camera",Map.of("position",List.of(Minecraft.getInstance().player.x,Minecraft.getInstance().player.y,Minecraft.getInstance().player.z),"yaw",Minecraft.getInstance().player.yaw,"pitch",Minecraft.getInstance().player.pitch,"fovDegrees",Minecraft.getInstance().options.fov));
            capture=true;phase=6;
        }catch(Throwable failure){finish(failure);}
    }
    public static void frame(){
        if(!enabled()||done||!capture)return;capture=false;var mc=Minecraft.getInstance();
        try{require(mc.width==1920&&mc.height==1080&&GL11.glGetError()==GL11.GL_NO_ERROR,"Unexpected terrain073 source capture state");Screenshot.take(mc.gameDir,"terrain073-resident-positive.png",mc.width,mc.height,mc.getRenderTarget());phase=7;}
        catch(Throwable failure){finish(failure);}
    }
    private static boolean pngComplete(Path path)throws Exception{
        if(!Files.isRegularFile(path)||Files.size(path)<12)return false;try(var channel=Files.newByteChannel(path)){var tail=ByteBuffer.allocate(12);channel.position(channel.size()-12);while(tail.hasRemaining()&&channel.read(tail)>0){}return tail.position()==12&&tail.getInt(0)==0&&tail.getInt(4)==0x49454e44;}
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
        if(done)return;done=true;report.put("passed",error==null);report.put("blueprintSha256",plan.sha256());report.put("phase",phase);report.put("correctnessOnly",true);report.put("fullSceneVisualParityQualified",false);report.put("publicationQualified",false);report.put("performanceClaim",false);report.put("finalResident",TerrainResidentScene.evidence());
        if(error!=null){report.put("error",error.toString());error.printStackTrace();}
        report.put("environment",Map.of("renderer",GL11.glGetString(GL11.GL_RENDERER),"openGl",GL11.glGetString(GL11.GL_VERSION),"java",System.getProperty("java.version")));
        try{Files.createDirectories(output().toAbsolutePath().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failed){failed.printStackTrace();}Minecraft.getInstance().stop();
    }
}
