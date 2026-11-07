// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

final class GpuVoxelSkinSelectorTest {
    private record Texture(String name){}
    private enum Pass { NORMAL,TRANSLUCENT,EMPTY }
    private record Batch(int serial){}
    private static final class Backend {
        final List<Object> arguments=new ArrayList<>();int calls;Throwable failure;
        Batch select(Texture texture,Pass pass)throws Throwable{arguments.add(this);arguments.add(texture);arguments.add(pass);calls++;if(failure!=null)throw failure;return pass==Pass.EMPTY?null:new Batch(calls);}
    }
    private static MethodHandle target()throws Exception{return MethodHandles.lookup().findVirtual(Backend.class,"select",MethodType.methodType(Batch.class,Texture.class,Pass.class));}
    @Test void discardedAndUsedReturnsExecuteEveryActualTypedSelectionInOrder()throws Throwable{
        Backend backend=new Backend();Texture a=new Texture("a"),b=new Texture("b");var select=new GpuVoxelSkinSelector(target());
        select.select(backend,a,Pass.NORMAL);Object second=select.select(backend,b,Pass.TRANSLUCENT);Object third=select.select(backend,b,Pass.TRANSLUCENT);assertNull(select.select(backend,a,Pass.EMPTY));
        assertEquals(4,backend.calls);assertEquals(new Batch(2),second);assertEquals(new Batch(3),third);assertNotSame(second,third);assertEquals(List.of(backend,a,Pass.NORMAL,backend,b,Pass.TRANSLUCENT,backend,b,Pass.TRANSLUCENT,backend,a,Pass.EMPTY),backend.arguments);
    }
    @Test void actualTargetExceptionIdentityAndReceiverTypeChecksArePreserved()throws Throwable{
        Backend backend=new Backend();Texture texture=new Texture("a");var select=new GpuVoxelSkinSelector(target());Exception failure=new Exception("native callback");backend.failure=failure;
        assertSame(failure,assertThrows(Exception.class,()->select.select(backend,texture,Pass.NORMAL)));assertEquals(1,backend.calls);assertSame(backend,backend.arguments.getFirst());
        assertThrows(ClassCastException.class,()->select.select(new Object(),texture,Pass.NORMAL));assertThrows(ClassCastException.class,()->select.select(backend,new Object(),Pass.NORMAL));assertThrows(ClassCastException.class,()->select.select(backend,texture,new Object()));assertEquals(1,backend.calls);
    }
    @Test void unknownArityPrimitiveReturnOrPrimitiveParametersAreRejectedCold()throws Exception{
        assertThrows(NullPointerException.class,()->new GpuVoxelSkinSelector(null));assertThrows(IllegalArgumentException.class,()->new GpuVoxelSkinSelector(target().bindTo(new Backend())));
        assertThrows(IllegalArgumentException.class,()->new GpuVoxelSkinSelector(MethodHandles.empty(MethodType.methodType(void.class,Object.class,Object.class,Object.class))));
        assertThrows(IllegalArgumentException.class,()->new GpuVoxelSkinSelector(MethodHandles.dropArguments(MethodHandles.constant(int.class,1),0,Object.class,Object.class,Object.class)));
        assertThrows(IllegalArgumentException.class,()->new GpuVoxelSkinSelector(MethodHandles.dropArguments(MethodHandles.identity(Object.class),0,int.class,Object.class)));
    }
    @Test void productionUsesOneExactErasedSignatureAtAllThreeSitesWithoutHotAdaptation()throws Exception{
        String helper="io/github/arcadiumclient/runtime/client/optimizations/GpuVoxelSkinSelector";ClassNode type=read(helper);MethodNode hot=type.methods.stream().filter(m->m.name.equals("select")).findFirst().orElseThrow();int exact=0;
        for(var ins:hot.instructions){assertNotEquals(Opcodes.NEW,ins.getOpcode());assertNotEquals(Opcodes.NEWARRAY,ins.getOpcode());assertNotEquals(Opcodes.ANEWARRAY,ins.getOpcode());if(ins instanceof MethodInsnNode call){assertEquals("java/lang/invoke/MethodHandle",call.owner);assertEquals("invokeExact",call.name);assertEquals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",call.desc);exact++;}}
        assertEquals(1,exact);ClassNode renderer=read("io/github/arcadiumclient/runtime/client/optimizations/GpuVoxelSkinRenderer");Set<String> callers=new HashSet<>();int coldReads=0;
        for(MethodNode method:renderer.methods)for(var ins:method.instructions){if(ins instanceof FieldInsnNode field&&field.owner.endsWith("/PlayerBatchAccess")&&field.name.equals("select")){assertEquals("<init>",method.name);coldReads++;}if(ins instanceof MethodInsnNode call&&call.owner.equals(helper)&&call.name.equals("select")){assertEquals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",call.desc);assertTrue(callers.add(method.name));}}
        assertEquals(1,coldReads);assertEquals(Set.of("preflight","submit","prepareTexture"),callers);
    }
    private static ClassNode read(String name)throws Exception{try(var stream=GpuVoxelSkinSelectorTest.class.getClassLoader().getResourceAsStream(name+".class")){assertNotNull(stream,name);ClassNode c=new ClassNode();new ClassReader(stream.readAllBytes()).accept(c,0);return c;}}
}
