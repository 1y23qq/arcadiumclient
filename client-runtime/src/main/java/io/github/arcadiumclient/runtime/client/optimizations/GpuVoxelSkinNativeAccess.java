// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import net.minecraft.resource.Identifier;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class GpuVoxelSkinNativeAccess {
    private static final String ROOT = "dev.rdh.argentum.impl.render.entity.instancing.";
    private static final String ADAPTER = ROOT + "GpuVoxelSkinGeometryArcadium";
    final PlayerBatchAccess capture = new PlayerBatchAccess();
    final MethodHandle geometry, submit, instanceUpload, buffers, initialize, upload, delete, moveFirst;
    final VarHandle vertexBuffer, instanceBuffer, tessellation, batchParts, managedActive;
    final MethodHandle modelConstructor,modelBegin,modelActive,modelFlush,modelDiscard,modelClose,poolWidth,poolHeight,capturedPlayer;
    final VarHandle modelPrograms,modelFrame,modelTextures,textureCache,cachedPool,poolKey,poolCapacity,poolLayers,layerSource,layerSourceId,layerGeneration,layerFrame;
    final GlVertexFormat instanceFormat;
    final Object normalPass;

    GpuVoxelSkinNativeAccess() throws ReflectiveOperationException {
        Class<?> geometryType = Class.forName(ROOT + "InstanceGeometry"), instancesType = Class.forName(ROOT + "Instances");
        geometryType.getDeclaredMethod("render", CommandList.class, instancesType);
        Class<?> adapter;
        try { adapter = Class.forName(ADAPTER, false, geometryType.getClassLoader()); }
        catch (ClassNotFoundException absent) { adapter = MethodHandles.privateLookupIn(geometryType, MethodHandles.lookup()).defineClass(adapter()); }
        geometry = MethodHandles.lookup().unreflectConstructor(adapter.getConstructor());
        Class<?> model = Class.forName(ROOT + "ModelInstancer"), pass = Class.forName(ROOT + "InstanceRenderPass");
        normalPass=enumValue(pass,"NORMAL");
        modelConstructor=MethodHandles.lookup().unreflectConstructor(model.getConstructor());
        modelBegin=method(model,"beginBatch");modelActive=method(model,"isBatchActive");modelFlush=method(model,"flush",CommandList.class);
        modelDiscard=method(model,"discardBatch");modelClose=method(model,"close",CommandList.class);
        modelPrograms=field(model,"programs");modelFrame=field(model,"frame");modelTextures=field(model,"textureArrays");
        Class<?> textureManager=Class.forName("dev.rdh.argentum.api.render.instancing.TextureArrayManager");
        textureCache=field(textureManager,"textures");cachedPool=field(Class.forName(textureManager.getName()+"$CachedTexture"),"pool");
        Class<?> pool=Class.forName(textureManager.getName()+"$Pool"),key=Class.forName(textureManager.getName()+"$PoolKey"),layer=Class.forName(textureManager.getName()+"$Layer");
        poolKey=field(pool,"key");poolCapacity=field(pool,"capacity");poolLayers=field(pool,"layers");
        poolWidth=method(key,"width");poolHeight=method(key,"height");
        layerSource=field(layer,"source");layerSourceId=field(layer,"sourceId");layerGeneration=field(layer,"generation");layerFrame=field(layer,"frame");
        capturedPlayer=method(Class.forName(ROOT+"EntityCapture"),"isPlayer");
        submit = method(model, "submit", geometryType, Identifier.class, pass, Matrix4f.class, int.class, Vector4fc.class, float.class, Vector4fc.class,
                Class.forName("dev.rdh.argentum.api.render.instancing.BoxTemplate"));
        batchParts = field(Class.forName(ROOT + "InstanceBatcher$TextureBatch"), "parts");
        moveFirst = orderingMethod(batchParts.varType());
        instanceUpload = MethodHandles.lookup().unreflect(instancesType.getMethod("upload"));
        Class<?> storage = Class.forName("dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer");
        buffers = MethodHandles.lookup().unreflectConstructor(storage.getConstructor(FloatBuffer.class, GlVertexFormat.class, GlVertexFormat.class));
        initialize = method(storage, "initialize", CommandList.class);
        upload = method(storage, "upload", CommandList.class, IntBuffer.class);
        delete = method(storage, "delete", CommandList.class);
        vertexBuffer = field(storage, "vertexBuffer"); instanceBuffer = field(storage, "instanceBuffer"); tessellation = field(storage, "tessellation");
        managedActive = managedActiveField(Class.forName("org.embeddedt.embeddium.impl.gl.device.GLRenderDevice"));
        instanceFormat = (GlVertexFormat) Class.forName(ROOT + "InstancedVertexFormats").getField("ENTITY_INSTANCE").get(null);
        if (instanceFormat.getStride() != 76) throw new IllegalStateException("Unknown native instance stride");
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static Object enumValue(Class<?> type,String name){return Enum.valueOf((Class)type,name);}
    private static final ClassValue<MethodHandle> textureGenerations=new ClassValue<>(){
        @Override protected MethodHandle computeValue(Class<?> type){
            try{
                var getter=type.getMethod("argentum$getGeneration");
                if(getter.getReturnType()!=int.class)throw new IllegalStateException("Unknown native texture generation");
                return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflect(getter).asType(MethodType.methodType(int.class,Object.class));
            }catch(NoSuchMethodException absent){return MethodHandles.dropArguments(MethodHandles.constant(int.class,0),0,Object.class);}
            catch(IllegalAccessException failure){throw new IllegalStateException(failure);}
        }
    };
    GpuVoxelSkinTextureBudget.Descriptor sourceDescriptor(Object main,Identifier texture,Object source,int sourceId)throws Throwable{
        Object cached=((Map<?,?>)textureCache.get(modelTextures.get(main))).get(source);
        if(cached==null)return null;
        Object pool=cachedPool.get(cached),layer=((Map<?,?>)poolLayers.get(pool)).get(texture);
        if(layer==null||layerSource.get(layer)!=source||(int)layerSourceId.get(layer)!=sourceId
                ||(int)layerFrame.get(layer)!=(int)modelFrame.get(main)
                ||(int)layerGeneration.get(layer)!=(int)textureGenerations.get(source.getClass()).invoke(source))return null;
        Object key=poolKey.get(pool);
        return new GpuVoxelSkinTextureBudget.Descriptor(key,(int)poolWidth.invoke(key),(int)poolHeight.invoke(key),(int)poolCapacity.get(pool));
    }

    void prioritize(Object batch, Object geometry, Object instances) throws Throwable {
        prioritize(moveFirst, batchParts.get(batch), geometry, instances);
    }
    static MethodHandle orderingMethod(Class<?> type) throws ReflectiveOperationException {
        if (!type.getName().equals("it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap"))
            throw new NoSuchMethodException("Unknown native voxel geometry ordering");
        return MethodHandles.publicLookup().unreflect(type.getMethod("getAndMoveToFirst", Object.class));
    }
    static void prioritize(MethodHandle method, Object parts, Object geometry, Object instances) throws Throwable {
        if (method.invoke(parts, geometry) != instances) throw new IllegalStateException("Unknown native voxel instance binding");
    }
    boolean acquireManagedScope(Object device, Runnable enter) { return acquireManagedScope(managedActive, device, enter); }
    static VarHandle managedActiveField(Class<?> type) throws ReflectiveOperationException {
        VarHandle active = field(type, "isActive");
        if (active.varType() != boolean.class) throw new NoSuchFieldException("Unknown native render scope state");
        return active;
    }
    static boolean acquireManagedScope(VarHandle active, Object device, Runnable enter) {
        boolean owned = !(boolean)active.get(device);
        if (owned) enter.run();
        return owned;
    }
    static void releaseManagedScope(boolean owned, Runnable exit) { if (owned) exit.run(); }

    private static byte[] adapter() {
        String owner = ADAPTER.replace('.', '/'), parent = (ROOT + "InstanceGeometry").replace('.', '/');
        String device = "Lorg/embeddedt/embeddium/impl/gl/device/CommandList;", instances = "L" + (ROOT + "Instances").replace('.', '/') + ";";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, owner, null, parent, null);
        writer.visitSource("GpuVoxelSkinNativeAccess.java", null);
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0); constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(0, 0); constructor.visitEnd();
        MethodVisitor draw = writer.visitMethod(Opcodes.ACC_PUBLIC, "render", "(" + device + instances + ")V", null, null);
        draw.visitCode(); draw.visitVarInsn(Opcodes.ALOAD, 0); draw.visitVarInsn(Opcodes.ALOAD, 1); draw.visitVarInsn(Opcodes.ALOAD, 2);
        draw.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/arcadiumclient/runtime/client/optimizations/GpuVoxelSkinPipeline", "drawNative", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", false);
        draw.visitInsn(Opcodes.RETURN); draw.visitMaxs(0, 0); draw.visitEnd();
        MethodVisitor delete = writer.visitMethod(Opcodes.ACC_PUBLIC, "delete", "(" + device + ")V", null, null);
        delete.visitCode(); delete.visitVarInsn(Opcodes.ALOAD, 0); delete.visitVarInsn(Opcodes.ALOAD, 1);
        delete.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/arcadiumclient/runtime/client/optimizations/GpuVoxelSkinPipeline", "deleteNative", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
        delete.visitInsn(Opcodes.RETURN); delete.visitMaxs(0, 0); delete.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }
    private static MethodHandle method(Class<?> type, String name, Class<?>... arguments) throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflect(type.getDeclaredMethod(name, arguments));
    }
    private static VarHandle field(Class<?> type, String name) throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));
    }
}
