// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GpuVoxelSkinAuxiliaryAbiTest {
    @Test void pinnedNativeBackendSupportsOwnedLifecycleAndReadOnlyDescriptorPreflight() throws Exception {
        String jar=System.getProperty("arcadium.voxelSkin072.argentumJar");assumeTrue(jar!=null,"Run with the pinned Argentum jar");
        String base="dev/rdh/argentum/impl/render/entity/instancing/",texture="dev/rdh/argentum/api/render/instancing/TextureArrayManager";
        try(ZipFile zip=new ZipFile(jar)){
            var model=members(zip,base+"ModelInstancer");
            for(String method:List.of("<init>()V","beginBatch()Z","isBatchActive()Z","discardBatch()V","close(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;)V"))assertTrue(model.contains(method),method);
            assertTrue(model.contains("flush(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;)L"+base+"ModelInstancer$BatchStats;"));
            assertTrue(model.contains("frame:I"));assertTrue(model.contains("textureArrays:L"+texture+";"));
            assertTrue(members(zip,texture).contains("textures:Ljava/util/Map;"));assertTrue(members(zip,texture+"$CachedTexture").contains("pool:L"+texture+"$Pool;"));
            var pool=members(zip,texture+"$Pool");assertTrue(pool.contains("key:L"+texture+"$PoolKey;"));assertTrue(pool.contains("capacity:I"));
            for(String field:List.of("frame:I","sourceId:I","generation:I"))assertTrue(members(zip,texture+"$Layer").contains(field));
            assertTrue(members(zip,"dev/rdh/argentum/impl/render/entity/EntityShadowBatch").contains("flush(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;)V"));
        }
    }
    private static Set<String> members(ZipFile zip,String owner)throws Exception{
        var entry=zip.getEntry(owner+".class");assertNotNull(entry,owner);Set<String> result=new HashSet<>();
        new ClassReader(zip.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9){
            @Override public FieldVisitor visitField(int access,String name,String descriptor,String signature,Object value){result.add(name+":"+descriptor);return null;}
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){result.add(name+descriptor);return null;}
        },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return result;
    }
}
