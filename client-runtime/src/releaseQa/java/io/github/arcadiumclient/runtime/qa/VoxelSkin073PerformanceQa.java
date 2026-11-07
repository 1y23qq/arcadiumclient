// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.client.optimizations.*;
import io.github.arcadiumclient.runtime.client.skins.UploadedSkinTexture;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.client.resource.model.ModelTransformations;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.Display;

public final class VoxelSkin073PerformanceQa {
    private static final String MODE=System.getProperty("arcadium.voxelSkin073.performance","");
    private static final int PLAYERS=Integer.getInteger("arcadium.playerPipeline072.players",0);
    private static final Identifier[] textures={new Identifier("arcadium_qa","textures/entity/p3_self.png"),new Identifier("arcadium_qa","textures/entity/p3_other.png")};
    private static final String[] KEYS={"compiledMeshEntries","analyticParts","nativeCapturedParts","mdiCalls","submittedInstances","verticesDrawn","preShadowFlushes","fallbackFlushes","modeQueries","modelViewQueries","projectionQueries","capturedRows","tableUploads","tableBytes","physicalDrawScopes","physicalMdiAttempts","physicalReplayAttempts","physicalFallbackUploads","forgeNativeRoutes","nativeBodyParts"};
    private static List<? extends ClientPlayerEntity> actors;
    private static Class<?> forgeProbe;
    private static boolean seeded,witness,beforeReady,afterRequested,afterReady;
    private static final int[] heldReturns=new int[PLAYERS+1];
    private static final String[] heldTransforms=new String[PLAYERS+1];
    private static String heldFailure;
    private static long[] witnessStart,measuredStart,measuredEnd;
    private static final Map<String,Object> evidence=new LinkedHashMap<>();
    private VoxelSkin073PerformanceQa(){}
    public static boolean enabled(){return !MODE.isEmpty();}
    public static boolean witnessActive(){return enabled()&&witness;}
    public static boolean beforeReady(){return beforeReady;}
    public static boolean afterReady(){return afterReady;}
    public static boolean awaitingAfter(){return afterRequested&&!afterReady;}
    public static void validate(boolean visual,boolean baseline,boolean profiling,String scene,int count,int warmup,int seconds){
        if(!enabled())return;
        require(Set.of("native","public","physical").contains(MODE)&&!visual&&!baseline&&!profiling&&scene.equals("3d-skin")&&count==PLAYERS&&(count==20||count==64)&&warmup==25&&seconds==30,"Dedicated voxel cost domain differs");
        require(Boolean.getBoolean("arcadium.actorScene073.performance")&&Boolean.getBoolean("arcadium.performance073.quietWindow"),"Voxel cost requires the owned quiet sampler");
        require(GpuVoxelSkinPhysicalProjection.ENABLED==MODE.equals("physical")&&GpuVoxelSkinPhysicalProjection.MODE==1,"Physical boot variant differs");
        require(!Boolean.getBoolean("arcadium.actorScene073.enabled")&&!Boolean.getBoolean("arcadium.actorScene073.fusedInput")&&!Boolean.getBoolean("arcadium.terrainResident072.enabled")&&!Boolean.getBoolean("arcadium.terrainResident073.events")&&!Boolean.getBoolean("arcadium.actorRoot073.enabled")&&!Boolean.getBoolean("arcadium.nativeLayerRoot072.replay")&&!Boolean.getBoolean("arcadium.namePreparation073.lowerEntity")&&"off".equals(System.getProperty("arcadium.residentBodies072.mode","off")),"Another private renderer experiment is active");
    }
    public static Identifier skin(ClientPlayerEntity player){if(!enabled()||!seeded)return null;int index=player.getNetworkId()-72000;return index>=0&&index<PLAYERS?textures[index&1]:player==Minecraft.getInstance().player?textures[0]:null;}
    public static void seed(Minecraft mc)throws Exception{
        if(!enabled())return;
        Class<?> pixels=Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels"),meshes=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshes");Object companion=meshes.getField("Companion").get(null);Method build=companion.getClass().getMethod("build",pixels,boolean.class);Class<?> key=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache$Key");Constructor<?> constructor=key.getDeclaredConstructor(Identifier.class,boolean.class);constructor.setAccessible(true);Field field=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache").getDeclaredField("players");field.setAccessible(true);Map<Object,Object> cache=(Map<Object,Object>)field.get(null);
        for(int i=0;i<2;i++){BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,i==0?(((x/4+y/4)&1)==0?0xff774433:0xff996644):(((x/4+y/4)&1)==0?0xff335577:0xff557799));mc.getTextureManager().register(textures[i],new UploadedSkinTexture(image));Object data=pixels.getConstructor(int.class,int.class,int[].class).newInstance(64,64,image.getRGB(0,0,64,64,null,0,64));for(boolean slim:new boolean[]{false,true})cache.put(constructor.newInstance(textures[i],slim),build.invoke(companion,data,slim));}
        Class<?> loader=Class.forName("net.minecraftforge.fml.common.Loader");Object instance=loader.getMethod("instance").invoke(null);Map<?,?> mods=(Map<?,?>)loader.getMethod("getIndexedModList").invoke(instance);Object container=mods.get("arcadiumrenderprobe");require(container!=null,"Real Forge render guest is absent");forgeProbe=container.getClass().getMethod("getMod").invoke(container).getClass();require(forgeProbe.getField("registered").getBoolean(null),"Real Forge observer is not registered");forgeProbe.getField("target").set(null,null);seeded=true;
    }
    public static void actorsReady(Minecraft mc,List<? extends ClientPlayerEntity> roster){
        if(!enabled())return;actors=roster;require(seeded&&actors.size()==Integer.getInteger("arcadium.playerPipeline072.players",0),"Owned voxel actor roster differs");
        Skin3DConfig.setDistance(32);Skin3DConfig.setAdaptiveDetail(false);Skin3DConfig.setDetailedPlayers(64);Skin3DConfig.INSTANCE.chooseEnabled(true);
        for(ClientPlayerEntity actor:actors){actor.setEquipment(0,new ItemStack(Items.DIAMOND_SWORD));for(int slot=1;slot<=4;slot++)actor.setEquipment(slot,null);actor.clearItemInUse();}
        mc.player.inventory.setItem(mc.player.inventory.selectedSlot,new ItemStack(Items.DIAMOND_SWORD));mc.player.clearItemInUse();
        evidence.put("mode",MODE);evidence.put("declaredActors",actors.size());evidence.put("actualForgeGuestRegistered",true);evidence.put("skinTextures",List.of(textures[0].toString(),textures[1].toString()));evidence.put("texturePixels","Two opaque64x64 R8 checker palettes; same native SkinMeshCache builder");evidence.put("held","Idle diamond swords on actors and actual local first-person player; no armor");
    }
    public static void armWitness(boolean after)throws Exception{
        require(enabled()&&!witness&&actors!=null,"Nested or unprepared voxel witness");afterRequested=after;Arrays.fill(heldReturns,0);Arrays.fill(heldTransforms,null);heldFailure=null;forgeProbe.getMethod("reset").invoke(null);forgeProbe.getField("mode").set(null,"observe");forgeProbe.getField("target").set(null,actors.getFirst());witnessStart=counters();witness=true;
    }
    public static void witnessFrame(List<Map<String,Object>> traces)throws Exception{
        if(!witnessActive())return;
        try{
            require(traces.size()==actors.size(),"Cold voxel trace roster differs");
            for(Map<String,Object> row:traces){require(((Number)row.get("nativeGpuBodyParts")).longValue()>0&&((Number)row.get("rendererCalls")).intValue()==1&&((Number)row.get("nativeVoxelCalls")).longValue()==6,"Actor did not retain all six actual native voxel mesh callbacks: "+row);long gpu=((Number)row.get("analyticGpuVoxelParts")).longValue()+((Number)row.get("nativeGpuVoxelParts")).longValue();require(gpu==(MODE.equals("native")?0:6),"Cold actor GPU outer-part route differs: "+row);}
            Map<String,Object> callback=new LinkedHashMap<>();for(String key:List.of("playerPre","livingPre","livingPost","playerPost","canceled","mutations"))callback.put(key,forgeProbe.getField(key).getLong(null));callback.put("sequence",forgeProbe.getField("sequence").get(null).toString());require(forgeProbe.getField("failure").get(null)==null&&forgeProbe.getMethod("restored").invoke(null).equals(true),"Forge callback observer failed");for(String key:List.of("playerPre","livingPre","livingPost","playerPost"))require(((Number)callback.get(key)).longValue()==1,"Cold real Forge callback was absent");require(callback.get("sequence").equals("playerPre,livingPre,livingPost,playerPost"),"Real Forge callback order changed");
            require(heldFailure==null,"Cold held observer failed: "+heldFailure);for(int i=0;i<heldReturns.length;i++)require(heldReturns[i]>0&&Objects.equals(heldTransforms[i],i==PLAYERS?"FIRST_PERSON":"THIRD_PERSON"),"An actual held sword did not render successfully: "+i);
            long[] now=counters();Map<String,Object> value=new LinkedHashMap<>();value.put("actors",traces);value.put("realForgeCallbacks",callback);value.put("routeDelta",delta(witnessStart,now));value.put("successfulHeldReturns",heldReturns.clone());value.put("actualHeldTransformTypes",heldTransforms.clone());value.put("ordinaryNativeRender",true);value.put("pixelOrBufferReadback",false);evidence.put(afterRequested?"coldAfter":"coldBefore",value);if(afterRequested)afterReady=true;else beforeReady=true;
        }finally{forgeProbe.getField("target").set(null,null);witness=false;}
    }
    public static void beginMeasured(Map<String,Object> settings)throws Exception{
        require(beforeReady&&!witness,"Cold source witness is absent");require(forgeProbe.getField("target").get(null)==null,"Forge string observer entered timing");validateSettings(settings);measuredStart=counters();evidence.put("settingsBefore",settings);evidence.put("productionPhysicalQueriesRemainMeasured",MODE.equals("physical"));
    }
    public static void endMeasured(Map<String,Object> settings)throws Exception{
        require(measuredStart!=null&&!witness,"Measured voxel boundary was not armed");validateSettings(settings);require(evidence.get("settingsBefore").equals(settings),"Voxel settings changed during sampling");measuredEnd=counters();Map<String,Object> change=delta(measuredStart,measuredEnd);require(measuredEnd[0]>measuredStart[0]&&measuredEnd[18]>measuredStart[18]&&measuredEnd[19]>measuredStart[19],"Measured native skin/Forge/body route was absent");long captured=measuredEnd[2]-measuredStart[2],analytic=measuredEnd[1]-measuredStart[1],draws=measuredEnd[3]-measuredStart[3];
        if(MODE.equals("native"))require(captured==0&&analytic==0&&draws==0,"Native outer control submitted optional GPU skins");else require(captured+analytic>0&&draws>0,"Optional GPU skin path never drew");
        if(MODE.equals("physical"))require(measuredEnd[11]>measuredStart[11]&&measuredEnd[8]-measuredStart[8]==measuredEnd[11]-measuredStart[11]&&measuredEnd[9]-measuredStart[9]==measuredEnd[11]-measuredStart[11]&&measuredEnd[10]-measuredStart[10]==measuredEnd[11]-measuredStart[11]&&measuredEnd[12]>measuredStart[12]&&measuredEnd[13]>measuredStart[13]&&measuredEnd[14]>measuredStart[14]&&measuredEnd[15]>measuredStart[15]&&measuredEnd[16]==measuredStart[16]&&measuredEnd[17]==measuredStart[17],"Physical source did not use actual queried rows/MDI without replay");else for(int i=8;i<=17;i++)require(measuredEnd[i]==measuredStart[i],"Nonphysical variant entered private physical path");
        evidence.put("routeDelta",change);evidence.put("actualPublicRoute",MODE.equals("native")?"native-outer":MODE.equals("physical")?"physical-mvp":analytic>0?"public-analytic-or-mixed":"public-native-capture");evidence.put("settingsAfter",settings);
    }
    public static boolean actualForgeBodyRoute(){return enabled()&&measuredStart!=null&&GpuPlayerPipeline.nativeCallbackRoutes>measuredStart[18];}
    public static int heldEnter(LivingEntity entity,ItemStack stack,ModelTransformations.Type transform){
        if(!witnessActive())return 0;
        try{int index=entity.getNetworkId()-72000;if(index<0||index>=PLAYERS||actors.get(index)!=entity){if(entity!=Minecraft.getInstance().player)return 0;index=PLAYERS;}require(stack!=null&&stack.getItem()==Items.DIAMOND_SWORD,"Actual held input differs");heldTransforms[index]=transform.name();return index+1;}catch(Throwable failure){heldFailure=failure.toString();return 0;}
    }
    public static void heldReturned(int token){if(token==0)return;try{require(witness&&token>0&&token<=heldReturns.length,"Held owner scope changed");heldReturns[token-1]++;}catch(Throwable failure){heldFailure=failure.toString();}}
    private static void validateSettings(Map<String,Object> settings){
        Minecraft mc=Minecraft.getInstance();require(mc.width==1920&&mc.height==1080&&Display.getWidth()==1920&&Display.getHeight()==1080&&Display.isActive()&&mc.options.fpsLimit==260&&!mc.options.vsync&&mc.options.viewDistance==8&&mc.options.fov==75&&mc.options.perspective==0,"Voxel cost video contract changed");require(settings.get("beta")instanceof Map<?,?> beta&&beta.size()==25,"All25 Beta values are required");require(BetaOptimizationsConfig.getGpuVoxelSkinLayers()==!MODE.equals("native")&&GpuVoxelSkinPipeline.enabled==!MODE.equals("native"),"Actual public GPU voxel switch differs");require(Skin3DConfig.getEnabled()&&Skin3DConfig.getDistance()==32&&!Skin3DConfig.getAdaptiveDetail()&&Skin3DConfig.getDetailedPlayers()==64,"Full detailed skin coverage changed");
        require(mc.player.x==0&&mc.player.y==6&&mc.player.z==-10&&mc.player.yaw==0&&mc.player.pitch==8,"Native voxel camera changed");
    }
    public static Map<String,Object> result(){Map<String,Object> result=new LinkedHashMap<>(evidence);result.put("counterOrder",List.of(KEYS));result.put("coldWitnessesComplete",beforeReady&&afterReady);result.put("qaReadbacksDuringTiming",0);result.put("profiling",false);result.put("diagnosticOnly",true);result.put("performanceCertified",false);result.put("adopted",false);result.put("scope","Owned20/64-actor voxel cost sample; new domain requires independent native quality and complete Root operation audit");return result;}
    private static Map<String,Object> delta(long[] a,long[] b){Map<String,Object> value=new LinkedHashMap<>();for(int i=0;i<KEYS.length;i++){require(b[i]>=a[i],"A voxel counter reset");value.put(KEYS[i],b[i]-a[i]);}return value;}
    private static long[] counters(){return new long[]{Features062Qa.voxelDraws,GpuVoxelSkinPipeline.analyticParts,GpuVoxelSkinPipeline.nativeParts,GpuVoxelSkinPipeline.drawCalls,GpuVoxelSkinPipeline.submittedInstances,GpuVoxelSkinPipeline.verticesDrawn,GpuVoxelSkinPipeline.preShadowFlushes,GpuVoxelSkinPipeline.fallbackFlushes,GpuVoxelSkinPhysicalProjection.modeQueries,GpuVoxelSkinPhysicalProjection.modelViewQueries,GpuVoxelSkinPhysicalProjection.projectionQueries,GpuVoxelSkinPhysicalProjection.capturedRows,GpuVoxelSkinPhysicalProjection.tableUploads,GpuVoxelSkinPhysicalProjection.tableUploadBytes,GpuVoxelSkinPhysicalProjection.drawScopes,GpuVoxelSkinPhysicalProjection.mdiAttempts,GpuVoxelSkinPhysicalProjection.replayAttempts,GpuVoxelSkinPhysicalProjection.fallbackPayloadUploads,GpuPlayerPipeline.nativeCallbackRoutes,NativePlayerPosePipeline.bodyPartsQueued};}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
}
