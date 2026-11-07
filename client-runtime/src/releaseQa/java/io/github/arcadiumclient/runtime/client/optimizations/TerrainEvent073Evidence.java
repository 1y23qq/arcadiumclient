// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.render.chunk.*;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.lists.*;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.gl.tessellation.GlVertexArrayTessellation;
import org.embeddedt.embeddium.impl.gl.array.GlVertexArray;
import org.lwjgl.opengl.*;

public final class TerrainEvent073Evidence {
    private static final Map<String,Field> fields=new HashMap<>();
    private static final List<String> errors=new ArrayList<>();
    private static final Map<String,Long> hooks=new TreeMap<>(),resources=new TreeMap<>();
    private static final Map<String,Map<String,Object>> passSnapshots=new LinkedHashMap<>();
    private static final Map<String,Steady> previous=new HashMap<>();
    private static final Set<String> steady=new HashSet<>();
    private static final List<Map<String,Object>> draws=new ArrayList<>();
    private static boolean preflight,shutdown;private static long actualDraws,nativeEmptyRows,nativeNonemptyRows;private static final List<Map<String,Object>> publications=new ArrayList<>();private static Map<String,Object> weaving=Map.of();
    private record Steady(Object inventory,Object page,long epoch,long order,long rows,long copied,long visits,long uploads,long ranks){}
    static record Context(Object scene,TerrainResidentPublishedPreflight owner,TerrainResidentPublishedPreflight.Prepared prepared,TerrainResidentInventory.Frame frame,TerrainResidentInventory inventory,TerrainResidentGpuBuffers buffers,RenderSectionManager manager,TerrainRenderPassToken pass){}
    static record TerrainRenderPassToken(org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass value){}
    private TerrainEvent073Evidence(){}
    public static boolean enabled(){return Boolean.getBoolean("arcadium.terrainResident073.events")&&System.getProperty("arcadium.terrainWorld073.output")!=null;}
    public static Object field(Object value,String name)throws Exception{return field(value.getClass(),value,name);}
    public static Object field(Class<?> type,Object value,String name)throws Exception{String key=type.getName()+"#"+name;Field f=fields.get(key);if(f==null){f=type.getDeclaredField(name);f.setAccessible(true);fields.put(key,f);}return f.get(value);}
    public static void error(String where,Throwable error){if(!enabled())return;if(errors.size()<64)errors.add(where+": "+error);}
    static void need(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void fake(){Minecraft mc=Minecraft.getInstance();need("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"Owned fake correctness fixture required");}
    public static void preflight()throws Exception{
        if(preflight)return;fake();String base="io.github.arcadiumclient.runtime.client.optimizations.";
        Map<String,List<String>> paths=new LinkedHashMap<>();
        paths.put("TerrainResidentScene",List.of("book","current","resourceEpoch"));paths.put("TerrainResidentScene$Scene",List.of("pass","published","eventEntries","latestPublished","latestSubmission"));
        paths.put("TerrainResidentScene$Scope",List.of("manager","original","published","scene","face","render","cull","cameraWords","fallbackClip","fallbackDistance","pass","admittedProgram"));
        paths.put("TerrainResidentScene$Boundary",List.of("scope","selected","region","storage","list"));paths.put("TerrainResidentScene$PublishedBatch",List.of("entry"));paths.put("TerrainResidentScene$PublishedEntry",List.of("region","storage","slot","cached","generation","bindings"));
        paths.put("TerrainResidentPublishedPreflight",List.of("buffers","pending","clip","currentDistance","orderRebuilds","steadyOrderReuses","replayRequests"));
        paths.put("TerrainResidentInventory$Frame",List.of("inventory","nativeList"));paths.put("TerrainResidentInventory",List.of("passes","epoch","nativeList","failure","regions","storage","depth"));
        paths.put("TerrainResidentInventory$Pass",List.of("slots","revision","ack"));paths.put("TerrainResidentInventory$Slot",List.of("region","storage","index","words","elements","ranks","generation","populated","pass"));paths.put("TerrainResidentInventory$Region",List.of("identity","generation","bindings"));
        paths.put("TerrainResidentInventoryHooks",List.of("managers","owners"));paths.put("TerrainResidentInventoryHooks$Session",List.of("inventory","published","nativeSequence","manager"));
        paths.put("TerrainResidentGpuBuffers",List.of("meshes","commands","counts","program","nativeOrder"));paths.put("TerrainResidentPassSubmission",List.of("attempted","gpuIssued","nativeIssued","ambiguous","lateFallbacks","duplicates"));
        for(var row:paths.entrySet()){Class<?> c=Class.forName(base+row.getKey(),false,TerrainEvent073Evidence.class.getClassLoader());for(String name:row.getValue()){Field f=c.getDeclaredField(name);f.setAccessible(true);fields.put(c.getName()+"#"+name,f);}}
        GlVertexArrayTessellation.class.getDeclaredField("array");TerrainResidentPublishedPreflight.Prepared.class.getDeclaredMethod("frame");TerrainResidentPublishedPreflight.Prepared.class.getDeclaredMethod("buffers");
        Map<String,List<String>> targets=new LinkedHashMap<>();
        targets.put("org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager",List.of("inventoryCreate","inventoryUpload","inventoryUpdate","inventoryDelete"));
        targets.put("org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion",List.of("inventoryStorage","inventoryDelete","inventoryRefresh","inventoryBindings","inventoryLoad","inventoryPrune","inventoryRemoveMeshes","inventoryMembership"));
        targets.put("org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage",List.of("inventorySet","inventoryRemove","inventoryIndex","inventoryResize","inventoryDelete"));
        targets.put("org.embeddedt.embeddium.impl.render.chunk.lists.RenderListManager",List.of("inventoryPublish"));targets.put("org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager",List.of("inventoryAttach","inventoryRetire"));targets.put("org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer",List.of("eventSubmissionEntry","eventSubmission"));
        var merged=new LinkedHashMap<String,Object>();for(var row:targets.entrySet()){Class<?> c=Class.forName(row.getKey(),false,Minecraft.class.getClassLoader());var names=Arrays.stream(c.getDeclaredMethods()).map(Method::getName).toList();for(String token:row.getValue())need(names.stream().anyMatch(name->name.contains(token)),"Missing actual woven event handler "+row.getKey()+"#"+token);merged.put(row.getKey(),names.stream().filter(name->row.getValue().stream().anyMatch(name::contains)).toList());}
        weaving=Map.of("actualMergedDeclarations",merged,"fieldPaths",paths,"actualFieldAndMethodPreflight",true);preflight=true;
    }
    static Context context(String name)throws Exception{
        Object book=field(TerrainResidentScene.class,null,"book");need(book!=null,"No event pass book");Object scene=null;
        for(Object value:((TerrainResidentPassBook<?>)book).values())if(((org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)field(value,"pass")).name().equals(name)){need(scene==null,"Duplicate event pass");scene=value;}
        need(scene!=null,"Missing event pass "+name);var owner=(TerrainResidentPublishedPreflight)field(scene,"published");var prepared=(TerrainResidentPublishedPreflight.Prepared)field(scene,"latestPublished");need(owner!=null&&prepared!=null,"Legacy-only pass is not event evidence");var frame=prepared.frame();var inventory=(TerrainResidentInventory)field(frame,"inventory");
        return new Context(scene,owner,prepared,frame,inventory,prepared.buffers(),(RenderSectionManager)inventory.manager(),new TerrainRenderPassToken((org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)field(scene,"pass")));
    }
    public static void hook(String name){if(!enabled())return;try{fake();hooks.merge(name,1L,Long::sum);if(name.equals("shutdown")){shutdown=true;writeFinal();}}catch(Throwable e){error("hook "+name,e);}}
    public static void source(String kind,SectionRenderDataStorage storage,int section){
        if(!enabled())return;hook(kind);try{fake();for(Object session:((Map<?,?>)field(TerrainResidentInventoryHooks.class,null,"managers")).values()){
            var inventory=(TerrainResidentInventory)field(session,"inventory");need(inventory.ownerThread(),"Native source observer outside owner");Object slot=((Map<?,?>)field(inventory,"storage")).get(storage);if(slot==null)continue;
            var row=new LinkedHashMap<String,Object>();row.put("kind",kind);row.put("storage",System.identityHashCode(storage));row.put("pass",field(slot,"pass"));row.put("sourceEpoch",field(inventory,"epoch"));row.put("localSection",section);row.put("nativeSequence",field(session,"nativeSequence"));
            if(section>=0){int mask=storage.getSliceMask(section),elements=((int[])field(slot,"elements"))[section];need((mask==0)==(elements==0),"Actual successful source empty-state differs from published row");if(mask==0)nativeEmptyRows++;else nativeNonemptyRows++;row.put("nativeMask",mask);row.put("publishedElements",elements);}
            row.put("actualNativeStack",StackWalker.getInstance().walk(stream->stream.filter(frame->frame.getClassName().startsWith("org.embeddedt.embeddium.impl.render.chunk.")).limit(4).map(frame->frame.getClassName()+"#"+frame.getMethodName()).toList()));if(publications.size()==128)publications.removeFirst();publications.add(row);break;
        }}catch(Throwable e){error("native source "+kind,e);}
    }
    public static void resource(String name){if(!enabled())return;try{fake();resources.merge(name,1L,Long::sum);}catch(Throwable e){error("resource "+name,e);}}
    public static Object beforeDraw(Object boundary,int mode,int type,long indirect,long count,int maximum,int stride){
        if(!enabled())return null;
        try{fake();preflight();actualDraws++;Object scope=field(boundary,"scope");var prepared=(TerrainResidentPublishedPreflight.Prepared)field(scope,"published");need(prepared!=null,"Missing actual prepared producer");var frame=prepared.frame();Object original=field(scope,"original");need(frame.valid(original),"Actual source epoch changed before GPU issue");Object selected=field(boundary,"selected"),entry=field(selected,"entry");int slot=(int)field(entry,"slot");var region=(RenderRegion)field(boundary,"region");var storage=(SectionRenderDataStorage)field(boundary,"storage");need(region==field(entry,"region")&&storage==field(entry,"storage")&&region.getStorage(((org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)field(scope,"pass")))==storage,"Current native region/storage owner differs");
            need(mode==4&&type==5125&&maximum==1792&&stride==20&&indirect==prepared.buffers().commandOffset(slot)&&count==prepared.buffers().countOffset(slot),"Actual indirect ABI/range differs");
            int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);var cached=(org.embeddedt.embeddium.impl.render.chunk.multidraw.CachedBatch)field(entry,"cached");Object tess=cached.getTessellation();need(tess instanceof GlVertexArrayTessellation,"Unknown actual VAO class");int expected=((GlVertexArray)field(GlVertexArrayTessellation.class,tess,"array")).handle();need(vao==expected&&vao!=0&&program!=0,"Current bound native VAO/program differs");
            need(GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING)==prepared.buffers().commandHandle()&&GL11.glGetInteger(ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB)==prepared.buffers().countHandle(),"Actual bound command/count owner differs");
            var row=new LinkedHashMap<String,Object>();row.put("pass",((org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass)field(scope,"pass")).name());row.put("sourceEpoch",frame.epoch);row.put("nativeOrderPublication",frame.orderPublication);row.put("slot",slot);row.put("vao",vao);row.put("program",program);row.put("origin",List.of(region.getOriginX(),region.getOriginY(),region.getOriginZ()));row.put("indirect",indirect);row.put("countOffset",count);row.put("actualNativeVaoOwner",true);
            if(draws.size()<512)draws.add(row);return row;
        }catch(Throwable e){error("actual draw",e);return null;}
    }
    public static void afterDraw(Object token){if(!enabled()||token==null)return;try{var row=(Map<?,?>)token;need(((Number)row.get("vao")).intValue()==GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)&&((Number)row.get("program")).intValue()==GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),"MDI changed native caller state");}catch(Throwable e){error("draw return",e);}}
    static Map<String,Object> counters(Context c)throws Exception{return Map.of("sourceEpoch",field(c.inventory,"epoch"),"nativeOrderPublication",c.frame.orderPublication,"metadataRowsRead",c.inventory.metadataRowsRead,"orderRowsCopied",c.inventory.orderRowsCopied,"topologyVisits",c.inventory.topologyVisits,"seals",c.inventory.seals,"unchangedSeals",c.inventory.unchangedSeals,"meshUploads",c.buffers.meshUploads,"rankUploads",c.buffers.orderUploads());}
    public static void returned(String pass){if(!enabled()||!GpuTerrainCommands.enabled||!List.of("solid","cutout_mipped").contains(pass))return;try{
        Context c=context(pass);var current=new Steady(c.inventory,c.buffers,c.frame.epoch,c.frame.orderPublication,c.inventory.metadataRowsRead,c.inventory.orderRowsCopied,c.inventory.topologyVisits,c.buffers.meshUploads,c.buffers.orderUploads());var old=pass.equals("cutout_mipped")?previous.put(pass,current):null;
        if(old!=null&&old.inventory==current.inventory&&old.page==current.page&&old.epoch==current.epoch&&old.order==current.order){need(old.rows==current.rows&&old.copied==current.copied&&old.visits==current.visits&&old.uploads==current.uploads&&old.ranks==current.ranks,"Stable actual frame repeated inventory/rank/metadata work");steady.addAll(List.of("solid","cutout_mipped"));}
        if(io.github.arcadiumclient.runtime.qa.TerrainFullFrameOracle073.controlling()){
            var snapshot=new LinkedHashMap<String,Object>();snapshot.put("eventProducer",true);snapshot.put("operation",TerrainScene073GpuEvidence.currentOperation());snapshot.put("source",counters(c));snapshot.put("actualGeometry",TerrainOracleGeometry073.capture(pass));snapshot.put("issueLedger",ledger(c.scene));snapshot.put("counts",TerrainScene073GpuEvidence.currentCounts(pass));passSnapshots.put(pass,snapshot);
        }
    }catch(Throwable e){error("pass return "+pass,e);}}
    private static Map<String,Object> ledger(Object scene)throws Exception{Object value=field(scene,"latestSubmission");var result=new LinkedHashMap<String,Object>();for(String name:List.of("gpuIssued","nativeIssued","ambiguous","duplicates","lateFallbacks"))result.put(name,field(value,name));result.put("attempted",((Map<?,?>)field(value,"attempted")).size());return result;}
    public static Map<String,Object> pass(String name){return passSnapshots.get(name);}
    public static boolean steadyReady(){return !enabled()||steady.containsAll(List.of("solid","cutout_mipped"));}
    public static void healthy(){if(enabled())need(errors.isEmpty(),"Terrain observer errors: "+errors);}
    public static Map<String,Object> report(){var value=new LinkedHashMap<String,Object>();value.put("schema","arcadium.terrain-event073.actual.v1");value.put("actualEventPath",true);value.put("preflight",preflight);value.put("weaving",weaving);value.put("hookCalls",new TreeMap<>(hooks));value.put("resourceCalls",new TreeMap<>(resources));value.put("actualDraws",List.copyOf(draws));value.put("actualDrawCount",actualDraws);value.put("steadyQualifiedPasses",List.copyOf(steady));value.put("nativeEmptyRows",nativeEmptyRows);value.put("nativeNonemptyRows",nativeNonemptyRows);value.put("successfulNativePublications",List.copyOf(publications));value.put("observerErrors",List.copyOf(errors));value.put("performanceClaim",false);return value;}
    private static void writeFinal(){try{var value=new LinkedHashMap<>(report());int managers=((Map<?,?>)field(TerrainResidentInventoryHooks.class,null,"managers")).size(),owners=((Map<?,?>)field(TerrainResidentInventoryHooks.class,null,"owners")).size();value.put("finalManagers",managers);value.put("finalRegistryOwners",owners);value.put("finalGpuBytes",TerrainGpuBudget.bytes());value.put("finalGpuOwners",TerrainGpuBudget.owners());value.put("actualShutdownObserved",shutdown);value.put("passed",preflight&&shutdown&&errors.isEmpty()&&!draws.isEmpty()&&steadyReady()&&managers==0&&owners==0&&TerrainGpuBudget.bytes()==0);Path path=Path.of(System.getProperty("arcadium.terrainWorld073.output")).resolveSibling("terrain-event-final.json");Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(value)+"\n");}catch(Throwable e){error("final event receipt",e);System.err.println("Terrain event final receipt failed: "+e);}}
}
