package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.AccessTransformerRules;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import static org.junit.jupiter.api.Assertions.*;

class ForgeClassPipelineCameraTest {
    @TempDir Path temporary;
    public static final class CoreObserver implements IClassTransformer {
        static int calls;
        static boolean changeBody;
        @Override public byte[] transform(String name,String transformed,byte[] bytes) {
            assertEquals(HostForgeCameraCompatibility.TARGET.replace('/','.'),name); assertEquals(name,transformed);
            ClassNode node=read(bytes); assertEquals(0,HostForgeCameraCompatibilityTest.bridgeCalls(node)); calls++;
            if (!changeBody) return bytes;
            HostForgeCameraCompatibilityTest.camera(node).instructions.insert(new InsnNode(Opcodes.NOP));
            return HostForgeCameraCompatibilityTest.bytes(node);
        }
    }

    public static final class ReentrantObserver implements IClassTransformer {
        static GameTransformer entrypoint;
        static byte[] camera,other;
        static int calls;
        @Override public byte[] transform(String name,String transformed,byte[] bytes) {
            assertEquals("test.Outer",name);assertEquals(name,transformed);calls++;
            camera=entrypoint.transform(HostForgeCameraCompatibility.TARGET.replace('/','.'));
            other=entrypoint.transform("test.Other");
            return bytes;
        }
    }

    @Test void normalizesTheActualMaterializedStockApiAfterCoremodsAndAlsoOnCacheHits() throws Exception {
        ForgeMappings mappings=HostForgeCameraCompatibilityTest.mappings(); ForgeJarTransformer transformer=new ForgeJarTransformer();
        Path official=HostForgeCameraCompatibilityTest.FORGE;
        Path api=transformer.transform(official,temporary.resolve("api"),mappings,true,ForgeCompatBootstrap.apiPatches(mappings,false,false,false));
        byte[] materialized; Set<String> stockApiClasses=new HashSet<>();
        try(ZipFile zip=new ZipFile(api.toFile())) {
            zip.stream().filter(entry->entry.getName().endsWith(".class")).forEach(entry->stockApiClasses.add(entry.getName().substring(0,entry.getName().length()-6)));
            try(var input=zip.getInputStream(zip.getEntry(HostForgeCameraCompatibility.TARGET+".class"))){materialized=input.readAllBytes();}
        }
        assertTrue(stockApiClasses.contains(HostForgeCameraCompatibility.TARGET));
        assertEquals(0,HostForgeCameraCompatibilityTest.bridgeCalls(read(materialized)));
        assertEquals(api,transformer.transform(official,temporary.resolve("api"),mappings,true,ForgeCompatBootstrap.apiPatches(mappings,false,false,false)));
        AtomicInteger reads=new AtomicInteger(); ClassLoader host=getClass().getClassLoader();
        FabricLauncher launcher=(FabricLauncher)Proxy.newProxyInstance(host,new Class<?>[]{FabricLauncher.class},(proxy,method,args)->switch(method.getName()){
            case "getTargetClassLoader" -> host;
            case "getClassByteArray" -> {assertEquals(HostForgeCameraCompatibility.TARGET.replace('/','.'),args[0]);assertEquals(false,args[1]);reads.incrementAndGet();yield materialized;}
            case "loadIntoTarget" -> Class.forName((String)args[0],true,host);
            default -> throw new AssertionError("Unexpected launcher call "+method.getName());
        });
        var constructor=ForgeClassPipeline.class.getDeclaredConstructor(FabricLauncher.class,ForgeMappings.class,AccessTransformerRules.class,Map.class,Set.class,byte[].class);constructor.setAccessible(true);
        LaunchClassLoader previous=Launch.classLoader;
        try(LaunchClassLoader loader=new LaunchClassLoader(new URL[0],launcher,mappings)) {
            Launch.classLoader=loader; loader.registerTransformer(CoreObserver.class.getName()); CoreObserver.calls=0; CoreObserver.changeBody=false;
            ForgeClassPipeline pipeline=constructor.newInstance(launcher,mappings,new AccessTransformerRules(),Map.of(),stockApiClasses,materialized);pipeline.finishRegistration();
            GameTransformer authoritative=new GameTransformer(); var patchedClasses=GameTransformer.class.getDeclaredField("patchedClasses");patchedClasses.setAccessible(true);patchedClasses.set(authoritative,pipeline);
            byte[] defined=authoritative.transform(HostForgeCameraCompatibility.TARGET.replace('/','.'));
            assertEquals(1,CoreObserver.calls);assertEquals(1,reads.get());assertEquals(1,HostForgeCameraCompatibilityTest.bridgeCalls(read(defined)));
            assertSame(defined,authoritative.transform(HostForgeCameraCompatibility.TARGET.replace('/','.')));assertEquals(1,CoreObserver.calls);assertEquals(1,reads.get());
            CoreObserver.changeBody=true; pipeline.finishRegistration();
            byte[] custom=authoritative.transform(HostForgeCameraCompatibility.TARGET.replace('/','.'));
            assertEquals(2,CoreObserver.calls);assertEquals(0,HostForgeCameraCompatibilityTest.bridgeCalls(read(custom)));
            assertEquals(Opcodes.NOP,HostForgeCameraCompatibilityTest.camera(read(custom)).instructions.getFirst().getOpcode());
            assertEquals(0,HostForgeCameraCompatibilityTest.bridgeCalls(read(materialized)));
        } finally { Launch.classLoader=previous;CoreObserver.changeBody=false; }
    }

