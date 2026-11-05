// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class ModernArmorAuxiliaryAccess {
    private static final String ROOT="dev.rdh.argentum.impl.render.entity.instancing.";
    private static final String ADAPTER=ROOT+"ModernArmorGeometryArcadium";
    final PlayerBatchAccess capture=new PlayerBatchAccess();
    final MethodHandle constructor,begin,active,flush,discard,close,program,wrapper,storage,initialize,draw,delete,instanceUpload;
    final VarHandle programs,textureArraysSupported,partBuffers,partVertexCount,vertices,vertexBuffer,instanceBuffer,vertexFormat,instanceFormat,uploadBuffer,batchParts,managedActive;

    ModernArmorAuxiliaryAccess()throws ReflectiveOperationException{
        Class<?> model=Class.forName(ROOT+"ModelInstancer"),geometry=Class.forName(ROOT+"InstanceGeometry"),instances=Class.forName(ROOT+"Instances");
        Class<?> part=Class.forName(ROOT+"PartGeometry"),buffers=Class.forName("dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer"),data=Class.forName("dev.rdh.argentum.api.render.instancing.InstanceDataBuffer");
        geometry.getDeclaredMethod("render",CommandList.class,instances);
        Class<?> adapter;
        try{adapter=Class.forName(ADAPTER,false,geometry.getClassLoader());}
        catch(ClassNotFoundException absent){adapter=MethodHandles.privateLookupIn(geometry,MethodHandles.lookup()).defineClass(adapter());}
        wrapper=MethodHandles.lookup().unreflectConstructor(adapter.getConstructor());
        constructor=MethodHandles.lookup().unreflectConstructor(model.getConstructor());
        begin=method(model,"beginBatch");active=method(model,"isBatchActive");flush=method(model,"flush",CommandList.class);discard=method(model,"discardBatch");close=method(model,"close",CommandList.class);program=method(model,"getProgram");
        programs=field(model,"programs");textureArraysSupported=field(model,"textureArraysSupported");
        partBuffers=field(part,"buffers");partVertexCount=field(part,"vertexCount");
        storage=MethodHandles.lookup().unreflectConstructor(buffers.getConstructor(FloatBuffer.class,GlVertexFormat.class,GlVertexFormat.class));
        initialize=method(buffers,"initialize",CommandList.class);draw=method(buffers,"draw",CommandList.class,IntBuffer.class,int.class,int.class);delete=method(buffers,"delete",CommandList.class);
        vertices=field(buffers,"vertices");vertexBuffer=field(buffers,"vertexBuffer");instanceBuffer=field(buffers,"instanceBuffer");vertexFormat=field(buffers,"vertexFormat");instanceFormat=field(buffers,"instanceFormat");
        uploadBuffer=field(data,"upload");instanceUpload=MethodHandles.lookup().unreflect(instances.getMethod("upload"));batchParts=field(Class.forName(ROOT+"InstanceBatcher$TextureBatch"),"parts");
        managedActive=field(Class.forName("org.embeddedt.embeddium.impl.gl.device.GLRenderDevice"),"isActive");
    }
    private static MethodHandle method(Class<?> type,String name,Class<?>...args)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflect(type.getDeclaredMethod(name,args));}
    private static VarHandle field(Class<?> type,String name)throws ReflectiveOperationException{return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));}

    static byte[] adapter(){
        String owner=ADAPTER.replace('.','/'),parent=(ROOT+"InstanceGeometry").replace('.','/'),command="Lorg/embeddedt/embeddium/impl/gl/device/CommandList;",instances="L"+(ROOT+"Instances").replace('.','/')+";";
        String bridge="io/github/arcadiumclient/runtime/client/optimizations/ModernArmorAuxiliary";
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,owner,null,parent,null);
        writer.visitSource("ModernArmorAuxiliaryAccess.java",null);
        MethodVisitor constructor=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);constructor.visitCode();constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(0,0);constructor.visitEnd();
        MethodVisitor render=writer.visitMethod(Opcodes.ACC_PUBLIC,"render","("+command+instances+")V",null,null);render.visitCode();render.visitVarInsn(Opcodes.ALOAD,0);render.visitVarInsn(Opcodes.ALOAD,1);render.visitVarInsn(Opcodes.ALOAD,2);render.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"drawNative","(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",false);render.visitInsn(Opcodes.RETURN);render.visitMaxs(0,0);render.visitEnd();
        MethodVisitor delete=writer.visitMethod(Opcodes.ACC_PUBLIC,"delete","("+command+")V",null,null);delete.visitCode();delete.visitVarInsn(Opcodes.ALOAD,0);delete.visitVarInsn(Opcodes.ALOAD,1);delete.visitMethodInsn(Opcodes.INVOKESTATIC,bridge,"deleteNative","(Ljava/lang/Object;Ljava/lang/Object;)V",false);delete.visitInsn(Opcodes.RETURN);delete.visitMaxs(0,0);delete.visitEnd();
        writer.visitEnd();return writer.toByteArray();
    }
}
