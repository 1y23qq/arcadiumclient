// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class NextRenderingQa {
    private static int phase,ticks,wait,scenario=-1;
    private static boolean done;
    private static long started,last;
    private static final boolean PREVIOUS=Boolean.getBoolean("arcadium.nextRendering.previousBinary");
    private static final boolean AUTO_BUDGET=Boolean.getBoolean("arcadium.nextRendering.autoBudget");
    private static final String[] CASES=PREVIOUS?new String[]{"normal-a","previous-a","normal-b","previous-b"}:AUTO_BUDGET?new String[]{"normal-a","players-all-a","normal-b","players-all-b"}:switch(System.getProperty("arcadium.nextRendering.focus","")){
        case "players" -> new String[]{"normal-a","players-a","players-all-a"};
        case "profile" -> new String[]{"players-all-a"};
        case "scene-verify" -> new String[]{"latest-a","retained-a"};
        case "retained" -> new String[]{"latest-a","retained-a","latest-b","retained-b"};
        case "equipment" -> new String[]{"latest-a","equipment-a","latest-b","equipment-b"};
        case "compat" -> new String[]{"normal-a","players-a","skin-resource","budget","fault"};
        default -> new String[]{"normal-a","previous-a","names-a","players-a","players-all-a","normal-b","previous-b","names-b","players-b","players-all-b"};
    };
    private static final List<Double> samples=new ArrayList<>();
    private static final List<Double> worldSubmit=new ArrayList<>(),gpuTimes=new ArrayList<>();
    private static final int[] gpuQueries=new int[64];
    private static int gpuCursor;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final List<int[]> golden=new ArrayList<>();
    private static boolean goldenComplete;
    private static int verifiedDraws,verifiedMatrices;
    private static double maximumMatrixError;
    private static String validationFailure;
    private static boolean reloaded;
    private static long budgetParts,budgetNames;
    private static boolean budgetMeasured;
    public static boolean modelFaultRequested,nameFaultRequested,modelFaultInjected,nameFaultInjected;
    public static boolean enabled(){return System.getProperty("arcadium.nextRendering.output")!=null;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public static void tick(){
        if(done)return;var mc=Minecraft.getInstance();wait++;
        try{
            require(RetainedSceneQa.failure==null,RetainedSceneQa.failure==null?"":RetainedSceneQa.failure);
            require(validationFailure==null,validationFailure==null?"":validationFailure);
            require(++ticks<9000,"Next rendering QA timed out");
            if(phase==0&&ticks>60&&mc.screen!=null&&mc.screen.getClass().getSimpleName().contains("MainMenu")){
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                Skin3DConfigReset();
                BetaOptimizationsConfig.setGpuModelTransforms(false);if(!PREVIOUS){BetaOptimizationsConfig.setGpuPlayerPipeline(false);BetaOptimizationsConfig.setGpuNameTags(false);BetaOptimizationsConfig.setGpuEquipmentPipeline(false);}BetaOptimizationsConfig.setRetainedHudBatches(false);BetaOptimizationsConfig.setWorldRenderScale(false);
                mc.options.pauseOnUnfocus=false;mc.options.vsync=false;mc.options.viewDistance=4;mc.options.fpsLimit=260;mc.options.cloudRenderMode=0;mc.options.perspective=0;Display.setVSyncEnabled(false);
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                if(Boolean.getBoolean("arcadium.nextRendering.classicGlint")){
                    Class<?> type=Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");Object config=type.getMethod("instance").invoke(null),items=type.getField("items").get(config);var field=items.getClass().getField("armorGlint");
                    for(Object value:field.getType().getEnumConstants())if(((Enum<?>)value).name().equals("VANILLA"))field.set(items,value);
                }
                if(System.getProperty("arcadium.nextRendering.focus","").equals("compat")){
                    for(String id:List.of("gpuEquipmentPipeline","retainedNameTagScenes","gpuPlayerPipeline","gpuNameTags","minimumGpuLabels","gpuModelTransforms","minimumPlayers","retainedHudBatches","worldRenderScale","worldResolution")){
                        var property=BetaOptimizationsConfig.INSTANCE.getTree().getProp(id);require(property!=null&&property.getMetadata("visualizer")!=null,"Beta setting missing from the GUI: "+id);
                    }
                    org.polyfrost.oneconfig.api.ui.v1.OneConfigUI.open(org.polyfrost.oneconfig.internal.ui.navigation.graph.OptimizationsRoute.INSTANCE);phase=10;wait=0;
                }else startWorld(mc);
            }else if(phase==10&&wait>35){
                capture("beta-player-settings.png");org.polyfrost.oneconfig.internal.ui.shell.ShellState.INSTANCE.getSelectedCategories().put("arcadium-optimizations-beta.json","Name Tags");phase=11;wait=0;
            }else if(phase==11&&wait>25){
                capture("beta-name-settings.png");report.put("allSettingsInBetaGui",true);mc.openScreen(null);startWorld(mc);
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&wait>70){
                mc.openScreen(null);mc.player.abilities.flying=true;
                mc.player.updatePositionAndAngles(0,19,-10,0,9);mc.player.lastYaw=mc.player.yaw;mc.player.lastPitch=mc.player.pitch;
                mc.world.setTimeOfDay(6000);
                int n=Integer.getInteger("arcadium.nextRendering.players",400);
                if(!PREVIOUS)verifyPoseMath(mc);
                var renderer=mc.getEntityRenderDispatcher().getRenderer(mc.player);
                if(!PREVIOUS)report.put("activeLayers",((io.github.arcadiumclient.runtime.mixin.client.access.LivingPipelineAccess)renderer).arcadium$layers().stream().map(layer->layer.getClass().getName()).toList());
                for(int i=0;i<n;i++){
                    var p=new Frozen(mc.world,new GameProfile(new UUID(0x7143000000000000L,i+1),"PacketQA"+i));
                    p.updatePositionAndAngles((i%20-9.5)*1.35,16,3+i/20*1.35,180+i%35,15+i%17);p.lastYaw=p.yaw;p.lastPitch=p.pitch;
                    p.bodyYaw=p.lastBodyYaw=p.yaw;p.headYaw=p.lastHeadYaw=p.yaw+15;p.walkAnimationProgress=2+i*.1f;p.walkAnimationSpeed=p.lastWalkAnimationSpeed=.6f;p.ticks=100+i%20;
                    if(Boolean.getBoolean("arcadium.nextRendering.poses")){
                        p.setSneaking(i%4==0);p.attackAnimationProgress=p.lastAttackAnimationProgress=(i%7)/7f;
                    }
                    mc.world.forceEntity(30000+i,p);
                    if(Boolean.getBoolean("arcadium.nextRendering.gear")){
                        p.setEquipment(0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
                        p.setEquipment(1,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
                        p.setEquipment(2,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
                        p.setEquipment(3,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE));
                        p.setEquipment(4,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
                        if(Boolean.getBoolean("arcadium.nextRendering.enchanted"))for(int slot=1;slot<=4;slot++)p.getEquipment(slot).addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING,1);
                        if(Boolean.getBoolean("arcadium.nextRendering.leather")){
                            var armor=new net.minecraft.item.Item[]{net.minecraft.item.Items.LEATHER_BOOTS,net.minecraft.item.Items.LEATHER_LEGGINGS,net.minecraft.item.Items.LEATHER_CHESTPLATE,net.minecraft.item.Items.LEATHER_HELMET};
                            for(int slot=1;slot<=4;slot++){var stack=new net.minecraft.item.ItemStack(armor[slot-1]);((net.minecraft.item.ArmorItem)stack.getItem()).setColor(stack,0x245CA8);if(Boolean.getBoolean("arcadium.nextRendering.enchanted"))stack.addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING,1);p.setEquipment(slot,stack);}
                        }
                    }
                }
                report.put("players",n);report.put("gpu",GL11.glGetString(GL11.GL_RENDERER));report.put("resolution",mc.width+"x"+mc.height);phase=2;wait=0;
            }else if(phase==2&&wait>100){phase=3;next();}
            else if(phase==3&&System.nanoTime()-started>Long.getLong("arcadium.nextRendering.caseSeconds",9)*1_000_000_000L){
                report.put(CASES[scenario],summary());
                if(CASES[scenario].startsWith("latest")||CASES[scenario].startsWith("equipment"))report.put(CASES[scenario]+"-diagnostics",List.of(GpuModelTransforms.status(),GpuPlayerPipeline.status(),GpuEquipmentPipeline.status(),RetainedNameTags.status()));
                if(CASES[scenario].equals("normal-a"))capture("normal.png");
                if(CASES[scenario].equals("names-a")){if(!AUTO_BUDGET)require(GpuNameTags.packetsDrawn>0,"GPU name tags were not used: "+GpuNameTags.status());capture("gpu-names.png");}
                if(CASES[scenario].equals("combined-a"))capture("combined.png");
                if(CASES[scenario].equals("players-a")){require(GpuPlayerPipeline.playersDrawn>0,"Full player pipeline was not used: "+GpuPlayerPipeline.status());capture("gpu-players.png");}
                if(CASES[scenario].equals("players-all-a"))capture("gpu-players-and-names.png");
                if(CASES[scenario].equals("latest-a"))capture("latest-baseline.png");
                if(CASES[scenario].equals("retained-a")){if(Boolean.getBoolean("arcadium.nextRendering.defaultBudgets"))require(RetainedNameTags.reused==0&&GpuNameTags.packetsDrawn==0,"Small scene used the retained GPU path");else require(RetainedNameTags.reused>0,"No name tag scenes reused: "+RetainedNameTags.status());capture("retained-names.png");}
                if(CASES[scenario].equals("equipment-a")){require(GpuEquipmentPipeline.partsQueued>0,"GPU equipment path was never used: "+GpuEquipmentPipeline.status());capture("gpu-equipment.png");}
                if(CASES[scenario].equals("skin-resource")){
                    require(Features062Qa.voxelDraws>0,"Voxel skins stopped rendering");require(GpuPlayerPipeline.rejectedModel>0,"Detailed skin players did not use the original path");
                    report.put("voxelDraws",Features062Qa.voxelDraws);report.put("voxelMeshCompiles",Features062Qa.meshCompiles);report.put("resourceReload",reloaded);capture("gpu-with-skins.png");
                }
                if(CASES[scenario].equals("budget")){
                    require(GpuModelTransforms.offloadedParts==budgetParts,"Few-player budget still used compute");require(GpuNameTags.packetsDrawn==budgetNames,"Few-label budget still used multi-draw");report.put("smallSceneBudget",true);
                }
                if(CASES[scenario].equals("fault")){
                    require(modelFaultInjected&&nameFaultInjected,"Failure injection did not run");require(GpuModelTransforms.cpuRecoveries>0&&GpuNameTags.cpuRecoveries>0,"Pending GPU work did not recover to CPU");
                    report.put("modelFailureRecovery",true);report.put("nameFailureRecovery",true);capture("after-gpu-fallback.png");
                }
                next();
            }
            if(phase==3&&CASES[scenario].equals("skin-resource")&&!reloaded&&System.nanoTime()-started>4_000_000_000L){mc.reloadResources();reloaded=true;}
            if(phase==3&&CASES[scenario].equals("budget")&&!budgetMeasured&&System.nanoTime()-started>2_000_000_000L){budgetParts=GpuModelTransforms.offloadedParts;budgetNames=GpuNameTags.packetsDrawn;budgetMeasured=true;report.put("budgetVisibleLabels",GpuNameTags.visibleLabels);}
            if(mc.world!=null){mc.world.setTimeOfDay(6000);if(RetainedSceneQa.enabled()&&phase==3){RetainedSceneQa.tick(mc);mc.player.yaw=(float)Math.sin(ticks*.013)*18;mc.player.pitch=9+(float)Math.cos(ticks*.01)*7;mc.player.lastYaw=mc.player.yaw;mc.player.lastPitch=mc.player.pitch;}}
        }catch(Throwable error){finish(error);}
    }
    private static void Skin3DConfigReset(){io.github.arcadiumclient.runtime.client.features.Skin3DConfig.INSTANCE.chooseEnabled(false);}
    private static void startWorld(Minecraft mc){mc.startGame("arcadium-next-rendering","Next Rendering QA",new WorldSettings(816,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;wait=0;}
    private static void next(){
        if(++scenario==CASES.length){
            if(!PREVIOUS){
            report.put("namePackets",GpuNameTags.packetsDrawn);report.put("gpuVertices",GpuNameTags.verticesReused);report.put("geometryUploads",GpuNameTags.geometryUploads);report.put("multiDraws",GpuNameTags.multiDraws);report.put("cpuRecoveries",GpuNameTags.cpuRecoveries);
            report.put("compactPlayers",GpuPlayerPipeline.playersDrawn);report.put("compactParts",GpuPlayerPipeline.partsQueued);report.put("playerStateFallback",GpuPlayerPipeline.rejectedState);report.put("playerLayerFallback",GpuPlayerPipeline.rejectedLayers);report.put("playerModelFallback",GpuPlayerPipeline.rejectedModel);
            report.put("goldenMatrices",golden.size());report.put("verifiedAgainstNativeMatrices",verifiedMatrices);report.put("maximumNativeMatrixError",maximumMatrixError);
            report.put("equipmentStates",GpuEquipmentPipeline.statesQueued);report.put("equipmentParts",GpuEquipmentPipeline.partsQueued);report.put("glintStates",GpuEquipmentPipeline.glintStates);report.put("leatherStates",GpuEquipmentPipeline.leatherStates);report.put("equipmentFallbacks",GpuEquipmentPipeline.fallbacks);
            report.put("retainedLabels",RetainedNameTags.reused);report.put("retainedSceneChecks",RetainedSceneQa.checks);report.put("retainedSceneMatrixError",RetainedSceneQa.error);report.put("retainedOptionsTested",RetainedSceneQa.testedOptions());if(RetainedSceneQa.enabled())require(RetainedSceneQa.checks>4000,"Too few scene comparisons");
            if(!AUTO_BUDGET&&!Set.of("profile","retained","scene-verify").contains(System.getProperty("arcadium.nextRendering.focus","")))require(verifiedMatrices>0,"No GPU player matrices were checked against the original renderer");
            else if(AUTO_BUDGET)require(GpuModelTransforms.offloadedParts==0&&GpuNameTags.packetsDrawn==0,"Small scene default budget used GPU work");
            }
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error after packet test");finish(null);return;
        }
        String mode=CASES[scenario];
        BetaOptimizationsConfig.setGpuModelTransforms(mode.startsWith("previous")||mode.startsWith("combined"));BetaOptimizationsConfig.setMinimumPlayers(1);
        if(!PREVIOUS){
            BetaOptimizationsConfig.setGpuPlayerPipeline(mode.startsWith("players")||mode.startsWith("latest")||mode.startsWith("retained")||mode.startsWith("equipment")||mode.equals("skin-resource")||mode.equals("budget")||mode.equals("fault"));
            BetaOptimizationsConfig.setGpuEquipmentPipeline(mode.startsWith("equipment"));BetaOptimizationsConfig.setRetainedNameTagScenes(mode.startsWith("retained")||mode.equals("skin-resource")||mode.equals("budget")||mode.equals("fault"));
            BetaOptimizationsConfig.setGpuNameTags(mode.startsWith("names")||mode.startsWith("combined")||mode.startsWith("players-all")||mode.startsWith("latest")||mode.startsWith("retained")||mode.startsWith("equipment")||mode.equals("skin-resource")||mode.equals("budget")||mode.equals("fault"));BetaOptimizationsConfig.setMinimumGpuLabels(1);
        }
        if(mode.equals("skin-resource"))try{
            var seed=Features062Qa.class.getDeclaredMethod("seedSkin",Minecraft.class);seed.setAccessible(true);seed.invoke(null,Minecraft.getInstance());
            io.github.arcadiumclient.runtime.client.features.Skin3DConfig.INSTANCE.chooseEnabled(true);Minecraft.getInstance().options.perspective=1;Features062Qa.voxelDraws=Features062Qa.meshCompiles=0;
        }catch(Exception error){throw new IllegalStateException(error);}
        if(mode.equals("budget")){
            BetaOptimizationsConfig.setMinimumPlayers(128);BetaOptimizationsConfig.setMinimumGpuLabels(128);budgetMeasured=false;
            int visible=0;for(var player:Minecraft.getInstance().world.players)if(player instanceof Frozen)player.setInvisible(visible++>=8);
        }
        if(mode.equals("fault")){for(var player:Minecraft.getInstance().world.players)if(player instanceof Frozen)player.setInvisible(false);modelFaultRequested=nameFaultRequested=true;}
        if(AUTO_BUDGET||Boolean.getBoolean("arcadium.nextRendering.defaultBudgets")){BetaOptimizationsConfig.setMinimumPlayers(24);BetaOptimizationsConfig.setMinimumGpuLabels(16);}
        samples.clear();worldSubmit.clear();gpuTimes.clear();started=System.nanoTime();last=0;
    }
    public static void frame(){if(done||phase!=3)return;long now=System.nanoTime();if(last!=0&&now-started>3_000_000_000L)samples.add((now-last)/1e6);last=now;if(!golden.isEmpty())goldenComplete=true;}
    public static boolean captureGolden(){return enabled()&&!done&&phase==3&&(CASES[scenario].equals("normal-a")||CASES[scenario].equals("latest-a"))&&!goldenComplete&&System.nanoTime()-started>1_000_000_000L;}
    public static void storeGolden(IntBuffer source){for(int p=source.position();p<source.limit();p+=19){int[] entry=new int[19];for(int i=0;i<19;i++)entry[i]=source.get(p+i);golden.add(entry);}}
    public static boolean validateDense(){return enabled()&&!done&&validationFailure==null&&phase==3&&CASES[scenario].equals("players-a")&&verifiedDraws<2;}
    public static boolean validateEquipment(){return enabled()&&!done&&validationFailure==null&&phase==3&&CASES[scenario].equals("equipment-a")&&verifiedDraws<2;}
    public static void verifyEquipmentGpu(int buffer,IntBuffer original){
        int previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);IntBuffer actual=BufferUtils.createIntBuffer(original.remaining());
        try{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffer);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,actual);}finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
        List<int[]> output=new ArrayList<>();for(int p=0;p<actual.limit();p+=19){int[] row=new int[19];for(int i=0;i<19;i++)row[i]=actual.get(p+i);output.add(row);}
        for(int p=original.position();p<original.limit();p+=19){
            if(Float.intBitsToFloat(original.get(p+1))!=-9876543f)continue;
            var matrix=GpuModelTransforms.resolve((int)Float.intBitsToFloat(original.get(p)),new org.joml.Matrix4f());int[] expected=new int[19];
            for(int c=0;c<4;c++)for(int r=0;r<3;r++)expected[c*3+r]=Float.floatToRawIntBits(matrix.get(c,r));for(int i=12;i<19;i++)expected[i]=original.get(p+i);
            double nativeError=match(expected,golden),gpuError=match(expected,output);
            if(nativeError>=.001||gpuError>=.001){report.put("failedEquipmentInstance",expected);validationFailure="Armor differs from native/GPU reference: "+nativeError+" / "+gpuError;return;}
            verifiedMatrices++;maximumMatrixError=Math.max(maximumMatrixError,Math.max(nativeError,gpuError));
        }
        verifiedDraws++;
    }
    private static double match(int[] expected,List<int[]> candidates){
        double best=Double.POSITIVE_INFINITY;float x=Float.intBitsToFloat(expected[9]),y=Float.intBitsToFloat(expected[10]),z=Float.intBitsToFloat(expected[11]);
        for(int[] entry:candidates){
            if(Math.abs(Float.intBitsToFloat(entry[9])-x)>.001||Math.abs(Float.intBitsToFloat(entry[10])-y)>.001||Math.abs(Float.intBitsToFloat(entry[11])-z)>.001)continue;
            boolean same=true;for(int i=12;i<19;i++)if(entry[i]!=expected[i]){same=false;break;}if(!same)continue;
            double error=0;for(int i=0;i<12;i++)error=Math.max(error,Math.abs(Float.intBitsToFloat(entry[i])-Float.intBitsToFloat(expected[i])));best=Math.min(best,error);
        }
        return best;
    }
    public static int beginGpuTime(){
        if(phase!=3||done)return 0;
        int slot=gpuCursor++%gpuQueries.length;
        if(gpuQueries[slot]==0)gpuQueries[slot]=GL15.glGenQueries();
        else if(GL15.glGetQueryObjecti(gpuQueries[slot],GL15.GL_QUERY_RESULT_AVAILABLE)!=0){
            if(System.nanoTime()-started>3_000_000_000L)gpuTimes.add(GL33.glGetQueryObjecti64(gpuQueries[slot],GL15.GL_QUERY_RESULT)/1e6);
        }else return 0;
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,gpuQueries[slot]);return gpuQueries[slot];
    }
    public static void endGpuTime(int query,long cpuStart){
        if(query!=0)GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        if(phase==3&&!done&&System.nanoTime()-started>3_000_000_000L)worldSubmit.add((System.nanoTime()-cpuStart)/1e6);
    }
    public static void verifyGpu(int buffer,int words){
        int previous=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);IntBuffer actual=BufferUtils.createIntBuffer(words);
        try{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffer);GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,actual);}finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,previous);}
        for(int p=0;p<words;p+=19){
            float x=Float.intBitsToFloat(actual.get(p+9)),y=Float.intBitsToFloat(actual.get(p+10)),z=Float.intBitsToFloat(actual.get(p+11));double best=Double.POSITIVE_INFINITY;
            for(var expected:golden){
                if(Math.abs(Float.intBitsToFloat(expected[9])-x)>.001||Math.abs(Float.intBitsToFloat(expected[10])-y)>.001||Math.abs(Float.intBitsToFloat(expected[11])-z)>.001)continue;
                boolean meta=true;for(int i=12;i<19;i++)if(expected[i]!=actual.get(p+i)){meta=false;break;}if(!meta)continue;
                double error=0;for(int i=0;i<12;i++)error=Math.max(error,Math.abs(Float.intBitsToFloat(expected[i])-Float.intBitsToFloat(actual.get(p+i))));best=Math.min(best,error);
            }
            if(best>=.001){
                int[] observed=new int[19];for(int i=0;i<19;i++)observed[i]=actual.get(p+i);
                final float xx=x,yy=y,zz=z;var nearest=golden.stream().sorted(Comparator.comparingDouble(e->Math.abs(Float.intBitsToFloat(e[9])-xx)+Math.abs(Float.intBitsToFloat(e[10])-yy)+Math.abs(Float.intBitsToFloat(e[11])-zz))).limit(4).toList();
                report.put("failedObservedInstance",observed);report.put("nearestNativeInstances",nearest);validationFailure="GPU player matrix differs from original renderer by "+best+" at "+p/19;return;
            }
            maximumMatrixError=Math.max(maximumMatrixError,best);verifiedMatrices++;
        }
        verifiedDraws++;
    }
    private static void verifyPoseMath(Minecraft mc){
        PlayerPoseOptions.refresh();Random random=new Random(818);float[] values=new float[6];int checks=0;
        for(boolean slim:new boolean[]{false,true}){
            var model=new net.minecraft.client.render.model.entity.PlayerModel(0,slim);var rest=new net.minecraft.client.render.model.entity.PlayerModel(0,slim);
            var actual=body(model);var defaults=body(rest);
            for(int sample=0;sample<256;sample++){
                float walk=random.nextFloat()*30,speed=random.nextFloat(),age=random.nextFloat()*1000,yaw=random.nextFloat()*180-90,pitch=random.nextFloat()*120-60,swing=random.nextFloat();
                model.attackAnimationProgress=swing;model.itemInLeftHand=sample%2;model.itemInRightHand=sample%3==0?3:sample%2;model.sneaking=sample%5==0;model.riding=sample%7==0;model.aimingBow=sample%11==0;
                model.setupAnimation(walk,speed,age,yaw,pitch,.0625f,mc.player);
                for(int i=0;i<12;i++){
                    var source=actual[i];var base=defaults[i];PlayerPoseMath.part(walk,speed,age,yaw,pitch,swing,model.itemInLeftHand,model.itemInRightHand,model.sneaking,model.riding,model.aimingBow,PlayerPoseMath.BONES[i],base.x,base.y,base.z,PlayerPoseOptions.flags(),values);
                    float[] expected={source.x,source.y,source.z,source.rotationX,source.rotationY,source.rotationZ};
                    for(int v=0;v<6;v++)require(Math.abs(values[v]-expected[v])<.00002,"Pose math differs at part "+i+" value "+v+": "+values[v]+" vs "+expected[v]);checks++;
                }
            }
        }
        report.put("nativePoseChecks",checks);
    }
    private static net.minecraft.client.render.model.ModelPart[] body(net.minecraft.client.render.model.entity.PlayerModel model){return new net.minecraft.client.render.model.ModelPart[]{model.head,model.body,model.rightArm,model.leftArm,model.rightLeg,model.leftLeg,model.hat,model.leftPants,model.rightPants,model.leftSleeve,model.rightSleeve,model.jacket};}
    private static Map<String,Object> summary(){
        var values=samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();require(values.length>0,"No samples");
        int low=Math.max(1,values.length/100);
        return Map.of("fps",1000/Arrays.stream(values).average().orElseThrow(),"frames",values.length,"onePercentLowFps",1000/Arrays.stream(values,values.length-low,values.length).average().orElseThrow(),"p99Ms",values[(int)(values.length*.99)],"worldSubmitMs",worldSubmit.stream().mapToDouble(Double::doubleValue).average().orElse(0),"worldGpuMs",gpuTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }
    private static void capture(String name)throws Exception{
        var mc=Minecraft.getInstance();int width=mc.width,height=mc.height;
        int previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        IntBuffer pixels=BufferUtils.createIntBuffer(width*height);
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,mc.getRenderTarget().frameBufferId);GL11.glReadPixels(0,0,width,height,GL12.GL_BGRA,GL12.GL_UNSIGNED_INT_8_8_8_8_REV,pixels);}
        finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);}
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);int[] row=new int[width];
        for(int y=0;y<height;y++){pixels.position((height-1-y)*width);pixels.get(row);image.setRGB(0,y,width,1,row,0,width);}
        ImageIO.write(image,"png",Path.of(System.getProperty("arcadium.nextRendering.output")).getParent().resolve(name).toFile());
    }
    private static void finish(Throwable error){
        if(done)return;done=true;report.put("passed",error==null);if(error!=null){report.put("error",error.toString());error.printStackTrace();}
        try{Files.writeString(Path.of(System.getProperty("arcadium.nextRendering.output")),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}
        Minecraft.getInstance().stop();
    }
    private static class Frozen extends RemoteClientPlayerEntity{
        private final int fixedAge;
        Frozen(net.minecraft.world.World world,GameProfile profile){super(world,profile);fixedAge=100+(int)(profile.getId().getLeastSignificantBits()-1)%20;}
        @Override public void tick(){ticks=fixedAge;}
    }
}
