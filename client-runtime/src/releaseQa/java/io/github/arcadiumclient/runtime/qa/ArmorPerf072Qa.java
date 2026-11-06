// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuModelTransforms;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class ArmorPerf072Qa {
    private static final int COUNT=20;
    private static final long WARMUP=25_000_000_000L,DURATION=30_000_000_000L;
    private static final boolean OPTIMIZED=Boolean.getBoolean("arcadium.modernGlint072.experimental");
    private static final boolean PROFILE_ONLY=Boolean.getBoolean("arcadium.armorPerf072.profileOnly");
    private static jdk.jfr.Recording recording;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<Actor> actors=new ArrayList<>();
    private static final long[] durations=new long[500_000];
    private static int phase,ticks,wait,frames,menuFrames;
    private static long readyAt,started,last,totalFrameTime;
    private static Future<?> environment;
    private static boolean cameraPrepared,done;
    private static String failure;
    private static long[] before;
    private static Object animationItems,originalStyle;
    private static java.lang.reflect.Field styleField;
    private static Map<String,Object> recordedSettings;
    private ArmorPerf072Qa(){}
    public static boolean enabled(){return System.getProperty("arcadium.armorPerf072.output")!=null;}
    public static float delta(float original){return enabled()?.5f:original;}

    public static void tick(){
        if(done)return;
        try{
            Minecraft mc=Minecraft.getInstance();wait++;require(++ticks<8000,"Armor performance fixture timed out");
            if(phase==0&&wait>60&&mc.screen!=null&&mc.screen.getClass().getSimpleName().contains("MainMenu")){
                require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Use the owned offline fixture");
                require(!Boolean.getBoolean("arcadium.forge.prepared"),"No Forge guests in the isolated armor benchmark");
                require(ModernArmorGlintPipeline.mvpMode()==1&&Boolean.getBoolean("arcadium.nativeLayerRoot072.replay"),"Pin the same MVP/root selection in both modes");
                var certificate=new com.google.gson.JsonParser().parse(Files.readString(Path.of(System.getProperty("arcadium.armorPerf072.certificate")))).getAsJsonObject();
                require(certificate.get("passed").getAsBoolean()&&certificate.get("mainSha256").getAsString().equals("33ffe46eab3a9424c8d8775df91ef9a9382b071ae226586e40f886e29aac7f86"),"Validate the explicit main58 native image certificate before timing");
                report.put("correctnessCertificate",certificate);report.put("diagnosticOnly",true);report.put("headlineOrFinalProfile",false);report.put("profile","armor-only20p");
                report.put("optimized",OPTIMIZED);report.put("warmupSeconds",25);report.put("measurementSeconds",30);report.put("correctnessReadbacksOrWrappers",false);report.put("jfr",false);
                report.put("profileOnly",PROFILE_ONLY);report.put("excludedFromFpsEvidence",PROFILE_ONLY);
                if(PROFILE_ONLY){
                    var configuration=jdk.jfr.Configuration.create(Path.of(System.getProperty("arcadium.armorPerf072.jfc")));var settings=configuration.getSettings();
                    Set<String> allowed=Set.of("jdk.ExecutionSample","jdk.NativeMethodSample","jdk.ObjectAllocationSample","jdk.JavaMonitorEnter","jdk.ThreadPark","jdk.ThreadSleep","jdk.GCPhasePause");
                    for(var setting:settings.entrySet())if(setting.getKey().endsWith("#enabled")&&Boolean.parseBoolean(setting.getValue()))require(allowed.contains(setting.getKey().substring(0,setting.getKey().indexOf('#'))),"Non-whitelisted render diagnostic event");
                    for(String event:List.of("jdk.JVMInformation","jdk.InitialSystemProperty","jdk.InitialEnvironmentVariable","jdk.SystemProcess","jdk.ProcessStart","jdk.InitialSecurityProperty","jdk.SecurityPropertyModification"))require("false".equals(settings.get(event+"#enabled")),"Sensitive inventory event was not disabled");
                    recording=new jdk.jfr.Recording(configuration);recording.setName("Arcadium armor render cost");recording.setMaxSize(256L*1024*1024);report.put("jfr",true);report.put("renderEventWhitelist",allowed);
                }
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                for(var method:BetaOptimizationsConfig.class.getMethods())if(Modifier.isStatic(method.getModifiers())&&method.getName().startsWith("set")&&Arrays.equals(method.getParameterTypes(),new Class<?>[]{boolean.class}))method.invoke(null,false);
                BetaOptimizationsConfig.setGpuModelTransforms(true);BetaOptimizationsConfig.setGpuEquipmentPipeline(true);BetaOptimizationsConfig.setGpuPlayerPipeline(false);BetaOptimizationsConfig.setMinimumPlayers(1);
                Skin3DConfig.INSTANCE.chooseEnabled(false);org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);mc.options.fpsLimit=260;mc.options.viewDistance=8;mc.options.fov=70;
                mc.options.cloudRenderMode=0;mc.options.perspective=0;mc.options.viewBobbing=false;mc.options.hideGui=true;
                Class<?> config=Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");Object value=config.getMethod("instance").invoke(null);animationItems=config.getField("items").get(value);styleField=animationItems.getClass().getField("armorGlint");originalStyle=styleField.get(animationItems);
                boolean selected=false;for(Object style:styleField.getType().getEnumConstants())if(((Enum<?>)style).name().equals("V1_19_4")){styleField.set(animationItems,style);selected=true;}require(selected,"The native modern glint style is absent");
                mc.startGame("arcadium-armor-perf072","Armor Performance QA",new WorldSettings(720721,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;wait=0;
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&wait>100){
                if(environment==null){environment=mc.getServer().executeTask(()->{var world=mc.getServer().getWorld(0);world.getGameRules().set("doMobSpawning","false");world.getGameRules().set("doDaylightCycle","false");world.getGameRules().set("randomTickSpeed","0");world.setTimeOfDay(6000);world.getData().setRaining(false);world.getData().setThundering(false);for(var entity:new ArrayList<>(world.entities))if(!(entity instanceof net.minecraft.entity.living.player.PlayerEntity))world.removeEntityNow(entity);return true;});return;}
                if(!environment.isDone()||mc.world.entities.stream().anyMatch(e->!(e instanceof net.minecraft.entity.living.player.PlayerEntity)))return;environment.get();
                if(!cameraPrepared){mc.openScreen(null);mc.player.abilities.flying=true;mc.player.updatePositionAndAngles(0,6,-10,0,5);cameraPrepared=true;return;}
                if(!ArmorPerf072Readiness.ready(mc,output()))return;
                require(mc.width==1920&&mc.height==1080&&Display.getWidth()==1920&&Display.getHeight()==1080,"Use the actual full 1920x1080 framebuffer");
                for(int i=0;i<COUNT;i++){Actor actor=new Actor(mc,new GameProfile(new UUID(0x72072a0000000000L,i+1),String.format(java.util.Locale.ROOT,"ArmorPerf%02d",i)),i);actor.updatePositionAndAngles((i%5-2)*2.4,4,3+(i/5)*2.4,190,12);actor.setEquipment(0,new ItemStack(Items.DIAMOND_SWORD));net.minecraft.item.Item[] pieces={Items.DIAMOND_BOOTS,Items.DIAMOND_LEGGINGS,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_HELMET};for(int slot=1;slot<=4;slot++){ItemStack stack=new ItemStack(pieces[slot-1]);stack.addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING,1);actor.setEquipment(slot,stack);}mc.world.forceEntity(727200+i,actor);actors.add(actor);}
                freeze(mc);phase=2;readyAt=System.nanoTime();report.put("gpu",GL11.glGetString(GL11.GL_RENDERER));report.put("openGl",GL11.glGetString(GL11.GL_VERSION));report.put("java",System.getProperty("java.runtime.version"));report.put("cpuThreads",Runtime.getRuntime().availableProcessors());report.put("playerCount",COUNT);progress("warming");
            }else if(phase==2){
                freeze(mc);
                if(System.nanoTime()-readyAt>=WARMUP&&Display.isActive()){
                    var roster=ArmorPerf072Readiness.actors(mc,actors);require(Boolean.TRUE.equals(roster.get("allRegistered")),"All twenty actors must remain in native chunks and sections");report.put("actorRegistration",roster);
                    require(GpuModelTransforms.ready(),"Model transform compute was not ready");require(ModernArmorGlintPipeline.shaderAdapterEnabled()==OPTIMIZED,"The requested production shader adapter mode changed");
                    require(!BetaOptimizationsConfig.getGpuPlayerPipeline()&&BetaOptimizationsConfig.getGpuModelTransforms()&&BetaOptimizationsConfig.getGpuEquipmentPipeline(),"The isolation settings changed");
                    recordedSettings=settings(mc);report.put("settingsBefore",recordedSettings);before=counters();report.put("countersBefore",before);progress("recording");if(recording!=null)recording.start();started=System.nanoTime();last=0;phase=3;
                }
            }else if(phase==3)freeze(mc);
        }catch(Throwable error){requestStop(error);}
    }

    private static void freeze(Minecraft mc){
        for(Actor actor:actors)actor.freeze();
        if(mc.player!=null){mc.player.yaw=mc.player.lastYaw=0;mc.player.pitch=mc.player.lastPitch=5;mc.player.lastX=mc.player.prevX=mc.player.x;mc.player.lastY=mc.player.prevY=mc.player.y;mc.player.lastZ=mc.player.prevZ=mc.player.z;}
    }
    public static void frame(){
        if(done)return;
        try{
            Minecraft mc=Minecraft.getInstance();
            if(phase==3){
                long now=System.nanoTime();require(Display.isActive(),"The measured game lost foreground focus");require(frames<durations.length,"Frame storage budget reached");
                if(last!=0){long duration=now-last;durations[frames++]=duration;totalFrameTime+=duration;}last=now;
                if(now-started>=DURATION){
                    stopRecording();
                    long[] after=counters();report.put("countersAfter",after);require(frames>1000,"Too few actual frames");long layers=after[OPTIMIZED?0:2]-before[OPTIMIZED?0:2];double perFrame=(double)layers/frames;
                    require(perFrame>=19.5&&perFrame<=20.5,"Actual armored activity was not twenty layer calls per frame: "+perFrame);
                    if(OPTIMIZED)require(after[1]>before[1]&&after[0]>before[0]&&after[2]==before[2],"The optimized armor path did not render without fallback");else require(after[0]==before[0]&&after[1]==before[1],"The OFF control queued optimized armor");
                    require(after[3]==0,"The body pipeline or physical layer replay unexpectedly ran");
                    report.put("actualArmorLayersPerFrame",perFrame);report.put("settingsAfter",settings(mc));require(recordedSettings.equals(settings(mc)),"Public/numeric settings changed during timing");
                    long[] sorted=Arrays.copyOf(durations,frames);Arrays.sort(sorted);int lows=Math.max(1,(int)Math.ceil(frames*.01));double slow=0;for(int i=frames-lows;i<frames;i++)slow+=sorted[i];
                    report.put("frames",frames);report.put("measuredFrameSeconds",totalFrameTime/1e9);
                    if(!PROFILE_ONLY){report.put("averageFps",frames*1e9/totalFrameTime);report.put("onePercentLowFps",1e9/(slow/lows));report.put("p99FrameMs",sorted[Math.min(frames-1,(int)Math.ceil(frames*.99)-1)]/1e6);StringBuilder rows=new StringBuilder("frame,nanoseconds\n");for(int i=0;i<frames;i++)rows.append(i).append(',').append(durations[i]).append('\n');Files.writeString(output().resolveSibling("frames.csv"),rows);}requestStop(null);
                }
            }
            if(phase==4){mc.options.pauseOnUnfocus=false;mc.openScreen(new net.minecraft.client.gui.screen.menu.GameMenuScreen());phase=5;menuFrames=0;progress("pause-menu");}
            else if(phase==5){require(++menuFrames<240,"Native pause menu did not pause");if(menuFrames<3||!mc.isPaused())return;click(mc,1);phase=6;menuFrames=0;progress("disconnect-requested");}
            else if(phase==6){if(mc.world==null)completeUnload(mc);else{require(++menuFrames<240&&mc.screen!=null&&mc.screen.getClass().getName().equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen"),"Use the actual native confirmation screen");if(menuFrames<3||!mc.isPaused())return;click(mc,0);completeUnload(mc);}}
            else if(phase==7)finish();
        }catch(Throwable error){if(phase<4)requestStop(error);else{failure=error.toString();finish();}}
    }
    private static void click(Minecraft mc,int id){var button=((io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess)mc.screen).arcadium$buttons().stream().filter(value->value.id==id&&value.active).findFirst().orElseThrow();((io.github.arcadiumclient.runtime.qa.mixin.ResolvedInstance072ScreenClick)mc.screen).resolved$click(button);}
    private static void completeUnload(Minecraft mc)throws Exception{require(mc.world==null,"Normal disconnect did not unload");int count=((Number)Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuModelAuxiliaries").getMethod("size").invoke(null)).intValue();require(count==0,"Owned auxiliary resources survived normal unload");report.put("normalWorldUnload",Map.of("worldNull",true,"auxiliaries",count));phase=7;progress("unloaded");}
    private static long[] counters(){long replay=0;try{Class<?> type=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.NativePlayerLayerRoot");var field=type.getDeclaredField("replays");field.setAccessible(true);replay=field.getLong(null);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}return new long[]{ModernArmorGlintPipeline.layersSupported,ModernArmorGlintPipeline.glintParts,ModernArmorGlintPipeline.nativeLayers,replay};}
    private static Map<String,Object> settings(Minecraft mc)throws Exception{
        Map<String,Object> beta=new TreeMap<>();for(var method:BetaOptimizationsConfig.class.getMethods())if(Modifier.isStatic(method.getModifiers())&&method.getParameterCount()==0&&method.getName().startsWith("get")&&(method.getReturnType().isPrimitive()||method.getReturnType()==String.class))try{BetaOptimizationsConfig.class.getMethod("set"+method.getName().substring(3),method.getReturnType());beta.put(method.getName(),method.invoke(null));}catch(NoSuchMethodException diagnosticNotSetting){}
        return Map.of("beta",beta,"width",mc.width,"height",mc.height,"worldRenderScale",BetaOptimizationsConfig.getWorldRenderScale(),"mainMinecraftFbo",mc.getRenderTarget().frameBufferId,"viewDistance",mc.options.viewDistance,"fov",mc.options.fov,"fpsLimit",mc.options.fpsLimit,"vsync",mc.options.vsync,"style",((Enum<?>)styleField.get(animationItems)).name());
    }
    private static Path output(){return Path.of(System.getProperty("arcadium.armorPerf072.output")).toAbsolutePath();}
    private static void stopRecording()throws Exception{if(recording!=null){if(recording.getState()==jdk.jfr.RecordingState.RUNNING){recording.stop();Path file=output().resolveSibling("diagnostic.jfr");recording.dump(file);report.put("privateDiagnosticRecording",file.toString());report.put("profilingNotFpsEvidence",true);}recording.close();recording=null;}}
    private static void progress(String value)throws Exception{report.put("stage",value);Files.createDirectories(output().getParent());Files.writeString(output().resolveSibling("progress.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    private static void requestStop(Throwable error){if(error!=null){failure=error.toString();error.printStackTrace();}if(phase<4)phase=Minecraft.getInstance().world==null?7:4;}
    private static void finish(){if(done)return;done=true;report.put("passed",failure==null);if(failure!=null)report.put("error",failure);try{stopRecording();if(styleField!=null)styleField.set(animationItems,originalStyle);Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){error.printStackTrace();}Minecraft.getInstance().stop();}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final class Actor extends RemoteClientPlayerEntity{
        final int index;Actor(Minecraft mc,GameProfile profile,int index){super(mc.world,profile);this.index=index;}
        void freeze(){ticks=160;yaw=lastYaw=bodyYaw=lastBodyYaw=190;headYaw=lastHeadYaw=205;pitch=lastPitch=12;walkAnimationProgress=2.4f+index*.11f;walkAnimationSpeed=lastWalkAnimationSpeed=.6f;attackAnimationProgress=lastAttackAnimationProgress=.35f;lastX=prevX=x;lastY=prevY=y;lastZ=prevZ=z;}
        @Override public void tick(){freeze();}
    }
}
