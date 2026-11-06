// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.Skin3DConfig;
import io.github.arcadiumclient.runtime.client.skins.UploadedSkinTexture;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.Future;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.resource.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class CeraAdmission072Qa {
    private static final Identifier GLOW = new Identifier("arcadium_qa", "textures/entity/glow.png"), ORDINARY = new Identifier("arcadium_qa", "textures/entity/ordinary.png"), SECOND = new Identifier("arcadium_qa", "textures/entity/ordinary_two.png");
    private static final String PACK = "Arcadium-Cera-Admission-QA.zip";
    private static final String RESIDENT_MODE = System.getProperty("arcadium.ceraAdmission072.residentBodyMode", "off");
    private static final long[][][] residentActors = new long[3][3][2];
    private static final long[] residentBefore = new long[2];
    private static boolean residentReload;
    private static int menuFrames;
    private static String terminalFailure;
    private static final boolean BASELINE = Boolean.getBoolean("arcadium.ceraAdmission072.baseline071");
    private static final String BASELINE_DIRECTORY = System.getProperty("arcadium.ceraAdmission072.baselineDirectory");
    private static final Map<String,Object> report = new LinkedHashMap<>();
    private static final Actor[] actors = new Actor[3];
    private static final long[][][] traces = new long[3][3][7];
    private static final Map<String,Long>[] submissions = new Map[]{null,new LinkedHashMap<>(),new LinkedHashMap<>()};
    private static final long[] before = new long[5];
    private static final Set<String> analyticTextures = new TreeSet<>();
    private static final List<String> analyticOrder = new ArrayList<>();
    private static int phase,ticks,wait,warm,scenario,mode,voxelDepth,analyticDepth,preparationDepth,rendering = -1;
    private static boolean done;
    private static boolean configured, configuredGpu, configuredSkin,cameraPrepared;
    private static Future<?> environment;
    private static String failure;
    private static BufferedImage base,second,emissive;
    private static volatile String nameEscape;
    private static boolean nameProbeComplete;
    public static String residentBodyMode(){return RESIDENT_MODE;}
    public static boolean residentBodyEnabled(){return !RESIDENT_MODE.equals("off");}
    public static boolean observingResidentBody(){return residentBodyEnabled()&&RESIDENT_MODE.equals("active")&&enabled()&&!done&&!BASELINE&&phase==2&&mode==2;}
    private static long[] residentQueued(){return new long[]{metric("GpuResidentBodies","actorsQueued"),metric("GpuResidentBodies","partsQueued")};}
    private static long bodyCount(String method){try{return ((Number)Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuResidentBodies").getMethod(method).invoke(null)).longValue();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public static boolean enabled(){return System.getProperty("arcadium.ceraAdmission072.output")!=null;}

    public static void tick(){
        if(!enabled()||done)return;
        try{
            Minecraft mc=Minecraft.getInstance();wait++;require(++ticks<6000,"Cera QA timed out");
            if(failure!=null)throw new IllegalStateException(failure);
            if(phase==0&&wait>65&&mc.screen!=null){
                require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"Only the owned fake-account fixture is allowed");
                require(!Boolean.getBoolean("arcadium.forge.prepared"),"Cera material qualification requires native non-Forge submission");
                if(residentBodyEnabled()){
                    require(!BASELINE&&List.of("control","active").contains(RESIDENT_MODE),"Resident Cera mode is restricted to the owned current fake fixture");
                    require(!Boolean.getBoolean("arcadium.modernGlint072.experimental")&&RESIDENT_MODE.equals(System.getProperty("arcadium.residentBodies072.mode")),"Resident mode/property mismatch or Modern experimental was enabled");
                    report.put("privateResidentBodyMode",RESIDENT_MODE);
                }
                io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus=false;mc.options.vsync=false;mc.options.fpsLimit=120;mc.options.viewDistance=3;
                mc.options.entityShadows=false;mc.options.cloudRenderMode=0;mc.options.viewBobbing=false;mc.options.hideGui=true;
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                selectVanillaGlint();makePack(mc);configure(true,false);
                report.put("purpose","Owned synthetic emissive pack/native callback and image qualification; no FPS claim");
                report.put("frozen071Control",BASELINE);
                report.put("actorTraceColumns",List.of("rendererCalls","compactBodies","nativeGpuBodyParts","gpuEquipmentParts","analyticGpuVoxels","nativeGpuVoxels","originalVoxelPathCalls"));
                mc.startGame("arcadium-cera-admission072","Cera admission QA",new WorldSettings(720729,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());
                phase=1;wait=0;
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&wait>120){
                if(environment==null){environment=mc.getServer().executeTask(()->{
                    var world=mc.getServer().getWorld(0);world.getGameRules().set("doMobSpawning","false");world.getGameRules().set("doDaylightCycle","false");world.getGameRules().set("randomTickSpeed","0");world.setTimeOfDay(6000);
                    world.getData().setRaining(false);world.getData().setThundering(false);
                    for(var entity:new ArrayList<>(world.entities))if(!(entity instanceof net.minecraft.entity.living.player.PlayerEntity))world.removeEntityNow(entity);
                    return true;
                });return;}
                if(!environment.isDone()||mc.world.entities.stream().anyMatch(entity->!(entity instanceof net.minecraft.entity.living.player.PlayerEntity)))return;
                environment.get();report.put("authoritativeNoMobWorld",true);
                if(!cameraPrepared){mc.openScreen(null);mc.player.abilities.flying=true;mc.player.updatePositionAndAngles(0,6,-10,0,8);cameraPrepared=true;return;}
                if(!CeraAdmission072Readiness.ready(mc))return;
                seedSkins(mc);
                for(int i=0;i<3;i++){
                    Actor actor=new Actor(mc,new GameProfile(new UUID(0x7207290000000000L,i+1),"CeraMaterial"+i),i);
                    actor.updatePositionAndAngles((i-1)*2.4,4,3,180,0);actor.getSyncedData().update(10,(byte)127);
                    for(PlayerModelPart part:PlayerModelPart.values())require(actor.isModelPartVisible(part),"Outer skin part is hidden");
                    actor.setEquipment(1,enchanted(Items.DIAMOND_BOOTS));
                    actor.setEquipment(3,enchanted(i==0?Items.IRON_CHESTPLATE:Items.DIAMOND_CHESTPLATE));
                    mc.world.forceEntity(729000+i,actor);actors[i]=actor;
                }
                freeze(mc);phase=2;warm=0;
                report.put("gpu",GL11.glGetString(GL11.GL_RENDERER));report.put("resolution",mc.width+"x"+mc.height);
            }
        }catch(Throwable error){finish(error);}
    }

    private static ItemStack enchanted(Item item){ItemStack stack=new ItemStack(item);stack.addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING,1);return stack;}
    private static void selectVanillaGlint()throws Exception{
        Class<?> type=Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");Object config=type.getMethod("instance").invoke(null),items=type.getField("items").get(config);
        var field=items.getClass().getField("armorGlint");
        for(Object value:field.getType().getEnumConstants())if(((Enum<?>)value).name().equals("VANILLA")){field.set(items,value);return;}
        throw new IllegalStateException("Vanilla armor glint is unavailable");
    }
    private static void configure(boolean gpu,boolean skin){
        gpu &= !BASELINE;
        if(configured&&configuredGpu==gpu&&configuredSkin==skin)return;
        try{
            for(var method:BetaOptimizationsConfig.class.getMethods())if(java.lang.reflect.Modifier.isStatic(method.getModifiers())&&method.getName().startsWith("set")&&Arrays.equals(method.getParameterTypes(),new Class<?>[]{boolean.class}))method.invoke(null,false);
            beta("GpuPlayerPipeline",gpu);beta("GpuEquipmentPipeline",gpu);beta("MinimumPlayers",1);beta("GpuVoxelSkinLayers",gpu&&skin);beta("CachedNameMasking",true);
            Skin3DConfig.INSTANCE.chooseEnabled(skin);
            try{Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline").getField("enabled").setBoolean(null,gpu&&skin);}catch(ClassNotFoundException unavailable){if(!BASELINE)throw unavailable;}
            configured=true;configuredGpu=gpu;configuredSkin=skin;
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void beta(String name,Object value)throws ReflectiveOperationException{try{BetaOptimizationsConfig.class.getMethod("set"+name,value instanceof Boolean?boolean.class:int.class).invoke(null,value);}catch(NoSuchMethodException unavailable){if(!BASELINE)throw unavailable;}}
    private static long metric(String name,String field){try{return Class.forName("io.github.arcadiumclient.runtime.client.optimizations."+name).getField(field).getLong(null);}catch(ReflectiveOperationException unavailable){if(BASELINE)return 0;throw new IllegalStateException(unavailable);}}
    private static long[] metrics(){return new long[]{metric("GpuPlayerPipeline","playersDrawn"),metric("NativePlayerPosePipeline","bodyPartsQueued"),metric("GpuEquipmentPipeline","partsQueued"),metric("GpuVoxelSkinPipeline","analyticParts"),metric("GpuVoxelSkinPipeline","nativeParts")};}
    private static String status(String name){try{return String.valueOf(Class.forName("io.github.arcadiumclient.runtime.client.optimizations."+name).getMethod("status").invoke(null));}catch(ReflectiveOperationException unavailable){return "Unavailable in frozen control";}}
    private static boolean emissive(Identifier texture)throws Exception{Object manager=Minecraft.getInstance().getTextureManager(),module=manager.getClass().getMethod("cera$getEmissiveTextures").invoke(manager);return module.getClass().getMethod("emissiveTexture",Identifier.class).invoke(module,texture)!=null;}
    private static void freeze(Minecraft mc){
        for(Actor actor:actors)if(actor!=null){actor.ticks=160;actor.yaw=actor.lastYaw=actor.bodyYaw=actor.lastBodyYaw=actor.headYaw=actor.lastHeadYaw=180;actor.pitch=actor.lastPitch=0;
            actor.walkAnimationProgress=actor.walkAnimationSpeed=actor.lastWalkAnimationSpeed=actor.attackAnimationProgress=actor.lastAttackAnimationProgress=0;
            actor.lastX=actor.prevX=actor.x;actor.lastY=actor.prevY=actor.y;actor.lastZ=actor.prevZ=actor.z;}
        mc.player.yaw=mc.player.lastYaw=0;mc.player.pitch=mc.player.lastPitch=8;
        mc.player.lastX=mc.player.prevX=mc.player.x;mc.player.lastY=mc.player.prevY=mc.player.y;mc.player.lastZ=mc.player.prevZ=mc.player.z;mc.world.setTimeOfDay(6000);mc.world.getData().setRaining(false);mc.world.getData().setThundering(false);
    }

    public static void world(float delta,long deadline,Operation<Void> original){
        if(!enabled()||done||phase!=2){original.call(delta,deadline);return;}
        try{
            Minecraft mc=Minecraft.getInstance();freeze(mc);boolean skin=scenario>0;
            if(++warm<=160){configure(true,skin);original.call(.5f,deadline);return;}
            require(emissive(GLOW),"Synthetic emissive texture was not identified by the real Cera API");require(!emissive(ORDINARY)&&!emissive(SECOND),"Ordinary neighbor became emissive");
            for(int i=0;i<3;i++)require(!actors[i].removed&&mc.world.entities.contains(actors[i])&&mc.world.getEntity(729000+i)==actors[i],"Synthetic actor is not in the native world");
            var registration=CeraAdmission072Readiness.actors(mc,Arrays.asList(actors));report.put("nativeActorRegistration",registration);require(Boolean.TRUE.equals(registration.get("allRegistered")),"Synthetic actor is absent from its actual stable native entity/render section");
            BufferedImage reference=render(mc,1,false,skin,deadline,original),optimized=render(mc,2,!BASELINE,skin,deadline,original);
            int max=0,changed=0,bright=0;
            for(int y=0;y<reference.getHeight();y++)for(int x=0;x<reference.getWidth();x++){
                int a=reference.getRGB(x,y),b=optimized.getRGB(x,y);if(a!=b)changed++;
                for(int shift=0;shift<32;shift+=8)max=Math.max(max,Math.abs((a>>>shift&255)-(b>>>shift&255)));
                if((a>>>16&255)>200&&(a>>>8&255)<80&&(a&255)>130)bright++;
            }
            String baseLabel=scenario==0?"body-and-armor":scenario==1?"3d-skin":"3d-skin-reversed";String label=baseLabel+(residentReload?"-reload":"");Path dir=output().getParent();Files.createDirectories(dir);
            ImageIO.write(reference,"png",dir.resolve(label+(BASELINE?"-native071.png":"-native.png")).toFile());ImageIO.write(optimized,"png",dir.resolve(label+(BASELINE?"-native071-repeat.png":"-guarded-gpu.png")).toFile());
            Map<String,Object> result=new LinkedHashMap<>();result.put("maximumChannelError",max);result.put("changedPixels",changed);result.put("nativeBrightEmissivePixels",bright);
            result.put("settings",Map.of("minimumPlayers",1,"gpuPlayerPipeline",!BASELINE,"gpuEquipmentPipeline",!BASELINE,"gpuModelTransforms",false,"residentPlayerState",false,"skin3d",skin,"gpuVoxelSkinLayers",skin&&!BASELINE,"renderDelta",.5f,"fpsLimit",120,"renderDistance",3));
            if(residentBodyEnabled()){result.put("residentBodies",CeraResidentBody072Evidence.report());result.put("residentActorTraces",Arrays.stream(residentActors[2]).map(long[]::clone).toArray(long[][]::new));}
            result.put("nativeSubmissions",new LinkedHashMap<>(submissions[1]));result.put("guardedSubmissions",new LinkedHashMap<>(submissions[2]));
            result.put("actualAnalyticSkinBindings",new ArrayList<>(analyticTextures));
            result.put("actualAnalyticSkinOrder",new ArrayList<>(analyticOrder));
            result.put("actualAuxiliaryLayerHits",new LinkedHashMap<>(CeraAdmission072PoolEvidence.counts()));
            result.put("actualVoxelDrawSources",new LinkedHashMap<>(CeraAdmission072PoolEvidence.drawnCounts()));
            result.put("actualPreparationLeases",CeraAdmission072LeaseEvidence.counts());result.put("actualUploadFacts",CeraAdmission072LeaseEvidence.facts());
            result.put("nativeActorTraces",Arrays.stream(traces[1]).map(long[]::clone).toArray(long[][]::new));result.put("guardedActorTraces",Arrays.stream(traces[2]).map(long[]::clone).toArray(long[][]::new));report.put(label,result);progress(label+"-captured");
            require(max<=(BASELINE?0:2),"Native/GPU image differs beyond the fixed color criterion: "+max);require(bright>20,"No visible bright emissive pixels in the reference");
            for(int actor=0;actor<3;actor++)require(traces[1][actor][0]>0&&traces[2][actor][0]>0,"An actor was not rendered by native PlayerRenderer");
            if(!BASELINE){require(traces[2][0][1]==0&&traces[2][0][2]==0,"Emissive body bypassed native submission");
                require(traces[2][1][1]+traces[2][2][1]>0,"Ordinary neighbors never used compact bodies");require(traces[2][1][3]+traces[2][2][3]>0,"Ordinary neighbor armor never used compact poses");
                require(traces[2][0][3]>0,"The ordinary boots slot inherited another slot's emissive fallback");
                require(BASELINE_DIRECTORY!=null,"A matching frozen071 reference directory is required");BufferedImage frozen=ImageIO.read(Path.of(BASELINE_DIRECTORY).resolve(baseLabel+"-native071.png").toFile());
                require(frozen!=null&&frozen.getWidth()==reference.getWidth()&&frozen.getHeight()==reference.getHeight(),"Frozen071 image dimensions changed");int frozenDifferences=0;
                for(int y=0;y<frozen.getHeight();y++)for(int x=0;x<frozen.getWidth();x++)if(frozen.getRGB(x,y)!=reference.getRGB(x,y))frozenDifferences++;
                result.put("nativeVsFrozen071ChangedPixels",frozenDifferences);require(frozenDifferences==0,"Native control differs from matching frozen071 full RGBA: "+frozenDifferences);}
            for(String texture:List.of("arcadium_qa:textures/entity/glow.png","minecraft:textures/models/armor/iron_layer_1.png")){
                long nativeBase=submissions[1].getOrDefault("0:NORMAL:"+texture,0L)+submissions[1].getOrDefault("0:TRANSLUCENT:"+texture,0L);
                long guardedBase=submissions[2].getOrDefault("0:NORMAL:"+texture,0L)+submissions[2].getOrDefault("0:TRANSLUCENT:"+texture,0L);
                require(nativeBase>0&&nativeBase==guardedBase,"Actual native base submission changed: "+texture);
            }
            for(String key:List.of("0:EMISSIVE_REPLACE:arcadium_qa:textures/entity/glow_e.png","0:EMISSIVE_REPLACE:minecraft:textures/models/armor/iron_layer_1_e.png")){
                require(submissions[1].getOrDefault(key,0L)>0&&Objects.equals(submissions[1].get(key),submissions[2].get(key)),"Actual Cera submit callback count changed: "+key);
            }
            if(skin){if(!BASELINE){require(traces[2][0][4]+traces[2][0][5]==0,"Emissive voxels were suppressed into the auxiliary atlas");require(traces[2][1][4]+traces[2][1][5]+traces[2][2][4]+traces[2][2][5]>0,"Ordinary skin voxels never reached the GPU");}
                require(traces[1][0][6]>0&&traces[1][0][6]==traces[2][0][6],"Emissive original voxel invocation was lost");
                if(!BASELINE){require(analyticTextures.contains(ORDINARY.toString())&&analyticTextures.contains(SECOND.toString()),"Both different cached ordinary skins must reach analytic source-binding checks");
                    for(Identifier id:List.of(ORDINARY,SECOND)){require(CeraAdmission072PoolEvidence.counts().getOrDefault(id.toString(),0L)>0,"No actual auxiliary layer cache hit: "+id);require(CeraAdmission072PoolEvidence.drawnCounts().getOrDefault(id.toString(),0L)>0,"No actual voxel MDI draw for source: "+id);}
                    for(Identifier id:List.of(ORDINARY,SECOND)){require(CeraAdmission072LeaseEvidence.counts().getOrDefault("used:"+id,0L)>0&&CeraAdmission072LeaseEvidence.counts().getOrDefault("queued:"+id,0L)>0,"No exact single-use preparation lease for queued skin: "+id);}
                    require(CeraAdmission072PoolEvidence.counts().getOrDefault("calls:preparation",0L)>0&&CeraAdmission072PoolEvidence.counts().getOrDefault("calls:queue",0L)==0,"Actual selection did not use preparation then cached queue lease");
                    for(String error:List.of("invalidUse","queueWithoutUse","unpreparedFact","unknownFactLayout","overflow"))require(CeraAdmission072LeaseEvidence.counts().getOrDefault(error,0L)==0,"Preparation lease observer failed: "+error);
                    for(String error:List.of("wrongOwner","unknownLayout","unbalanced","depthOverflow","poolOverflow","bindingOverflow"))require(CeraAdmission072PoolEvidence.counts().getOrDefault(error,0L)==0,"Pool observer failed: "+error);
                    if(scenario==2){var previous=new ArrayList<>((List<String>)((Map<?,?>)report.get("3d-skin")).get("actualAnalyticSkinOrder"));Collections.reverse(previous);require(previous.equals(analyticOrder),"Reversed source assignment did not exercise opposite cached source order");}}}
            if(residentBodyEnabled()){
                Map<String,Object> body=(Map<String,Object>)result.get("residentBodies");
                require(Boolean.TRUE.equals(body.get("balancedScopes"))&&((Number)body.get("errors")).longValue()==0&&((Number)body.get("replayedParts")).longValue()==0,"Resident body scope/error/replay gate failed");
                if(RESIDENT_MODE.equals("active")){
                    require(residentActors[2][0][0]==0&&residentActors[2][1][0]==1&&residentActors[2][2][0]==1,"Emissive actor entered resident bodies or an ordinary actor did not use exactlyone record");
                    require(((Number)body.get("actorsQueued")).longValue()==2&&((Number)body.get("partsQueued")).longValue()>2&&((Number)body.get("actualDraws")).intValue()>0&&((Number)body.get("actualInstances")).intValue()>=2,"Resident whole-body record/actual288vertex draw never executed");
                    require(((Number)body.get("poseDispatches")).longValue()>0&&((Number)body.get("restorations")).intValue()>0,"Resident bone dispatch/state restore was not exercised");
                }else require(((Number)body.get("actorsQueued")).longValue()==0&&((Number)body.get("actualDraws")).intValue()==0,"Control source unexpectedly enabled resident bodies");
            }
            require(GL11.glGetError()==0,"OpenGL error after Cera comparison");
            result.put("validated",true);progress(label+"-validated");
            if(!BASELINE&&!nameProbeComplete){nameReplay(mc,skin,deadline,original);nameProbeComplete=true;}
            if(residentBodyEnabled()&&scenario==2&&!residentReload){
                require(bodyCount("owners")>0||RESIDENT_MODE.equals("control"),"Resident reload had no owned runtime");
                mc.reloadResources();require(bodyCount("owners")==0&&bodyCount("profiles")==0,"Native resource reload did not release resident ownership");
                report.put("residentResourceReload",Map.of("actualMinecraftReload",true,"ownersAfter",bodyCount("owners"),"profilesAfter",bodyCount("profiles")));
                seedSkins(mc);residentReload=true;warm=0;progress("resident-native-resource-reloaded");return;
            }
            if(++scenario==3)phase=3;else{configure(true,true);warm=0;}
        }catch(Throwable error){failure=error.toString();}finally{mode=0;rendering=-1;}
    }
    private static BufferedImage render(Minecraft mc,int which,boolean gpu,boolean skin,long deadline,Operation<Void> original){
        require(analyticDepth==0&&preparationDepth==0,"Voxel observer scope was unbalanced");
        mode=which;for(long[] row:traces[which])Arrays.fill(row,0);for(long[] row:residentActors[which])Arrays.fill(row,0);CeraResidentBody072Evidence.begin();submissions[which].clear();analyticTextures.clear();analyticOrder.clear();CeraAdmission072PoolEvidence.reset();CeraAdmission072LeaseEvidence.reset();configure(gpu,skin);freeze(mc);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);original.call(.5f,deadline);return capture(mc);
    }
    private static void nameReplay(Minecraft mc,boolean skin,long deadline,Operation<Void> original)throws Throwable{
        Class<?> formatter=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.EntityNameFormatting");
        var enabled=formatter.getField("resetNameCacheEnabled");boolean old=enabled.getBoolean(null),hide=mc.options.hideGui;
        long beforeCalls=metric("EntityNameFormatting","resetCalls"),beforeHits=metric("EntityNameFormatting","resetHits");
        try{
            mc.options.hideGui=false;enabled.setBoolean(null,true);render(mc,1,true,skin,deadline,original);render(mc,1,true,skin,deadline,original);
            long worldCalls=metric("EntityNameFormatting","resetCalls")-beforeCalls,worldHits=metric("EntityNameFormatting","resetHits")-beforeHits;
            require(metric("RenderHookAllocationControl","nameSites")==1&&worldCalls>0&&worldHits>0,"Actual Sarcio world name site did not execute/reset-cache hit");
            enabled.setBoolean(null,false);BufferedImage nativeNames=render(mc,1,true,skin,deadline,original);Map<String,Long> nativeCallbacks=new LinkedHashMap<>(submissions[1]);
            enabled.setBoolean(null,true);BufferedImage cachedNames=render(mc,2,true,skin,deadline,original);Map<String,Long> cachedCallbacks=new LinkedHashMap<>(submissions[2]);
            int changed=0;for(int y=0;y<nativeNames.getHeight();y++)for(int x=0;x<nativeNames.getWidth();x++)if(nativeNames.getRGB(x,y)!=cachedNames.getRGB(x,y))changed++;
            ImageIO.write(nativeNames,"png",output().getParent().resolve("name-reset-native.png").toFile());ImageIO.write(cachedNames,"png",output().getParent().resolve("name-reset-cached.png").toFile());
            require(changed==0&&nativeCallbacks.equals(cachedCallbacks),"Name-only cache replay changed full RGBA or actual native submissions");
            MethodHandle append=MethodHandles.publicLookup().unreflect(formatter.getMethod("appendReset",String.class)).asType(MethodType.methodType(String.class,String.class));
            com.sun.management.ThreadMXBean bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
            require(bean.isThreadAllocatedMemorySupported(),"Per-site thread allocation measurement is unavailable");boolean allocations=bean.isThreadAllocatedMemoryEnabled();bean.setThreadAllocatedMemoryEnabled(true);
            long plain,cached;try{enabled.setBoolean(null,false);plain=nameAllocations(append,bean);enabled.setBoolean(null,true);cached=nameAllocations(append,bean);}finally{bean.setThreadAllocatedMemoryEnabled(allocations);}
            require(plain>0&&cached<plain,"Real appendReset per-site allocations were not reduced");
            report.put("nameReset",Map.of("actualWorldCalls",worldCalls,"actualWorldHits",worldHits,"actualNameSites",metric("RenderHookAllocationControl","nameSites"),"changedPixels",changed,"nativeCallbacksEqual",true,"loopCalls",100000,"warmupCalls",20000,"plainAllocatedBytes",plain,"cachedAllocatedBytes",cached,"scope","Prelinked real appendReset with volatile escape; no reflection inside measured loops"));progress("name-cache-validated");
        }finally{enabled.setBoolean(null,old);mc.options.hideGui=hide;}
    }
    private static long nameAllocations(MethodHandle append,com.sun.management.ThreadMXBean bean)throws Throwable{
        String input="CeraMaterialNameProbe";for(int i=0;i<20000;i++)nameEscape=(String)append.invokeExact(input);
        long thread=Thread.currentThread().threadId(),before=bean.getThreadAllocatedBytes(thread);
        for(int i=0;i<100000;i++)nameEscape=(String)append.invokeExact(input);
        return bean.getThreadAllocatedBytes(thread)-before;
    }
    public static void player(Entity entity,boolean enter){
        if(!enabled()||done||phase!=2||mode==0||!(entity instanceof Actor actor))return;
        if(enter){rendering=actor.index;traces[mode][rendering][0]++;System.arraycopy(metrics(),0,before,0,5);if(residentBodyEnabled())System.arraycopy(residentQueued(),0,residentBefore,0,2);}
        else{long[] after=metrics();for(int i=0;i<5;i++)traces[mode][actor.index][i+1]+=after[i]-before[i];if(residentBodyEnabled()){long[] resident=residentQueued();for(int i=0;i<2;i++)residentActors[mode][actor.index][i]+=resident[i]-residentBefore[i];}rendering=-1;}
    }
    public static void submit(Identifier texture,Object pass){if(enabled()&&!done&&mode!=0&&rendering>=0)submissions[mode].merge(rendering+":"+((Enum<?>)pass).name()+":"+texture,1L,Long::sum);}
    public static void voxel(boolean enter){if(enabled()&&!done)voxelDepth+=enter?1:-1;}
    public static void originalVoxelPath(){if(enabled()&&!done&&mode!=0&&rendering>=0&&voxelDepth>0)traces[mode][rendering][6]++;}
    public static void analyticTexture(int mask,Identifier texture){
        if(!enabled()||done||BASELINE||mode!=2||mask==0)return;
        try{int bound=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);var source=Minecraft.getInstance().getTextureManager().get(texture);
            require(source!=null,"Analytic skin source is missing");int expected=source.getGlId();
            require(bound==expected&&GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==bound,"Analytic voxel call entered with another skin bound");if(analyticTextures.add(texture.toString()))analyticOrder.add(texture.toString());
        }catch(Throwable error){failure=error.toString();}
    }
    public static boolean observingSelection(){return enabled()&&!done&&!BASELINE&&mode==2;}
    public static boolean analyticScope(){return observingSelection()&&(analyticDepth>0||preparationDepth>0);}
    public static String poolPhase(){return preparationDepth>0?"preparation":"queue";}
    public static void beginPreparation(Object storage){
        if(!observingSelection())return;
        try{Object auxiliary=CeraAdmission072LeaseEvidence.value(storage,"auxiliary");
            require((boolean)Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline").getMethod("isAuxiliaryBackend",Object.class).invoke(null,auxiliary),"Preparation selected an unregistered auxiliary backend");
            CeraAdmission072PoolEvidence.owner(CeraAdmission072LeaseEvidence.value(auxiliary,"textureArrays"));preparationDepth++;
        }catch(Throwable error){failure=error.toString();}
    }
    public static void endPreparation(Object texture,Object fact,boolean accepted){if(observingSelection()&&preparationDepth>0){CeraAdmission072LeaseEvidence.prepared(texture,fact,accepted);preparationDepth--;}}
    public static void analyticQueued(Object texture,int mask){CeraAdmission072LeaseEvidence.queued(texture,mask);}
    public static int beginAnalytic(int mask,Identifier texture){if(!enabled()||done||BASELINE||mode!=2||mask==0)return 0;analyticTexture(mask,texture);return ++analyticDepth;}
    public static void endAnalytic(int token){if(token>0)analyticDepth--;}
    public static void voxelDraw(IntBuffer payload,int count){
        if(!enabled()||done||BASELINE||mode!=2)return;
        try{int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),texture,program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),location=GL20.glGetUniformLocation(program,"uTextureArray");
            require(location>=0,"Actual voxel draw has no native array sampler");int unit=GL20.glGetUniformi(program,location);require(unit>=0&&unit<32,"Native array sampler unit is unsupported");
            try{GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);texture=GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);}finally{GL13.glActiveTexture(active);}
            CeraAdmission072PoolEvidence.recordDraw(texture,payload,count);
        }catch(Throwable error){failure=error.toString();}
    }
    public static long clock(long original){return enabled()?1234567L:original;}
    public static void frame(){
        if(!enabled()||done)return;
        if(failure!=null&&phase<3){terminalFailure=failure;failure=null;phase=3;}
        try{
            Minecraft mc=Minecraft.getInstance();
            if(phase==3){progress("before-normal-pause-menu");mc.openScreen(new net.minecraft.client.gui.screen.menu.GameMenuScreen());phase=4;menuFrames=0;return;}
            if(phase==4){
                require(++menuFrames<240,"Normal Cera pause menu did not become paused");require(mc.screen instanceof net.minecraft.client.gui.screen.menu.GameMenuScreen,"Normal Cera pause menu was replaced");if(menuFrames<3||!mc.isPaused())return;
                var button=((io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess)mc.screen).arcadium$buttons().stream().filter(value->value.id==1&&value.active).findFirst().orElseThrow();
                report.put("menuWasPaused",mc.isPaused());report.put("worldUnloadVia","GameMenuScreen.buttonClicked(1)");progress("before-normal-menu-disconnect");
                ((io.github.arcadiumclient.runtime.qa.mixin.ResolvedInstance072ScreenClick)mc.screen).resolved$click(button);report.put("afterMenuAction",normalUnloadState(mc));
                if(mc.world!=null&&mc.screen!=null&&mc.screen.getClass().getName().equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen")){phase=5;menuFrames=0;return;}
                completeNormalUnload(mc);
            }
            if(phase==5){
                require(++menuFrames<240,"Normal Cera confirmation did not become paused");require(mc.screen!=null&&mc.screen.getClass().getName().equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen"),"Normal Cera confirmation was replaced");if(menuFrames<3||!mc.isPaused())return;
                var button=((io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess)mc.screen).arcadium$buttons().stream().filter(value->value.id==0&&value.active).findFirst();if(button.isEmpty())return;
                report.put("normalConfirmationScreen",mc.screen.getClass().getName());progress("before-normal-confirmation");((io.github.arcadiumclient.runtime.qa.mixin.ResolvedInstance072ScreenClick)mc.screen).resolved$click(button.get());report.put("afterConfirmation",normalUnloadState(mc));completeNormalUnload(mc);
            }
            if(phase==6)finish(terminalFailure==null?null:new IllegalStateException(terminalFailure));
        }catch(Throwable error){finish(error);}
    }
    private static Map<String,Object> normalUnloadState(Minecraft mc){
        long bodyOwners=BASELINE?0:bodyCount("owners"),bodyProfiles=BASELINE?0:bodyCount("profiles");int auxiliaries=0;
        if(!BASELINE)try{auxiliaries=((Number)Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuModelAuxiliaries").getMethod("size").invoke(null)).intValue();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        return Map.of("worldNull",mc.world==null,"residentBodyOwners",bodyOwners,"residentBodyProfiles",bodyProfiles,"auxiliaries",auxiliaries,"screen",mc.screen==null?"null":mc.screen.getClass().getName());
    }
    private static void completeNormalUnload(Minecraft mc)throws Exception{
        var state=normalUnloadState(mc);require(Boolean.TRUE.equals(state.get("worldNull"))&&((Number)state.get("residentBodyOwners")).longValue()==0&&((Number)state.get("residentBodyProfiles")).longValue()==0&&((Number)state.get("auxiliaries")).intValue()==0,"Normal Cera menu did not unload world/owned renderer resources");report.put("normalWorldUnload",state);phase=6;progress("normal-world-unloaded");
    }

    private static void makePack(Minecraft mc)throws Exception{
        base=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);second=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);emissive=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){base.setRGB(x,y,((x/4+y/4)&1)==0?0xff555555:0xff777777);second.setRGB(x,y,((x/4+y/4)&1)==0?0xff335577:0xff557799);if(x%8<3&&y%8<3)emissive.setRGB(x,y,0xfff520bc);}
        Path zip=mc.getResourcePacks().getDirectory().toPath().resolve(PACK);Files.createDirectories(zip.getParent());
        try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){
            entry(out,"pack.mcmeta","{\"pack\":{\"pack_format\":1,\"description\":\"Arcadium native material QA\"}}".getBytes(StandardCharsets.UTF_8));
            entry(out,"assets/minecraft/optifine/emissive.properties","suffix.emissive=_e\n".getBytes(StandardCharsets.UTF_8));
            byte[] normal=png(base),glow=png(emissive);
            for(String path:List.of("assets/arcadium_qa/textures/entity/glow.png","assets/arcadium_qa/textures/entity/ordinary.png","assets/minecraft/textures/models/armor/iron_layer_1.png","assets/minecraft/textures/models/armor/diamond_layer_1.png"))entry(out,path,normal);
            entry(out,"assets/arcadium_qa/textures/entity/ordinary_two.png",png(second));
            entry(out,"assets/arcadium_qa/textures/entity/glow_e.png",glow);entry(out,"assets/minecraft/textures/models/armor/iron_layer_1_e.png",glow);
        }
        var repo=mc.getResourcePacks();repo.reload();var selected=repo.getAvailablePacks().stream().filter(pack->pack.getName().equals(PACK)).findFirst().orElseThrow();
        repo.setSelectedPacks(List.of(selected));mc.options.resourcePacks.clear();mc.options.resourcePacks.add(PACK);mc.reloadResources();
        report.put("ownedPack",zip.toString());report.put("resourcePackFormat",1);
    }
    private static void seedSkins(Minecraft mc)throws Exception{
        mc.getTextureManager().register(GLOW,new UploadedSkinTexture(base));mc.getTextureManager().register(ORDINARY,new UploadedSkinTexture(base));mc.getTextureManager().register(SECOND,new UploadedSkinTexture(second));
        Class<?> px=Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels"),meshes=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshes");
        Object companion=meshes.getField("Companion").get(null);
        var build=companion.getClass().getMethod("build",px,boolean.class);Class<?> key=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache$Key");
        var constructor=key.getDeclaredConstructor(Identifier.class,boolean.class);constructor.setAccessible(true);
        var field=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache").getDeclaredField("players");field.setAccessible(true);var cache=(Map<Object,Object>)field.get(null);
        for(Identifier id:List.of(GLOW,ORDINARY,SECOND)){BufferedImage image=id==SECOND?second:base;int[] pixels=image.getRGB(0,0,64,64,null,0,64);Object data=px.getConstructor(int.class,int.class,int[].class).newInstance(64,64,pixels);
            for(boolean slim:new boolean[]{false,true})cache.put(constructor.newInstance(id,slim),build.invoke(companion,data,slim));}
    }
    private static byte[] png(BufferedImage image)throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    private static void entry(ZipOutputStream out,String name,byte[] value)throws Exception{out.putNextEntry(new ZipEntry(name));out.write(value);out.closeEntry();}
    private static BufferedImage capture(Minecraft mc){
        int old=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);IntBuffer pixels=BufferUtils.createIntBuffer(mc.width*mc.height);
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,mc.getRenderTarget().frameBufferId);GL11.glReadPixels(0,0,mc.width,mc.height,GL12.GL_BGRA,GL12.GL_UNSIGNED_INT_8_8_8_8_REV,pixels);}finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,old);}
        BufferedImage image=new BufferedImage(mc.width,mc.height,BufferedImage.TYPE_INT_ARGB);int[] row=new int[mc.width];
        for(int y=0;y<mc.height;y++){pixels.position((mc.height-1-y)*mc.width);pixels.get(row);image.setRGB(0,y,mc.width,1,row,0,mc.width);}return image;
    }
    private static Path output(){return Path.of(System.getProperty("arcadium.ceraAdmission072.output")).toAbsolutePath();}
    private static void progress(String stage)throws Exception{report.put("stage",stage);Files.createDirectories(output().getParent());Files.writeString(output().resolveSibling("progress.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    private static void finish(Throwable error){
        if(done)return;done=true;report.put("passed",error==null);report.put("materialAdmission",status("CeraEmissiveAdmission"));
        if(!BASELINE){report.put("emissiveFallbacks",metric("CeraEmissiveAdmission","emissiveFallbacks"));report.put("ordinaryTextureQueries",metric("CeraEmissiveAdmission","ordinaryTextures"));report.put("unavailableFallbacks",metric("CeraEmissiveAdmission","unavailableFallbacks"));}report.put("voxel",status("GpuVoxelSkinPipeline"));
        if(error!=null){report.put("error",error.toString());error.printStackTrace();}
        try{Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception write){write.printStackTrace();}Minecraft.getInstance().stop();
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static final class Actor extends RemoteClientPlayerEntity{
        final int index;Actor(Minecraft mc,GameProfile profile,int index){super(mc.world,profile);this.index=index;}
        @Override public void tick(){ticks=160;}
        @Override public Identifier getSkinTextureLocation(){return index==0?GLOW:index==1?(scenario==2?SECOND:ORDINARY):(scenario==2?ORDINARY:SECOND);}
        @Override public int getLightLevel(float delta){return 0;}
    }
}
