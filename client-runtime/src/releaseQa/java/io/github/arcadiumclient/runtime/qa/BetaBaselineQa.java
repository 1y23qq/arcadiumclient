// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;

public final class BetaBaselineQa {
    private static int phase,ticks,wait;
    private static long started,last;
    private static boolean done;
    private static final List<Double> samples=new ArrayList<>();
    public static boolean enabled(){return System.getProperty("arcadium.betaBaseline.output")!=null;}
    public static void tick(){
        if(done)return;var mc=Minecraft.getInstance();wait++;
        try{
            if(++ticks>1600)throw new IllegalStateException("Baseline timed out");
            if(phase==0 && ticks>65 && mc.screen!=null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus=false;mc.options.vsync=false;mc.options.viewDistance=4;mc.options.fpsLimit=260;Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                mc.startGame("beta-baseline","Beta Baseline",new WorldSettings(493,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;wait=0;
            }else if(phase==1 && mc.world!=null && mc.player!=null && wait>70){
                mc.openScreen(null);mc.player.abilities.flying=true;mc.player.updatePositionAndAngles(0,7,-10,0,9);mc.player.lastYaw=mc.player.yaw;mc.player.lastPitch=mc.player.pitch;mc.world.setTimeOfDay(6000);
                for(int i=0;i<400;i++){
                    var player=new Frozen(mc.world,new GameProfile(new UUID(0x7142000000000000L,i+1),"BetaQA"+i));
                    player.updatePositionAndAngles((i%20-9.5)*1.35,4,3+(i/20)*1.35,180+i%35,15+i%17);player.lastYaw=player.yaw;player.lastPitch=player.pitch;
                    player.bodyYaw=player.lastBodyYaw=player.yaw;player.headYaw=player.lastHeadYaw=player.yaw+15;player.walkAnimationProgress=2+i*.1f;player.walkAnimationSpeed=player.lastWalkAnimationSpeed=.6f;player.ticks=100+i%20;
                    mc.world.forceEntity(20000+i,player);
                }
                var board=mc.world.getScoreboard();var objective=board.createObjective("beta",net.minecraft.scoreboard.criterion.ScoreboardCriterion.DUMMY);objective.setDisplayName("Arcadium Beta QA");
                for(int i=0;i<15;i++)board.getScore("HUD upload reuse test line "+i,objective).set(15-i);board.setDisplayObjective(1,objective);
                phase=2;started=System.nanoTime();last=0;
            }else if(phase==2 && System.nanoTime()-started>22_000_000_000L)finish(null);
        }catch(Throwable error){finish(error);}
    }
    public static void frame(){if(done||phase!=2)return;long now=System.nanoTime();if(last!=0&&now-started>6_000_000_000L)samples.add((now-last)/1e6);last=now;}
    private static void finish(Throwable error){
        if(done)return;done=true;
        var sorted=samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();Map<String,Object> result=new LinkedHashMap<>();result.put("passed",error==null);result.put("frames",sorted.length);
        if(sorted.length>0){result.put("fps",1000/Arrays.stream(sorted).average().orElseThrow());result.put("p99Ms",sorted[(int)(sorted.length*.99)]);}
        if(error!=null){result.put("error",error.toString());error.printStackTrace();}
        try{Files.writeString(Path.of(System.getProperty("arcadium.betaBaseline.output")),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception failure){failure.printStackTrace();}
        Minecraft.getInstance().stop();
    }
    private static class Frozen extends RemoteClientPlayerEntity{Frozen(net.minecraft.world.World world,GameProfile profile){super(world,profile);}@Override public void tick(){}}
}
