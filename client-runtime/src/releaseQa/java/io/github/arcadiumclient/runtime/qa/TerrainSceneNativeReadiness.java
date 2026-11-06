// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunksAccess;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunkStateAccess;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunkMapAccess;

public final class TerrainSceneNativeReadiness {
    private record Section(int x,int y,int z){}
    private int cameraX=Integer.MIN_VALUE,cameraZ=Integer.MIN_VALUE,haloStage;
    private long signature;
    private int equalReadySamples;
    private Future<Map<String,Object>> serverTask;
    private Map<String,Object> server;
    public Map<String,Object> sample(Minecraft mc,TerrainResidentSceneBlueprint.Plan blueprint)throws Exception{
        if(mc.getServer()==null||mc.world==null||mc.player==null)throw new IllegalStateException("Native readiness requires the owned integrated world");
        if(!"ArcadiumTest".equals(mc.getSession().getUsername())||!"0".equals(mc.getSession().getAccessToken())||mc.getCurrentServerEntry()!=null)throw new IllegalStateException("Readiness cannot inspect a human or remote world");
        int cx=(int)Math.floor(mc.player.x)>>4,cz=(int)Math.floor(mc.player.z)>>4;
        if(cx!=cameraX||cz!=cameraZ){cameraX=cx;cameraZ=cz;haloStage=0;equalReadySamples=0;}
        if(haloStage==0){mc.options.viewDistance=9;haloStage=1;}
        if(serverTask!=null&&serverTask.isDone()){server=serverTask.get();serverTask=null;}
        if(serverTask==null){
            var owner=mc.getServer();int expectedX=cx,expectedZ=cz;
            serverTask=owner.executeTask(()->{
                var world=owner.getWorld(0);var tracker=world.getChunkMap();int radius=((PlayerPipeline072ChunkMapAccess)tracker).player072$trackingRadius();
                var chunks=new HashMap<List<Integer>,net.minecraft.world.chunk.WorldChunk>();for(var c:world.chunkCache.getChunks())chunks.put(List.of(c.chunkX,c.chunkZ),c);
                int players=0,count=0,pending=0,unlit=0,unpopulated=0,unticked=0,unsent=0,remaining=-1;boolean center=false;
                for(var entity:world.players)if(entity instanceof ServerPlayerEntity player){
                    players++;center=((int)Math.floor(player.trackedX)>>4)==expectedX&&((int)Math.floor(player.trackedZ)>>4)==expectedZ;remaining=player.pendingChunks.size();
                    for(int x=expectedX-8;x<=expectedX+8;x++)for(int z=expectedZ-8;z<=expectedZ+8;z++){
                        var c=chunks.get(List.of(x,z));if(c==null)continue;count++;
                        boolean queued=false;for(var pos:player.pendingChunks)if(pos.x==x&&pos.z==z){queued=true;break;}
                        if(queued)pending++;if(!c.isLightPopulated())unlit++;if(!c.isTerrainPopulated())unpopulated++;if(!((PlayerPipeline072ChunkStateAccess)c).player072$hasTicked())unticked++;if(!tracker.isChunkWithinView(player,x,z))unsent++;
                    }
                }
                var data=new LinkedHashMap<String,Object>();data.put("cameraChunk",List.of(expectedX,expectedZ));data.put("trackingRadius",radius);data.put("players",players);data.put("trackingCenterMatches",center);data.put("originalD8Chunks",count);data.put("pendingOriginalD8",pending);data.put("unlit",unlit);data.put("unpopulated",unpopulated);data.put("unticked",unticked);data.put("unsent",unsent);data.put("pendingAll",remaining);return data;
            });
        }
        boolean originalReady=server!=null&&server.get("cameraChunk").equals(List.of(cx,cz))&&Integer.valueOf(1).equals(server.get("players"))&&Boolean.TRUE.equals(server.get("trackingCenterMatches"))&&Integer.valueOf(289).equals(server.get("originalD8Chunks"));
        if(originalReady)for(String name:List.of("pendingOriginalD8","unlit","unpopulated","unticked","unsent"))originalReady&=Integer.valueOf(0).equals(server.get(name));
        if(haloStage==1&&originalReady&&Integer.valueOf(9).equals(server.get("trackingRadius"))){mc.options.viewDistance=8;haloStage=2;equalReadySamples=0;}
        Object renderer;
        try{renderer=mc.worldRenderer.getClass().getMethod("argentum$getWorldRenderer").invoke(mc.worldRenderer);}catch(NoSuchMethodException alternate){renderer=mc.worldRenderer.getClass().getMethod("celeritas$getWorldRenderer").invoke(mc.worldRenderer);}
        var result=new LinkedHashMap<String,Object>();result.put("ready",false);result.put("cameraChunk",List.of(cx,cz));result.put("haloStage",haloStage);result.put("server",server==null?Map.of():server);
        if(renderer==null)return result;Object manager=renderer.getClass().getMethod("getRenderSectionManager").invoke(renderer);if(manager==null)return result;
        Object builder=manager.getClass().getMethod("getBuilder").invoke(manager);
        int busy=((Number)builder.getClass().getMethod("getBusyThreadCount").invoke(builder)).intValue(),scheduled=((Number)builder.getClass().getMethod("getScheduledJobCount").invoke(builder)).intValue();
        boolean idle=(boolean)builder.getClass().getMethod("isBuildQueueEmpty").invoke(builder)&&busy==0&&scheduled==0&&size(manager,"buildResults")==0&&size(manager,"asyncSubmittedTasks")==0;
        Set<List<Integer>> loaded=new HashSet<>(),expected=new HashSet<>();for(var c:((PlayerPipeline072ChunksAccess)mc.world.getChunkSource()).player072$loadedChunks())loaded.add(List.of(c.chunkX,c.chunkZ));
        for(int x=cx-8;x<=cx+8;x++)for(int z=cz-8;z<=cz+8;z++)expected.add(List.of(x,z));
        Set<Section> sections=new HashSet<>();for(Object section:(Collection<?>)manager.getClass().getMethod("getAllRenderSections").invoke(manager)){Class<?> type=section.getClass();sections.add(new Section(((Number)type.getMethod("getChunkX").invoke(section)).intValue(),((Number)type.getMethod("getChunkY").invoke(section)).intValue(),((Number)type.getMethod("getChunkZ").invoke(section)).intValue()));}
        int low=((Number)renderer.getClass().getMethod("getMinimumBuildHeight").invoke(renderer)).intValue()>>4,high=(((Number)renderer.getClass().getMethod("getMaximumBuildHeight").invoke(renderer)).intValue()+15)>>4,missing=0;
        for(var column:expected)for(int y=low;y<high;y++)if(!sections.contains(new Section(column.get(0),y,column.get(1))))missing++;
        Set<Section> targets=new HashSet<>();for(var p:blueprint.blocks())if(expected.contains(List.of(p.x()>>4,p.z()>>4)))targets.add(new Section(p.x()>>4,p.y()>>4,p.z()>>4));
        boolean targetReady=true;Method isReady=renderer.getClass().getMethod("isSectionReady",int.class,int.class,int.class);
        for(var section:targets)targetReady&=(boolean)isReady.invoke(renderer,section.x,section.y,section.z);
        boolean ready=haloStage==2&&mc.options.viewDistance==8&&originalReady&&Integer.valueOf(8).equals(server.get("trackingRadius"))&&Integer.valueOf(0).equals(server.get("pendingAll"))&&idle&&loaded.equals(expected)&&missing==0&&sections.size()==expected.size()*(high-low)&&targetReady;
        long next=31L*loaded.hashCode()+sections.hashCode();equalReadySamples=ready&&signature==next?equalReadySamples+1:0;signature=next;
        result.put("ready",ready&&equalReadySamples>=1);result.put("loadedColumns",loaded.size());result.put("expectedColumns",289);result.put("missingSections",missing);result.put("sectionCount",sections.size());result.put("expectedSectionCount",expected.size()*(high-low));result.put("targetsReady",targetReady);result.put("targetSections",targets.size());result.put("builderIdle",idle);result.put("consecutiveMatchingReadySamples",equalReadySamples+1);return result;
    }
    private static int size(Object owner,String name)throws Exception{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);Object value=field.get(owner);return value instanceof Collection<?> collection?collection.size():-1;}catch(NoSuchFieldException absent){}return -1;}
}
