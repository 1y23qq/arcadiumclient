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
    private int cameraX=Integer.MIN_VALUE,cameraZ=Integer.MIN_VALUE,haloStage,bootstrapDistanceChanges;
    private long signature;
    private int equalReadySamples,equalDatasetSamples;
    private Future<Map<String,Object>> serverTask;
    private Map<String,Object> server;
    public Map<String,Object> sample(Minecraft mc,TerrainResidentSceneBlueprint.Plan blueprint)throws Exception{
        if(mc.getServer()==null||mc.world==null||mc.player==null)throw new IllegalStateException("Native readiness requires the owned integrated world");
        if(!"ArcadiumTest".equals(mc.getSession().getUsername())||!"0".equals(mc.getSession().getAccessToken())||mc.getCurrentServerEntry()!=null)throw new IllegalStateException("Readiness cannot inspect a human or remote world");
        int cx=trackingChunk(mc.player.x),cz=trackingChunk(mc.player.z);
        if(cx!=cameraX||cz!=cameraZ){cameraX=cx;cameraZ=cz;equalReadySamples=equalDatasetSamples=0;signature=0;}
        if(haloStage==0){mc.options.viewDistance=9;bootstrapDistanceChanges++;haloStage=1;}
        if(serverTask!=null&&serverTask.isDone()){server=serverTask.get();serverTask=null;}
        if(serverTask==null){
            var owner=mc.getServer();int expectedX=cx,expectedZ=cz;
            serverTask=owner.executeTask(()->{
                var world=owner.getWorld(0);var tracker=world.getChunkMap();int radius=((PlayerPipeline072ChunkMapAccess)tracker).player072$trackingRadius();
                var chunks=new HashMap<List<Integer>,net.minecraft.world.chunk.WorldChunk>();for(var c:world.chunkCache.getChunks())chunks.put(List.of(c.chunkX,c.chunkZ),c);
                int players=0,count=0,pending=0,unlit=0,unpopulated=0,unticked=0,unsent=0,remaining=-1,unreadyCount=0,unlitWithMissingNeighbors=0,unlitWithoutMissingNeighbors=0;boolean center=false;
                var unreadyColumns=new ArrayList<Map<String,Object>>();var missingColumns=new ArrayList<List<Integer>>();
                for(var entity:world.players)if(entity instanceof ServerPlayerEntity player){
                    players++;center=trackingChunk(player.trackedX)==expectedX&&trackingChunk(player.trackedZ)==expectedZ;remaining=player.pendingChunks.size();
                    for(int x=expectedX-8;x<=expectedX+8;x++)for(int z=expectedZ-8;z<=expectedZ+8;z++){
                        var c=chunks.get(List.of(x,z));if(c==null){if(missingColumns.size()<32)missingColumns.add(List.of(x,z));continue;}count++;
                        boolean queued=false;for(var pos:player.pendingChunks)if(pos.x==x&&pos.z==z){queued=true;break;}
                        boolean light=c.isLightPopulated(),terrain=c.isTerrainPopulated(),ticked=((PlayerPipeline072ChunkStateAccess)c).player072$hasTicked(),sent=tracker.isChunkWithinView(player,x,z);
                        if(queued)pending++;if(!light)unlit++;if(!terrain)unpopulated++;if(!ticked)unticked++;if(!sent)unsent++;
                        var neighbors=new ArrayList<List<Integer>>();for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!chunks.containsKey(List.of(x+dx,z+dz)))neighbors.add(List.of(x+dx,z+dz));
                        if(!light){if(neighbors.isEmpty())unlitWithoutMissingNeighbors++;else unlitWithMissingNeighbors++;}
                        if(queued||!light||!terrain||!ticked||!sent){unreadyCount++;if(unreadyColumns.size()<32)unreadyColumns.add(Map.of("column",List.of(x,z),"pending",queued,"terrainPopulated",terrain,"lightPopulated",light,"hasTicked",ticked,"sent",sent,"missingNativeNeighborColumns",neighbors));}
                    }
                }
                var data=new LinkedHashMap<String,Object>();data.put("cameraChunk",List.of(expectedX,expectedZ));data.put("trackingRadius",radius);data.put("players",players);data.put("trackingCenterMatches",center);data.put("originalD8Chunks",count);data.put("pendingOriginalD8",pending);data.put("unlit",unlit);data.put("unpopulated",unpopulated);data.put("unticked",unticked);data.put("unsent",unsent);data.put("pendingAll",remaining);data.put("loadedServerColumns",chunks.size());data.put("unreadyColumnsTotal",unreadyCount);data.put("unreadyColumnsSample",unreadyColumns);data.put("unreadyColumnSampleLimit",32);data.put("missingOriginalD8ColumnsSample",missingColumns);data.put("unlitWithMissingNativeNeighbors",unlitWithMissingNeighbors);data.put("unlitWithoutMissingNativeNeighbors",unlitWithoutMissingNeighbors);return data;
            });
        }
        boolean originalReady=server!=null&&server.get("cameraChunk").equals(List.of(cx,cz))&&Integer.valueOf(1).equals(server.get("players"))&&Boolean.TRUE.equals(server.get("trackingCenterMatches"))&&Integer.valueOf(289).equals(server.get("originalD8Chunks"));
        if(originalReady)for(String name:List.of("pendingOriginalD8","unlit","unpopulated","unticked","unsent"))originalReady&=Integer.valueOf(0).equals(server.get(name));
        if(haloStage==1&&originalReady&&Integer.valueOf(9).equals(server.get("trackingRadius"))){mc.options.viewDistance=8;bootstrapDistanceChanges++;haloStage=2;equalReadySamples=equalDatasetSamples=0;}
        Object renderer;
        try{renderer=mc.worldRenderer.getClass().getMethod("argentum$getWorldRenderer").invoke(mc.worldRenderer);}catch(NoSuchMethodException alternate){renderer=mc.worldRenderer.getClass().getMethod("celeritas$getWorldRenderer").invoke(mc.worldRenderer);}
        var result=new LinkedHashMap<String,Object>();result.put("ready",false);result.put("cameraChunk",List.of(cx,cz));result.put("haloStage",haloStage);result.put("bootstrapDistanceChanges",bootstrapDistanceChanges);result.put("server",server==null?Map.of():server);
        if(renderer==null)return result;Object manager=renderer.getClass().getMethod("getRenderSectionManager").invoke(renderer);if(manager==null)return result;
        result.put("nativeRenderManagerIdentity",System.identityHashCode(manager));result.put("nativeEffectiveRenderDistance",renderer.getClass().getMethod("getEffectiveRenderDistance").invoke(renderer));
        Object builder=manager.getClass().getMethod("getBuilder").invoke(manager);
        int busy=((Number)builder.getClass().getMethod("getBusyThreadCount").invoke(builder)).intValue(),scheduled=((Number)builder.getClass().getMethod("getScheduledJobCount").invoke(builder)).intValue();
        boolean idle=(boolean)builder.getClass().getMethod("isBuildQueueEmpty").invoke(builder)&&busy==0&&scheduled==0&&size(manager,"buildResults")==0&&size(manager,"asyncSubmittedTasks")==0;
        Set<List<Integer>> loaded=new HashSet<>(),expected=new HashSet<>();for(var c:((PlayerPipeline072ChunksAccess)mc.world.getChunkSource()).player072$loadedChunks())loaded.add(List.of(c.chunkX,c.chunkZ));
        for(int x=cx-8;x<=cx+8;x++)for(int z=cz-8;z<=cz+8;z++)expected.add(List.of(x,z));
        Map<Section,Object> nativeSections=new HashMap<>();Set<Section> sections=new HashSet<>();for(Object section:(Collection<?>)manager.getClass().getMethod("getAllRenderSections").invoke(manager)){Class<?> type=section.getClass();Section coordinates=new Section(((Number)type.getMethod("getChunkX").invoke(section)).intValue(),((Number)type.getMethod("getChunkY").invoke(section)).intValue(),((Number)type.getMethod("getChunkZ").invoke(section)).intValue());sections.add(coordinates);nativeSections.put(coordinates,section);}
        int low=((Number)renderer.getClass().getMethod("getMinimumBuildHeight").invoke(renderer)).intValue()>>4,high=(((Number)renderer.getClass().getMethod("getMaximumBuildHeight").invoke(renderer)).intValue()+15)>>4,missing=0;
        for(var column:expected)for(int y=low;y<high;y++)if(!sections.contains(new Section(column.get(0),y,column.get(1))))missing++;
        Set<Section> targets=new HashSet<>();for(var p:blueprint.blocks())if(expected.contains(List.of(p.x()>>4,p.z()>>4)))targets.add(new Section(p.x()>>4,p.y()>>4,p.z()>>4));
        boolean targetReady=true;Method isReady=renderer.getClass().getMethod("isSectionReady",int.class,int.class,int.class),isVisible=manager.getClass().getMethod("isSectionVisible",int.class,int.class,int.class);
        var unbuilt=new ArrayList<Map<String,Object>>();int emptyBuilt=0;
        var orderedTargets=new ArrayList<>(targets);orderedTargets.sort(Comparator.comparingInt(Section::x).thenComparingInt(Section::y).thenComparingInt(Section::z));
        for(var section:orderedTargets){
            boolean ready=(boolean)isReady.invoke(renderer,section.x,section.y,section.z);targetReady&=ready;Object nativeSection=nativeSections.get(section);
            if(nativeSection!=null){
                Class<?> type=nativeSection.getClass();boolean built=(boolean)type.getMethod("isBuilt").invoke(nativeSection),anything=(boolean)type.getMethod("hasAnythingToRender").invoke(nativeSection);
                if(built&&!anything)emptyBuilt++;
                if(!ready)unbuilt.add(Map.of("section",List.of(section.x,section.y,section.z),"built",built,"anythingToRender",anything,"nativeVisible",isVisible.invoke(manager,section.x,section.y,section.z),"pending",String.valueOf(type.getMethod("getPendingUpdate").invoke(nativeSection))));
            }
        }
        boolean dataset=haloStage==2&&mc.options.viewDistance==8&&originalReady&&Integer.valueOf(8).equals(server.get("trackingRadius"))&&Integer.valueOf(0).equals(server.get("pendingAll"))&&idle&&loaded.equals(expected)&&missing==0&&sections.size()==expected.size()*(high-low);
        long next=31L*loaded.hashCode()+sections.hashCode();equalDatasetSamples=dataset&&signature==next?equalDatasetSamples+1:0;equalReadySamples=dataset&&targetReady&&signature==next?equalReadySamples+1:0;signature=next;
        result.put("datasetReady",dataset&&equalDatasetSamples>=1);result.put("unreadyTargets",unbuilt);result.put("emptyBuiltTargets",emptyBuilt);
        result.put("ready",dataset&&targetReady&&equalReadySamples>=1);result.put("loadedColumns",loaded.size());result.put("expectedColumns",289);result.put("missingSections",missing);result.put("sectionCount",sections.size());result.put("expectedSectionCount",expected.size()*(high-low));result.put("targetsReady",targetReady);result.put("targetSections",targets.size());result.put("builderIdle",idle);result.put("consecutiveMatchingReadySamples",equalReadySamples+1);return result;
    }
    public static int trackingChunk(double coordinate){if(!Double.isFinite(coordinate))throw new IllegalArgumentException("Non-finite owned tracker coordinate");return ((int)coordinate)>>4;}
    private static int size(Object owner,String name)throws Exception{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);Object value=field.get(owner);return value instanceof Collection<?> collection?collection.size():-1;}catch(NoSuchFieldException absent){}return -1;}
}
