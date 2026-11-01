// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.*;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.text.Formatting;
import org.lwjgl.opengl.*;

public final class HypixelLabQa {
    private static final List<Double> frames=new ArrayList<>(),submit=new ArrayList<>(),gpu=new ArrayList<>();
    private static final int[] queries=new int[64];
    private static long last,start,stamp;
    private static int ticks,cursor;
    private static String stage="idle";
    private static KeyBinding held;
    private static long releaseAt;
    private static boolean inBedwars;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private HypixelLabQa(){}
    public static boolean enabled(){return System.getProperty("arcadium.hypixelLab.output")!=null;}
    public static boolean keyState(KeyBinding key,boolean original){
        if(!enabled()||key!=held)return original;
        Minecraft mc=Minecraft.getInstance();
        return System.nanoTime()<releaseAt&&mc.screen==null&&mc.world!=null&&mc.getCurrentServerEntry()!=null&&mc.getCurrentServerEntry().ip.equals("mc.hypixel.net");
    }
    private static Path output(){return Path.of(System.getProperty("arcadium.hypixelLab.output"));}
    public static void tick(){
        if(!enabled())return;Minecraft mc=Minecraft.getInstance();
        inBedwars=sidebar().replace(" ","").toUpperCase(Locale.ROOT).contains("BEDWARS");
        if(held!=null&&(System.nanoTime()>=releaseAt||mc.screen!=null||mc.world==null)){KeyBinding.set(held.getKeyCode(),false);held=null;}
        if(++ticks%20!=0)return;
        try{
            if(mc.world!=null&&mc.getCurrentServerEntry()==null&&"mc.hypixel.net".equals(System.getProperty("arcadium.hypixelLab.server")))mc.setCurrentServerEntry(new ServerListEntry("Hypixel","mc.hypixel.net",false));
            Display.setTitle("Arcadium 0.6.5 Hypixel Lab");
            Path control=output().resolveSibling("control.json");
            if(Files.exists(control)&&Files.getLastModifiedTime(control).toMillis()!=stamp){
                stamp=Files.getLastModifiedTime(control).toMillis();JsonObject value=JsonParser.parseString(Files.readString(control)).getAsJsonObject();
                if(value.has("quit")&&value.get("quit").getAsBoolean()){mc.stop();return;}
                boolean all=value.has("beta")&&value.get("beta").getAsBoolean();
                BetaOptimizationsConfig.setGpuModelTransforms(all);BetaOptimizationsConfig.setGpuPlayerPipeline(all);
                BetaOptimizationsConfig.setGpuEquipmentPipeline(all);BetaOptimizationsConfig.setGpuNameTags(all);
                BetaOptimizationsConfig.setRetainedNameTagScenes(all);BetaOptimizationsConfig.setRetainedHudBatches(all);var particleOption=BetaOptimizationsConfig.INSTANCE.getTree().getProp("gpuParticles");if(particleOption!=null)particleOption.setAs(all);
                for(String key:List.of("retainedParticleRecipes","fastInvisibleHolograms","retainedScoreboardPreparation","directFramebufferPresentation","exactParticleVisibilityCache")){var property=BetaOptimizationsConfig.INSTANCE.getTree().getProp(key);if(property!=null)property.setAs(all);}
                BetaOptimizationsConfig.setWorldRenderScale(false);
                mc.options.vsync=false;Display.setVSyncEnabled(false);mc.options.fpsLimit=260;
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                if(value.has("settings"))for(var e:value.getAsJsonObject("settings").entrySet()){var property=BetaOptimizationsConfig.INSTANCE.getTree().getProp(e.getKey());if(property!=null){if(e.getValue().getAsJsonPrimitive().isBoolean())property.setAs(e.getValue().getAsBoolean());else property.setAs(e.getValue().getAsInt());}}
                stage=value.get("stage").getAsString();frames.clear();submit.clear();gpu.clear();start=System.nanoTime();last=0;
                if(value.has("move")&&held==null&&mc.screen==null&&mc.world!=null&&mc.getCurrentServerEntry()!=null&&mc.getCurrentServerEntry().ip.equals("mc.hypixel.net")){
                    KeyBinding key=switch(value.get("move").getAsString()){case "forward"->mc.options.forwardKey;case "back"->mc.options.backKey;case "left"->mc.options.leftKey;case "right"->mc.options.rightKey;default->null;};
                    if(key!=null&&!key.isPressed()){held=key;releaseAt=System.nanoTime()+Math.clamp(value.has("moveMs")?value.get("moveMs").getAsInt():300,50,600)*1_000_000L;KeyBinding.set(key.getKeyCode(),true);}
                }
            }
            Map<String,Object> status=new LinkedHashMap<>();status.put("stage",stage);status.put("seconds",(System.nanoTime()-start)/1e9);
            status.put("screen",mc.screen==null?"none":mc.screen.getClass().getSimpleName());status.put("connected",mc.world!=null);
            status.put("resolution",mc.width+"x"+mc.height);status.put("server",mc.getCurrentServerEntry()==null?"":mc.getCurrentServerEntry().ip);
            status.put("players",mc.world==null?0:mc.world.players.size());status.put("renderDistance",mc.options.viewDistance);
            status.put("sidebar",sidebar());status.put("isBedwarsScene",bedwarsScene());
            if(mc.player!=null)status.put("view",List.of(mc.player.x,mc.player.y,mc.player.z,mc.player.yaw,mc.player.pitch));
            status.put("diagnostics",List.of(GpuModelTransforms.status(),GpuPlayerPipeline.status(),GpuEquipmentPipeline.status(),GpuNameTags.status(),RetainedNameTags.status(),RetainedHudBuffers.status()));
            try{status.put("particleDiagnostics",Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuParticles").getMethod("status").invoke(null));}catch(ReflectiveOperationException ignored){}
            try{status.put("hologramDiagnostics",Class.forName("io.github.arcadiumclient.runtime.client.optimizations.FastInvisibleHolograms").getMethod("status").invoke(null));}catch(ReflectiveOperationException ignored){}
            for(String[] metric:new String[][]{{"ScoreboardPreparation","scoreboardDiagnostics"},{"FastFramePresentation","presentationDiagnostics"}}){
                try{Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client.optimizations."+metric[0]);
                    if(metric[0].equals("ScoreboardPreparation")){Map<String,Object> counters=new LinkedHashMap<>();for(String key:List.of("snapshots","snapshotHits","visibilityComputations","visibilityHits"))counters.put(key,type.getField(key).get(null));status.put(metric[1],counters);}
                    else status.put(metric[1],type.getMethod("status").invoke(null));
                }catch(ReflectiveOperationException ignored){}
            }
            status.put("samples",frames.size());
            if(!frames.isEmpty()){
                double[] ordered=frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();int low=Math.max(1,ordered.length/100);
                status.put("fps",1000/Arrays.stream(ordered).average().orElseThrow());status.put("onePercentLow",1000/Arrays.stream(ordered,ordered.length-low,ordered.length).average().orElseThrow());
                status.put("p99Ms",ordered[Math.min(ordered.length-1,(int)(ordered.length*.99))]);
                status.put("worldCpuMs",submit.stream().mapToDouble(Double::doubleValue).average().orElse(0));status.put("worldGpuMs",gpu.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            }
            Files.writeString(output(),JSON.toJson(status));
        }catch(Exception failure){org.apache.logging.log4j.LogManager.getLogger("Arcadium/Hypixel Lab").warn("Could not write benchmark status",failure);}
    }
    private static String sidebar(){var world=Minecraft.getInstance().world;if(world==null)return "";var score=world.getScoreboard().getDisplayObjective(1);return score==null?"":Formatting.strip(score.getDisplayName());}
    private static boolean bedwarsScene(){return inBedwars;}
    private static boolean sampling(){return !stage.equals("idle")&&System.nanoTime()-start>3_000_000_000L&&System.nanoTime()-start<32_000_000_000L&&Minecraft.getInstance().world!=null&&Minecraft.getInstance().screen==null&&bedwarsScene();}
    public static void frame(){if(!enabled())return;long now=System.nanoTime();if(sampling()&&last!=0&&frames.size()<300000)frames.add((now-last)/1e6);last=now;}
    public static int beginGpu(){
        if(!sampling())return 0;int slot=cursor++%queries.length;
        if(queries[slot]==0)queries[slot]=GL15.glGenQueries();
        else if(GL15.glGetQueryObjecti(queries[slot],GL15.GL_QUERY_RESULT_AVAILABLE)!=0)gpu.add(GL33.glGetQueryObjecti64(queries[slot],GL15.GL_QUERY_RESULT)/1e6);else return 0;
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,queries[slot]);return queries[slot];
    }
    public static void endGpu(int query,long cpu){if(query!=0){GL15.glEndQuery(GL33.GL_TIME_ELAPSED);submit.add((System.nanoTime()-cpu)/1e6);}}
}