    @Test void reentrantStockDefinitionUsesOnlyMaterializedBytesWithoutReplayingTheLegacyChain() throws Exception {
        ForgeMappings mappings=HostForgeCameraCompatibilityTest.mappings();
        Path api=new ForgeJarTransformer().transform(HostForgeCameraCompatibilityTest.FORGE,temporary.resolve("recursive-api"),mappings,true,ForgeCompatBootstrap.apiPatches(mappings,false,false,false));
        byte[] materialized;
        try(ZipFile zip=new ZipFile(api.toFile());var input=zip.getInputStream(zip.getEntry(HostForgeCameraCompatibility.TARGET+".class"))){materialized=input.readAllBytes();}
        ClassNode custom=read(materialized);HostForgeCameraCompatibilityTest.camera(custom).instructions.insert(new InsnNode(Opcodes.NOP));byte[] unsupported=HostForgeCameraCompatibilityTest.bytes(custom);
        byte[] outer=simple("test/Outer"),other=simple("test/Other");
        Path extra=jar(temporary.resolve("outer-and-shadow.jar"),Map.of("test/Outer.class",outer,"test/Other.class",other,HostForgeCameraCompatibility.TARGET+".class",unsupported));
        for(int scenario=0;scenario<3;scenario++) {
            byte[] available=scenario==1?unsupported:materialized;
            Path sourceApi=scenario==1?jar(temporary.resolve("modified-api.jar"),Map.of(HostForgeCameraCompatibility.TARGET+".class",unsupported)):api;
            Map<String,byte[]> patches=new HashMap<>();patches.put("test.Other",other);
            if(scenario==2)patches.put(HostForgeCameraCompatibility.TARGET.replace('/','.'),unsupported);
            AtomicInteger reads=new AtomicInteger();ClassLoader host=getClass().getClassLoader();
            FabricLauncher launcher=(FabricLauncher)Proxy.newProxyInstance(host,new Class<?>[]{FabricLauncher.class},(proxy,method,args)->switch(method.getName()){
                case "getTargetClassLoader" -> host;
                case "getClassByteArray" -> {assertEquals("test.Outer",args[0],"Reentrant camera must not request class bytes");reads.incrementAndGet();yield outer;}
                case "loadIntoTarget" -> Class.forName((String)args[0],true,host);
                default -> throw new AssertionError("Unexpected launcher call "+method.getName());
            });
            GameTransformer entrypoint=new GameTransformer();var field=GameTransformer.class.getDeclaredField("patchedClasses");field.setAccessible(true);field.set(entrypoint,patches);
            ForgeClassPipeline pipeline=install(launcher,mappings,entrypoint,List.of(sourceApi,extra));pipeline.finishRegistration();assertSame(pipeline,field.get(entrypoint));
            LaunchClassLoader previous=Launch.classLoader;
            try(LaunchClassLoader loader=new LaunchClassLoader(new URL[0],launcher,mappings)) {
                Launch.classLoader=loader;loader.registerTransformer(ReentrantObserver.class.getName());ReentrantObserver.calls=0;ReentrantObserver.entrypoint=entrypoint;
                byte[] sourceBefore=available.clone(),unsupportedBefore=unsupported.clone(),jarBefore=Files.readAllBytes(sourceApi);assertNotNull(entrypoint.transform("test.Outer"));
                assertEquals(1,ReentrantObserver.calls);assertEquals(1,reads.get());assertSame(other,ReentrantObserver.other);
                if(scenario==0){assertNotNull(ReentrantObserver.camera);assertEquals(1,HostForgeCameraCompatibilityTest.bridgeCalls(read(ReentrantObserver.camera)));}
                else if(scenario==1)assertNull(ReentrantObserver.camera);
                else assertSame(unsupported,ReentrantObserver.camera);
                assertArrayEquals(sourceBefore,available);assertArrayEquals(unsupportedBefore,unsupported);assertArrayEquals(jarBefore,Files.readAllBytes(sourceApi));
            } finally {Launch.classLoader=previous;ReentrantObserver.entrypoint=null;ReentrantObserver.camera=ReentrantObserver.other=null;}
        }
    }
    private static ForgeClassPipeline install(FabricLauncher launcher,ForgeMappings mappings,GameTransformer transformer,List<Path> jars)throws Exception{
        GameProvider provider=(GameProvider)Proxy.newProxyInstance(GameProvider.class.getClassLoader(),new Class<?>[]{GameProvider.class},(proxy,method,args)->{assertEquals("getEntrypointTransformer",method.getName());return transformer;});
        var field=FabricLoaderImpl.class.getDeclaredField("provider");field.setAccessible(true);Object previous=field.get(FabricLoaderImpl.INSTANCE);
        try{field.set(FabricLoaderImpl.INSTANCE,provider);return ForgeClassPipeline.install(launcher,mappings,new AccessTransformerRules(),jars);}finally{field.set(FabricLoaderImpl.INSTANCE,previous);}
    }
    private static Path jar(Path path,Map<String,byte[]> entries)throws Exception{try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(path))){for(var entry:entries.entrySet()){output.putNextEntry(new ZipEntry(entry.getKey()));output.write(entry.getValue());output.closeEntry();}}return path;}
    private static byte[] simple(String name){ClassNode node=new ClassNode();node.version=Opcodes.V1_8;node.access=Opcodes.ACC_PUBLIC;node.name=name;node.superName="java/lang/Object";return HostForgeCameraCompatibilityTest.bytes(node);}
    private static ClassNode read(byte[] bytes){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
}
