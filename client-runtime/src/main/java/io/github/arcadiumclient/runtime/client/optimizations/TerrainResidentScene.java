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
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

public final class TerrainResidentScene {
    private static final ThreadLocal<Scope> current=new ThreadLocal<>();
    private static final TerrainResidentPublications publications=new TerrainResidentPublications(4096);
    private static TerrainResidentPassBook<Scene> book;
    private static final List<TerrainResidentPassBook<Scene>> retiring=new ArrayList<>(2);
    private static TerrainResidentNativeAccess api;
    private static boolean failed;
    private static long resourceEpoch;
    private static String status="Waiting for resident terrain";
    public static long passSerial,passesPrepared,regionDraws,sourceVersionUpdates,metadataUploads,orderCopiedBytes,orderComparedBytes;
    public static long publicationEpoch,preparedPublicationEpoch,submittedPublicationEpoch,initiallyEmptyNativePasses,unsettledFades,budgetFallbacks,unsupported,errors;
    public static long cachedBatchesCreated,cachedBatchesReused;
    static {
        GpuTerrainCommands.attachResident(new GpuTerrainCommands.Backend(){
            public String status(){return "GPU resident terrain: "+status;}
            public long residentBytes(){return TerrainResidentScene.residentBytes();}
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
        private TerrainResidentPassBook<Scene> book;
        private Scene scene;
        private Object admittedProgram;
        private boolean submissionHook,cull;
        private TerrainResidentPublishedPreflight.Prepared published;
        private TerrainResidentPassSubmission<RenderRegion> submission;
        private Boundary boundary;
        private long resources;
        private int[] cameraWords;private float[] fallbackClip;private float fallbackDistance;
        private Scope(Scope previous,RenderSectionManager manager,Object renderer,ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable original,TerrainRenderPass pass,CameraTransform face,CameraTransform render){this.previous=previous;this.manager=manager;this.renderer=renderer;this.matrices=matrices;this.device=device;this.original=original;this.pass=pass;this.face=face;this.render=render;serial=++passSerial;}
    }
    public static Scope enter(RenderSectionManager manager,Object renderer,ChunkRenderMatrices matrices,CommandList device,ChunkRenderListIterable original,TerrainRenderPass pass,CameraTransform face,CameraTransform render){
        if(!requested()||!GpuTerrainCommands.enabled||pass==null||!pass.name().equals("solid")&&!pass.name().equals("cutout_mipped"))return null;
        Scope value=new Scope(current.get(),manager,renderer,matrices,device,original,pass,face,render);current.set(value);return value;
    }
    public static void exit(Scope value){
        if(value==null)return;
        if(current.get()!=value){failed=true;errors++;status="Unbalanced resident terrain scope";current.remove();return;}
        if(!value.entered&&!value.original.hasPass(value.pass)){initiallyEmptyNativePasses++;status="Native pass has no listed geometry; using native rendering";}
        if(value.previous==null)current.remove();else current.set(value.previous);
    }
    public static boolean admit(DefaultChunkRenderer renderer,boolean nativeHasPass){
        if(TerrainResidentPublishedPreflight.BOOT)return eventAdmit(renderer,nativeHasPass);
        if(nativeHasPass)return true;
        Scope value=current.get();
        if(value==null||failed||value.previous!=null||value.renderer!=renderer)return false;
        try{
            if(!TerrainResidentGpuBuffers.trustedContext())return false;
            var caps=GL.getCapabilities();
            if(!caps.OpenGL43||!(caps.OpenGL46||caps.GL_ARB_indirect_parameters))return false;
            if(api==null)api=new TerrainResidentNativeAccess();
            if(!api.residentPass(renderer,value.pass)||!api.normalPass(value.manager)
                    ||!TerrainResidentNativeAccess.cameras(value.matrices,value.face,value.render))return false;
            Collection<RenderRegion> regions=api.regions(value.manager).getLoadedRegions();
            if(regions.size()>1024)return false;
            var ticket=api.cachedAdmission(renderer,value.pass);if(ticket==null)return false;
            long now=System.nanoTime();boolean geometry=false;
            for(RenderRegion region:regions){
                if(region==null)return false;
                SectionRenderDataStorage storage=region.getStorage(value.pass);
                if(storage==null||storage.isEmpty())continue;
                if(!api.storage(storage)||!ticket.fact().settled(now,region.getNewestSectionLoadTime()))return false;
                geometry=true;
            }
            if(geometry)value.admittedProgram=ticket.program();return geometry;
        }catch(Throwable error){unsupported++;status="Resident pass admission unavailable";return false;}
    }
    public static Iterator<ChunkRenderList> prepare(DefaultChunkRenderer renderer,Iterator<ChunkRenderList> original,boolean cull){
        Scope value=current.get();if(value==null||failed||value.previous!=null||value.renderer!=renderer)return original;value.entered=true;value.ready=false;
        if(TerrainResidentPublishedPreflight.BOOT)return prepareEvents(value,renderer,original,cull);
        try{
            var caps=GL.getCapabilities();if(!caps.OpenGL43||!(caps.OpenGL46||caps.GL_ARB_indirect_parameters)){unsupported++;status="Compute and indirect-count rendering are unavailable";return original;}
            if(api==null)api=new TerrainResidentNativeAccess();
            if(!api.normalPass(value.manager)||!TerrainResidentNativeAccess.cameras(value.matrices,value.face,value.render)){unsupported++;status="Unsupported terrain pass or camera";return original;}
            var fact=api.program(renderer,value.pass);if(fact==null||!api.admittedProgram(renderer,value.admittedProgram)){unsupported++;status="Native terrain shader provenance is unavailable";return original;}
            collectRetiring();if(!retiring.isEmpty()){status="Resident native cleanup is still owned; using native terrain";return original;}
            if(book==null||!book.live(value.manager)){reset();book=new TerrainResidentPassBook<>(value.manager);}
            Scene scene=book.get(value.pass);
            if(scene==null){scene=new Scene(value.manager,value.pass);if(!book.put(value.pass,scene)){unsupported++;status="Resident pass inventory is full";return original;}}
            if(scene.failed)return original;
            value.book=book;value.scene=scene;
            Iterator<ChunkRenderList> replacement=scene.prepare(value,renderer,cull,fact);
            if(replacement==null)return original;
            value.ready=true;scene.preparations++;passesPrepared++;status="Resident "+value.pass.name()+" terrain prepared";return replacement;
        }catch(Throwable error){if(value.scene!=null)value.scene.failed=true;errors++;status="Resident terrain preflight failed: "+error.getClass().getSimpleName();return original;}
    }
    public static CachedBatch select(SectionRenderDataStorage storage,TerrainRenderPass pass){
        Scope value=current.get();
        if(TerrainResidentPublishedPreflight.BOOT){if(value==null||value.published==null||value.scene==null||pass!=value.pass)return null;var entry=value.scene.eventEntries.get(storage);return entry==null?null:entry.cached;}
        if(!valid(value)||pass!=value.pass)return null;
        Entry entry=value.scene.byStorage.get(storage);return entry!=null&&entry.serial==value.serial&&publications.current(storage)==entry.data.version?entry.cached:null;
    }
    public static void sourceChanged(Object storage,int slot){
        if(TerrainResidentPublishedPreflight.BOOT){sourceVersionUpdates++;return;}
        if(book==null)return;invalidateScenes();long version=publications.changed(storage,slot);sourceVersionUpdates++;publicationEpoch=publications.epoch();if(version<0){failed=true;unsupported++;status="Unknown native section publication";}
    }
    public static void sourceChanged(Object storage){
        if(TerrainResidentPublishedPreflight.BOOT){sourceVersionUpdates++;return;}
        if(book==null)return;invalidateScenes();long version=publications.changed(storage);sourceVersionUpdates++;publicationEpoch=publications.epoch();if(version<0){failed=true;unsupported++;status="Unknown native arena publication";}
    }
    public static void released(Object storage){if(TerrainResidentPublishedPreflight.BOOT)return;publications.released(storage);if(book!=null)for(Scene scene:book.values()){scene.ready=false;scene.byStorage.remove(storage);scene.entries.values().removeIf(entry->entry.storage==storage);}}
    private static void invalidateScenes(){for(Scene scene:book.values())scene.ready=false;}
    private static boolean valid(Scope value){return value!=null&&value.ready&&book!=null&&value.book==book&&book.live(value.manager)&&value.scene!=null&&book.get(value.pass)==value.scene&&value.scene.ready;}
    private static boolean ready(){if(book!=null)for(Scene scene:book.values())if(scene.ready)return true;return false;}
    private static long residentBytes(){long bytes=0;if(book!=null)for(Scene scene:book.values())bytes+=scene.retainedBytes();for(var old:retiring)for(Scene scene:old.values())bytes+=scene.retainedBytes();return bytes;}
    private static Map<String,Object> passEvidence(){
        var result=new LinkedHashMap<String,Object>();
        if(book!=null)for(Scene scene:book.values())result.put(scene.pass.name(),Map.of("ready",scene.ready,"failed",scene.failed,
            "regions",scene.byStorage.size(),"preparations",scene.preparations,"submissions",scene.submissions,
            "publicationEpoch",scene.epoch,"residentBytes",scene.buffers==null?0:scene.buffers.residentBytes()));
        return result;
    }
    public static long sectionVersion(int x,int y,int z){
        return sectionVersion(x,y,z,"solid");
    }
    public static long sectionVersion(int x,int y,int z,String passName){
        if(book==null)return -1;
        for(Scene scene:book.values())if(scene.pass.name().equals(passName))for(Entry entry:scene.entries.values()){
            int dx=x-(entry.region.getOriginX()>>4),dy=y-(entry.region.getOriginY()>>4),dz=z-(entry.region.getOriginZ()>>4);
            if(dx<0||dx>=8||dy<0||dy>=4||dz<0||dz>=8)continue;
            for(int slot=0;slot<256;slot++)if(org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex.unpackX(slot)==dx&&org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex.unpackY(slot)==dy&&org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex.unpackZ(slot)==dz)return publications.slot(entry.storage,slot);
        }
        return -1;
    }
    public static Map<String,Object> evidence(){
        Map<String,Object> value=new LinkedHashMap<>();value.put("ready",ready());value.put("retainedPasses",book==null?0:book.size());value.put("retiredBooks",retiring.size());value.put("reservedGpuBytes",TerrainGpuBudget.bytes());value.put("reservedGpuOwners",TerrainGpuBudget.owners());value.put("passes",passEvidence());value.put("passSerial",passSerial);value.put("publicationEpoch",publications.epoch());value.put("preparedPublicationEpoch",preparedPublicationEpoch);value.put("submittedPublicationEpoch",submittedPublicationEpoch);value.put("regionDraws",regionDraws);value.put("passesPrepared",passesPrepared);value.put("sourceVersionUpdates",sourceVersionUpdates);value.put("metadataUploads",metadataUploads);value.put("orderCopiedBytes",orderCopiedBytes);value.put("orderComparedBytes",orderComparedBytes);value.put("cachedBatchesCreated",cachedBatchesCreated);value.put("cachedBatchesReused",cachedBatchesReused);value.put("residentBytes",residentBytes());value.put("initiallyEmptyNativePasses",initiallyEmptyNativePasses);value.put("unsettledFades",unsettledFades);value.put("budgetFallbacks",budgetFallbacks);value.put("unsupported",unsupported);value.put("errors",errors);value.put("status",status);return value;
    }
    public static Map<String,Object> eventEvidence(){
        var result=new LinkedHashMap<String,Object>();result.put("startupEnabled",TerrainResidentPublishedPreflight.BOOT);var passes=new LinkedHashMap<String,Object>();
        if(book!=null)for(Scene scene:book.values())if(scene.published!=null){var values=new LinkedHashMap<String,Object>();values.put("retainedBytes",scene.published.retainedBytes());values.put("preparations",scene.preparations);values.put("orderRebuilds",scene.published.orderRebuilds);values.put("steadyOrderReuses",scene.published.steadyOrderReuses);values.put("explicitReplayRequests",scene.published.replayRequests);
            if(scene.latestSubmission!=null){var state=scene.latestSubmission;values.put("gpuIssued",state.gpuIssued);values.put("nativeIssued",state.nativeIssued);values.put("empty",state.empty);values.put("ambiguous",state.ambiguous);values.put("duplicatesRefused",state.duplicates);values.put("lateFallbacks",state.lateFallbacks);values.put("aborted",state.aborted());}
            if(scene.latestPublished!=null){var frame=scene.latestPublished.frame();values.put("sourceEpoch",frame.epoch);values.put("nativeOrderPublication",frame.orderPublication);values.put("activeRegions",frame.activeRegions);values.put("slotHighWater",frame.highWater);}passes.put(scene.pass.name(),values);}
        result.put("passes",passes);result.put("budgetBytes",TerrainGpuBudget.bytes());result.put("budgetOwners",TerrainGpuBudget.owners());return result;
    }
    private static void collectRetiring(){
        Throwable failure=null;for(var iterator=retiring.iterator();iterator.hasNext();){var old=iterator.next();try{old.close();if(old.size()==0)iterator.remove();}catch(Throwable error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}}
        if(failure instanceof Error error)throw error;if(failure instanceof RuntimeException error)throw error;if(failure!=null)throw new IllegalStateException("Resident retirement failed",failure);
    }
    static long resourceToken(){return resourceEpoch;}
    static boolean resourceCurrent(long token){return token==resourceEpoch;}
    public static void reset(){
        if(resourceEpoch==Long.MAX_VALUE){failed=true;throw new IllegalStateException("Resident resource epoch exhausted");}resourceEpoch++;
        TerrainResidentPassBook<Scene> old=book;book=null;publications.clear();
        if(old!=null&&!retiring.contains(old)){if(retiring.size()==2){book=old;failed=true;throw new IllegalStateException("Resident retirement book bound exceeded");}retiring.add(old);}
        try{collectRetiring();}finally{failed=false;}
    }
    public static void shutdown(){try{reset();}finally{if(TerrainResidentPublishedPreflight.BOOT)TerrainResidentInventoryHooks.shutdown();TerrainResidentShaderFacts.shutdown();current.remove();}}
    public static void managerDeleted(Object manager){if(book!=null&&book.owner==manager)reset();}
    public static void submissionEntered(DefaultChunkRenderer renderer){Scope value=current.get();if(value!=null&&value.renderer==renderer)value.submissionHook=true;}
    static void closeRetained(AutoCloseable close,java.util.function.BooleanSupplier empty,Object budgetOwner){
        try{close.close();}catch(RuntimeException|Error failure){throw failure;}catch(Exception failure){throw new IllegalStateException(failure);}finally{if(empty.getAsBoolean())TerrainGpuBudget.release(budgetOwner);}
    }
    static <R> TerrainResidentPassSubmission.Result drive(TerrainResidentPassSubmission<R> submission,R region,TerrainResidentPassSubmission.Driver<R> driver)throws Throwable{return submission.run(region,driver);}
    private static Scene eventScene(Scope value)throws ReflectiveOperationException{
        collectRetiring();if(!retiring.isEmpty())return null;if(book==null||!book.live(value.manager)){reset();book=new TerrainResidentPassBook<>(value.manager);}
        Scene scene=book.get(value.pass);if(scene==null){scene=new Scene(value.manager,value.pass);if(!book.put(value.pass,scene))return null;}value.book=book;value.scene=scene;return scene;
    }
    private static boolean eventAdmit(DefaultChunkRenderer renderer,boolean nativeHasPass){
        Scope value=current.get();if(value==null||value.previous!=null||!value.submissionHook||value.renderer!=renderer)return nativeHasPass;
        try{if(api==null)api=new TerrainResidentNativeAccess();if(!api.normalPass(value.manager)||!api.residentPass(renderer,value.pass))return nativeHasPass;Scene scene=eventScene(value);return scene!=null&&scene.published().admit(renderer,value.original,nativeHasPass,value.matrices,value.face,value.render);}catch(Throwable unknown){return nativeHasPass;}
    }
    private static int[] cameraWords(Scope scope){int[] words=new int[32];int at=0;for(int col=0;col<4;col++)for(int row=0;row<4;row++){words[at++]=Float.floatToRawIntBits(scope.matrices.projection().get(col,row));words[at++]=Float.floatToRawIntBits(scope.matrices.modelView().get(col,row));}return words;}
    private static boolean cameraUnchanged(Scope scope){if(scope.cameraWords==null)return false;int at=0;for(int col=0;col<4;col++)for(int row=0;row<4;row++){if(scope.cameraWords[at++]!=Float.floatToRawIntBits(scope.matrices.projection().get(col,row))||scope.cameraWords[at++]!=Float.floatToRawIntBits(scope.matrices.modelView().get(col,row)))return false;}return true;}
    private static Iterator<ChunkRenderList> prepareEvents(Scope value,DefaultChunkRenderer renderer,Iterator<ChunkRenderList> original,boolean cull){
        if(!value.submissionHook)return original;
        try{
            if(api==null)api=new TerrainResidentNativeAccess();if(!api.normalPass(value.manager)||!api.residentPass(renderer,value.pass))return original;Scene scene=eventScene(value);if(scene==null)return original;
            var prepared=scene.published().prepare(renderer,value.device,value.original,value.matrices,value.face,value.render,cull);if(prepared==null)return original;
            if(scene.eventLayout!=prepared.frame().layoutVersion){
                scene.eventPresent.clear();scene.eventPrefix.clear();
                for(var ref:prepared.frame().drawOrder()){var region=(RenderRegion)ref.region();if(ref.nativeRow()!=null)scene.eventPrefix.put(region,true);if(ref.storage()!=null){var storage=(SectionRenderDataStorage)ref.storage();scene.eventPresent.put(storage,true);var old=scene.eventEntries.get(storage);if(old==null||old.slot!=ref.gpuSlot()||old.generation!=ref.storageGeneration()||old.bindings!=ref.bindings()){var tess=api.tessellation(renderer,value.device,region,value.pass);scene.eventEntries.put(storage,new PublishedEntry(scene,region,storage,ref.gpuSlot(),ref.storageGeneration(),ref.bindings(),tess,prepared.frame().maximumElements));cachedBatchesCreated++;}else cachedBatchesReused++;}}
                scene.eventEntries.keySet().removeIf(storage->!scene.eventPresent.containsKey(storage));scene.eventLayout=prepared.frame().layoutVersion;
            }
            if(!prepared.valid(value.original))return original;value.published=prepared;value.submission=new TerrainResidentPassSubmission<>();value.boundary=new Boundary(value);scene.latestSubmission=value.submission;scene.latestPublished=prepared;value.cull=cull;value.resources=resourceEpoch;value.cameraWords=cameraWords(value);value.fallbackClip=new float[16];scene.published.copyClip(value.fallbackClip);value.fallbackDistance=scene.published.distance();value.ready=scene.ready=true;scene.preparations++;passesPrepared++;scene.epoch=prepared.frame().epoch;preparedPublicationEpoch=scene.epoch;
            var iterator=prepared.drawOrder().iterator();return new Iterator<>(){public boolean hasNext(){return !value.submission.aborted()&&TerrainResidentInventoryHooks.alive(value.manager)&&iterator.hasNext();}public ChunkRenderList next(){if(!hasNext())throw new NoSuchElementException();return iterator.next();}};
        }catch(Throwable unknown){value.ready=false;errors++;status="Published preflight declined: "+unknown.getClass().getSimpleName();return original;}
    }
    private static boolean publishedGpuValid(Scope value,DefaultChunkRenderer renderer){
        if(!valid(value)||value.resources!=resourceEpoch||value.published==null||!value.published.valid(value.original)||!cameraUnchanged(value)||value.submission.aborted())return false;
        try{value.scene.published.requireOwner();return api.normalPass(value.manager)&&api.program(renderer,value.pass)!=null;}catch(Throwable unknown){return false;}
    }
    public static void submit(DefaultChunkRenderer renderer,MultiDrawBatch selected,CommandList device,GlTessellation tessellation,GlPrimitiveType primitive,RenderRegion region,SectionRenderDataStorage storage,ChunkRenderList list,Operation<Void> original){
        Scope value=current.get();if(!TerrainResidentPublishedPreflight.BOOT||value==null||value.published==null){original.call(selected,device,tessellation,primitive);return;}
        var boundary=value.boundary;if(boundary.inUse){value.submission.fallback();throw new IllegalStateException("Reentrant same-pass submission");}boundary.set(renderer,selected,region,storage,list,primitive);boundary.inUse=true;try{original.call(boundary,device,tessellation,primitive);}finally{boundary.inUse=false;}
    }
    private static final class PublishedEntry {
        final RenderRegion region;final SectionRenderDataStorage storage;final int slot;final long generation,bindings;final CachedBatch cached;
        PublishedEntry(Scene scene,RenderRegion region,SectionRenderDataStorage storage,int slot,long generation,long bindings,GlTessellation tess,int maximum){this.region=region;this.storage=storage;this.slot=slot;this.generation=generation;this.bindings=bindings;cached=new CachedBatch(new PublishedBatch(scene,this,maximum),tess,new byte[0],0,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE){@Override public boolean isValidFor(byte[] x,int n,int a,int b,int c){return current.get()!=null&&current.get().scene==scene&&current.get().published!=null;}};}
    }
    private static final class PublishedBatch extends MultiDrawBatch {
        final Scene scene;final PublishedEntry entry;
        PublishedBatch(Scene scene,PublishedEntry entry,int maximum){super(1792);this.scene=scene;this.entry=entry;size=1792;maxElementCount=maximum;}
        public void execute(CommandList d,GlTessellation t,GlPrimitiveType p){throw new IllegalStateException("Published batch requires the actual final native submission hook");}
        public void upload(CommandList d){}public void delete(){}public void appendDrawCommand(int b,int n,long p){throw new UnsupportedOperationException();}public void mergeIntoLastCommand(int n){throw new UnsupportedOperationException();}
    }
    private static final class Boundary extends MultiDrawBatch implements TerrainResidentPassSubmission.Driver<RenderRegion> {
        final Scope scope;DefaultChunkRenderer renderer;MultiDrawBatch selected;RenderRegion region;SectionRenderDataStorage storage;ChunkRenderList list;GlPrimitiveType primitive;CommandList activeDevice;GlTessellation activeTess;boolean inUse;
        Boundary(Scope scope){super(0);this.scope=scope;}
        void set(DefaultChunkRenderer renderer,MultiDrawBatch selected,RenderRegion region,SectionRenderDataStorage storage,ChunkRenderList list,GlPrimitiveType primitive){this.renderer=renderer;this.selected=selected;this.region=region;this.storage=storage;this.list=list;this.primitive=primitive;size=selected.size();maxElementCount=selected.getIndexBufferSize();}
        public boolean alive(){return current.get()==scope&&TerrainResidentInventoryHooks.alive(scope.manager);}
        public boolean gpuValid(){return selected instanceof PublishedBatch&&publishedGpuValid(scope,renderer);}
        public TerrainResidentPassSubmission.Result execute(RenderRegion unused,boolean gpu)throws Throwable{return gpu?gpu(activeDevice,activeTess):nativeFresh(activeDevice);}
        @Override public void execute(CommandList device,GlTessellation tess,GlPrimitiveType ignored){
            activeDevice=device;activeTess=tess;
            try{drive(scope.submission,region,this);}catch(RuntimeException|Error failure){throw failure;}catch(Throwable failure){throw new IllegalStateException(failure);}finally{activeDevice=null;activeTess=null;}
        }
        private TerrainResidentPassSubmission.Result gpu(CommandList device,GlTessellation tess)throws Throwable{
            PublishedBatch batch=(PublishedBatch)selected;var buffers=scope.published.buffers();int oldIndirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING),oldParameter=GL11.glGetInteger(ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB);boolean late=false;
            try{
                GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,buffers.commandHandle());GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB,buffers.countHandle());
                try(var draw=device.beginTessellating(tess)){
                    if(!publishedGpuValid(scope,renderer))late=true;
                    else{if(GL.getCapabilities().OpenGL46)GL46.glMultiDrawElementsIndirectCount(primitive.getId(),GL11.GL_UNSIGNED_INT,buffers.commandOffset(batch.entry.slot),buffers.countOffset(batch.entry.slot),1792,20);else ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(primitive.getId(),GL11.GL_UNSIGNED_INT,buffers.commandOffset(batch.entry.slot),buffers.countOffset(batch.entry.slot),1792,20);regionDraws++;scope.scene.submissions++;submittedPublicationEpoch=scope.scene.epoch;}
                }
            }finally{GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,oldIndirect);GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB,oldParameter);}
            if(late){scope.submission.fallback();return nativeFresh(device);}return TerrainResidentPassSubmission.Result.GPU;
        }
        private TerrainResidentPassSubmission.Result nativeFresh(CommandList device)throws Throwable{
            scope.submission.fallback();
            try{scope.scene.published.requireOwner();}catch(Throwable foreign){return TerrainResidentPassSubmission.Result.ABORT;}
            if(scope.submission.aborted())return TerrainResidentPassSubmission.Result.ABORT;
            for(int attempt=0;attempt<2;attempt++){
                if(!TerrainResidentInventoryHooks.alive(scope.manager)||api.program(renderer,scope.pass)==null)return TerrainResidentPassSubmission.Result.ABORT;
                var actual=region.getStorage(scope.pass);if(actual==null||actual.isEmpty())return TerrainResidentPassSubmission.Result.EMPTY;
                long sequence=TerrainResidentInventoryHooks.nativeSequence(scope.manager);int revision=region.getDataRevision();Object resources=region.getResources();ChunkRenderList nativeRow=list;
                boolean prefix=scope.scene.eventPrefix.containsKey(region);byte[] nativeRanks=prefix?list.getSectionsWithGeometry():new byte[0];int nativeCount=prefix?list.getSectionsWithGeometryCount():0;
                byte[] sections=TerrainResidentNativeFallbackPlan.order(nativeRanks,nativeCount,section->{
                    if(actual.getSliceMask(section)==0)return false;int x=region.getOriginX()+LocalSectionIndex.unpackX(section)*16,y=region.getOriginY()+LocalSectionIndex.unpackY(section)*16,z=region.getOriginZ()+LocalSectionIndex.unpackZ(section)*16;
                    return TerrainResidentFrustumPlan.withinDistance(x,y,z,scope.face.intX,scope.face.intY,scope.face.intZ,scope.face.fracX,scope.face.fracY,scope.face.fracZ,scope.fallbackDistance)&&TerrainResidentFrustumPlan.visible(scope.fallbackClip,x,y,z,scope.render.intX,scope.render.intY,scope.render.intZ,scope.render.fracX,scope.render.fracY,scope.render.fracZ);
                });nativeRow=new ChunkRenderList(region);for(byte section:sections)nativeRow.add(section&255,1);
                MultiDrawBatch fresh=BatchAssembler.fillRegion(region,actual,nativeRow,scope.face,scope.pass,scope.cull);if(fresh==null)return TerrainResidentPassSubmission.Result.EMPTY;
                try{
                    fresh.upload(device);if(fresh.isEmpty())return TerrainResidentPassSubmission.Result.EMPTY;
                    api.ensureIndices(renderer,device,scope.pass,fresh.getIndexBufferSize());GlTessellation freshTess=api.tessellation(renderer,device,region,scope.pass);
                    if(sequence!=TerrainResidentInventoryHooks.nativeSequence(scope.manager)||revision!=region.getDataRevision()||resources!=region.getResources()||actual!=region.getStorage(scope.pass))continue;
                    if(!TerrainResidentInventoryHooks.alive(scope.manager)||api.program(renderer,scope.pass)==null)return TerrainResidentPassSubmission.Result.ABORT;
                    fresh.execute(device,freshTess,primitive);return TerrainResidentPassSubmission.Result.NATIVE;
                }finally{fresh.delete();}
            }
            return TerrainResidentPassSubmission.Result.ABORT;
        }
        public void upload(CommandList d){}public void delete(){}public void appendDrawCommand(int b,int n,long p){throw new UnsupportedOperationException();}public void mergeIntoLastCommand(int n){throw new UnsupportedOperationException();}
    }
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
    private static final class Scene implements AutoCloseable {
        final RenderSectionManager owner;
        final TerrainRenderPass pass;
        final IdentityHashMap<RenderRegion,Entry> entries=new IdentityHashMap<>();
        final IdentityHashMap<SectionRenderDataStorage,Entry> byStorage=new IdentityHashMap<>();
        final IdentityHashMap<RenderRegion,Boolean> present=new IdentityHashMap<>();
        final IdentityHashMap<RenderRegion,ChunkRenderList> nativeLists=new IdentityHashMap<>();
        final ArrayList<ChunkRenderList> prefix=new ArrayList<>();
        final ArrayList<Entry> eligible=new ArrayList<>();
        Object[] identities=new Object[0];TerrainResidentRegionData[] rows=new TerrainResidentRegionData[0];byte[][] orders=new byte[0][];int[] sizes=new int[0];
        final Matrix4f clipMatrix=new Matrix4f();final float[] clip=new float[16];
        int scratchCount;

        TerrainResidentGpuBuffers buffers;
        TerrainResidentResources pendingAllocation;
        int capacity;
        boolean ready,failed;
        long epoch,preparations,submissions;
        TerrainResidentPublishedPreflight published;long eventLayout=-1;
        TerrainResidentPassSubmission<RenderRegion> latestSubmission;TerrainResidentPublishedPreflight.Prepared latestPublished;
        final IdentityHashMap<SectionRenderDataStorage,PublishedEntry> eventEntries=new IdentityHashMap<>();
        final IdentityHashMap<RenderRegion,Boolean> eventPrefix=new IdentityHashMap<>();
        final IdentityHashMap<SectionRenderDataStorage,Boolean> eventPresent=new IdentityHashMap<>();
        Scene(RenderSectionManager owner,TerrainRenderPass pass){this.owner=owner;this.pass=pass;}
        TerrainResidentPublishedPreflight published()throws ReflectiveOperationException{if(published==null)published=new TerrainResidentPublishedPreflight(owner,pass,pass.name().equals("solid")?0:1);return published;}
        Iterator<ChunkRenderList> prepare(Scope value,DefaultChunkRenderer renderer,boolean cull,TerrainResidentShaderFacts.Fact fact)throws Throwable{
            ready=false;
            if(value.pass!=pass||!value.book.live(owner))return null;
            Collection<RenderRegion> loaded=api.regions(owner).getLoadedRegions();if(loaded.size()>1024){budgetFallbacks++;status="Loaded region inventory exceeds the resident bound";return null;}
            present.clear();byStorage.clear();for(RenderRegion region:loaded){if(region==null||present.put(region,true)!=null)throw new IllegalStateException("Unknown native region identity");}
            prefix.clear();nativeLists.clear();
            for(var iterator=value.original.iterator(false);iterator.hasNext();){ChunkRenderList list=iterator.next();RenderRegion region=list.getRegion();if(!present.containsKey(region)||nativeLists.put(region,list)!=null){unsupported++;status="Native region order is stale or duplicated";return null;}prefix.add(list);}
            eligible.clear();long now=System.nanoTime();int maximum=0;
            for(RenderRegion region:loaded){
                SectionRenderDataStorage storage=region.getStorage(value.pass);if(storage==null||storage.isEmpty()){entries.remove(region);continue;}
                if(!api.storage(storage)){unsupported++;status="Unsupported resident section layout";return null;}
                if(!fact.settled(now,region.getNewestSectionLoadTime())){unsettledFades++;status="Native section fade has not settled";return null;}
                long version=publications.current(storage);if(version<0){unsupported++;status="Section publication metadata is unavailable";return null;}
                Entry entry=entries.get(region);if(entry==null||entry.storage!=storage){entry=new Entry(region,storage);entries.put(region,entry);}
                if(entry.data==null||entry.data.version!=version){entry.data=new TerrainResidentRegionData(region,storage,version);entry.all=new ChunkRenderList(region);for(int slot=0;slot<256;slot++)if(storage.getSliceMask(slot)!=0)entry.all.add(slot,1);}
                if(entry.data.sectionsWithGeometry==0)continue;maximum=Math.max(maximum,entry.data.maximumElements);eligible.add(entry);
            }
            entries.keySet().removeIf(region->!present.containsKey(region));
            if(eligible.isEmpty()){status="No resident "+pass.name()+" geometry";return null;}
            eligible.sort(Comparator.comparingInt((Entry entry)->entry.region.getOriginX()).thenComparingInt(entry->entry.region.getOriginY()).thenComparingInt(entry->entry.region.getOriginZ()));
            for(int i=1;i<eligible.size();i++){RenderRegion a=eligible.get(i-1).region,b=eligible.get(i).region;if(a.getOriginX()==b.getOriginX()&&a.getOriginY()==b.getOriginY()&&a.getOriginZ()==b.getOriginZ()){unsupported++;status="Duplicate native region coordinates";return null;}}
            int count=eligible.size();
            if(buffers==null||capacity<count){
                closeBuffers();capacity=Math.min(1024,Integer.highestOneBit(Math.max(1,count-1))<<1);if(count==1)capacity=1;
                if(!TerrainGpuBudget.reserve(this,(long)capacity*TerrainResidentGpuBuffers.REGION_BYTES)){budgetFallbacks++;status="Shared terrain command budget is full";return null;}
                try(var input=TerrainResidentScene.class.getResourceAsStream("/assets/arcadium/shaders/terrain_resident_commands.comp")){
                    if(input==null)throw new IllegalStateException("Resident terrain compute resource is absent");buffers=new TerrainResidentGpuBuffers(value.device,capacity,new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }catch(Throwable error){if(error instanceof TerrainResidentResources.AllocationFailure retained)pendingAllocation=retained.pending;if(pendingAllocation==null){TerrainGpuBudget.release(this);capacity=0;}throw error;}
            }
            api.ensureIndices(renderer,value.device,value.pass,maximum);
            ensureScratch(count);Arrays.fill(orders,0,count,null);Arrays.fill(sizes,0,count,0);
            byStorage.clear();
            for(int i=0;i<count;i++){
                Entry entry=eligible.get(i);entry.slot=i;identities[i]=entry.storage;rows[i]=entry.data;ChunkRenderList nativeList=nativeLists.get(entry.region);if(nativeList!=null){orders[i]=nativeList.getSectionsWithGeometry();sizes[i]=nativeList.getSectionsWithGeometryCount();}
                GlTessellation tessellation=api.tessellation(renderer,value.device,entry.region,value.pass);if(tessellation==null)throw new IllegalStateException("Native terrain tessellation is unavailable");
                entry.cache(this,tessellation);byStorage.put(entry.storage,entry);
            }
            clipMatrix.set(value.matrices.projection()).mul(value.matrices.modelView()).get(clip);CameraTransform face=value.face,render=value.render;
            long uploads=buffers.meshUploads,copied=buffers.orderCopiedBytes(),compared=buffers.orderComparedBytes();
            buffers.prepare(identities,rows,count,orders,sizes,clip,render.intX,render.intY,render.intZ,render.fracX,render.fracY,render.fracZ,face.intX,face.intY,face.intZ,face.fracX,face.fracY,face.fracZ,api.searchDistance(owner,value.matrices.projection()),true,cull);
            metadataUploads+=buffers.meshUploads-uploads;orderCopiedBytes+=buffers.orderCopiedBytes()-copied;orderComparedBytes+=buffers.orderComparedBytes()-compared;
            for(Entry entry:eligible)if(publications.current(entry.storage)!=entry.data.version){unsupported++;status="Native section changed during resident preflight";return null;}
            epoch=publications.epoch();publicationEpoch=epoch;preparedPublicationEpoch=epoch;for(Entry entry:eligible)entry.serial=value.serial;
            for(Entry entry:eligible)if(!nativeLists.containsKey(entry.region))prefix.add(entry.all);
            ready=true;return prefix.iterator();
        }
        void ensureScratch(int count){
            if(count<1||count>1024)throw new IllegalArgumentException("Resident CPU workspace bound exceeded");
            if(identities.length<count){int n=Math.min(1024,Integer.highestOneBit(Math.max(1,count-1))<<1);if(count==1)n=1;identities=new Object[n];rows=new TerrainResidentRegionData[n];orders=new byte[n][];sizes=new int[n];}
            for(int i=count;i<scratchCount;i++){identities[i]=null;rows[i]=null;orders[i]=null;sizes[i]=0;}scratchCount=count;
        }
        long retainedBytes(){if(published!=null)return published.retainedBytes();return buffers==null&&pendingAllocation==null?0:(long)capacity*TerrainResidentGpuBuffers.REGION_BYTES;}
        void closeBuffers(){
            ready=false;try{if(buffers!=null){buffers.close();if(buffers.resourcesReleased())buffers=null;}if(pendingAllocation!=null){pendingAllocation.close();if(pendingAllocation.empty())pendingAllocation=null;}}
            finally{if(buffers==null&&pendingAllocation==null){capacity=0;TerrainGpuBudget.release(this);}}
        }
        @Override public void close(){
            if(published!=null){boolean own=false;try{published.requireOwner();own=api!=null&&!api.managed();if(own)RenderDevice.enterManagedCode();closeRetained(published,()->published.retainedBytes()==0,published);}finally{ready=false;eventEntries.clear();eventPrefix.clear();eventPresent.clear();latestPublished=null;latestSubmission=null;eventLayout=-1;if(own)RenderDevice.exitManagedCode();}return;}
            ready=false;boolean own=false;
            try{if(buffers!=null)buffers.requireOwner();if(pendingAllocation!=null)pendingAllocation.requireCurrent();own=api!=null&&!api.managed();if(own)RenderDevice.enterManagedCode();closeBuffers();}
            finally{entries.clear();byStorage.clear();present.clear();nativeLists.clear();prefix.clear();eligible.clear();Arrays.fill(identities,null);Arrays.fill(rows,null);Arrays.fill(orders,null);scratchCount=0;if(own)RenderDevice.exitManagedCode();}
        }
    }
    private static final class Cached extends CachedBatch {
        final Scene scene;
        final Entry entry;
        Cached(Scene scene,Entry entry,GlTessellation tessellation){super(new Batch(scene,entry),tessellation,new byte[0],0,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE);this.scene=scene;this.entry=entry;}
        @Override public boolean isValidFor(byte[] ignored,int length,int x,int y,int z){Scope value=current.get();return valid(value)&&value.scene==scene&&entry.serial==value.serial&&publications.current(entry.storage)==entry.data.version;}
    }
    private static final class Batch extends MultiDrawBatch {
        private final Scene scene;
        private final Entry entry;
        Batch(Scene scene,Entry entry){super(1792);this.scene=scene;this.entry=entry;size=1792;maxElementCount=entry.data.maximumElements;}
        void refresh(){maxElementCount=entry.data.maximumElements;}
        @Override public void upload(CommandList device){}
        @Override public void execute(CommandList device,GlTessellation tessellation,GlPrimitiveType primitive){
            Scope value=current.get();if(!valid(value)||value.scene!=scene||entry.serial!=value.serial||publications.current(entry.storage)!=entry.data.version)throw new IllegalStateException("Resident terrain frame expired before submission");
            int oldIndirect=GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING),oldParameter=GL11.glGetInteger(ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB);
            try{
                GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,scene.buffers.commandHandle());GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB,scene.buffers.countHandle());
                try(var draw=device.beginTessellating(tessellation)){
                    if(GL.getCapabilities().OpenGL46)GL46.glMultiDrawElementsIndirectCount(primitive.getId(),GL11.GL_UNSIGNED_INT,scene.buffers.commandOffset(entry.slot),scene.buffers.countOffset(entry.slot),1792,20);
                    else ARBIndirectParameters.glMultiDrawElementsIndirectCountARB(primitive.getId(),GL11.GL_UNSIGNED_INT,scene.buffers.commandOffset(entry.slot),scene.buffers.countOffset(entry.slot),1792,20);
                    regionDraws++;scene.submissions++;submittedPublicationEpoch=scene.epoch;
                }
            }finally{GL15.glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER,oldIndirect);GL15.glBindBuffer(ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB,oldParameter);}
        }
        @Override public void delete(){} // Buffers are owned by the scene.
        @Override public void appendDrawCommand(int base,int count,long pointer){throw new UnsupportedOperationException();}
        @Override public void mergeIntoLastCommand(int count){throw new UnsupportedOperationException();}
    }
}
