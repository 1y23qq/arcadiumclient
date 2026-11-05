// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.objectweb.asm.*;

final class GpuResidentBodyAccess {
    private static final String ROOT="dev.rdh.argentum.impl.render.entity.instancing.";
    private static final String ADAPTER=ROOT+"ResidentBodyGeometryArcadium";
    final PlayerBatchAccess capture=new PlayerBatchAccess();
    final MethodHandle geometry,nativeCube,program,storage,initialize,upload,delete,instanceUpload;
    final VarHandle instanceBuffer,tessellation,managedActive,profileMirrors,profileUv,profileTextureSize,profileSize,uploadBuffer,batchParts;
    final VarHandle modelTextures,modelFrame,textureCache,cachedPool,poolKey,poolLayers,layerSource,layerSourceId,layerGeneration,layerFrame,layerIndex;
    final MethodHandle poolWidth,poolHeight,poolMin,poolMag;
    final GlVertexFormat vertexFormat,instanceFormat;
    GpuResidentBodyAccess()throws ReflectiveOperationException {
        Class<?> base=Class.forName(ROOT+"InstanceGeometry"),instances=Class.forName(ROOT+"Instances");
        base.getDeclaredMethod("render",CommandList.class,instances);
        Class<?> adapter;
        try{adapter=Class.forName(ADAPTER,false,base.getClassLoader());}
        catch(ClassNotFoundException absent){adapter=MethodHandles.privateLookupIn(base,MethodHandles.lookup()).defineClass(adapter());}
        geometry=MethodHandles.lookup().unreflectConstructor(adapter.getConstructor());
        nativeCube=method(Class.forName(ROOT+"BoxGeometry"),"build",boolean.class);program=method(Class.forName(ROOT+"ModelInstancer"),"getProgram");
        Class<?> buffer=Class.forName("dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer");
        storage=MethodHandles.lookup().unreflectConstructor(buffer.getConstructor(FloatBuffer.class,GlVertexFormat.class,GlVertexFormat.class));
        initialize=method(buffer,"initialize",CommandList.class);upload=method(buffer,"upload",CommandList.class,IntBuffer.class);delete=method(buffer,"delete",CommandList.class);
        instanceUpload=MethodHandles.lookup().unreflect(instances.getMethod("upload"));
        instanceBuffer=field(buffer,"instanceBuffer");tessellation=field(buffer,"tessellation");managedActive=field(Class.forName("org.embeddedt.embeddium.impl.gl.device.GLRenderDevice"),"isActive");
        Class<?> formats=Class.forName(ROOT+"InstancedVertexFormats");vertexFormat=(GlVertexFormat)formats.getField("ENTITY_VERTEX").get(null);instanceFormat=(GlVertexFormat)formats.getField("ENTITY_INSTANCE").get(null);
        if(vertexFormat.getStride()!=48||instanceFormat.getStride()!=76)throw new IllegalStateException("Unsupported resident body native formats");
        Class<?> profile=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuPlayerPipeline$Profile");
        profileMirrors=field(profile,"mirrored");profileUv=field(profile,"uv");profileTextureSize=field(profile,"textureSize");profileSize=field(profile,"size");
        uploadBuffer=field(Class.forName("dev.rdh.argentum.api.render.instancing.InstanceDataBuffer"),"upload");batchParts=field(Class.forName(ROOT+"InstanceBatcher$TextureBatch"),"parts");
        Class<?> model=Class.forName(ROOT+"ModelInstancer"),textures=Class.forName("dev.rdh.argentum.api.render.instancing.TextureArrayManager");
        modelTextures=field(model,"textureArrays");modelFrame=field(model,"frame");textureCache=field(textures,"textures");cachedPool=field(Class.forName(textures.getName()+"$CachedTexture"),"pool");
        Class<?> pool=Class.forName(textures.getName()+"$Pool"),key=Class.forName(textures.getName()+"$PoolKey"),layer=Class.forName(textures.getName()+"$Layer");
        poolKey=field(pool,"key");poolLayers=field(pool,"layers");layerSource=field(layer,"source");layerSourceId=field(layer,"sourceId");layerGeneration=field(layer,"generation");layerFrame=field(layer,"frame");layerIndex=field(layer,"index");
        poolWidth=method(key,"width");poolHeight=method(key,"height");poolMin=method(key,"minFilter");poolMag=method(key,"magFilter");
    }
    GpuVoxelSkinUploadFacts.Fact material(Object main,Identifier texture)throws Throwable {
        var source=Minecraft.getInstance().getTextureManager().get(texture);if(source==null)return null;
        int name=source.getGlId(),generation=GpuVoxelSkinUploadFacts.generation(source);
        GpuVoxelSkinUploadFacts.Fact fact=GpuVoxelSkinUploadFacts.current(source,name,generation);if(fact==null)return null;
        Object cached=((Map<?,?>)textureCache.get(modelTextures.get(main))).get(source);if(cached==null)return null;
        Object pool=cachedPool.get(cached),key=poolKey.get(pool),layer=((Map<?,?>)poolLayers.get(pool)).get(texture);
        if(layer==null||layerSource.get(layer)!=source||(int)layerSourceId.get(layer)!=name||(int)layerGeneration.get(layer)!=generation
                ||(int)layerFrame.get(layer)!=(int)modelFrame.get(main)||(int)layerIndex.get(layer)!=(int)capture.selectedLayer.get(main)
                ||!GpuVoxelSkinUploadFacts.matches(pool,(int)layerIndex.get(layer),fact))return null;
        if((int)poolWidth.invoke(key)!=64||(int)poolHeight.invoke(key)!=64
                ||(int)poolMin.invoke(key)!=org.lwjgl.opengl.GL11.GL_NEAREST||(int)poolMag.invoke(key)!=org.lwjgl.opengl.GL11.GL_NEAREST)return null;
        return fact;
    }
    private static MethodHandle method(Class<?> type,String name,Class<?>...arguments)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflect(type.getDeclaredMethod(name,arguments));}
    private static VarHandle field(Class<?> type,String name)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));}
    private static byte[] adapter() {
        String owner=ADAPTER.replace('.','/'),parent=(ROOT+"InstanceGeometry").replace('.','/'),command="Lorg/embeddedt/embeddium/impl/gl/device/CommandList;",instances="L"+(ROOT+"Instances").replace('.','/')+";";
        String bridge="io/github/arcadiumclient/runtime/client/optimizations/GpuResidentBodies";
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,owner,null,parent,null);writer.visitSource("GpuResidentBodyAccess.java",null);
        MethodVisitor constructor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.visitCode();constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(0,0);constructor.visitEnd();
        MethodVisitor box=writer.visitMethod(Opcodes.ACC_PUBLIC,"usesBoxInstancing","()Z",null,null);box.visitCode();box.visitInsn(Opcodes.ICONST_1);box.visitInsn(Opcodes.IRETURN);box.visitMaxs(0,0);box.visitEnd();
        MethodVisitor draw=writer.visitMethod(Opcodes.ACC_PUBLIC,"render","("+command+instances+")V",null,null);draw.visitCode();draw.visitVarInsn(Opcodes.ALOAD,0);draw.visitVarInsn(Opcodes.ALOAD,1);draw.visitVarInsn(Opcodes.ALOAD,2);draw.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"drawNative","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false);draw.visitInsn(Opcodes.RETURN);draw.visitMaxs(0,0);draw.visitEnd();
        MethodVisitor delete=writer.visitMethod(Opcodes.ACC_PUBLIC,"delete","("+command+")V",null,null);delete.visitCode();delete.visitVarInsn(Opcodes.ALOAD,0);delete.visitVarInsn(Opcodes.ALOAD,1);delete.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"deleteNative","(Ljava/lang/Object;Ljava/lang/Object;)V",false);delete.visitInsn(Opcodes.RETURN);delete.visitMaxs(0,0);delete.visitEnd();writer.visitEnd();return writer.toByteArray();
    }
}
