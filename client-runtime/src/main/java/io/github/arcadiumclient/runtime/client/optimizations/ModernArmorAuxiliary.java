// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.buffer.GlBufferUsage;
import org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.RenderDevice;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class ModernArmorAuxiliary {
    private static final int STRIDE=19,MAX_TEXTURES=32,MAX_PROGRAMS=4,MAX_DIMENSION=128;
    private static final IdentityHashMap<Object,State> owners=new IdentityHashMap<>(), auxiliaries=new IdentityHashMap<>();
    private static final IdentityHashMap<Object,Geometry> wrappers=new IdentityHashMap<>();
    private static final ModernArmorAuxiliaryBudget budget=new ModernArmorAuxiliaryBudget();
    private static ModernArmorAuxiliaryAccess api;
    private static boolean failed;
    private static String status="Waiting for original modern armor preparation";
    public static long coldLayers,preparedCopies,queuedParts,preShadowFlushes,nativeFallbackFlushes,closedGeometries,failures;

    private static final class TextureToken {
        final Texture source;final int id;
        TextureToken(Texture source){this.source=source;id=source.getGlId();}
    }
    private static final class Geometry {
        final State owner;final Object source,sourceBuffers,wrapper;final int count;final long bytes;
        final GlVertexFormat vertexFormat,instanceFormat;
        Object buffers;boolean ready;
        Geometry(State owner,Object source,Object wrapper)throws Throwable{
            this.owner=owner;this.source=source;this.wrapper=wrapper;sourceBuffers=api.partBuffers.get(source);count=(int)api.partVertexCount.get(source);
            vertexFormat=(GlVertexFormat)api.vertexFormat.get(sourceBuffers);instanceFormat=(GlVertexFormat)api.instanceFormat.get(sourceBuffers);
            if(count!=24||vertexFormat.getStride()<16||vertexFormat.getStride()>64||instanceFormat.getStride()!=76)throw new IllegalArgumentException("Unsupported original armor vertex format");
            bytes=(long)count*vertexFormat.getStride();FloatBuffer cold=(FloatBuffer)api.vertices.get(sourceBuffers);GlMutableBuffer live=(GlMutableBuffer)api.vertexBuffer.get(sourceBuffers);
            if(cold!=null?(long)cold.remaining()*4!=bytes:live==null||live.getSize()!=bytes)throw new IllegalArgumentException("Original armor vertex extent changed");
        }
    }
    private static final class State {
        final Object main,auxiliary;
        final IdentityHashMap<Object,Geometry> geometries=new IdentityHashMap<>();
        final IdentityHashMap<Model,List<Object>> models=new IdentityHashMap<>();
        final HashSet<Identifier> requestedTextures=new HashSet<>();
        final HashMap<Identifier,TextureToken> textures=new HashMap<>();
        long epoch=Long.MIN_VALUE;int pending;boolean trusted,closed;
        State(Object main,Object auxiliary){this.main=main;this.auxiliary=auxiliary;}
    }
    private ModernArmorAuxiliary(){}

    static boolean prepareModel(Object main,Model model,Object[] originals){
        if(failed||!ModernArmorGlintPipeline.shaderAdapterEnabled())return false;
        try{
            State state=state(main);if(state==null)return false;
            List<Object> required=state.models.get(model);
            if(required==null){
                if(!budget.profile(main,model))return decline("Modern armor profile budget reached");
                required=new ArrayList<>(originals.length);
                for(Object original:originals){
                    Geometry geometry=state.geometries.get(original);
                    if(geometry==null){geometry=new Geometry(state,original,api.wrapper.invoke());
                        if(!budget.geometry(main,model,geometry.wrapper,geometry.bytes))return decline("Modern armor geometry budget reached");
                        state.geometries.put(original,geometry);wrappers.put(geometry.wrapper,geometry);}
                    required.add(geometry.wrapper);
                }
                state.models.put(model,required);
            }
            boolean ready=state.trusted;
            for(Object wrapper:required)ready&=wrappers.get(wrapper).ready;
            if(!ready){coldLayers++;return decline("Preparing original armor stores; retaining the original layer");}
            return begin(state);
        }catch(Throwable error){fail(error);return false;}
    }
    static boolean layer(Object main,Model first,Model second){
        State state=owners.get(main);if(state==null||!state.models.containsKey(first)||!state.models.containsKey(second))return false;
        ArrayList<Object> parts=new ArrayList<>(state.models.get(first));parts.addAll(state.models.get(second));
        return budget.layer(main,parts);
    }
    static boolean prepareTextures(Object main,Collection<Identifier> textures){
        if(failed||api==null)return false;State state=owners.get(main);if(state==null)return false;
        try{
            for(Identifier texture:textures){
                if(!standardTexture(texture)||CeraEmissiveAdmission.nativeSubmitRequired(texture))return decline("Modern armor material retains native submission");
                if(!state.requestedTextures.contains(texture)&&state.requestedTextures.size()==MAX_TEXTURES)return decline("Modern armor texture budget reached");
                state.requestedTextures.add(texture);
            }
            for(Identifier texture:textures){TextureToken known=state.textures.get(texture);Texture actual=Minecraft.getInstance().getTextureManager().get(texture);
                if(known==null||known.source!=actual||actual.getGlId()!=known.id){state.textures.remove(texture);coldLayers++;return decline("Preparing armor textures; retaining the original layer");}}
            return begin(state);
        }catch(Throwable error){fail(error);return false;}
    }
    private static State state(Object main)throws Throwable{
        if(main==null||GpuModelAuxiliaries.isAuxiliaryBackend(main)||GpuModelTransforms.batchOwner()!=main)return null;
        State known=owners.get(main);if(known!=null)return known.closed?null:known;
        if(api==null)api=new ModernArmorAuxiliaryAccess();
        if(!budget.owner(main))return null;
        Object auxiliary=api.constructor.invoke();
        if(api.programs.get(auxiliary)==api.programs.get(main))throw new IllegalStateException("Auxiliary and main programs share mutable ownership");
        if(!GpuModelAuxiliaries.register(main,auxiliary)){budget.release(main);return null;}
        State created=new State(main,auxiliary);owners.put(main,created);auxiliaries.put(auxiliary,created);return created;
    }
    private static boolean begin(State state)throws Throwable{
        if(state.epoch==GpuModelTransforms.batchEpoch())return (boolean)api.active.invoke(state.auxiliary);
        if(state.pending!=0)throw new IllegalStateException("Previous modern armor batch was not flushed");
        if((boolean)api.active.invoke(state.auxiliary))api.discard.invoke(state.auxiliary);
        if(!(boolean)api.begin.invoke(state.auxiliary))return decline("Owned modern armor renderer unavailable");
        api.textureArraysSupported.set(state.auxiliary,false);
        budget.begin(state.main);state.epoch=GpuModelTransforms.batchEpoch();return true;
    }
    static Object destination(Object main){State state=owners.get(main);return state!=null&&state.trusted&&!state.closed&&state.epoch==GpuModelTransforms.batchEpoch()?state.auxiliary:null;}
    static Object geometry(Object main,Object original){State state=owners.get(main);Geometry geometry=state==null?null:state.geometries.get(original);return geometry!=null&&geometry.ready?geometry.wrapper:null;}
    static Object instances(Object wrapper,Object batch)throws Throwable{
        Geometry geometry=wrappers.get(wrapper);if(geometry==null||!geometry.ready)return null;
        Map<?,?> parts=(Map<?,?>)api.batchParts.get(batch);Object known=parts.get(wrapper);
        if(known!=null)return known;
        if(!budget.canInstance())return null;
        Object created=api.capture.instances.invoke(wrapper,batch);
        if((int)api.capture.count.get(created)!=0||(int)api.capture.size.get(created)!=0)throw new IllegalStateException("New owned armor instances are not empty");
        api.capture.data.set(created,new int[STRIDE]);api.uploadBuffer.set(created,BufferUtils.createIntBuffer(STRIDE));
        if(!budget.instance(wrapper,created))throw new IllegalStateException("Preflight modern instance capacity was exceeded");
        return created;
    }
    static boolean reserve(Object wrapper,Object instances,int words)throws Throwable{
        Geometry geometry=wrappers.get(wrapper);if(geometry==null||words<STRIDE||words%STRIDE!=0)return false;
        int[] data=(int[])api.capture.data.get(instances);IntBuffer upload=(IntBuffer)api.uploadBuffer.get(instances);
        int capacity=ModernArmorAuxiliaryBudget.capacity(data.length/STRIDE,words/STRIDE);
        if(capacity==Integer.MAX_VALUE||!budget.grow(instances,capacity,Math.max(upload.capacity()/STRIDE,capacity),capacity))return false;
        if(data.length<capacity*STRIDE)api.capture.data.set(instances,java.util.Arrays.copyOf(data,capacity*STRIDE));
        if(upload.capacity()<capacity*STRIDE)api.uploadBuffer.set(instances,BufferUtils.createIntBuffer(capacity*STRIDE));
        return true;
    }
    static void queued(Object wrapper,Object instances,int count){
        Geometry geometry=wrappers.get(wrapper);if(geometry==null||!budget.used(instances,count))throw new IllegalStateException("Owned modern armor counters exceed retained capacity");
        geometry.owner.pending++;queuedParts++;
        status="Original armor queued in its owned auxiliary";
    }
    static boolean ownsProgram(int program) {
        if (api == null) return false;
        for (State state : owners.values()) for (Object value : ((Map<?,?>)api.programs.get(state.auxiliary)).values())
            if (((GlProgram<?>)value).handle() == program) return true;
        return false;
    }
    public static boolean isAuxiliaryBackend(Object value){return auxiliaries.containsKey(value);}
    public static void beforeShadows(Object commandList){
        if(owners.isEmpty())return;
        try{
            CommandList commands=(CommandList)commandList;
            if(!ResolvedUploadPolicy.trusted(commands))return;
            for(State state:new ArrayList<>(owners.values()))if(state.main==GpuModelTransforms.batchOwner()&&!state.closed){
                flush(state,commands,true);
                if(!failed)try{prepareStores(state,commands);}catch(Throwable coldFailure){
                    owners.remove(state.main);try{close(state,commands);}catch(Throwable cleanup){coldFailure.addSuppressed(cleanup);}fail(coldFailure);
                }
            }
        }catch(Throwable error){fail(error);}
    }
    public static void beforeMainFlush(Object main,Object commandList){
        if(owners.isEmpty()||isAuxiliaryBackend(main))return;
        State state=owners.get(main);if(state==null||state.pending==0)return;
        try{flush(state,(CommandList)commandList,false);}catch(Throwable error){fail(error);}
    }
    private static void prepareStores(State state,CommandList commands)throws Throwable{
        if(state.epoch!=GpuModelTransforms.batchEpoch()&&!begin(state))return;
        for(Geometry geometry:state.geometries.values())if(!geometry.ready){
            api.initialize.invoke(geometry.sourceBuffers,commands);
            GlMutableBuffer original=(GlMutableBuffer)api.vertexBuffer.get(geometry.sourceBuffers);
            if(original==null||original.getSize()!=geometry.bytes)throw new IllegalStateException("Original armor store changed during cold preparation");
            geometry.buffers=api.storage.invoke(BufferUtils.createFloatBuffer(0),geometry.vertexFormat,geometry.instanceFormat);
            api.initialize.invoke(geometry.buffers,commands);
            GlMutableBuffer target=(GlMutableBuffer)api.vertexBuffer.get(geometry.buffers),instances=(GlMutableBuffer)api.instanceBuffer.get(geometry.buffers);
            if(target==original||instances==api.instanceBuffer.get(geometry.sourceBuffers))throw new IllegalStateException("Owned modern geometry aliases main buffers");
            commands.allocateStorage(target,geometry.bytes,GlBufferUsage.STATIC_DRAW);
            commands.copyBufferSubData(original,target,0,0,geometry.bytes);
            commands.allocateStorage(instances,76,GlBufferUsage.STREAM_DRAW);
            if(target.getSize()!=geometry.bytes||instances.getSize()!=76)throw new IllegalStateException("Owned modern geometry extent was not allocated");
            geometry.ready=true;preparedCopies++;
        }
        for(Identifier identifier:state.requestedTextures)if(!state.textures.containsKey(identifier)){
            if(CeraEmissiveAdmission.nativeSubmitRequired(identifier))continue;
            Minecraft.getInstance().getTextureManager().bind(identifier);
            Texture texture=Minecraft.getInstance().getTextureManager().get(identifier);
            int width=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_WIDTH),height=GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_HEIGHT);
            if(width<1||height<1||width>MAX_DIMENSION||height>MAX_DIMENSION)continue;
            if(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)!=texture.getGlId())continue;
            state.textures.put(identifier,new TextureToken(texture));
        }
        GlProgram<?> program=(GlProgram<?>)api.program.invoke(state.auxiliary);int programs=0;
        for(State owner:owners.values())programs+=((Map<?,?>)api.programs.get(owner.auxiliary)).size();
        if(programs>MAX_PROGRAMS||!ModernArmorGlintPipeline.verifiedProgram(program.handle()))throw new IllegalStateException("Owned modern armor program was not preflighted");
        for(Object value:((Map<?,?>)api.programs.get(state.main)).values())if(((GlProgram<?>)value).handle()==program.handle())throw new IllegalStateException("Owned modern program aliases main");
        state.trusted=true;
        status="Owned original armor stores ready";
    }
    private static void flush(State state,CommandList commands,boolean beforeShadows)throws Throwable{
        if(state.pending==0)return;
        api.flush.invoke(state.auxiliary,commands);state.pending=0;
        if(beforeShadows){preShadowFlushes++;status="Original armor drawn before native shadows";}else{nativeFallbackFlushes++;fail(new IllegalStateException("Modern armor missed the actual shadow boundary"));}
    }
    public static void drawNative(Object wrapper,Object commandList,Object instances){
        Geometry geometry=wrappers.get(wrapper);if(geometry==null||!geometry.ready||geometry.buffers==null)throw new IllegalStateException("Unprepared modern armor geometry reached native draw");
        try{api.draw.invoke(geometry.buffers,(CommandList)commandList,(IntBuffer)api.instanceUpload.invoke(instances),geometry.count,(int)api.capture.count.get(instances));
            GlMutableBuffer buffer=(GlMutableBuffer)api.instanceBuffer.get(geometry.buffers);
            if(buffer.getSize()%76!=0||buffer.getSize()>(long)budget.gpuCapacity(wrapper)*76)throw new IllegalStateException("Owned modern instance store exceeded retained capacity");
        }catch(Throwable error){fail(error);throw new IllegalStateException("Modern armor auxiliary draw failed",error);}
    }
    public static void deleteNative(Object wrapper,Object commandList){Geometry geometry=wrappers.get(wrapper);if(geometry!=null)try{delete(geometry,(CommandList)commandList);}catch(Throwable error){fail(error);}}
    private static void delete(Geometry geometry,CommandList commands)throws Throwable{
        if(geometry.buffers!=null){api.delete.invoke(geometry.buffers,commands);geometry.buffers=null;closedGeometries++;}geometry.ready=false;
    }
    public static void beforeMainClose(Object main,Object commandList){
        State state=owners.remove(main);if(state==null)return;
        try{close(state,(CommandList)commandList);}catch(Throwable error){fail(error);}
    }
    private static void close(State state,CommandList commands)throws Throwable{
        state.closed=true;
        try{for(Geometry geometry:state.geometries.values())delete(geometry,commands);api.close.invoke(state.auxiliary,commands);}
        finally{for(Geometry geometry:state.geometries.values())wrappers.remove(geometry.wrapper);auxiliaries.remove(state.auxiliary);GpuModelAuxiliaries.unregister(state.auxiliary);budget.release(state.main);}
    }
    public static void close(){
        if(owners.isEmpty())return;
        boolean managed=false;
        try{managed=!(boolean)api.managedActive.get(RenderDevice.INSTANCE);if(managed)RenderDevice.enterManagedCode();
            try(CommandList commands=RenderDevice.INSTANCE.createCommandList()){
                for(State state:new ArrayList<>(owners.values())){owners.remove(state.main);close(state,commands);}
            }
        }catch(Throwable error){fail(error);}finally{if(managed)RenderDevice.exitManagedCode();}
    }
    private static boolean standardTexture(Identifier identifier){
        String value=identifier.toString();
        if(value.equals("overflowanimations:textures/misc/enchanted_glint_item.png")||value.equals("overflowanimations:textures/misc/enchanted_glint_armor.png"))return true;
        for(String material:List.of("leather","chainmail","iron","gold","diamond"))for(int layer=1;layer<=2;layer++){
            String prefix="minecraft:textures/models/armor/"+material+"_layer_"+layer;
            if(value.equals(prefix+".png")||material.equals("leather")&&value.equals(prefix+"_overlay.png"))return true;
        }return false;
    }
    private static boolean decline(String reason){status=reason;return false;}
    private static void fail(Throwable error){failures++;failed=true;status="Modern auxiliary disabled; retaining original armor";org.apache.logging.log4j.LogManager.getLogger("Arcadium/Modern Armor").warn(status,error);}
    public static String status(){return status+" | Copies: "+preparedCopies+" | Pre-shadow flushes: "+preShadowFlushes+" | Retained instance slots: "+budget.units();}
}
