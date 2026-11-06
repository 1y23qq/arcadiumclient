// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;
import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

public final class TerrainResidentScene {
    private static final ThreadLocal<Scope> current=new ThreadLocal<>();
    private static final TerrainResidentPublications publications=new TerrainResidentPublications(4096);
    private static Scene scene;
    private static TerrainResidentNativeAccess api;
    private static boolean failed;
    private static String status="Waiting for resident terrain";
    public static long passSerial,passesPrepared,regionDraws,sourceVersionUpdates,metadataUploads,orderCopiedBytes,orderComparedBytes;
    public static long publicationEpoch,preparedPublicationEpoch,submittedPublicationEpoch,initiallyEmptyNativePasses,unsettledFades,budgetFallbacks,unsupported,errors;
    public static long cachedBatchesCreated,cachedBatchesReused;
    static {
        GpuTerrainCommands.attachResident(new GpuTerrainCommands.Backend(){
            public String status(){return "GPU resident terrain: "+status;}
            public long residentBytes(){return scene==null||scene.buffers==null?0:scene.buffers.residentBytes();}
            public void reset(){TerrainResidentScene.reset();}
            public void shutdown(){TerrainResidentScene.shutdown();}
            public boolean residentSceneLoaded(){return true;}
        });
    }
    private TerrainResidentScene(){}
    public static boolean requested(){return GpuTerrainCommands.residentBootRequested();}
    public static final class Scope {
        private final Scope previous;
        private final RenderSectionManager manager;
        private final Object renderer;
        private final ChunkRenderMatrices matrices;
        private final CommandList device;
        private final ChunkRenderListIterable original;
        private final TerrainRenderPass pass;
        private final CameraTransform face,render;
        private final long serial;
        private boolean ready,entered;
        private Scope(Scope previous,RenderSectionManager manager,Object renderer,ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable original,TerrainRenderPass pass,CameraTransform face,CameraTransform render){this.previous=previous;this.manager=manager;this.renderer=renderer;this.matrices=matrices;this.device=device;this.original=original;this.pass=pass;this.face=face;this.render=render;serial=++passSerial;}
    }
    public static Scope enter(RenderSectionManager manager,Object renderer,ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable original,TerrainRenderPass pass,CameraTransform face,CameraTransform render){
        if(!requested()||!GpuTerrainCommands.enabled||!pass.name().equals("solid"))return null;
        Scope value=new Scope(current.get(),manager,renderer,matrices,device,original,pass,face,render);current.set(value);return value;
    }
    public static void exit(Scope value){
        if(value==null)return;
        if(current.get()!=value){failed=true;errors++;status="Unbalanced resident terrain scope";current.remove();return;}
        if(!value.entered&&!value.original.hasPass(value.pass)){initiallyEmptyNativePasses++;status="Native pass has no listed geometry; using native rendering";}
        if(value.previous==null)current.remove();else current.set(value.previous);
    }
    public static Iterator<ChunkRenderList> prepare(DefaultChunkRenderer renderer,Iterator<ChunkRenderList> original,boolean cull){
        Scope value=current.get();if(value==null||failed||value.previous!=null||value.renderer!=renderer)return original;value.entered=true;value.ready=false;
        try{
            var caps=GL.getCapabilities();if(!caps.OpenGL43||!(caps.OpenGL46||caps.GL_ARB_indirect_parameters)){unsupported++;status="Compute and indirect-count rendering are unavailable";return original;}
            if(api==null)api=new TerrainResidentNativeAccess();
            if(!api.normalPass(value.manager)||!TerrainResidentNativeAccess.cameras(value.matrices,value.face,value.render)){unsupported++;status="Unsupported terrain pass or camera";return original;}
            var fact=api.program(renderer,value.pass);if(fact==null){unsupported++;status="Native terrain shader provenance is unavailable";return original;}
            if(scene==null||scene.owner!=value.manager){reset();scene=new Scene(value.manager);}
            Iterator<ChunkRenderList> replacement=scene.prepare(value,renderer,cull,fact);
            if(replacement==null)return original;
            value.ready=true;passesPrepared++;status="Resident opaque terrain prepared";return replacement;
        }catch(Throwable error){failed=true;errors++;status="Resident terrain preflight failed: "+error.getClass().getSimpleName();return original;}
    }
    public static CachedBatch select(SectionRenderDataStorage storage,TerrainRenderPass pass){
        Scope value=current.get();if(value==null||!value.ready||scene==null||!scene.ready||pass!=value.pass)return null;
        Entry entry=scene.byStorage.get(storage);return entry!=null&&entry.serial==value.serial&&publications.current(storage)==entry.data.version?entry.cached:null;
    }
    public static void sourceChanged(Object storage,int slot){
        if(scene==null)return;scene.ready=false;long version=publications.changed(storage,slot);sourceVersionUpdates++;publicationEpoch=publications.epoch();if(version<0){failed=true;unsupported++;status="Unknown native section publication";}
    }
    public static void sourceChanged(Object storage){
        if(scene==null)return;scene.ready=false;long version=publications.changed(storage);sourceVersionUpdates++;publicationEpoch=publications.epoch();if(version<0){failed=true;unsupported++;status="Unknown native arena publication";}
    }
    public static void released(Object storage){publications.released(storage);if(scene!=null){scene.ready=false;scene.byStorage.remove(storage);}}
    public static long sectionVersion(int x,int y,int z){
        if(scene==null)return -1;
        for(Entry entry:scene.entries.values()){
            int dx=x-(entry.region.getOriginX()>>4),dy=y-(entry.region.getOriginY()>>4),dz=z-(entry.region.getOriginZ()>>4);
            if(dx<0||dx>=8||dy<0||dy>=4||dz<0||dz>=8)continue;
            for(int slot=0;slot<256;slot++)if(org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex.unpackX(slot)==dx&&org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex.unpackY(slot)==dy&&org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex.unpackZ(slot)==dz)return publications.slot(entry.storage,slot);
        }
        return -1;
    }
    public static Map<String,Object> evidence(){
        Map<String,Object> value=new LinkedHashMap<>();value.put("ready",scene!=null&&scene.ready);value.put("passSerial",passSerial);value.put("publicationEpoch",publications.epoch());value.put("preparedPublicationEpoch",preparedPublicationEpoch);value.put("submittedPublicationEpoch",submittedPublicationEpoch);value.put("regionDraws",regionDraws);value.put("passesPrepared",passesPrepared);value.put("sourceVersionUpdates",sourceVersionUpdates);value.put("metadataUploads",metadataUploads);value.put("orderCopiedBytes",orderCopiedBytes);value.put("orderComparedBytes",orderComparedBytes);value.put("cachedBatchesCreated",cachedBatchesCreated);value.put("cachedBatchesReused",cachedBatchesReused);value.put("residentBytes",scene==null||scene.buffers==null?0:scene.buffers.residentBytes());value.put("initiallyEmptyNativePasses",initiallyEmptyNativePasses);value.put("unsettledFades",unsettledFades);value.put("budgetFallbacks",budgetFallbacks);value.put("unsupported",unsupported);value.put("errors",errors);value.put("status",status);return value;
    }
    public static void reset(){Scene old=scene;scene=null;publications.clear();if(old!=null)old.close();failed=false;}
    public static void shutdown(){try{reset();}finally{TerrainResidentShaderFacts.shutdown();current.remove();}}
    public static void managerDeleted(Object manager){if(scene!=null&&scene.owner==manager)reset();}
    private static final class Entry {
        final RenderRegion region;
        final SectionRenderDataStorage storage;
        TerrainResidentRegionData data;
        ChunkRenderList all;
        Cached cached;
        GlTessellation tessellation;
        long serial;
        int slot;
        Entry(RenderRegion region,SectionRenderDataStorage storage){this.region=region;this.storage=storage;}
        void cache(Scene scene,GlTessellation value){
            if(cached==null||tessellation!=value){cached=new Cached(scene,this,value);tessellation=value;cachedBatchesCreated++;}
            else{((Batch)cached.getBatch()).refresh();cachedBatchesReused++;}
        }
    }
    private static final class Scene {
        final RenderSectionManager owner;
        final IdentityHashMap<RenderRegion,Entry> entries=new IdentityHashMap<>();
        final IdentityHashMap<SectionRenderDataStorage,Entry> byStorage=new IdentityHashMap<>();
        TerrainResidentGpuBuffers buffers;
        int capacity;
        boolean ready;
        long epoch;
        Scene(RenderSectionManager owner){this.owner=owner;}
        Iterator<ChunkRenderList> prepare(Scope value,DefaultChunkRenderer renderer,boolean cull,TerrainResidentShaderFacts.Fact fact)throws Throwable{
            ready=false;
            Collection<RenderRegion> loaded=api.regions(owner).getLoadedRegions();if(loaded.size()>1024){budgetFallbacks++;status="Loaded region inventory exceeds the resident bound";return null;}
            var present=new IdentityHashMap<RenderRegion,Boolean>();for(RenderRegion region:loaded){if(region==null||present.put(region,true)!=null)throw new IllegalStateException("Unknown native region identity");}
            var prefix=new ArrayList<ChunkRenderList>();var nativeLists=new IdentityHashMap<RenderRegion,ChunkRenderList>();
            for(var iterator=value.original.iterator(false);iterator.hasNext();){ChunkRenderList list=iterator.next();RenderRegion region=list.getRegion();if(!present.containsKey(region)||nativeLists.put(region,list)!=null){unsupported++;status="Native region order is stale or duplicated";return null;}prefix.add(list);}
            var eligible=new ArrayList<Entry>();long now=System.nanoTime();int maximum=0;
            for(RenderRegion region:loaded){
                SectionRenderDataStorage storage=region.getStorage(value.pass);if(storage==null||storage.isEmpty())continue;
                if(!api.storage(storage)){unsupported++;status="Unsupported resident section layout";return null;}
                if(!fact.settled(now,region.getNewestSectionLoadTime())){unsettledFades++;status="Native section fade has not settled";return null;}
                long version=publications.current(storage);if(version<0){unsupported++;status="Section publication metadata is unavailable";return null;}
                Entry entry=entries.get(region);if(entry==null||entry.storage!=storage){entry=new Entry(region,storage);entries.put(region,entry);}
                if(entry.data==null||entry.data.version!=version){entry.data=new TerrainResidentRegionData(region,storage,version);entry.all=new ChunkRenderList(region);for(int slot=0;slot<256;slot++)if(storage.getSliceMask(slot)!=0)entry.all.add(slot,1);}
                if(entry.data.sectionsWithGeometry==0)continue;maximum=Math.max(maximum,entry.data.maximumElements);eligible.add(entry);
            }
            entries.keySet().removeIf(region->!present.containsKey(region));
            if(eligible.isEmpty()){status="No resident opaque geometry";return null;}
            eligible.sort(Comparator.comparingInt((Entry entry)->entry.region.getOriginX()).thenComparingInt(entry->entry.region.getOriginY()).thenComparingInt(entry->entry.region.getOriginZ()));
            for(int i=1;i<eligible.size();i++){RenderRegion a=eligible.get(i-1).region,b=eligible.get(i).region;if(a.getOriginX()==b.getOriginX()&&a.getOriginY()==b.getOriginY()&&a.getOriginZ()==b.getOriginZ()){unsupported++;status="Duplicate native region coordinates";return null;}}
            int count=eligible.size();
            if(buffers==null||capacity<count){
                closeBuffers();capacity=Math.min(1024,Integer.highestOneBit(Math.max(1,count-1))<<1);if(count==1)capacity=1;
                if(!TerrainGpuBudget.reserve(this,(long)capacity*TerrainResidentGpuBuffers.REGION_BYTES)){budgetFallbacks++;status="Shared terrain command budget is full";return null;}
                try(var input=TerrainResidentScene.class.getResourceAsStream("/assets/arcadium/shaders/terrain_resident_commands.comp")){
                    if(input==null)throw new IllegalStateException("Resident terrain compute resource is absent");buffers=new TerrainResidentGpuBuffers(value.device,capacity,new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }catch(Throwable error){TerrainGpuBudget.release(this);throw error;}
            }
            api.ensureIndices(renderer,value.device,value.pass,maximum);
            Object[] identities=new Object[count];TerrainResidentRegionData[] rows=new TerrainResidentRegionData[count];byte[][] orders=new byte[count][];int[] sizes=new int[count];
            byStorage.clear();
            for(int i=0;i<count;i++){
                Entry entry=eligible.get(i);entry.slot=i;identities[i]=entry.storage;rows[i]=entry.data;ChunkRenderList nativeList=nativeLists.get(entry.region);if(nativeList!=null){orders[i]=nativeList.getSectionsWithGeometry();sizes[i]=nativeList.getSectionsWithGeometryCount();}
                GlTessellation tessellation=api.tessellation(renderer,value.device,entry.region,value.pass);if(tessellation==null)throw new IllegalStateException("Native terrain tessellation is unavailable");
                entry.cache(this,tessellation);byStorage.put(entry.storage,entry);
            }
            float[] clip=new Matrix4f(value.matrices.projection()).mul(value.matrices.modelView()).get(new float[16]);CameraTransform face=value.face,render=value.render;
            long uploads=buffers.meshUploads,copied=buffers.orderCopiedBytes(),compared=buffers.orderComparedBytes();
            buffers.prepare(identities,rows,count,orders,sizes,clip,render.intX,render.intY,render.intZ,render.fracX,render.fracY,render.fracZ,face.intX,face.intY,face.intZ,face.fracX,face.fracY,face.fracZ,api.searchDistance(owner,value.matrices.projection()),true,cull);
            metadataUploads+=buffers.meshUploads-uploads;orderCopiedBytes+=buffers.orderCopiedBytes()-copied;orderComparedBytes+=buffers.orderComparedBytes()-compared;
            for(Entry entry:eligible)if(publications.current(entry.storage)!=entry.data.version){unsupported++;status="Native section changed during resident preflight";return null;}
            epoch=publications.epoch();publicationEpoch=epoch;preparedPublicationEpoch=epoch;for(Entry entry:eligible)entry.serial=value.serial;
            for(Entry entry:eligible)if(!nativeLists.containsKey(entry.region))prefix.add(entry.all);
            ready=true;return prefix.iterator();
        }
        void closeBuffers(){TerrainResidentGpuBuffers old=buffers;buffers=null;capacity=0;try{if(old!=null)old.close();}finally{TerrainGpuBudget.release(this);}}
        void close(){
            ready=false;boolean own=api!=null&&!api.managed();if(own)RenderDevice.enterManagedCode();
            try{closeBuffers();entries.clear();byStorage.clear();}finally{if(own)RenderDevice.exitManagedCode();}
        }
    }
    private static final class Cached extends CachedBatch {
        final Scene scene;
        final Entry entry;
        Cached(Scene scene,Entry entry,GlTessellation tessellation){super(new Batch(scene,entry),tessellation,new byte[0],0,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);this.scene=scene;this.entry=entry;}
        @Override public boolean isValidFor(byte[] ignored,int length,int x,int y,int z){Scope value=current.get();return value!=null&&value.ready&&scene.ready&&entry.serial==value.serial&&publications.current(entry.storage)==entry.data.version;}
    }
    private static final class Batch extends MultiDrawBatch {
        private final Scene scene;
        private final Entry entry;
        Batch(Scene scene,Entry entry){super(1792);this.scene=scene;this.entry=entry;size=1792;maxElementCount=entry.data.maximumElements;}
        void refresh(){maxElementCount=entry.data.maximumElements;}
        @Override public void upload(CommandList device){}
        @Override public void execute(CommandList device,GlTessellation tessellation,GlPrimitiveType primitive){
            Scope value=current.get();if(value==null||!value.ready||!scene.ready||entry.serial!=value.serial)throw new IllegalStateException("Resident terrain frame expired before submission");
            int oldIndirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING),oldParameter=GL11.glGetInteger(ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB);
            try{
                GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,scene.buffers.commandHandle());GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB,scene.buffers.countHandle());
                try(var draw=device.beginTessellating(tessellation)){
                    if(GL.getCapabilities().OpenGL46)GL46.glMultiDrawElementsIndirectCount(primitive.getId(),GL11.GL_UNSIGNED_INT,scene.buffers.commandOffset(entry.slot),scene.buffers.countOffset(entry.slot),1792,20);
                    else ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(primitive.getId(),GL11.GL_UNSIGNED_INT,scene.buffers.commandOffset(entry.slot),scene.buffers.countOffset(entry.slot),1792,20);
                    regionDraws++;submittedPublicationEpoch=scene.epoch;
                }
            }finally{GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,oldIndirect);GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB,oldParameter);}
        }
        @Override public void delete(){} // Buffers are owned by the scene.
        @Override public void appendDrawCommand(int base,int count,long pointer){throw new UnsupportedOperationException();}
        @Override public void mergeIntoLastCommand(int count){throw new UnsupportedOperationException();}
    }
}
