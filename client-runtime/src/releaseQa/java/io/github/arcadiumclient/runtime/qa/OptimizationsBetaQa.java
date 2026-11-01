// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.*;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.internal.ui.api.BetaOptimizationPage;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.OptimizationsRoute;
import org.polyfrost.oneconfig.internal.ui.shell.LocalNavController;

public final class OptimizationsBetaQa {
    public static int verifiedDraws,verifiedInstances;
    private static double matrixError;
    private static int phase, ticks, wait, scenario = -1;
    private static boolean done;
    private static long started, lastFrame;
    private static final List<Double> samples = new ArrayList<>();
    private static final Map<String,Object> report = new LinkedHashMap<>();
    private static final String[] cases = System.getProperty("arcadium.optimizationsBeta.focus","").equals("scale")
            ? new String[]{"scale-100","scale-75","scale-50"}
            : new String[]{"baseline-a", "gpu-a", "baseline-b", "gpu-b", "hud-base", "hud-retained", "scale-100", "scale-75", "scale-50", "compat-3d", "compat-fallback"};
    private static final List<Double> gpuSamples=new ArrayList<>();
    private static final int[] queries=new int[64];
    private static int queryCursor;
    public static boolean enabled() { return System.getProperty("arcadium.optimizationsBeta.output") != null; }
    private static void require(boolean value,String message) { if(!value) throw new IllegalStateException(message); }

