// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
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
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.config.v1.Property;

public final class TerrainSceneSetup072Qa {
    private static final TerrainResidentSceneBlueprint.Plan plan=TerrainResidentSceneBlueprint.create();
    private static final TerrainSceneNativeReadiness readiness=new TerrainSceneNativeReadiness();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static Future<Map<String,Object>> building,moving;
    private static int ticks,phase;private static boolean capture,done;
    private TerrainSceneSetup072Qa(){}
    public static boolean enabled(){return System.getProperty("arcadium.terrainWorld072.output")!=null;}
    private static Path output(){return Path.of(System.getProperty("arcadium.terrainWorld072.output"));}
    public static void tick(){
        if(!enabled()||done)return;var mc=Minecraft.getInstance();
        try{
            if(++ticks>6000)throw new IllegalStateException("Owned native terrain setup timed out atphase "+phase);
            if(!"ArcadiumTest".equals(mc.getSession().getUsername())||!"0".equals(mc.getSession().getAccessToken())||mc.getCurrentServerEntry()!=null)throw new IllegalStateException("Use only the owned offline setup fixture");
            if(Boolean.getBoolean("arcadium.terrainResident072.enabled"))throw new IllegalStateException("Setup-only native control cannot enable the resident frontend");
            if(phase==0&&ticks>60&&mc.screen!=null&&mc.world==null){
                ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);mc.options.viewDistance=8;mc.options.fpsLimit=260;mc.options.cloudRenderMode=0;mc.options.perspective=0;mc.options.viewBobbing=false;mc.options.hideGui=true;mc.options.fov=75;mc.options.difficulty=Difficulty.PEACEFUL;org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                var settings=new TreeMap<String,Object>();int booleans=0;
                for(var entry:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(entry.getValue() instanceof Property<?> property&&property.get() instanceof Boolean){boolean value=!entry.getKey().equals("worldRenderScale")&&!entry.getKey().equals("gpuTerrainCommands");property.setAs(value);settings.put(entry.getKey(),value);booleans++;}
                if(booleans!=21)throw new IllegalStateException("Unexpected public Beta property inventory");
                BetaOptimizationsConfig.setMinimumPlayers(1);BetaOptimizationsConfig.setMinimumGpuLabels(1);BetaOptimizationsConfig.setMinimumGpuParticles(1);BetaOptimizationsConfig.setWorldResolution(75);
                settings.put("minimumPlayers",1);settings.put("minimumGpuLabels",1);settings.put("minimumGpuParticles",1);settings.put("worldResolution",75);report.put("betaSettings",settings);
                mc.startGame("arcadium-terrain-world072","Owned terrain world072",new WorldSettings(720912,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;
            }else if(phase==1&&mc.world!=null&&mc.player!=null){mc.openScreen(null);building=TerrainResidentSceneWorld.build(mc,plan);phase=2;}
            else if(phase==2&&building.isDone()){report.put("worldBuild",building.get());moving=TerrainResidentSceneWorld.move(mc,plan.cameras().getFirst());phase=3;}
            else if(phase==3&&moving.isDone()){report.put("serverCamera",moving.get());phase=4;}
            if(phase>=4){
                TerrainResidentSceneWorld.freezeClientCamera(mc,plan.cameras().getFirst());
                if(mc.screen!=null)throw new IllegalStateException("Unexpected GUI during native terrain world setup");
                if(!Display.isActive())throw new IllegalStateException("Keep the owned native terrain window focused");
                if(phase==4){var state=readiness.sample(mc,plan);report.put("nativeReadiness",state);if(Boolean.TRUE.equals(state.get("ready"))){capture=true;phase=5;}}
                else if(phase==6&&pngComplete(mc.gameDir.toPath().resolve("screenshots/terrain-world072-native.png"))){
                    var file=mc.gameDir.toPath().resolve("screenshots/terrain-world072-native.png");var image=ImageIO.read(file.toFile());
                    if(image==null||image.getWidth()!=1920||image.getHeight()!=1080)throw new IllegalStateException("Native terrain source capture is incomplete or resized");
                    var colors=new HashSet<Integer>();for(int y=0;y<1080;y+=8)for(int x=0;x<1920;x+=8)colors.add(image.getRGB(x,y));
                    if(colors.size()<32)throw new IllegalStateException("Native terrain source capture is blank");
                    report.put("nativeFrame",Map.of("file",file.getFileName().toString(),"width",1920,"height",1080,"sampledDistinctColors",colors.size(),"fullUncropped",true));
                    report.put("environment",Map.of("gpu",GL11.glGetString(GL11.GL_RENDERER),"openGl",GL11.glGetString(GL11.GL_VERSION),"java",System.getProperty("java.version")));finish(null);
                }
            }
        }catch(Throwable error){finish(error);}
    }
    public static void frame(){
        if(!enabled()||done||!capture)return;capture=false;var mc=Minecraft.getInstance();
        try{
            if(mc.width!=1920||mc.height!=1080||GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Unexpected native terrain capture format/state");
            Screenshot.take(mc.gameDir,"terrain-world072-native.png",mc.width,mc.height,mc.getRenderTarget());phase=6;
        }catch(Throwable error){finish(error);}
    }
    private static boolean pngComplete(Path path)throws Exception{
        if(!Files.isRegularFile(path)||Files.size(path)<12)return false;try(var channel=Files.newByteChannel(path)){var tail=ByteBuffer.allocate(12);channel.position(channel.size()-12);while(tail.hasRemaining()&&channel.read(tail)>0){}return tail.position()==12&&tail.getInt(0)==0&&tail.getInt(4)==0x49454e44;}
    }
    private static void finish(Throwable error){
        if(done)return;done=true;report.put("passed",error==null);report.put("sourceOnlySetupController",false);report.put("residentFrontendQualified",false);report.put("performanceClaim",false);report.put("blueprintSha256",plan.sha256());report.put("phase",phase);
        if(error!=null){report.put("error",error.toString());error.printStackTrace();}
        try{Files.createDirectories(output().toAbsolutePath().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failed){failed.printStackTrace();}Minecraft.getInstance().stop();
    }
}
