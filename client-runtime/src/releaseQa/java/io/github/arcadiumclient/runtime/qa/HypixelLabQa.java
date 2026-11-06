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
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.lwjgl.opengl.*;

public final class HypixelLabQa {
    private static final List<Double> frames=new ArrayList<>(),submit=new ArrayList<>(),gpu=new ArrayList<>();
    private static final int[] queries=new int[64];
    private static long last,start,stamp;
    private static int ticks,cursor;
    private static String stage="idle";
    private static KeyBinding held;
    private static KeyBinding heldModifier;
    private static long releaseAt;
    private static boolean inBedwars;
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private HypixelLabQa(){}
    public static boolean enabled(){return System.getProperty("arcadium.hypixelLab.output")!=null;}
    public static boolean keyState(KeyBinding key,boolean original){
        if(!enabled()||key!=held&&key!=heldModifier)return original;
        Minecraft mc=Minecraft.getInstance();
        return System.nanoTime()<releaseAt&&mc.screen==null&&mc.world!=null&&mc.getCurrentServerEntry()!=null&&mc.getCurrentServerEntry().ip.equals("mc.hypixel.net");
    }
    private static Path output(){return Path.of(System.getProperty("arcadium.hypixelLab.output"));}
    public static void tick(){
        if(!enabled())return;
        if(LiveRenderer072RejectionQa.enabled()){LiveRenderer072RejectionQa.tick();return;}
        Minecraft mc=Minecraft.getInstance();
        inBedwars=bedwarsWithoutAllocation(mc);
        if(LiveRenderer072Qa.running()){
            LiveRenderer072Qa.tick(inBedwars);
            if(!LiveRenderer072Qa.running()&&LiveRenderer072Qa.changedController()){
                try{
                    Path changed=output().resolveSibling("control.json");
                    if(Files.exists(changed)){
                        stamp=Files.getLastModifiedTime(changed).toMillis();
                        JsonObject interrupted=JsonParser.parseString(Files.readString(changed)).getAsJsonObject();
                        if(interrupted.has("quit")&&interrupted.get("quit").getAsBoolean())mc.stop();
                    }
                }catch(Exception ignored){}
            }
            return;
        }
        LivePaired066Qa.sceneTick(inBedwars);
        if(held!=null&&(System.nanoTime()>=releaseAt||mc.screen!=null||mc.world==null)){
            KeyBinding.set(held.getKeyCode(),false);held=null;
            if(heldModifier!=null){KeyBinding.set(heldModifier.getKeyCode(),false);heldModifier=null;}
        }
        if(++ticks%20!=0)return;
        try{
            if(mc.world!=null&&mc.getCurrentServerEntry()==null&&"mc.hypixel.net".equals(System.getProperty("arcadium.hypixelLab.server")))mc.setCurrentServerEntry(new ServerListEntry("Hypixel","mc.hypixel.net",false));
            if(!LivePaired066Qa.running())Display.setTitle("Arcadium Hypixel Lab");
            Path control=output().resolveSibling("control.json");
            if(Files.exists(control)&&Files.getLastModifiedTime(control).toMillis()!=stamp){
                stamp=Files.getLastModifiedTime(control).toMillis();JsonObject value=JsonParser.parseString(Files.readString(control)).getAsJsonObject();
                if(LivePaired066Qa.running())LivePaired066Qa.cancel("Controller changed during the paired run");
                if(value.has("quit")&&value.get("quit").getAsBoolean()){mc.stop();return;}
                String nextStage=value.get("stage").getAsString();
                if(LiveRenderer072Qa.handles(nextStage)){
                    stage=nextStage;frames.clear();submit.clear();gpu.clear();last=0;
                    LiveRenderer072Qa.arm(output(),inBedwars,held!=null,value);return;
                }
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
                if(value.has("command")&&mc.player!=null&&mc.screen==null&&mc.getCurrentServerEntry()!=null&&mc.getCurrentServerEntry().ip.equals("mc.hypixel.net")){
                    String command=value.get("command").getAsString();
                    if(command.equals("/lobby bedwars")||command.equals("/lobby main")||command.equals("/swaplobby 1")||command.equals("/swaplobby 2"))mc.player.sendChat(command);
                }
                if(value.has("move")&&held==null&&mc.screen==null&&mc.world!=null&&mc.getCurrentServerEntry()!=null&&mc.getCurrentServerEntry().ip.equals("mc.hypixel.net")){
                    KeyBinding key=switch(value.get("move").getAsString()){case "forward"->mc.options.forwardKey;case "back"->mc.options.backKey;case "left"->mc.options.leftKey;case "right"->mc.options.rightKey;default->null;};
                    if(key!=null&&!key.isPressed()){
                        held=key;releaseAt=System.nanoTime()+Math.clamp(value.has("moveMs")?value.get("moveMs").getAsInt():300,50,600)*1_000_000L;KeyBinding.set(key.getKeyCode(),true);
                        if(value.has("sneak")&&value.get("sneak").getAsBoolean()&&!mc.options.sneakKey.isPressed()){
                            heldModifier=mc.options.sneakKey;KeyBinding.set(heldModifier.getKeyCode(),true);
                        }
                    }
                }
                if(LivePaired066Qa.handles(stage))LivePaired066Qa.arm(output(),inBedwars,held!=null,value);
            }
            if(LivePaired066Qa.handles(stage)||LiveRenderer072Qa.handles(stage))return;
            Map<String,Object> status=new LinkedHashMap<>();status.put("stage",stage);status.put("seconds",(System.nanoTime()-start)/1e9);
            status.put("screen",mc.screen==null?"none":mc.screen.getClass().getSimpleName());status.put("connected",mc.world!=null);
            status.put("focused",Display.isActive());
            status.put("resolution",mc.width+"x"+mc.height);status.put("server",mc.getCurrentServerEntry()==null?"":mc.getCurrentServerEntry().ip);
            status.put("players",mc.world==null?0:mc.world.players.size());status.put("renderDistance",mc.options.viewDistance);
            Map<String,Object> settingsSnapshot=new LinkedHashMap<>();
            for(var entry:BetaOptimizationsConfig.INSTANCE.getTree().map.entrySet())if(entry.getValue() instanceof Property<?> property){
                Map<String,Object> detail=new LinkedHashMap<>();detail.put("value",property.get());detail.put("title",Objects.toString(property.getTitle(),entry.getKey()));
                detail.put("category",Objects.toString(property.getMetadata("category"),""));settingsSnapshot.put(entry.getKey(),detail);
            }
            status.put("betaSettings",settingsSnapshot);
            status.put("gameSettings",Map.of("vsync",mc.options.vsync,"maxFps",mc.options.fpsLimit,"guiScale",mc.options.guiScale,"fancyGraphics",mc.options.fancyGraphics,"unfocusedFpsEnabled",org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps));
            status.put("renderer",Map.of("gpu",GL11.glGetString(GL11.GL_RENDERER),"vendor",GL11.glGetString(GL11.GL_VENDOR),"openGl",GL11.glGetString(GL11.GL_VERSION),"java",System.getProperty("java.version")));
            status.put("sidebar",sidebar());status.put("isBedwarsScene",bedwarsScene());
            if(mc.player!=null)status.put("view",List.of(mc.player.x,mc.player.y,mc.player.z,mc.player.yaw,mc.player.pitch));
            status.put("diagnostics",List.of(GpuModelTransforms.status(),GpuPlayerPipeline.status(),GpuEquipmentPipeline.status(),GpuNameTags.status(),RetainedNameTags.status(),RetainedHudBuffers.status()));
            Map<String,Object> renderCounters=new LinkedHashMap<>();
            for(String name:List.of("GpuPlayerPipeline","NativePlayerPosePipeline","GpuEquipmentPipeline","GpuVoxelSkinPipeline","GpuModelTransforms","GpuTerrainCommands","GpuResidentPlayerState","GpuResolvedInstanceCache","CeraEmissiveAdmission","RetainedHudBuffers","EntityNameFormatting","GpuParticles","NametagLayout")){
                Map<String,Object> values=new LinkedHashMap<>();
                try{
                    Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client."+(name.equals("NametagLayout")?"branding.":"optimizations.")+name);
                    for(var field:type.getFields())if(java.lang.reflect.Modifier.isStatic(field.getModifiers())&&!java.lang.reflect.Modifier.isFinal(field.getModifiers())){
                        Class<?> kind=field.getType();if(kind==long.class||kind==int.class||kind==boolean.class)values.put(field.getName(),field.get(null));
                    }
                    values.put("present",true);
                }catch(ReflectiveOperationException ignored){values.put("present",false);}
                renderCounters.put(name,values);
            }
            status.put("renderCounters",renderCounters);
            Map<String,Object> materialAdmission=new LinkedHashMap<>();
            try{
                Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.CeraEmissiveAdmission");
                materialAdmission.put("status",type.getMethod("status").invoke(null));
                for(String key:List.of("checked","trusted","present","lookupFailed")){
                    var field=type.getDeclaredField(key);field.setAccessible(true);materialAdmission.put(key,field.getBoolean(null));
                }
                materialAdmission.put("presentInRuntime",true);
            }catch(ReflectiveOperationException unavailable){materialAdmission.put("presentInRuntime",false);}
            status.put("nativeMaterialAdmission",materialAdmission);
            try{status.put("particleDiagnostics",Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuParticles").getMethod("status").invoke(null));}catch(ReflectiveOperationException ignored){}
            try{status.put("hologramDiagnostics",Class.forName("io.github.arcadiumclient.runtime.client.optimizations.FastInvisibleHolograms").getMethod("status").invoke(null));}catch(ReflectiveOperationException ignored){}
            for(String[] metric:new String[][]{{"ScoreboardPreparation","scoreboardDiagnostics"},{"FastFramePresentation","presentationDiagnostics"},{"RenderHookAllocationControl","renderHooksDiagnostics"},{"EfficientMotionBlur","motionBlurDiagnostics"}}){
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
    private static boolean bedwarsWithoutAllocation(Minecraft mc){
        if(mc.world==null)return false;var objective=mc.world.getScoreboard().getDisplayObjective(1);if(objective==null)return false;
        String text=objective.getDisplayName(),match="BEDWARS";int found=0;
        for(int i=0;i<text.length();i++){
            char character=text.charAt(i);if(character=='\u00a7'&&i+1<text.length()){i++;continue;}if(character==' ')continue;
            if(character>='a'&&character<='z')character-=32;
            if(character==match.charAt(found)){if(++found==match.length())return true;}else found=character=='B'?1:0;
        }
        return false;
    }
    private static boolean bedwarsScene(){return inBedwars;}
    private static boolean sampling(){return !LivePaired066Qa.handles(stage)&&!LiveRenderer072Qa.handles(stage)&&!stage.equals("idle")&&System.nanoTime()-start>3_000_000_000L&&System.nanoTime()-start<32_000_000_000L&&Minecraft.getInstance().world!=null&&Minecraft.getInstance().screen==null&&bedwarsScene();}
    public static void frameHead(){if(!enabled())return;if(LiveRenderer072RejectionQa.enabled()){LiveRenderer072RejectionQa.head();return;}if(LiveRenderer072Qa.handles(stage)){LiveRenderer072Qa.head(inBedwars);return;}if(LivePaired066Qa.handles(stage))LivePaired066Qa.head(inBedwars);}
    public static void frame(){if(!enabled())return;if(LiveRenderer072RejectionQa.enabled()){LiveRenderer072RejectionQa.frame();return;}if(LiveRenderer072Qa.handles(stage)){LiveRenderer072Qa.tail(inBedwars);return;}if(LivePaired066Qa.handles(stage)){LivePaired066Qa.tail(inBedwars);return;}long now=System.nanoTime();if(sampling()&&last!=0&&frames.size()<300000)frames.add((now-last)/1e6);last=now;}
    public static int beginGpu(){
        if(!sampling())return 0;int slot=cursor++%queries.length;
        if(queries[slot]==0)queries[slot]=GL15.glGenQueries();
        else if(GL15.glGetQueryObjecti(queries[slot],GL15.GL_QUERY_RESULT_AVAILABLE)!=0)gpu.add(GL33.glGetQueryObjecti64(queries[slot],GL15.GL_QUERY_RESULT)/1e6);else return 0;
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,queries[slot]);return queries[slot];
    }
    public static void endGpu(int query,long cpu){if(query!=0){GL15.glEndQuery(GL33.GL_TIME_ELAPSED);submit.add((System.nanoTime()-cpu)/1e6);}}
}
