// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

final class GpuVoxelSkinBorrowedBlendStateTest {
    private static final class State implements GpuVoxelSkinBorrowedBlendState.Access {
        Object context=new Object();
        long nativeContext=41;
        final GpuVoxelSkinBorrowedBlendState.Owner owner=new GpuVoxelSkinBorrowedBlendState.Owner();
        final int[] actual={1,770,771,770,771,32774,32779},cache={0,770,771,1,0};
        int writes,order,failRead=-1;boolean failCache;
        public Object context(){return owner.current(context,nativeContext,Thread.currentThread());}
        public boolean enabled(){return actual[0]!=0;}
        public int factor(int index){if(index==failRead)throw new IllegalStateException("read");return actual[index+1];}
        public int equation(boolean alpha){return actual[alpha?6:5];}
        public boolean cachedEnabled(){return cache[0]!=0;}
        public int cachedFactor(int index){return cache[index+1];}
        public void restoreCache(boolean enabled,int s,int d,int sa,int da){
            writes++;order=order*10+1;if(failCache)throw new IllegalStateException("cache");
            int[] values={s,d,sa,da};boolean changed=false;for(int i=0;i<4;i++)changed|=cache[i+1]!=values[i];
            if(changed)for(int i=0;i<4;i++)actual[i+1]=cache[i+1]=values[i];
            if(cache[0]!=(enabled?1:0))actual[0]=cache[0]=enabled?1:0;
        }
        public void restoreActual(boolean enabled,int s,int d,int sa,int da,int er,int ea){writes++;order=order*10+2;int[] values={enabled?1:0,s,d,sa,da,er,ea};System.arraycopy(values,0,actual,0,7);}
        void mutate(int step){actual[0]^=1;cache[0]^=1;actual[1]=cache[1]=1;actual[2]=cache[2]=1;actual[3]=cache[3]=step;actual[4]=cache[4]=1;actual[5]=actual[6]=32778;}
    }
    @Test void restoresDriverAndCacheIndependentlyIncludingObservedAlphaDisagreement(){
        for(int active=0;active<2;active++)for(int cached=0;cached<2;cached++){
            State state=new State();state.actual[0]=active;state.cache[0]=cached;int[] actual=state.actual.clone(),cache=state.cache.clone();
            GpuVoxelSkinBorrowedBlendState scope=new GpuVoxelSkinBorrowedBlendState(state);long ticket=scope.capture();state.mutate(1);scope.restore(ticket);
            assertArrayEquals(actual,state.actual);assertArrayEquals(cache,state.cache);assertEquals(12,state.order);assertEquals(2,state.writes);
        }
    }
    @Test void restoresAfterThrowWithoutChangingTheBodyException(){
        State state=new State();int[] actual=state.actual.clone(),cache=state.cache.clone();var scope=new GpuVoxelSkinBorrowedBlendState(state);RuntimeException expected=new RuntimeException("body");
        RuntimeException caught=assertThrows(RuntimeException.class,()->{long ticket=scope.capture();try{state.mutate(0);throw expected;}finally{scope.restore(ticket);}});
        assertSame(expected,caught);assertArrayEquals(actual,state.actual);assertArrayEquals(cache,state.cache);
    }
    @Test void boundedNestedScopesRestoreTheirOwnSnapshotsInLifoOrder(){
        State state=new State();var scope=new GpuVoxelSkinBorrowedBlendState(state);long[] tickets=new long[4];int[][] actual=new int[4][],cached=new int[4][];
        for(int i=0;i<4;i++){actual[i]=state.actual.clone();cached[i]=state.cache.clone();tickets[i]=scope.capture();state.mutate(i);}
        assertThrows(IllegalStateException.class,scope::capture);assertThrows(IllegalStateException.class,()->scope.restore(tickets[0]));assertEquals(0,state.writes);
        for(int i=3;i>=0;i--){scope.restore(tickets[i]);assertArrayEquals(actual[i],state.actual);assertArrayEquals(cached[i],state.cache);}
        assertThrows(IllegalStateException.class,()->scope.restore(tickets[0]));assertEquals(8,state.writes);
    }
    @Test void foreignContextCannotWriteDriverOrGlobalCacheAndDoesNotConsumeOwnerTicket(){
        State state=new State();Object original=state.context;var scope=new GpuVoxelSkinBorrowedBlendState(state);long ticket=scope.capture();state.mutate(0);state.context=new Object();
        assertThrows(IllegalStateException.class,()->scope.restore(ticket));assertThrows(IllegalStateException.class,scope::capture);assertEquals(0,state.writes);
        assertEquals(1,scope.pending());state.context=original;scope.restore(ticket);assertEquals(2,state.writes);assertEquals(0,scope.pending());state.context=new Object();assertFalse(scope.available());assertThrows(IllegalStateException.class,scope::capture);
    }
    @Test void sameCapabilitiesWithAnotherNativeContextCannotRestoreOrRebind(){
        State state=new State();var scope=new GpuVoxelSkinBorrowedBlendState(state);long ticket=scope.capture();Object sameCapabilities=state.context;state.nativeContext=42;
        assertSame(sameCapabilities,state.context);assertFalse(scope.available());assertThrows(IllegalStateException.class,()->scope.restore(ticket));assertEquals(0,state.writes);assertEquals(1,scope.pending());
        state.nativeContext=41;assertTrue(scope.available());scope.restore(ticket);assertEquals(0,scope.pending());assertEquals(2,state.writes);
    }
    @Test void absentContextCannotBindOrReadState(){
        State state=new State();state.nativeContext=0;var scope=new GpuVoxelSkinBorrowedBlendState(state);assertFalse(scope.available());assertThrows(IllegalStateException.class,scope::capture);assertEquals(0,state.writes);assertEquals(0,scope.pending());
        state.nativeContext=41;assertTrue(scope.available());long ticket=scope.capture();scope.restore(ticket);
    }
    @Test void foreignThreadCannotWriteOrConsumeTheOwnerSnapshot()throws Exception{
        State state=new State();var scope=new GpuVoxelSkinBorrowedBlendState(state);long ticket=scope.capture();AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread other=new Thread(()->{try{scope.restore(ticket);}catch(Throwable error){failure.set(error);}});other.start();other.join();assertInstanceOf(IllegalStateException.class,failure.get());assertEquals(0,state.writes);scope.restore(ticket);assertEquals(2,state.writes);
    }
    @Test void failedReadDoesNotOpenScopeOrPerformStateWrites(){
        State state=new State();var scope=new GpuVoxelSkinBorrowedBlendState(state);state.failRead=2;assertThrows(IllegalStateException.class,scope::capture);assertEquals(0,state.writes);
        state.failRead=-1;long ticket=scope.capture();assertEquals(1,ticket);scope.restore(ticket);
    }
    @Test void failedCacheRestorationStillAttemptsDriverRestoreAndRetiresScope(){
        State state=new State();int[] actual=state.actual.clone();var scope=new GpuVoxelSkinBorrowedBlendState(state);long ticket=scope.capture();state.mutate(1);state.failCache=true;
        assertEquals("cache",assertThrows(IllegalStateException.class,()->scope.restore(ticket)).getMessage());assertArrayEquals(actual,state.actual);assertEquals(12,state.order);assertThrows(IllegalStateException.class,()->scope.restore(ticket));
    }
    @Test void productionScopeHasNoHotAllocationsReflectionOrGlobalCacheFieldWrites()throws Exception{
        for(String suffix:new String[]{"","$NativeAccess","$Owner"}){
            ClassNode node=read("io/github/arcadiumclient/runtime/client/optimizations/GpuVoxelSkinBorrowedBlendState"+suffix);
            for(MethodNode method:node.methods){boolean hot=suffix.isEmpty()?method.name.equals("capture")||method.name.equals("restore"):List.of("current","context","enabled","factor","equation","cachedEnabled","cachedFactor","restoreCache","restoreActual").contains(method.name);if(!hot)continue;
                for(var ins:method.instructions){if(ins.getOpcode()==Opcodes.NEW)assertTrue(ins instanceof TypeInsnNode type&&type.desc.startsWith("java/lang/Illegal"),"Only exceptional errors may allocate");assertNotEquals(Opcodes.NEWARRAY,ins.getOpcode());assertNotEquals(Opcodes.ANEWARRAY,ins.getOpcode());if(ins instanceof MethodInsnNode call)assertFalse(call.owner.startsWith("java/lang/reflect/")||call.owner.equals("java/lang/Integer")||call.owner.equals("java/lang/Boolean"));if(ins instanceof FieldInsnNode field)assertFalse(field.getOpcode()==Opcodes.PUTSTATIC&&field.owner.contains("GlStateManager"));}
            }
        }
        MethodNode context=read("io/github/arcadiumclient/runtime/client/optimizations/GpuVoxelSkinBorrowedBlendState$NativeAccess").methods.stream().filter(m->m.name.equals("context")).findFirst().orElseThrow();
        assertTrue(Arrays.stream(context.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode call&&call.owner.equals("org/lwjgl/opengl/WGL")&&call.name.equals("wglGetCurrentContext")));
        ClassNode renderer=read("io/github/arcadiumclient/runtime/client/optimizations/GpuVoxelSkinRenderer");MethodNode flush=renderer.methods.stream().filter(m->m.name.equals("flush")).findFirst().orElseThrow();int captures=0,restores=0,nativeCalls=0;
        for(var ins:flush.instructions)if(ins instanceof MethodInsnNode call){if(call.owner.endsWith("GpuVoxelSkinBorrowedBlendState")&&call.name.equals("capture"))captures++;if(call.owner.endsWith("GpuVoxelSkinBorrowedBlendState")&&call.name.equals("restore"))restores++;if(call.owner.equals("java/lang/invoke/MethodHandle")&&call.name.equals("invoke")&&call.desc.contains("CommandList;)"))nativeCalls++;}
        assertEquals(1,captures);assertEquals(2,restores);assertTrue(nativeCalls>0);assertTrue(flush.tryCatchBlocks.stream().anyMatch(t->"java/lang/Throwable".equals(t.type)));
    }
    private static ClassNode read(String name)throws Exception{try(var stream=GpuVoxelSkinBorrowedBlendStateTest.class.getClassLoader().getResourceAsStream(name+".class")){assertNotNull(stream,name);ClassNode node=new ClassNode();new ClassReader(stream.readAllBytes()).accept(node,0);return node;}}
}
