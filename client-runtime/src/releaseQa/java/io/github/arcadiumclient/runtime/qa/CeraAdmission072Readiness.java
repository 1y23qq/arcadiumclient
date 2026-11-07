// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.qa.mixin.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.world.chunk.WorldChunk;

public final class CeraAdmission072Readiness {
    private static Future<Map<String,Object>> pending;
    private static Map<String,Object> server=Map.of();
    private static final Map<List<Integer>,WorldChunk> registeredChunks=new HashMap<>();
    private static int stage;
    private static long nextSnapshot,stableSince;
    private static String stable="";
    private CeraAdmission072Readiness(){}

    public static boolean ready(Minecraft mc)throws Exception{
        return ready(mc,Path.of(System.getProperty("arcadium.ceraAdmission072.output")));
    }

    public static boolean ready(Minecraft mc,Path receipt)throws Exception{
        if(stage==0){mc.options.viewDistance=4;stage=1;}
        if(pending!=null&&pending.isDone()){server=pending.get();pending=null;}
        if(pending==null&&System.nanoTime()>=nextSnapshot){nextSnapshot=System.nanoTime()+250_000_000L;pending=mc.getServer().executeTask(()->server(mc));}
        Map<String,Object> state=new LinkedHashMap<>();state.put("stage",stage);state.put("server",server);
        boolean inner=Boolean.TRUE.equals(server.get("innerReady"));int radius=((Number)server.getOrDefault("radius",-1)).intValue();
        if(stage==1&&radius==4&&inner){mc.options.viewDistance=3;stage=2;stableSince=0;state.put("transition","Native D4 halo populated original D3; restore D3");}
        Map<String,Object> client=client(mc);state.put("client",client);
        boolean complete=stage==2&&radius==3&&inner&&((Number)server.getOrDefault("pendingTotal",-1)).intValue()==0&&Boolean.TRUE.equals(client.get("ready"));
        String signature=String.valueOf(client.get("loaded"))+":"+client.get("sections");
        if(!complete||!signature.equals(stable)){stable=signature;stableSince=System.nanoTime();}
        boolean ready=complete&&System.nanoTime()-stableSince>=2_000_000_000L;state.put("ready",ready);
        Path output=receipt.toAbsolutePath().resolveSibling("readiness.json");
        Files.createDirectories(output.getParent());Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(state));
        if(ready&&registeredChunks.isEmpty())for(WorldChunk chunk:loaded(mc))registeredChunks.put(List.of(chunk.chunkX,chunk.chunkZ),chunk);
        return ready;
    }

    private static Map<String,Object> server(Minecraft mc){
        var world=mc.getServer().getWorld(0);var tracker=world.getChunkMap();int radius=((PlayerPipeline072ChunkMapAccess)tracker).player072$trackingRadius();
        Map<List<Integer>,WorldChunk> chunks=new HashMap<>();for(var chunk:world.chunkCache.getChunks())chunks.put(List.of(chunk.chunkX,chunk.chunkZ),chunk);
        Map<String,Object> result=new LinkedHashMap<>();result.put("radius",radius);List<Object> players=new ArrayList<>();boolean all=world.players.size()==1;int totalPending=0;
        for(var value:world.players)if(value instanceof ServerPlayerEntity player){
            int cx=(int)Math.floor(player.trackedX)>>4,cz=(int)Math.floor(player.trackedZ)>>4;Set<List<Integer>> pending=new HashSet<>();
            for(var coordinate:player.pendingChunks)pending.add(List.of(coordinate.x,coordinate.z));totalPending+=pending.size();
            int found=0,notReady=0;List<Object> coordinates=new ArrayList<>();
            for(int x=-3;x<=3;x++)for(int z=-4;z<=2;z++){
                var key=List.of(x,z);var chunk=chunks.get(key);boolean ready=chunk!=null&&chunk.isLoaded()&&chunk.isTerrainPopulated()&&chunk.isLightPopulated()&&chunk.isPopulated()
                        &&((PlayerPipeline072ChunkStateAccess)chunk).player072$hasTicked()&&tracker.isChunkWithinView(player,x,z)&&!pending.contains(key);
                if(ready)found++;else{notReady++;coordinates.add(Map.of("coordinate",key,"exists",chunk!=null,"pending",pending.contains(key)));}
            }
            all &=cx==0&&cz==-1&&found==49&&notReady==0;
            players.add(Map.of("center",List.of(cx,cz),"position",List.of(player.x,player.y,player.z),"pending",pending.size(),"innerReadyCount",found,"notReady",coordinates));
        }
        result.put("players",players);result.put("innerReady",all);result.put("pendingTotal",totalPending);return result;
    }

    private static List<WorldChunk> loaded(Minecraft mc){return ((PlayerPipeline072ChunksAccess)mc.world.getChunkSource()).player072$loadedChunks();}
    private static Object renderer(Minecraft mc)throws Exception{
        try{return mc.worldRenderer.getClass().getMethod("argentum$getWorldRenderer").invoke(mc.worldRenderer);}
        catch(NoSuchMethodException other){return mc.worldRenderer.getClass().getMethod("celeritas$getWorldRenderer").invoke(mc.worldRenderer);}
    }
    private static Map<String,Object> client(Minecraft mc)throws Exception{
        Map<String,Object> result=new LinkedHashMap<>();SortedSet<String> coordinates=new TreeSet<>(),sections=new TreeSet<>();
        for(var chunk:loaded(mc))coordinates.add(chunk.chunkX+","+chunk.chunkZ);
        Set<String> expected=new TreeSet<>();for(int x=-3;x<=3;x++)for(int z=-4;z<=2;z++)expected.add(x+","+z);
        Object renderer=renderer(mc);if(renderer==null){result.put("ready",false);result.put("loaded",coordinates);return result;}
        Object manager=renderer.getClass().getMethod("getRenderSectionManager").invoke(renderer);if(manager==null){result.put("ready",false);return result;}
        for(Object section:(Collection<?>)manager.getClass().getMethod("getAllRenderSections").invoke(manager))sections.add(section.getClass().getMethod("getChunkX").invoke(section)+","+section.getClass().getMethod("getChunkY").invoke(section)+","+section.getClass().getMethod("getChunkZ").invoke(section));
        Object builder=manager.getClass().getMethod("getBuilder").invoke(manager);
        boolean idle=(boolean)builder.getClass().getMethod("isBuildQueueEmpty").invoke(builder)&&((Number)builder.getClass().getMethod("getBusyThreadCount").invoke(builder)).intValue()==0
                &&((Number)builder.getClass().getMethod("getScheduledJobCount").invoke(builder)).intValue()==0&&collection(manager,"buildResults")==0&&collection(manager,"asyncSubmittedTasks")==0;
        boolean targets=(boolean)renderer.getClass().getMethod("isSectionReady",int.class,int.class,int.class).invoke(renderer,-1,0,0)
                &&(boolean)renderer.getClass().getMethod("isSectionReady",int.class,int.class,int.class).invoke(renderer,0,0,0);
        int missing=0;for(int x=-3;x<=3;x++)for(int z=-4;z<=2;z++)for(int y=0;y<16;y++)if(!sections.contains(x+","+y+","+z))missing++;
        result.put("loaded",coordinates);result.put("sections",sections);result.put("loadedCount",coordinates.size());result.put("sectionCount",sections.size());
        result.put("cameraYawPitch",List.of(mc.player.yaw,mc.player.pitch,mc.player.lastYaw,mc.player.lastPitch));result.put("cameraIsActualLocalPlayer",mc.getCamera()==mc.player);
        result.put("missingSections",missing);result.put("builderIdle",idle);result.put("targetSectionsReady",targets);result.put("camera",List.of(mc.player.x,mc.player.y,mc.player.z));
        result.put("ready",coordinates.equals(expected)&&sections.size()==49*16&&missing==0&&idle&&targets);return result;
    }

    public static Map<String,Object> actors(Minecraft mc,List<? extends Entity> actors)throws Exception{
        Map<List<Integer>,WorldChunk> actual=new HashMap<>();for(var chunk:loaded(mc))actual.put(List.of(chunk.chunkX,chunk.chunkZ),chunk);
        Object renderer=renderer(mc),manager=renderer==null?null:renderer.getClass().getMethod("getRenderSectionManager").invoke(renderer);
        Map<String,Object> rendered=new HashMap<>();if(manager!=null)for(Object section:(Collection<?>)manager.getClass().getMethod("getAllRenderSections").invoke(manager))rendered.put(section.getClass().getMethod("getChunkX").invoke(section)+","+section.getClass().getMethod("getChunkY").invoke(section)+","+section.getClass().getMethod("getChunkZ").invoke(section),section);
        List<Object> entries=new ArrayList<>();boolean all=true;
        for(Entity actor:actors){int x=(int)Math.floor(actor.x)>>4,y=(int)Math.floor(actor.y)>>4,z=(int)Math.floor(actor.z)>>4;var key=List.of(x,z);var chunk=actual.get(key);
            boolean member=chunk!=null&&entityMember(chunk,y,actor),same=chunk!=null&&registeredChunks.get(key)==chunk;Object section=rendered.get(x+","+y+","+z);var bounds=actor.getShape();
            Map<String,Object> entry=new LinkedHashMap<>();entry.put("position",List.of(actor.x,actor.y,actor.z));entry.put("nativeChunk",List.of(actor.chunkX,actor.chunkY,actor.chunkZ));entry.put("inChunk",actor.inChunk);
            entry.put("actualChunkIdentity",chunk==null?0:System.identityHashCode(chunk));entry.put("registeredChunkIdentity",registeredChunks.get(key)==null?0:System.identityHashCode(registeredChunks.get(key)));entry.put("sameNativeChunkObject",same);
            entry.put("actualEntitySectionContainsActor",member);entry.put("renderSectionPresent",section!=null);entry.put("renderSectionIdentity",section==null?0:System.identityHashCode(section));
            entry.put("renderSectionReady",renderer!=null&&(boolean)renderer.getClass().getMethod("isSectionReady",int.class,int.class,int.class).invoke(renderer,x,y,z));
            entry.put("bounds",List.of(bounds.minX,bounds.minY,bounds.minZ,bounds.maxX,bounds.maxY,bounds.maxZ));entry.put("globalEntity",mc.world.entities.contains(actor));entry.put("nativeEntityId",mc.world.getEntity(actor.getNetworkId())==actor);
            entries.add(entry);all &=same&&member&&actor.inChunk&&section!=null;
        }
        return Map.of("actors",entries,"allRegistered",all,"camera",List.of(mc.player.x,mc.player.y,mc.player.z),"entityDebug",mc.worldRenderer.getEntityDebugInfo());
    }

    private static boolean entityMember(WorldChunk chunk,int section,Object actor)throws Exception{
        for(Class<?> type=chunk.getClass();type!=null;type=type.getSuperclass())for(Field field:type.getDeclaredFields()){
            if(!field.getType().isArray())continue;field.setAccessible(true);Object array=field.get(chunk);if(array==null||section<0||section>=Array.getLength(array))continue;
            Object bucket=Array.get(array,section);if(bucket instanceof Iterable<?> values)for(Object value:values)if(value==actor)return true;
        }
        return false;
    }
    private static int collection(Object instance,String name)throws Exception{for(Class<?> type=instance.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);return ((Collection<?>)field.get(instance)).size();}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
}