    public static void tick() {
        if(done)return;
        var mc=Minecraft.getInstance();
        try {
            require(++ticks<9000,"Optimization QA timed out, phase "+phase); wait++;
            if(phase==0 && ticks>65 && mc.screen!=null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                ClientPreferences.setSetupComplete(true);
                require(BetaOptimizationPage.INSTANCE.getAvailable(),"Optimization sidebar page not registered");
                require(!BetaOptimizationsConfig.getGpuModelTransforms() && !BetaOptimizationsConfig.getRetainedHudBatches() && !BetaOptimizationsConfig.getWorldRenderScale(),"Beta default was enabled");
                var tree=BetaOptimizationsConfig.INSTANCE.getTree();
                for(String key:List.of("gpuModelTransforms","minimumPlayers","retainedHudBatches","worldRenderScale","worldResolution"))
                    require(tree.getProp(key)!=null && tree.getProp(key).getMetadata("visualizer")!=null,"Missing GUI control: "+key);
                OneConfigUI.open(OptimizationsRoute.INSTANCE); phase=1; wait=0;
            } else if(phase==1 && wait>35 && LocalNavController.INSTANCE.isReady()) {
                require(mc.screen.getClass().getSimpleName().equals("OneConfigUIScreen"),"Optimization settings opened outside Arcadium Options");
                Screenshot.take(mc.gameDir,"optimizations-beta.png",mc.width,mc.height,mc.getRenderTarget());
                report.put("settingsInSidebar",true); report.put("defaultsOff",true);
                mc.openScreen(null);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;mc.options.viewDistance=4;mc.options.fpsLimit=260;
                Display.setVSyncEnabled(false);org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                mc.startGame("arcadium-optimizations-beta","Optimization QA",new WorldSettings(493,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=2;wait=0;
            } else if(phase==2 && mc.world!=null && mc.player!=null && wait>70) {
                mc.openScreen(null); mc.player.abilities.flying=true;
                mc.player.updatePositionAndAngles(0,7,-10,0,9);mc.player.lastYaw=mc.player.yaw;mc.player.lastPitch=mc.player.pitch;
                mc.world.setTimeOfDay(6000);
                int resolution=Integer.getInteger("arcadium.optimizationsBeta.width",0);
                if(resolution>0){mc.width=resolution;mc.height=resolution*9/16;mc.getRenderTarget().resize(mc.width,mc.height);}
                int n=Integer.getInteger("arcadium.optimizationsBeta.players",400);
                for(int i=0;i<n;i++) {
                    var player=new FrozenPlayer(mc.world,new GameProfile(new UUID(0x7142000000000000L,i+1),"BetaQA"+i));
                    double x=(i%20-9.5)*1.35,z=3+(i/20)*1.35;
                    player.updatePositionAndAngles(x,4,z,180+i%35,15+i%17);player.lastYaw=player.yaw;player.lastPitch=player.pitch;
                    player.bodyYaw=player.lastBodyYaw=player.yaw;player.headYaw=player.lastHeadYaw=player.yaw+15;
                    player.walkAnimationProgress=2+i*.1f;player.walkAnimationSpeed=player.lastWalkAnimationSpeed=.6f;player.ticks=100+i%20;
                    mc.world.forceEntity(20000+i,player);
                }
                var board=mc.world.getScoreboard();var objective=board.createObjective("beta",ScoreboardCriterion.DUMMY);objective.setDisplayName("Arcadium Beta QA");
                for(int i=0;i<15;i++)board.getScore("HUD upload reuse test line "+i,objective).set(15-i);
                board.setDisplayObjective(1,objective);
                report.put("gpu",GL11.glGetString(GL11.GL_RENDERER));report.put("gl",GL11.glGetString(GL11.GL_VERSION));report.put("window",mc.width+"x"+mc.height);
                report.put("syntheticPlayers",n);phase=3;wait=0;
            } else if(phase==3 && wait>80) { phase=4; next(); }
            else if(phase==4 && System.nanoTime()-started>8_000_000_000L) {
                report.put(cases[scenario],summary());
                if(cases[scenario].equals("gpu-a")) {
                    Screenshot.take(mc.gameDir,"gpu-transforms.png",mc.width,mc.height,mc.getRenderTarget());
                    if(Integer.getInteger("arcadium.optimizationsBeta.players",400)>0)
                        require(GpuModelTransforms.offloadedParts>0 && GpuModelTransforms.dispatches>0,"GPU path was never used: "+GpuModelTransforms.status());
                }
                if(cases[scenario].equals("baseline-a"))Screenshot.take(mc.gameDir,"baseline.png",mc.width,mc.height,mc.getRenderTarget());
                if(cases[scenario].equals("hud-retained"))require(RetainedHudBuffers.hits>0,"HUD batches were never retained");
                if(cases[scenario].equals("scale-75")){require(WorldRenderScale.scaledFrames>0,"World scaling was not active");Screenshot.take(mc.gameDir,"world-75.png",mc.width,mc.height,mc.getRenderTarget());}
                if(cases[scenario].equals("compat-3d")) {
                    require(Features062Qa.voxelDraws>0,"Voxel skin parts stopped rendering");
                    require(Features062Qa.meshCompiles>0 && Features062Qa.meshCompiles<200,"Voxel meshes were repeatedly rebuilt");
                    report.put("voxelDraws",Features062Qa.voxelDraws);report.put("compiledVoxelMeshes",Features062Qa.meshCompiles);
                    Screenshot.take(mc.gameDir,"beta-with-voxel-skins.png",mc.width,mc.height,mc.getRenderTarget());
                }
                if(cases[scenario].equals("compat-fallback")) {
                    require(!GpuModelTransforms.ready(),"Simulated compute failure did not fall back");report.put("computeFailureFallback",true);
                    require(WorldRenderScale.status().endsWith("Off"),"World resolution was not restored after disabling");
                }
                next();
            }
        } catch(Throwable error) {finish(error);}
    }

    private static void next() {
        if(++scenario>=cases.length) {
            report.put("gpuParts",GpuModelTransforms.offloadedParts);report.put("fallbackParts",GpuModelTransforms.fallbackParts);report.put("dispatches",GpuModelTransforms.dispatches);
            report.put("hudHits",RetainedHudBuffers.hits);report.put("hudUploads",RetainedHudBuffers.uploads);report.put("savedHudBytes",RetainedHudBuffers.savedBytes);
            report.put("verifiedGpuInstances",verifiedInstances);report.put("maximumMatrixError",matrixError);report.put("gpuSortBatches",GpuModelTransforms.sortedBatches);
            if(Integer.getInteger("arcadium.optimizationsBeta.players",400)>0 && !System.getProperty("arcadium.optimizationsBeta.focus","").equals("scale"))require(verifiedInstances>0,"GPU matrix readback validation did not run");
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error after beta benchmark");finish(null);return;
        }
        String name=cases[scenario];
        BetaOptimizationsConfig.setGpuModelTransforms(name.startsWith("gpu-")||name.startsWith("compat-"));BetaOptimizationsConfig.setMinimumPlayers(Integer.getInteger("arcadium.optimizationsBeta.players",400)==0?24:1);
        BetaOptimizationsConfig.setRetainedHudBatches(name.equals("hud-retained")||name.equals("compat-3d"));
        BetaOptimizationsConfig.setWorldRenderScale(name.equals("scale-75")||name.equals("scale-50")||name.equals("compat-3d"));
        BetaOptimizationsConfig.setWorldResolution(name.equals("scale-50")?50:75);
        if(name.equals("compat-3d"))try {
            var seed=Features062Qa.class.getDeclaredMethod("seedSkin",Minecraft.class);seed.setAccessible(true);seed.invoke(null,Minecraft.getInstance());
            io.github.arcadiumclient.runtime.client.features.Skin3DConfig.INSTANCE.chooseEnabled(true);Minecraft.getInstance().options.perspective=1;
            Features062Qa.voxelDraws=Features062Qa.meshCompiles=0;
        }catch(Exception error){throw new IllegalStateException(error);}
        if(name.equals("compat-fallback"))GpuModelTransforms.disable(new IllegalStateException("QA: simulated compute failure"));
        samples.clear();gpuSamples.clear();started=System.nanoTime();lastFrame=0;
    }

    public static void frame() {
        if(done || phase!=4)return;
        long now=System.nanoTime();if(lastFrame!=0 && now-started>2_000_000_000L)samples.add((now-lastFrame)/1_000_000d);lastFrame=now;
    }

    public static int beginGpuTime() {
        if(phase!=4||done)return 0;
        int slot=queryCursor++%queries.length;
        if(queries[slot]==0)queries[slot]=GL15.glGenQueries();
        else if(GL15.glGetQueryObjecti(queries[slot],GL15.GL_QUERY_RESULT_AVAILABLE)!=0) {
            if(System.nanoTime()-started>2_000_000_000L)gpuSamples.add(GL33.glGetQueryObjecti64(queries[slot],GL15.GL_QUERY_RESULT)/1_000_000d);
        }else return 0;
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,queries[slot]);return queries[slot];
    }
    public static void endGpuTime(int query){if(query!=0)GL15.glEndQuery(GL33.GL_TIME_ELAPSED);}

    public static void verify(int buffer,java.nio.IntBuffer original) {
        int previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);
        var actual=org.lwjgl.BufferUtils.createIntBuffer(original.remaining());
        try {GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffer);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,actual);}
        finally {GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
        int n=original.remaining()/19;var expected=new float[n][12];var metadata=new int[n][7];
        for(int i=0;i<n;i++) {
            int p=i*19;float marker=Float.intBitsToFloat(original.get(p+1));
            if(marker==-9876543f) {
                var matrix=GpuModelTransforms.resolve((int)Float.intBitsToFloat(original.get(p)),new org.joml.Matrix4f());
                for(int c=0;c<4;c++)for(int r=0;r<3;r++)expected[i][c*3+r]=matrix.get(c,r);
            } else for(int c=0;c<12;c++)expected[i][c]=Float.intBitsToFloat(original.get(p+c));
            for(int c=0;c<7;c++)metadata[i][c]=original.get(p+12+c);
        }
        boolean[] used=new boolean[n];double previousDistance=Double.POSITIVE_INFINITY;
        for(int i=0;i<n;i++) {
            int p=i*19;double best=Double.POSITIVE_INFINITY;int match=-1;
            float x=Float.intBitsToFloat(actual.get(p+9)),y=Float.intBitsToFloat(actual.get(p+10)),z=Float.intBitsToFloat(actual.get(p+11));
            double distance=x*x+y*y+z*z;require(distance<=previousDistance+0.002,"GPU sort order changed");previousDistance=distance;
            for(int j=0;j<n;j++)if(!used[j]) {
                if(Math.abs(expected[j][9]-x)>0.0001 || Math.abs(expected[j][10]-y)>0.0001 || Math.abs(expected[j][11]-z)>0.0001)continue;
                boolean same=true;for(int c=0;c<7;c++)if(metadata[j][c]!=actual.get(p+12+c)){same=false;break;}if(!same)continue;
                double error=0;for(int c=0;c<12;c++)error=Math.max(error,Math.abs(expected[j][c]-Float.intBitsToFloat(actual.get(p+c))));
                if(error<best){best=error;match=j;}
            }
            require(match>=0 && best<0.0001,"GPU matrix differs from CPU by "+best+" at instance "+i);used[match]=true;matrixError=Math.max(matrixError,best);
        }
        verifiedDraws++;verifiedInstances+=n;
    }

    private static Map<String,Object> summary() {
        require(!samples.isEmpty(),"No frame samples");var sorted=samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double average=Arrays.stream(sorted).average().orElseThrow();int lows=Math.max(1,sorted.length/100);
        double low=Arrays.stream(sorted,sorted.length-lows,sorted.length).average().orElseThrow();
        return Map.of("frames",sorted.length,"meanMs",average,"fps",1000/average,"onePercentLowFps",1000/low,"p95Ms",sorted[(int)(sorted.length*.95)],"p99Ms",sorted[(int)(sorted.length*.99)],"worldGpuMs",gpuSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static void finish(Throwable error) {
        if(done)return;done=true;report.put("passed",error==null);if(error!=null){report.put("error",error.toString());error.printStackTrace();}
        try {Path path=Path.of(System.getProperty("arcadium.optimizationsBeta.output"));Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception failure){failure.printStackTrace();}Minecraft.getInstance().stop();
    }

    private static final class FrozenPlayer extends RemoteClientPlayerEntity {
        FrozenPlayer(net.minecraft.world.World world,GameProfile profile){super(world,profile);}
        @Override public void tick() { }
    }
}
