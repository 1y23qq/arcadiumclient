package io.github.arcadiumclient.runtime.client.optimizations;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resource.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ActorScene073MinecraftEvidence {
    private static final Map<String,Actor> actors=new LinkedHashMap<>();
    private static final List<Map<String,Object>> frames=new ArrayList<>(),resets=new ArrayList<>();
    private static final Actor[] scopes=new Actor[8];
    private static Actor active;
    private static int depth,resolveDepth;
    private static final long[] resolveDispatches=new long[8];
    private static long frame,frameDispatches;
    private static String failure;
    private static boolean parentFinished,cleanupComplete;
    private static ByteBuffer rgba,depths;
    private static Access access;
    private ActorScene073MinecraftEvidence(){}
    public static boolean enabled(){return System.getProperty("arcadium.actorScene073.minecraft")!=null;}
    private static boolean on(){return "on".equals(System.getProperty("arcadium.actorScene073.minecraft"));}
    private static Path output(){return Path.of(System.getProperty("arcadium.actorScene073.minecraftOutput"));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void owned(){
        Minecraft mc=Minecraft.getInstance();require(PlayerPipeline072Qa.enabled()&&"visual".equals(System.getProperty("arcadium.playerPipeline072.mode")),"Actor-scene observation is visual-only");
        require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"Actor-scene observation requires the owned offline identity");
        require(Set.of("off","on").contains(System.getProperty("arcadium.actorScene073.minecraft"))&&GpuPlayerPackets.actorSceneEnabled()==on(),"The real private actor-scene startup flag differs from the declared case");
        require(System.getProperty("arcadium.residentBodies072.mode","off").equals("off"),"Whole-body experiments must remain off");
    }
    public static void beginWorld(){
        if(!enabled())return;
        try{owned();require(depth==0&&active==null&&resolveDepth==0,"An actor observation scope leaked across world frames");frame++;frameDispatches=ActorSceneGpu.dispatches;for(Actor actor:actors.values())actor.clearFrame();}
        catch(Throwable error){failed(error);}
    }
    public static int enter(ClientPlayerEntity player){
        if(!enabled()||!player.getName().matches("PipelineQA[0-2]"))return -1;
        try{owned();require(depth<scopes.length,"Actor observation nesting exceeded its bound");Actor actor=actors.get(player.getName());if(actor==null){require(actors.size()<3,"Unexpected extra fake actor");actor=new Actor(player.getName());actors.put(player.getName(),actor);}int token=depth;scopes[depth++]=active;active=actor;actor.calls++;return token;}
        catch(Throwable error){failed(error);return -1;}
    }
    public static void leave(int token,boolean accepted){
        if(token<0)return;
        try{require(depth==token+1&&active!=null,"Actor observation scope order changed");if(accepted)active.captures++;active=scopes[token];scopes[token]=null;depth=token;}
        catch(Throwable error){failed(error);active=null;depth=0;Arrays.fill(scopes,null);}
    }
    public static void selected(Object main,Identifier texture,Object batch){
        if(!enabled()||active==null)return;
        try{if(access==null)access=new Access();Actor actor=active;actor.selections++;Object pool=access.batchPool.get(batch),layer=((Map<?,?>)access.layers.get(pool)).get(texture);require(layer!=null,"Actual selected native texture layer is absent");Object source=access.source.get(layer);int index=(int)access.index.get(layer),name=(int)access.sourceId.get(layer),generation=(int)access.generation.get(layer),layerFrame=(int)access.layerFrame.get(layer),mainFrame=(int)access.modelFrame.get(main);var fact=GpuVoxelSkinUploadFacts.current(source,name,generation);
            actor.sourceValid=fact!=null&&GpuVoxelSkinUploadFacts.matches(pool,index,fact)&&layerFrame==mainFrame;
            actor.texture=texture.toString();actor.sourceClass=source==null?"null":source.getClass().getName();actor.pool=System.identityHashCode(pool);actor.source=System.identityHashCode(source);actor.layer=index;actor.textureName=name;actor.generation=generation;actor.nativeFrame=mainFrame;actor.layerFrame=layerFrame;actor.uploadRevision=fact==null?0:fact.revision;
        }catch(Throwable error){active.sourceValid=false;active.sourceError=error.toString();}
    }
    public static void material(int job){
        if(!enabled()||active==null)return;
        try{ActorSceneData scene=scene();require(scene!=null,"Actual material observation ran with no actor scene");active.materialCalls++;active.job=job;active.batch=(long)field(GpuPlayerPackets.class,"actorBatch");active.material=scene.materialId(job);active.gpuReady=scene.gpuReady(job);}
        catch(Throwable error){failed(error);}
    }
    public static long dispatches(){return ActorSceneGpu.dispatches;}
    public static void resolveBegin(){if(!enabled())return;try{require(resolveDepth<resolveDispatches.length,"Actor resolver nesting exceeded the diagnostic bound");resolveDispatches[resolveDepth++]=ActorSceneGpu.dispatches;}catch(Throwable error){failed(error);}}
    public static void resolveEnd(Object source,int rows,long batch,boolean accepted){if(!enabled())return;try{require(resolveDepth>0&&source instanceof ActorSceneData,"Actor resolver scope has no real scene");long before=resolveDispatches[--resolveDepth];resolved((ActorSceneData)source,rows,batch,accepted,before);}catch(Throwable error){failed(error);}}
    private static void resolved(ActorSceneData scene,int rows,long batch,boolean accepted,long before){
        if(!enabled()||!accepted||ActorSceneGpu.dispatches==before)return;
        try{require(rows<=64,"Owned three-actor batch exceeded the diagnostic row bound");int associated=0;for(Actor actor:actors.values())if(actor.batch==batch&&actor.job>=0&&actor.job<rows&&scene.gpuReady(actor.job)){actor.gpuRows++;associated++;}require(associated<=3,"A dispatched actor was associated twice");}
        catch(Throwable error){failed(error);}
    }
    public static void capture(Minecraft mc,String filename)throws Exception{
        if(!enabled())return;owned();require(failure==null,"Actor source observation failed: "+failure);require(frames.size()<4&&actors.size()==3,"Actor capture inventory differs from the explicit three-player fixture");
        require(mc.width==1920&&mc.height==1080,"Actor-scene image proof requires full1920x1080");String stage=filename.substring(filename.indexOf("p-")+2,filename.length()-4);boolean supported=!stage.equals("settings-changed");
        Method state=PlayerPipeline072Qa.class.getDeclaredMethod("actorRenderState");state.setAccessible(true);Object nativeTraces=state.invoke(null);
        if(supported){for(Actor actor:actors.values()){require(actor.captures>0&&actor.selections>0&&actor.sourceValid,"An actor lacks actual selected native upload/copy provenance: "+actor.name);if(on())require(actor.materialCalls>0&&actor.material>0&&actor.gpuReady&&actor.gpuRows>0,"An actor did not reach the actual GPU-resolved scene: "+actor.name);else require(actor.materialCalls==0&&actor.gpuRows==0,"The OFF control used actor-scene production work");}require(on()?ActorSceneGpu.dispatches>frameDispatches:ActorSceneGpu.dispatches==frameDispatches,"Actual per-world dispatches differ from the selected private mode");}
        else{
            require(ActorSceneGpu.dispatches==frameDispatches,"Detailed native layers unexpectedly entered the actor scene");
            for(Object value:(List<?>)nativeTraces){Map<?,?> trace=(Map<?,?>)value;require(((Number)trace.get("nativeGpuBodyParts")).longValue()>0&&((Number)trace.get("nativeVoxelCalls")).longValue()==6,"Detailed skins lost original voxel callbacks or native GPU body capture");}
        }
        Map<String,Object> pixels=readFramebuffer(mc,filename);var row=new LinkedHashMap<String,Object>();row.put("file",filename);row.put("stage",stage);row.put("frame",frame);row.put("supportedActorSceneStage",supported);row.put("pixels",pixels);row.put("actors",actors.values().stream().map(Actor::data).toList());row.put("nativeActorTraces",nativeTraces);row.put("counters",counters());frames.add(row);write();
    }
    public static void finish(Throwable parentFailure){
        if(!enabled())return;
        parentFinished=true;if(parentFailure!=null)failed(parentFailure);
        try{owned();require(frames.size()==4,"Actor-scene proof did not capture all four exact stages");if(!on()){require(ActorSceneGpu.bytes()==0&&!contextAttached(),"The OFF control allocated actor-scene GPU storage");cleanupComplete=true;}write();}
        catch(Throwable error){failed(error);try{write();}catch(Exception ignored){}}
    }
    public static Object closing(){
        if(!enabled()||!on())return null;
        try{return new Owned();}catch(Throwable error){failed(error);return null;}
    }
    public static void closed(Object token){
        if(!enabled()||token==null)return;
        try{Owned owned=(Owned)token;for(int name:owned.buffers)require(!GL15.glIsBuffer(name),"Actual actor-scene reset retained a native buffer");for(int name:owned.programs)require(!GL20.glIsProgram(name),"Actual actor-scene reset retained its program");for(long name:owned.fences)require(!GL32.glIsSync(name),"Actual actor-scene reset retained an input fence");require(ActorSceneGpu.bytes()==0&&!contextAttached(),"Actor-scene Java ownership survived actual teardown");if(!owned.buffers.isEmpty()||!owned.programs.isEmpty())resets.add(Map.of("frame",frame,"buffersDeleted",owned.buffers.size(),"programsDeleted",owned.programs.size(),"fencesDeleted",owned.fences.size(),"actualGlDeletionVerified",true));if(parentFinished){require(!resets.isEmpty(),"The positive GPU case never observed native resource deletion");cleanupComplete=true;}write();}
        catch(Throwable error){failed(error);try{write();}catch(Exception ignored){}}
    }
    private static Map<String,Object> readFramebuffer(Minecraft mc,String filename)throws Exception{
        int width=mc.width,height=mc.height,bytes=width*height*4;if(rgba==null){rgba=BufferUtils.createByteBuffer(bytes);depths=BufferUtils.createByteBuffer(bytes);}require(rgba.capacity()==bytes,"Framebuffer size changed during the fixed scene");
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),oldReadBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);int[] parameters={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL12.GL_PACK_IMAGE_HEIGHT,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SKIP_ROWS,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST},old=new int[parameters.length];for(int i=0;i<old.length;i++)old[i]=GL11.glGetInteger(parameters[i]);
        int target=mc.getRenderTarget().frameBufferId,readBuffer=0,depthBits=0;boolean targetBound=false;
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"A GL error preceded the correctness-only framebuffer read");
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,target);targetBound=true;readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);require(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"The actual Minecraft read framebuffer is incomplete");GL11.glReadBuffer(target==0?GL11.GL_BACK:GL30.GL_COLOR_ATTACHMENT0);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);for(int i=0;i<parameters.length;i++)GL11.glPixelStorei(parameters[i],i==0?1:0);if(target!=0)depthBits=GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE);else depthBits=GL11.glGetInteger(GL11.GL_DEPTH_BITS);require(depthBits>0,"The actual framebuffer has no depth attachment");rgba.clear();depths.clear();GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,rgba);GL11.glReadPixels(0,0,width,height,GL11.GL_DEPTH_COMPONENT,GL11.GL_UNSIGNED_INT,depths);require(GL11.glGetError()==GL11.GL_NO_ERROR,"Full framebuffer correctness read failed");}
        finally{if(targetBound)GL11.glReadBuffer(readBuffer);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo);for(int i=0;i<parameters.length;i++)GL11.glPixelStorei(parameters[i],old[i]);}
        require(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)==oldRead&&GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==oldDraw&&GL11.glGetInteger(GL11.GL_READ_BUFFER)==oldReadBuffer&&GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)==pbo,"Framebuffer/PBO state did not restore");for(int i=0;i<parameters.length;i++)require(GL11.glGetInteger(parameters[i])==old[i],"Pixel-pack state did not restore");
        Path folder=output().getParent();Files.createDirectories(folder);String stem=filename.substring(0,filename.length()-4),colorName=stem+".rgba8",depthName=stem+".depth32u";byte[] color=new byte[bytes],depth=new byte[bytes];rgba.get(0,color);depths.get(0,depth);Files.write(folder.resolve(colorName),color);Files.write(folder.resolve(depthName),depth);
        return Map.of("width",width,"height",height,"fullUncropped",true,"rgba8",Map.of("file",colorName,"bytes",bytes,"sha256",sha(color)),"depth32u",Map.of("file",depthName,"bytes",bytes,"sha256",sha(depth),"nativeByteOrder",ByteOrder.nativeOrder().toString(),"nativeDepthBits",depthBits),"readbackCorrectnessOnly",true,"pixelPackAndFramebufferStateRestored",true);
    }
    private static Map<String,Object> counters()throws Exception{
        var data=new LinkedHashMap<String,Object>();data.put("resolvedActorRows",ActorSceneGpu.resolvedActorRows);data.put("dispatches",ActorSceneGpu.dispatches);data.put("streamRows",ActorSceneGpu.streamRows+GpuPlayerPackets.actorSceneStreamRows);data.put("busyFallbacks",ActorSceneGpu.busyFallbacks);data.put("failures",ActorSceneGpu.failures);data.put("contextRefusals",ActorSceneGpu.contextRefusals);data.put("actorBytes",ActorSceneGpu.bytes());data.put("maximumActorBytes",ActorSceneGpu.MAX_BYTES);
        ActorSceneData scene=scene();if(scene!=null)data.put("scene",Map.of("tickWrites",scene.tickWrites,"tickReuses",scene.tickReuses,"captures",scene.frameCaptures,"gpuOnlyCaptures",scene.gpuOnlyCaptures,"lazyFullCpuRows",scene.cpuInterpolationRows,"overrideWords",scene.overrideWords,"constantOverrideWords",scene.constantOverrideWords,"scalarOverrideInterpolations",scene.overrideInterpolationWords,"poseMismatches",scene.poseMismatches));Object material=field(GpuPlayerPackets.class,"materials");if(material instanceof ActorSceneMaterials m)data.put("materials",Map.of("checks",m.checks,"created",m.created,"reuses",m.reuses,"fallbacks",m.fallbacks,"residentIdentities",m.size()));return data;
    }
    private static ActorSceneData scene()throws Exception{return (ActorSceneData)field(GpuPlayerPackets.class,"scene");}
    private static boolean contextAttached()throws Exception{return ((ActorSceneContext)field(ActorSceneGpu.class,"context")).attached();}
    private static Object field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void failed(Throwable error){if(failure==null)failure=error.toString();}
    private static void write()throws Exception{var data=new LinkedHashMap<String,Object>();data.put("passed",failure==null&&parentFinished&&cleanupComplete&&frames.size()==4);data.put("mode",on()?"on":"off");data.put("parentFinished",parentFinished);data.put("cleanupComplete",cleanupComplete);data.put("failure",failure);data.put("frames",frames);data.put("resets",resets);data.put("correctnessOnly",true);data.put("syntheticMaterialInjection",false);data.put("performanceClaim",false);data.put("gpuVisibilityAndGroupedIndirectQualified",false);Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(data));}
    private static final class Actor {
        final String name;long calls,captures,selections,materialCalls,gpuRows,batch;int job=-1,material,pool,source,layer,textureName,generation,nativeFrame,layerFrame;long uploadRevision;boolean sourceValid,gpuReady;String texture,sourceClass,sourceError;
        Actor(String name){this.name=name;}
        void clearFrame(){calls=captures=selections=materialCalls=gpuRows=0;job=-1;material=0;batch=Long.MIN_VALUE;sourceValid=gpuReady=false;sourceError=null;}
        Map<String,Object> data(){var out=new LinkedHashMap<String,Object>();out.put("name",name);out.put("captureCalls",calls);out.put("acceptedCaptures",captures);out.put("nativeTextureSelections",selections);out.put("materialObservations",materialCalls);out.put("actualDispatchedGpuRows",gpuRows);out.put("job",job);out.put("batch",batch);out.put("materialIdentity",material);out.put("gpuReady",gpuReady);out.put("nativeSelectedSourceValid",sourceValid);out.put("texture",texture);out.put("sourceClass",sourceClass);out.put("poolIdentity",pool);out.put("sourceIdentity",source);out.put("selectedLayer",layer);out.put("sourceGlName",textureName);out.put("sourceGeneration",generation);out.put("nativeFrame",nativeFrame);out.put("layerFrame",layerFrame);out.put("uploadAndCopyRevision",uploadRevision);if(sourceError!=null)out.put("sourceError",sourceError);return out;}
    }
    private static final class Owned {
        final Set<Integer> buffers=new LinkedHashSet<>(),programs=new LinkedHashSet<>();final Set<Long> fences=new LinkedHashSet<>();
        Owned()throws Exception{int output=(int)field(ActorSceneGpu.class,"output"),program=(int)field(ActorSceneGpu.class,"program");if(output!=0)buffers.add(output);if(program!=0)programs.add(program);for(Object slot:(Object[])field(ActorSceneGpu.class,"slots"))if(slot!=null){int ticks=(int)field(slot,"ticks"),frames=(int)field(slot,"frames");long fence=(long)field(slot,"fence");if(ticks!=0)buffers.add(ticks);if(frames!=0)buffers.add(frames);if(fence!=0)fences.add(fence);}}
    }
    private static final class Access {
        final VarHandle batchPool,layers,index,layerFrame,modelFrame,source,sourceId,generation;
        Access()throws Exception{String root="dev.rdh.argentum.";Class<?> model=Class.forName(root+"impl.render.entity.instancing.ModelInstancer"),batch=Class.forName(root+"impl.render.entity.instancing.InstanceBatcher$TextureBatch"),pool=Class.forName(root+"api.render.instancing.TextureArrayManager$Pool"),layer=Class.forName(root+"api.render.instancing.TextureArrayManager$Layer");batchPool=handle(batch,"pool");layers=handle(pool,"layers");index=handle(layer,"index");layerFrame=handle(layer,"frame");modelFrame=handle(model,"frame");source=handle(layer,"source");sourceId=handle(layer,"sourceId");generation=handle(layer,"generation");}
        private static VarHandle handle(Class<?> type,String name)throws Exception{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));}
    }
}
