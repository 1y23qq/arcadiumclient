// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.*;
import org.embeddedt.embeddium.impl.render.chunk.region.*;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

public final class TerrainResidentInventoryHooks {
    public static final String PROPERTY="arcadium.terrainResident073.events";
    private static final boolean BOOT=Boolean.getBoolean(PROPERTY)&&GpuTerrainCommands.residentBootRequested();
    private static final IdentityHashMap<Object,Session> owners=new IdentityHashMap<>();
    private static final IdentityHashMap<RenderSectionManager,Session> managers=new IdentityHashMap<>();
    private static final VarHandle REGIONS=field(RenderSectionManager.class,"regions"),LISTS=field(RenderSectionManager.class,"renderListManager"),CONFIG=field(RenderSectionManager.class,"renderPassConfiguration"),STRATEGY=field(SectionRenderDataStorage.class,"storageStrategy");
    private static final class Session {
        final RenderSectionManager manager;final RenderRegionManager regions;final RenderListManager lists;final TerrainResidentInventory inventory;
        final RenderPassConfiguration<?> configuration;final IdentityHashMap<SectionRenderDataStorage,RenderRegion> storage=new IdentityHashMap<>();
        Object published;int publishedFrame=-1;long nativeSequence;final IdentityHashMap<SectionRenderDataStorage,ChunkRenderList> generated=new IdentityHashMap<>();
        Session(RenderSectionManager manager){this.manager=manager;regions=(RenderRegionManager)REGIONS.get(manager);lists=(RenderListManager)LISTS.get(manager);configuration=(RenderPassConfiguration<?>)CONFIG.get(manager);inventory=new TerrainResidentInventory(manager);}
    }
    public static final class Scope {
        private final Session session;private final TerrainResidentInventory.Ticket ticket;
        private Scope(Session session,TerrainResidentInventory.Ticket ticket){this.session=session;this.ticket=ticket;}
    }
    private TerrainResidentInventoryHooks(){}
    private static VarHandle field(Class<?> type,String name){try{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));}catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}}
    public static synchronized void attach(RenderSectionManager manager){
        if(!BOOT||managers.containsKey(manager))return;if(managers.size()==2){for(Session s:managers.values())s.inventory.invalidate("Native manager bound exceeded");return;}
        Session s=new Session(manager);managers.put(manager,s);owners.put(manager,s);owners.put(s.regions,s);owners.put(s.lists,s);
        try{if(s.regions.getLoadedRegions().size()!=0||s.lists.getRenderLists().getNumRegions()!=0)throw new IllegalStateException("Late unobserved native bootstrap");Scope scope=begin(manager);published(s.lists);end(scope,true);}
        catch(Throwable error){s.inventory.invalidate("Unverified native initial publication: "+error.getClass().getSimpleName());}
    }
    public static synchronized Scope begin(Object owner){
        if(!BOOT)return null;Session s=owners.get(owner);if(s==null)return null;
        if(!s.inventory.ownerThread()){s.inventory.invalidate("Native callback on foreign thread");return null;}
        s.nativeSequence++;try{return new Scope(s,s.inventory.failure()==null?s.inventory.begin():null);}catch(RuntimeException ignored){return new Scope(s,null);}
    }
    public static synchronized void end(Scope scope,boolean succeeded){
        if(scope==null)return;scope.session.nativeSequence++;try{if(scope.ticket!=null)scope.session.inventory.end(scope.ticket,succeeded);else if(!succeeded)scope.session.inventory.invalidate("Native source failed while using native fallback");}catch(RuntimeException error){scope.session.inventory.invalidate("Incomplete native operation scope");}
    }
    private static Session current(Object owner){Session s=owners.get(owner);if(s!=null&&!s.inventory.ownerThread()){s.inventory.invalidate("Foreign source callback");return null;}return s!=null&&s.inventory.failure()==null?s:null;}
    public static synchronized void region(RenderRegionManager manager,RenderRegion region){
        Session s=current(manager);if(s==null)return;try{s.inventory.addRegion(region,region.getOriginX(),region.getOriginY(),region.getOriginZ());if(s.inventory.failure()==null)owners.put(region,s);}catch(Throwable e){s.inventory.invalidate("Native region publication failed");}
    }
    public static synchronized void storage(RenderRegion region,TerrainRenderPass pass,SectionRenderDataStorage storage){
        Session s=current(region);if(s==null)return;int index=pass==s.configuration.defaultSolidMaterial().pass?0:pass==s.configuration.defaultCutoutMippedMaterial().pass?1:-1;if(index<0)return;
        try{if(!TerrainResidentPassIdentity.accepts(pass,s.configuration)||storage.getClass()!=SectionRenderDataStorage.class||storage.getPrimitiveType()!=QuadPrimitiveType.TRIANGULATED||STRATEGY.get(storage)!=SectionRenderDataUnsafe.Strategy.COMPACT)throw new IllegalStateException("Unknown compact storage ABI");
            if(s.storage.containsKey(storage))return;s.inventory.loadTime(region,region.getNewestSectionLoadTime());s.inventory.addStorage(region,storage,index,rows(region,storage));s.storage.put(storage,region);owners.put(storage,s);
        }catch(Throwable e){s.inventory.invalidate("Native storage publication failed");}
    }
    public static synchronized void section(SectionRenderDataStorage storage,int section){
        Session s=current(storage);if(s==null)return;try{s.inventory.section(storage,section,row(s.storage.get(storage),storage,section));s.generated.remove(storage);}catch(Throwable e){s.inventory.invalidate("Native section snapshot failed");}
    }
    public static synchronized void relocated(SectionRenderDataStorage storage){
        Session s=current(storage);if(s==null)return;try{s.inventory.relocate(storage,rows(s.storage.get(storage),storage));s.generated.remove(storage);}catch(Throwable e){s.inventory.invalidate("Native relocation snapshot failed");}
    }
    public static synchronized void deletedStorage(SectionRenderDataStorage storage){
        Session s=current(storage);if(s==null)return;try{s.inventory.removeStorage(storage);s.storage.remove(storage);s.generated.remove(storage);owners.remove(storage);}catch(Throwable e){s.inventory.invalidate("Native storage removal failed");}
    }
    public static synchronized void deletedRegion(RenderRegion region){
        Session s=current(region);if(s==null)return;try{s.inventory.removeRegion(region);owners.remove(region);}catch(Throwable e){s.inventory.invalidate("Native region removal failed");}
    }
    public static synchronized void loadTime(RenderRegion region){Session s=current(region);if(s!=null)try{s.inventory.loadTime(region,region.getNewestSectionLoadTime());}catch(Throwable e){s.inventory.invalidate("Native load-time publication failed");}}
    public static synchronized void bindings(RenderRegion region){Session s=current(region);if(s!=null)try{s.inventory.bindingsChanged(region);}catch(Throwable e){s.inventory.invalidate("Native binding publication failed");}}
    public static synchronized void published(RenderListManager lists){
        Session s=current(lists);if(s==null)return;try{SortedRenderLists actual=lists.getRenderLists();if(actual==s.published){if(lists.getLastUpdatedFrame()!=s.publishedFrame)throw new IllegalStateException("Native publication changed without a new list identity");return;}if(actual.getClass()!=SortedRenderLists.class)throw new IllegalStateException("Unknown native list class");
            var rows=new ArrayList<TerrainResidentInventory.OrderRow>();var iterator=actual.iterator(false);while(iterator.hasNext()){if(rows.size()==1024)throw new IllegalStateException("Native prefix bound");ChunkRenderList row=iterator.next();if(row.getClass()!=ChunkRenderList.class)throw new IllegalStateException("Unknown native row class");rows.add(new TerrainResidentInventory.OrderRow(row.getRegion(),row,row.getSectionsWithGeometry(),row.getSectionsWithGeometryCount()));}
            s.inventory.publishOrder(actual,lists.getLastUpdatedFrame(),rows);s.published=actual;s.publishedFrame=lists.getLastUpdatedFrame();
        }catch(Throwable e){s.inventory.invalidate("Native list publication unavailable");}
    }
    public static synchronized boolean alive(RenderSectionManager manager){Session s=owners.get(manager);return s!=null&&s.inventory.ownerThread();}
    public static synchronized long nativeSequence(RenderSectionManager manager){Session s=owners.get(manager);return s!=null&&s.inventory.ownerThread()?s.nativeSequence:-1;}
    public static synchronized TerrainResidentInventory.Frame frame(RenderSectionManager manager,int pass,ChunkRenderListIterable actual){
        Session s=current(manager);if(s==null)return null;try{return s.inventory.seal(pass,actual);}catch(RuntimeException e){return null;}
    }
    public static synchronized List<ChunkRenderList> renderLists(RenderSectionManager manager,TerrainResidentInventory.Frame frame,ChunkRenderListIterable actual){
        Session s=current(manager);if(s==null||!frame.valid(actual))throw new IllegalStateException("Unknown published draw-order scope");var result=new ArrayList<ChunkRenderList>();
        for(var row:frame.drawOrder()){
            if(row.nativeRow()!=null){if(!(row.nativeRow() instanceof ChunkRenderList nativeRow))throw new IllegalStateException("Unknown original native row");result.add(nativeRow);}
            else{var storage=(SectionRenderDataStorage)row.storage();RenderRegion region=s.storage.get(storage);if(region!=row.region())throw new IllegalStateException("Unpublished suffix storage owner");
                ChunkRenderList generated=s.generated.get(storage);if(generated==null){generated=new ChunkRenderList(region);for(int section=0;section<256;section++)if(storage.getSliceMask(section)!=0)generated.add(section,1);s.generated.put(storage,generated);}result.add(generated);}
        }return List.copyOf(result);
    }
    public static synchronized void acknowledge(RenderSectionManager manager,TerrainResidentInventory.Frame frame){Session s=current(manager);if(s!=null)s.inventory.acknowledge(frame);}
    public static synchronized void replay(RenderSectionManager manager,int pass){Session s=current(manager);if(s!=null)s.inventory.requestReplay(pass);}
    public static synchronized void retired(RenderSectionManager manager){
        Session s=managers.remove(manager);if(s==null)return;s.inventory.reset();owners.values().removeIf(value->value==s);s.storage.clear();s.generated.clear();
    }
    public static synchronized void shutdown(){for(Session s:managers.values()){if(s.inventory.ownerThread())s.inventory.reset();else s.inventory.invalidate("Final shutdown outside inventory owner");s.storage.clear();s.generated.clear();}owners.clear();managers.clear();}
    private static int[] rows(RenderRegion region,SectionRenderDataStorage storage){int[] result=new int[5120];for(int section=0;section<256;section++)System.arraycopy(row(region,storage,section),0,result,section*20,20);return result;}
    private static int[] row(RenderRegion region,SectionRenderDataStorage storage,int section){
        if(region==null||section<0||section>=256)throw new IllegalArgumentException("Unknown native source row");int[] words=new int[20];words[0]=region.getOriginX()+LocalSectionIndex.unpackX(section)*16;words[1]=region.getOriginY()+LocalSectionIndex.unpackY(section)*16;words[2]=region.getOriginZ()+LocalSectionIndex.unpackZ(section)*16;
        int mask=storage.getSliceMask(section);if((mask&~127)!=0)throw new IllegalArgumentException("Unknown native slice mask");if(mask==0)return words;long pointer=storage.getDataPointer(section);
        for(int face=0;face<7;face++)if((mask&(1<<face))!=0){int first=SectionRenderDataUnsafe.Strategy.COMPACT.getVertexOffset(pointer,face),end=SectionRenderDataUnsafe.Strategy.COMPACT.getRunVertexEnd(pointer,face,storage.getPrimitiveType());if(first<0||end<first)throw new IllegalArgumentException("Invalid current arena span");words[4+face]=first;words[12+face]=SectionRenderDataUnsafe.elementsForVertices(end-first,storage.getPrimitiveType());}return words;
    }
}
