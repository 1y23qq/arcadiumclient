// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.internal.OneConfigConfig;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;

public final class TextPerformance070Qa {
    private static final long DURATION=30_000_000_000L;
    private static final long[] frames=new long[262144];
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<ArmorStandEntity> stands=new ArrayList<>();
    private static int ticks,phase,count;
    private static long settled,started,last;
    private static boolean done;
    private static Object world;
    private TextPerformance070Qa() { }

    public static void tick() {
        if(done||System.getProperty("arcadium.textPerf070.output")==null)return;
        Minecraft mc=Minecraft.getInstance();
        try {
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Use the isolated offline fixture");
            require(++ticks<6000,"Performance fixture timed out");
            if(phase==0&&ticks>65&&mc.screen!=null) {
                ClientPreferences.setSetupComplete(true);OneConfigConfig.limitUnfocusedFps=false;
                OneConfigConfig.useVanillaFontInGame=true;OneConfigConfig.useVanillaFontForModText=true;
                OneConfigConfig.useInterInGame=false;OneConfigConfig.useInterForModText=false;
                NickHiderConfig.setEnabled(true);NickHiderConfig.setNickname("Guest");
                for(var entry:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(entry.getValue() instanceof Property<?> property) {
                    if(property.get() instanceof Boolean)property.setAs(!entry.getKey().equals("worldRenderScale"));
                    if(entry.getKey().startsWith("minimum"))property.setAs(1);
                    if(entry.getKey().equals("worldResolution"))property.setAs(75);
                }
                mc.options.vsync=false;Display.setVSyncEnabled(false);mc.options.fpsLimit=260;mc.options.viewDistance=12;mc.options.fov=75;mc.options.pauseOnUnfocus=false;
                mc.startGame("arcadium-text-perf070","Text performance fixture",new WorldSettings(70031,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;
            } else if(phase==1&&mc.world!=null&&mc.player!=null) {
                mc.openScreen(null);mc.player.abilities.flying=true;mc.player.updatePositionAndAngles(0,6,-14,0,5);mc.player.lastYaw=mc.player.yaw;mc.player.lastPitch=mc.player.pitch;
                int players=Integer.getInteger("arcadium.textPerf070.players",20),labels=Integer.getInteger("arcadium.textPerf070.labels",124);
                for(int i=0;i<players;i++) {
                    Frozen actor=new Frozen(mc.world,new GameProfile(new UUID(0x7000000000004000L+i,0x8000000000000070L),"PerfPlayer"+i));
                    actor.updatePositionAndAngles((i%7-3)*1.6,4,4+i/7*2.2,180,0);actor.lastYaw=actor.yaw;actor.bodyYaw=actor.lastBodyYaw=180;actor.headYaw=actor.lastHeadYaw=180;
                    mc.world.forceEntity(70000+i,actor);
                }
                for(int i=0;i<labels;i++) {
                    ArmorStandEntity stand=new ArmorStandEntity(mc.world,(i%13-6)*1.2,5+i/13*.45,5+i/13*.6);
                    stand.setInvisible(i<94);stand.setCustomName("§bQuest "+i+" §7[INFO] §f"+(i%4==0?"ArcadiumTest":"Bedwars Lobby"));stand.setCustomNameVisible(true);
                    mc.world.forceEntity(71000+i,stand);stands.add(stand);
                }
                var board=mc.world.getScoreboard();var objective=board.createObjective("perf070",ScoreboardCriterion.DUMMY);objective.setDisplayName("§eBEDWARS §bArcadiumTest");
                for(int i=0;i<15;i++)board.getScore("§bLine "+i+" §fArcadiumTest",objective).set(15-i);board.setDisplayObjective(1,objective);
                world=mc.world;settled=System.nanoTime();phase=2;
                report.put("fixture",Map.of("remotePlayers",players,"armorStands",labels,"invisibleStands",Math.min(94,labels),"scoreboardLines",15));
                status("warming");
            }
            if(phase>=2) {
                require(mc.world==world&&mc.screen==null,"The performance scene changed");
                mc.world.setTimeOfDay(6000);
                for(var stand:stands)stand.velocityX=stand.velocityY=stand.velocityZ=0;
                if(phase==2&&System.nanoTime()-settled>25_000_000_000L) {
                    if(!Display.isActive()){status("waiting_for_focus");return;}
                    report.put("environment",Map.of("cpuThreads",Runtime.getRuntime().availableProcessors(),"gpu",GL11.glGetString(GL11.GL_RENDERER),"openGl",GL11.glGetString(GL11.GL_VERSION),"java",System.getProperty("java.runtime.version"),"width",mc.width,"height",mc.height));
                    report.put("settings",settings(mc));report.put("countersBefore",counters());
                    started=System.nanoTime();last=0;phase=3;status("recording");
                }
            }
        } catch(Throwable failure){finish(failure);}
    }

    public static void frame() {
        if(phase!=3||done)return;
        try {
            require(Display.isActive(),"Focus was lost during the performance capture");
            long now=System.nanoTime();
            if(last!=0){require(count<frames.length,"Frame capacity exceeded");frames[count++]=now-last;}last=now;
            if(now-started>=DURATION) {
                long[] sorted=Arrays.copyOf(frames,count);Arrays.sort(sorted);long total=0,slow=0;for(long ns:sorted)total+=ns;
                int low=Math.max(1,(count+99)/100);for(int i=count-low;i<count;i++)slow+=sorted[i];
                report.put("frames",count);report.put("includedSeconds",total/1e9);report.put("fps",1e9*count/total);report.put("onePercentLowFps",1e9*low/slow);
                report.put("p99FrameMs",sorted[(int)Math.ceil(count*.99)-1]/1e6);report.put("maxFrameMs",sorted[count-1]/1e6);
                report.put("countersAfter",counters());report.put("settingsAfter",settings(Minecraft.getInstance()));
                require(report.get("settings").equals(report.get("settingsAfter")),"Settings changed during the performance capture");finish(null);
            }
        } catch(Throwable failure){finish(failure);}
    }

    private static Map<String,Object> settings(Minecraft mc) {
        Map<String,Object> beta=new TreeMap<>();for(var entry:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(entry.getValue() instanceof Property<?> property)beta.put(entry.getKey(),property.get());
        return Map.of("beta",beta,"game",Map.of("renderDistance",mc.options.viewDistance,"fpsLimit",mc.options.fpsLimit,"vsync",mc.options.vsync,"fov",mc.options.fov),
            "fonts",Map.of("vanillaInGame",OneConfigConfig.useVanillaFontInGame,"vanillaForMods",OneConfigConfig.useVanillaFontForModText,"interInGame",OneConfigConfig.useInterInGame,"interForMods",OneConfigConfig.useInterForModText),
            "nickHider",Map.of("enabled",NickHiderConfig.getEnabled(),"nickname",NickHiderConfig.getNickname()),"diagnosticRecording",false,"gpuTimerQueries",false);
    }
    private static Map<String,Object> counters() {
        Map<String,Object> result=new TreeMap<>();
        for(String name:List.of("GpuNameTags","GpuPlayerPipeline","RetainedHudBuffers","GpuModelTransforms"))try {
            Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client.optimizations."+name);
            for(Field field:type.getFields())if(Modifier.isStatic(field.getModifiers())&&(field.getType()==long.class||field.getType()==int.class))result.put(name+"."+field.getName(),field.get(null));
        }catch(ReflectiveOperationException ignored){}
        return result;
    }
    private static void status(String value)throws Exception {
        Path file=Path.of(System.getProperty("arcadium.textPerf070.output")).resolveSibling("status.json");
        Files.createDirectories(file.toAbsolutePath().getParent());Files.writeString(file,new GsonBuilder().create().toJson(Map.of("state",value,"phase",phase)));
    }
    private static void finish(Throwable failure) {
        if(done)return;done=true;report.put("passed",failure==null);report.put("syntheticOffline",true);report.put("phase",phase);
        report.put("scope","Native deterministic text-heavy fixture. Not a measurement on the tester's PC and not a real Hypixel capture.");
        if(failure!=null){report.put("failure",failure.toString());failure.printStackTrace();}
        try {Path file=Path.of(System.getProperty("arcadium.textPerf070.output"));Files.createDirectories(file.toAbsolutePath().getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception error){error.printStackTrace();}Minecraft.getInstance().stop();
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final class Frozen extends RemoteClientPlayerEntity {Frozen(net.minecraft.world.World world,GameProfile profile){super(world,profile);}@Override public void tick(){} }
}
