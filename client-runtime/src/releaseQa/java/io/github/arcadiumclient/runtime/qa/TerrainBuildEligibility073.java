// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import io.github.arcadiumclient.runtime.qa.mixin.PlayerPipeline072ChunksAccess;
import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.render.chunk.*;
import org.embeddedt.embeddium.impl.render.chunk.lists.RenderListManager;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller;
import org.embeddedt.embeddium.impl.render.viewport.*;

public final class TerrainBuildEligibility073 {
    private record Key(int x,int y,int z) { }
    private static volatile Set<Key> watched=Set.of();
    private static final Map<Key,Map<String,Object>> visits=new ConcurrentHashMap<>(),queues=new ConcurrentHashMap<>();
    private static volatile String error;
    private static Method withinDistance;
    private TerrainBuildEligibility073() { }
    public static void visit(Viewport viewport,float distance,int frame,int x,int y,int z,int compact,boolean eligible) {
        Key key=new Key(x,y,z);if(!watched.contains(key))return;
        try {
            if(withinDistance==null){Method method=OcclusionCuller.class.getDeclaredMethod("isWithinRenderDistance",CameraTransform.class,int.class,int.class,int.class,float.class);method.setAccessible(true);withinDistance=method;}
            boolean range=(boolean)withinDistance.invoke(null,viewport.getTransform(),x,y,z,distance),frustum=OcclusionCuller.isWithinFrustum(viewport,x,y,z);
            var pending=PackedSectionMetadata.getCompactPendingUpdate(compact);boolean inFlight=PackedSectionMetadata.isCompactBuildInFlight(compact);
            var row=new LinkedHashMap<String,Object>();row.put("frame",frame);row.put("eligible",eligible);row.put("withinNativeDistance",range);row.put("withinNativeFrustum",frustum);row.put("searchDistance",distance);row.put("compactMetadata",compact);row.put("compactPending",String.valueOf(pending));row.put("compactBuildInFlight",inFlight);row.put("nativeCandidatePredicate",eligible&&pending!=null&&!inFlight);var camera=viewport.getTransform();row.put("camera",List.of(camera.x,camera.y,camera.z));
            row.put("reason",!range?"outside native search distance":!frustum?"outside native frustum":!eligible?"native occlusion/aperture rejection":pending==null?"no compact pending update":inFlight?"native build already in flight":"eligible native rebuild candidate");var previous=visits.get(key);if(eligible&&pending!=null&&!inFlight)row.put("lastCandidateFrame",frame);else if(previous!=null&&previous.containsKey("lastCandidateFrame"))row.put("lastCandidateFrame",previous.get("lastCandidateFrame"));visits.put(key,row);trim(visits);
        }catch(Exception failure){error=failure.toString();}
    }
    public static void queue(Object manager,ChunkUpdateType type) {
        if(watched.isEmpty()||type!=ChunkUpdateType.INITIAL_BUILD)return;
        try {
            var lists=(RenderListManager)field(manager,"renderListManager");var pending=lists.getRebuildLists().byUpdateType().get(type);Set<Key> included=new HashSet<>();
            if(pending!=null)for(var section:pending)included.add(new Key(section.getChunkX(),section.getChunkY(),section.getChunkZ()));
            for(var key:watched){var row=new LinkedHashMap<String,Object>();row.put("frame",lists.getLastUpdatedFrame());row.put("nativeQueueContainsTarget",included.contains(key));row.put("nativeInitialQueueSize",pending==null?0:pending.size());row.put("nativeInitialListAbsent",pending==null);var previous=queues.get(key);if(included.contains(key))row.put("lastQueuedFrame",lists.getLastUpdatedFrame());else if(previous!=null&&previous.containsKey("lastQueuedFrame"))row.put("lastQueuedFrame",previous.get("lastQueuedFrame"));queues.put(key,row);}trim(queues);
        }catch(Exception failure){error=failure.toString();}
    }
    public static Map<String,Object> snapshot(Minecraft mc,Object manager,List<Map<String,Object>> unbuilt) {
        if(!"ArcadiumTest".equals(mc.getSession().getUsername())||!"0".equals(mc.getSession().getAccessToken())||mc.getServer()==null||mc.getCurrentServerEntry()!=null)throw new IllegalStateException("Only the owned offline scheduler can be observed");
        Set<Key> next=new HashSet<>();for(var row:unbuilt){var p=(List<?>)row.get("section");if(next.size()<32)next.add(new Key(((Number)p.get(0)).intValue(),((Number)p.get(1)).intValue(),((Number)p.get(2)).intValue()));}
        if(!next.equals(watched))watched=Set.copyOf(next);
        Set<List<Integer>> loaded=new HashSet<>();for(var c:((PlayerPipeline072ChunksAccess)mc.world.getChunkSource()).player072$loadedChunks())loaded.add(List.of(c.chunkX,c.chunkZ));
        int graphFrame=-1;try{graphFrame=((RenderListManager)field(manager,"renderListManager")).getLastUpdatedFrame();}catch(Exception failure){error=failure.toString();}
        var rows=new ArrayList<Map<String,Object>>();for(var key:next){var row=new LinkedHashMap<String,Object>();row.put("section",List.of(key.x,key.y,key.z));row.put("originColumnLoaded",loaded.contains(List.of(key.x,key.z)));var missing=new ArrayList<List<Integer>>();for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!loaded.contains(List.of(key.x+dx,key.z+dz)))missing.add(List.of(key.x+dx,key.z+dz));row.put("missingLoadedNeighborColumns",missing);row.put("neighborDiagnosticIsNotAnAdmissionGate",true);var visit=visits.getOrDefault(key,Map.of());row.put("lastActualCollectorVisit",visit);row.put("collectorRecordMatchesCurrentNativeGraph",Integer.valueOf(graphFrame).equals(visit.get("frame")));row.put("lastActualInitialQueue",queues.getOrDefault(key,Map.of()));rows.add(row);}
        var result=new LinkedHashMap<String,Object>();result.put("readOnly",true);result.put("currentNativeGraphFrame",graphFrame);result.put("observedTargets",rows);result.put("totalUnbuiltTargets",unbuilt.size());result.put("sampleLimit",32);result.put("historyLimit",64);result.put("nativeVisibleMeaning","visited-cell frame stamp; not final collector eligibility or queue membership");if(error!=null)result.put("observerError",error);return result;
    }
    public static Map<String,Object> target(int x,int y,int z){Key key=new Key(x,y,z);return Map.of("section",List.of(x,y,z),"lastActualCollectorVisit",visits.getOrDefault(key,Map.of()),"lastActualInitialQueue",queues.getOrDefault(key,Map.of()));}
    private static void trim(Map<Key,Map<String,Object>> map){if(map.size()>64)for(var key:map.keySet())if(!watched.contains(key)){map.remove(key);if(map.size()<=64)break;}}
    private static Object field(Object owner,String name)throws Exception{for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
}
