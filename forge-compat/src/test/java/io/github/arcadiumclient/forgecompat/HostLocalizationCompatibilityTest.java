package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HostLocalizationCompatibilityTest {
    private static final String NATIVE="net/ornithemc/osl/localization/impl/mixin/client/LocaleMixin",API="net/ornithemc/osl/localization/impl/Locale",FACADE="net/ornithemc/osl/localization/impl/Localization";
    private static final String TARGET="fixture/localization/ClientLocale",MANAGER="fixture/localization/LanguageManager",RESOURCE="fixture/localization/Resource",IDENTIFIER="net/ornithemc/osl/core/api/util/NamespacedIdentifier";
    private static final String OPERATION="com/llamalad7/mixinextras/injector/wrapoperation/Operation",ACCESS="net/ornithemc/osl/resource/loader/impl/resource/ResourceLocationAccess";
    @TempDir static Path temporary;
    private Path nativeJar,runtime;
    private ForgeMappings mappings;
    private ClassNode mixin,manager;
    private byte[] english;
    private String locale,managerName,resource,identifier,properties;

    @BeforeAll void setup() throws Exception {
        Path loom=Path.of(System.getProperty("user.home"),".gradle/caches/fabric-loom/1.8.9");MemoryMappingTree tree=new MemoryMappingTree();MappingReader.read(new StringReader(Files.readString(loom.resolve("calamus-gen2-intermediary-v2.tiny"))),tree);
        var constructor=Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class,String.class);constructor.setAccessible(true);
        try(var input=Files.newBufferedReader(Path.of("../build/forge-compat/dependencies/forge-1.8.9.srg"))){mappings=new ForgeMappings((MappingResolver)constructor.newInstance(tree,"intermediary"),input);}
        Path patches=temporary.resolve("patches.jar");try(var input=getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")){assertNotNull(input);Files.copy(input,patches);}
        Path patched=new ForgeGamePatcher().patch(loom.resolve("minecraft-client.jar"),patches,temporary.resolve("game"));var transformer=new io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer();transformer.index(patched,mappings,true);
        runtime=transformer.transform(patched,temporary.resolve("runtime"),mappings,true,null);
        Path osl=ForgeTestFixtures.nativeDirectory();try(var paths=Files.list(osl)){nativeJar=paths.filter(path->path.getFileName().toString().startsWith("osl-0.22.0")).findFirst().orElse(null);}
        if(nativeJar==null){try(var paths=Files.list(osl)){for(Path p:paths.filter(path->path.toString().endsWith(".jar")).toList())try(ZipFile zip=new ZipFile(p.toFile())){if(zip.getEntry(NATIVE+".class")!=null){nativeJar=p;break;}}}}
        if(nativeJar!=null){try(ZipFile zip=new ZipFile(nativeJar.toFile())){if(zip.getEntry(NATIVE+".class")==null)nativeJar=null;}}
        if(nativeJar==null){Path aggregate=osl.resolve("ornithe-standard-libraries-0.22.0.jar");try(ZipFile zip=new ZipFile(aggregate.toFile())){for(ZipEntry entry:Collections.list(zip.entries()))if(entry.getName().contains("osl-localization-0.1.2+mc13w26a-mc18w01a.jar")){nativeJar=temporary.resolve("osl-localization.jar");try(var input=zip.getInputStream(entry)){Files.copy(input,nativeJar);}break;}}}
        assertNotNull(nativeJar,"Pinned OSL localization fixture is missing");mixin=read(nativeJar,NATIVE);
        locale=mappings.classFromSrg("net/minecraft/client/resources/Locale");managerName=mappings.classFromSrg("net/minecraft/client/resources/LanguageManager");resource=mappings.classFromSrg("net/minecraft/client/resources/IResource");identifier=mappings.classFromSrg("net/minecraft/util/ResourceLocation");properties=mappings.fieldFromSrg("net/minecraft/client/resources/Locale","field_135032_a","Ljava/util/Map;");manager=read(runtime,managerName);
        try(ZipFile zip=new ZipFile(loom.resolve("minecraft-client.jar").toFile());var input=zip.getInputStream(zip.getEntry("assets/minecraft/lang/en_US.lang"))){english=input.readAllBytes();}
    }

    @Test void reproducesTheJourneyMapAuxiliaryLocaleHijackUsingActualOslHandlersAndRealMinecraftEnglish() throws Exception {
        try(Fixture fixture=fixture(false)){
            Object main=fixture.create();fixture.active(main);fixture.load(main,english,"lang/en_US.lang");assertEnglish(fixture.map(main));
            Object auxiliary=fixture.create();fixture.load(auxiliary,english,"lang/en_US.lang");fixture.map(main).clear();fixture.load(main,english,"lang/en_US.lang");
            assertTrue(fixture.map(main).isEmpty());assertEnglish(fixture.map(auxiliary));assertEquals("tile.stone.stone.name",fixture.map(main).getOrDefault("tile.stone.stone.name","tile.stone.stone.name"));
        }
    }

    @Test void keepsMainAndAuxiliaryTranslationsIndependentAcrossRepeatedLangJsonAndLanguageReloads() throws Exception {
        try(Fixture fixture=fixture(true)){
            Object main=fixture.create();fixture.active(main);fixture.load(main,english,"lang/en_US.lang");assertEnglish(fixture.map(main));
            Object auxiliary=fixture.create();fixture.load(auxiliary,"{\"tile.stone.stone.name\":\"Pierre\",\"fixture.auxiliary\":\"auxiliary\"}".getBytes(StandardCharsets.UTF_8),"lang/fr_FR.json");
            assertEquals("Stone",fixture.translation("tile.stone.stone.name"));assertEquals("Pierre",fixture.map(auxiliary).get("tile.stone.stone.name"));
            for(int repeat=0;repeat<3;repeat++){
                fixture.map(main).clear();fixture.load(main,english,"lang/en_US.lang");fixture.load(main,"tile.stone.stone.name=石\nfixture.active=日本語\n".getBytes(StandardCharsets.UTF_8),"lang/ja_JP.lang");
                assertEquals("石",fixture.map(main).get("tile.stone.stone.name"));assertEquals("石",fixture.translation("tile.stone.stone.name"));assertEquals("Gold Ore",fixture.translation("tile.oreGold.name"));
                fixture.load(auxiliary,"tile.stone.stone.name=Pierre\nfixture.auxiliary=auxiliary\n".getBytes(StandardCharsets.UTF_8),"lang/fr_FR.lang");
                assertEquals("石",fixture.translation("tile.stone.stone.name"));assertEquals("Pierre",fixture.map(auxiliary).get("tile.stone.stone.name"));assertFalse(fixture.map(main).containsKey("fixture.auxiliary"));
            }
        }
    }

    @Test void restoresNestedParserContextsAndTheActiveLanguageAfterTheOriginalParserThrows() throws Exception {
        try(Fixture fixture=fixture(true)){
            Object main=fixture.create();fixture.active(main);fixture.load(main,english,"lang/en_US.lang");Object outer=fixture.create(),inner=fixture.create();fixture.map(outer).put("fixture.context","outer");fixture.map(inner).put("fixture.context","inner");
            InputStream nested=new ByteArrayInputStream("fixture.outer=loaded\n".getBytes(StandardCharsets.UTF_8)){
                private boolean called;
                @Override public synchronized int read(byte[] output,int offset,int length){if(!called){called=true;try{assertEquals("outer",fixture.translation("fixture.context"));fixture.load(inner,"fixture.inner=loaded\n".getBytes(StandardCharsets.UTF_8),"lang/en_US.lang");assertEquals("outer",fixture.translation("fixture.context"));}catch(Exception exception){throw new RuntimeException(exception);}}return super.read(output,offset,length);}
            };
            fixture.load(outer,nested,"lang/en_US.lang");assertEquals("loaded",fixture.map(outer).get("fixture.outer"));assertEquals("loaded",fixture.map(inner).get("fixture.inner"));assertFalse(fixture.map(main).containsKey("fixture.inner"));assertFalse(fixture.map(outer).containsKey("fixture.inner"));assertEquals("Stone",fixture.translation("tile.stone.stone.name"));
            IOException expected=new IOException("owned parser input");InputStream broken=new InputStream(){@Override public int read()throws IOException{throw expected;}};
            Throwable failure=assertThrows(InvocationTargetException.class,()->fixture.load(outer,broken,"lang/en_US.lang")).getCause();assertSame(expected,failure);assertEquals("Stone",fixture.translation("tile.stone.stone.name"));
        }
    }

    @Test void retainsTheOriginalUnknownResourceOperationAndExceptionIdentity() throws Exception {
        try(Fixture fixture=fixture(true)){
            Object main=fixture.create();fixture.active(main);fixture.load(main,english,"lang/en_US.lang");Object auxiliary=fixture.create();var operation=(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void>)arguments->{assertSame(auxiliary,arguments[0]);assertEquals(2,arguments.length);fixture.fallbackCalls++;return null;};
            fixture.load(auxiliary,new ByteArrayInputStream(new byte[0]),"lang/custom.binary",operation);assertEquals(1,fixture.fallbackCalls);assertTrue(fixture.map(auxiliary).isEmpty());assertEquals("Stone",fixture.translation("tile.stone.stone.name"));
            RuntimeException expected=new RuntimeException("original operation");var failing=(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void>)arguments->{throw expected;};assertSame(expected,assertThrows(InvocationTargetException.class,()->fixture.load(auxiliary,new ByteArrayInputStream(new byte[0]),"lang/custom.binary",failing)).getCause());
        }
    }

    @Test void routesConcurrentAuxiliaryParsersPerThreadWhileOtherTranslationReadersKeepTheMainMap() throws Exception {
        try(Fixture fixture=fixture(true)){
            Object main=fixture.create();fixture.active(main);fixture.load(main,english,"lang/en_US.lang");Object first=fixture.create(),second=fixture.create();
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger closes=new AtomicInteger();
            InputStream blocked=new ByteArrayInputStream("fixture.first=first\ntile.stone.stone.name=First\n".getBytes(StandardCharsets.UTF_8)){
                private boolean waiting;
                @Override public synchronized int read(byte[] output,int offset,int length){if(!waiting){waiting=true;entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Parser release timed out");}catch(InterruptedException exception){throw new RuntimeException(exception);}}return super.read(output,offset,length);}
                @Override public void close()throws IOException{closes.incrementAndGet();super.close();}
            };
            ExecutorService workers=Executors.newFixedThreadPool(2);try{
                Future<?> pending=workers.submit(()->{fixture.load(first,blocked,"lang/en_US.lang");return null;});
                try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals("Stone",fixture.translation("tile.stone.stone.name"));
                    Future<?> other=workers.submit(()->{fixture.load(second,"fixture.second=second\ntile.stone.stone.name=Second\n".getBytes(StandardCharsets.UTF_8),"lang/en_US.lang");return null;});other.get(5,TimeUnit.SECONDS);
                    assertEquals("Stone",fixture.translation("tile.stone.stone.name"));assertEquals("Second",fixture.map(second).get("tile.stone.stone.name"));
                }finally{release.countDown();}pending.get(5,TimeUnit.SECONDS);
            }finally{workers.shutdownNow();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS));}
            assertEquals(1,closes.get());assertEquals("First",fixture.map(first).get("tile.stone.stone.name"));assertFalse(fixture.map(first).containsKey("fixture.second"));assertFalse(fixture.map(second).containsKey("fixture.first"));assertEquals("Stone",fixture.translation("tile.stone.stone.name"));
        }
    }

    @Test void preservesEveryNativeAnnotationAndUnrelatedHandlerAndKeepsTheGeneratedGetterIdempotent() throws Exception {
        HostLocalizationCompatibility adjuster=new HostLocalizationCompatibility(mappings);int changed=0;
        for(MethodNode original:mixin.methods)if(original.visibleAnnotations!=null)for(AnnotationNode item:original.visibleAnnotations){MethodNode handler=copy(original);byte[] before=methodBytes(handler);AdjustableAnnotationNode annotation=AdjustableAnnotationNode.fromNode(item);byte[] metadata=annotationBytes(annotation);
            assertSame(annotation,adjuster.adjust(List.of(locale),NATIVE,handler,annotation));assertArrayEquals(metadata,annotationBytes(annotation));
            if(Arrays.stream(handler.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode call&&call.owner.equals(Type.getInternalName(HostLocalizationCompatibility.class)))){changed++;assertEquals(original.desc,handler.desc);new Analyzer<>(new BasicVerifier()).analyze(NATIVE,handler);byte[] once=methodBytes(handler);adjuster.adjust(List.of(locale),NATIVE,handler,annotation);assertArrayEquals(once,methodBytes(handler));}else assertArrayEquals(before,methodBytes(handler));
        }
        assertEquals(2,changed);ClassNode normalized=copy(manager);byte[] old=bytes(normalized);HostLocalizationCompatibility.normalize(normalized,mappings);assertEquals(manager.methods.size()+1,normalized.methods.size());
        for(int i=0;i<manager.methods.size();i++)assertArrayEquals(methodBytes(manager.methods.get(i)),methodBytes(normalized.methods.get(i)));byte[] once=bytes(normalized);HostLocalizationCompatibility.normalize(normalized,mappings);assertArrayEquals(once,bytes(normalized));assertFalse(Arrays.equals(old,once));
        MethodNode unrelated=copy(method(mixin,"osl$localization$initLocale"));byte[] before=methodBytes(unrelated);adjuster.adjust(List.of(locale),"fixture.Unrelated",unrelated,AdjustableAnnotationNode.fromNode(unrelated.visibleAnnotations.get(0)));assertArrayEquals(before,methodBytes(unrelated));
    }

    @Test void keepsNativeTranslationMapLookupsConstantTimeWithoutScanningEntries() throws Exception {
        try(Fixture fixture=fixture(true)){
            Object main=fixture.create();fixture.active(main);CountingMap values=new CountingMap();values.put("fixture.hot","translated");main.getClass().getField("translations").set(main,values);HostLocalizationCompatibility.bind(fixture.api,values,values);
            Field field=fixture.api.getClass().getDeclaredField("map");field.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String,String> view=(Map<String,String>)field.get(fixture.api);
            for(int i=0;i<1000;i++){assertEquals("translated",view.get("fixture.hot"));assertEquals("translated",view.getOrDefault("fixture.hot","missing"));assertTrue(view.containsKey("fixture.hot"));assertEquals(1,view.size());assertFalse(view.isEmpty());}
            assertEquals(1000,values.gets);assertEquals(1000,values.defaults);assertEquals(1000,values.contains);assertEquals("translated",fixture.translation("fixture.hot"));assertSame(view,field.get(fixture.api));
        }
    }

    public static final class CountingMap extends HashMap<String,String> {
        int gets,defaults,contains;
        @Override public String get(Object key){gets++;return super.get(key);}
        @Override public String getOrDefault(Object key,String fallback){defaults++;return super.getOrDefault(key,fallback);}
        @Override public boolean containsKey(Object key){contains++;return super.containsKey(key);}
        @Override public Set<Entry<String,String>> entrySet(){throw new AssertionError("Hot lookup scanned entrySet");}
    }

    private Fixture fixture(boolean fixed)throws Exception{
        ClassNode originalLocale=read(runtime,locale),target=new ClassNode();target.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,TARGET,null,"java/lang/Object",null);target.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"translations","Ljava/util/Map;",null,null));
        Remapper binding=new Remapper(Opcodes.ASM9){@Override public String map(String name){if(name.equals(locale)||name.equals(NATIVE))return TARGET;if(name.equals(managerName))return MANAGER;if(name.equals(resource))return RESOURCE;if(name.equals(identifier))return IDENTIFIER;return name;}@Override public String mapFieldName(String owner,String name,String descriptor){return(owner.equals(locale)||owner.equals(NATIVE))&&name.equals(properties)?"translations":name;}};
        HostLocalizationCompatibility adjuster=new HostLocalizationCompatibility(mappings);
        for(MethodNode original:mixin.methods)if(original.name.equals("resourceLocation")||original.name.equals("osl$localization$initLocale")||original.name.equals("osl$localization$loadExtraTranslations")&&original.desc.startsWith("(L"+locale+";Ljava/io/InputStream;")){
            MethodNode handler=copy(original);if(fixed&&handler.visibleAnnotations!=null)for(AnnotationNode annotation:handler.visibleAnnotations)adjuster.adjust(List.of(locale),NATIVE,handler,AdjustableAnnotationNode.fromNode(annotation));target.methods.add(handler);
        }
        MethodNode ctor=copy(method(originalLocale,"<init>"));for(AbstractInsnNode instruction:ctor.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN){InsnList init=new InsnList();init.add(new VarInsnNode(Opcodes.ALOAD,0));init.add(new InsnNode(Opcodes.ACONST_NULL));init.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,TARGET,"osl$localization$initLocale","(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",false));ctor.instructions.insertBefore(instruction,init);}target.methods.add(ctor);
        ClassNode bound=new ClassNode();target.accept(new ClassRemapper(bound,binding));
        MethodNode load=new MethodNode(Opcodes.ACC_PUBLIC,"load","(Ljava/io/InputStream;L"+OPERATION+";L"+RESOURCE+";)V",null,null);for(int slot:new int[]{0,0,1,2,3})load.visitVarInsn(Opcodes.ALOAD,slot);load.visitMethodInsn(Opcodes.INVOKESPECIAL,TARGET,"osl$localization$loadExtraTranslations","(L"+TARGET+";Ljava/io/InputStream;L"+OPERATION+";L"+RESOURCE+";)V",false);load.visitInsn(Opcodes.RETURN);bound.methods.add(load);
        ClassNode canonical=copy(manager);HostLocalizationCompatibility.normalize(canonical,mappings);canonical.methods.removeIf(method->!method.name.equals("arcadium$forge$activeLocaleMap"));canonical.fields.removeIf(field->!field.desc.equals("L"+locale+";"));canonical.fields.forEach(field->field.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC);canonical.interfaces.clear();canonical.visibleAnnotations=null;canonical.invisibleAnnotations=null;canonical.innerClasses.clear();canonical.version=Opcodes.V17;ClassNode mappedManager=new ClassNode();canonical.accept(new ClassRemapper(mappedManager,binding));
        ClassNode facade=new ClassNode();facade.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,FACADE,null,"java/lang/Object",null);MethodNode get=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"getLocale","()L"+API+";",null,null);get.visitMethodInsn(Opcodes.INVOKESTATIC,API,"instance","()L"+API+";",false);get.visitInsn(Opcodes.ARETURN);facade.methods.add(get);
        ClassNode resourceInterface=new ClassNode();resourceInterface.visit(Opcodes.V17,Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT|Opcodes.ACC_INTERFACE,RESOURCE,null,"java/lang/Object",null);resourceInterface.methods.add(new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,mappings.methodFromSrg("net/minecraft/client/resources/IResource","func_177241_a","()Lnet/minecraft/util/ResourceLocation;"),"()L"+IDENTIFIER+";",null,null));
        Map<String,byte[]> definitions=Map.of(TARGET.replace('/','.'),bytes(bound),MANAGER.replace('/','.'),bytes(mappedManager),FACADE.replace('/','.'),bytes(facade),RESOURCE.replace('/','.'),bytes(resourceInterface));
        List<URL> urls=new ArrayList<>();urls.add(nativeJar.toAbsolutePath().toUri().toURL());Path aggregate=ForgeTestFixtures.nativeJar("ornithe-standard-libraries-0.22.0.jar");extract(aggregate,urls);
        URLClassLoader loader=new URLClassLoader(urls.toArray(URL[]::new),getClass().getClassLoader()){@Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("net.minecraft."))throw new AssertionError("Minecraft class definition attempted: "+name);if(!definitions.containsKey(name))return super.loadClass(name,resolve);synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null){byte[] value=definitions.get(name);type=defineClass(name,value,0,value.length);}if(resolve)resolveClass(type);return type;}}};return new Fixture(loader);
    }

    private void extract(Path parent,List<URL> urls)throws Exception{boolean aggregate=parent.getFileName().toString().equals("ornithe-standard-libraries-0.22.0.jar");try(ZipFile zip=new ZipFile(parent.toFile())){for(ZipEntry entry:Collections.list(zip.entries()))if(entry.getName().endsWith(".jar")&&(!aggregate||List.of("osl-core-0.11.0+mc14w27a-mc1.14.4.jar","osl-resource-loader-0.8.1+mc1.8.2-pre5-mc1.12.2.jar","osl-text-components-0.1.0-alpha.6+mc13w36a-mc1.14.4.jar").stream().anyMatch(entry.getName()::endsWith))){Path jar=temporary.resolve(entry.getName().replace('/','_'));if(!Files.exists(jar))try(var input=zip.getInputStream(entry)){Files.copy(input,jar);}URL url=jar.toUri().toURL();if(!urls.contains(url)){urls.add(url);extract(jar,urls);}}}}

    private final class Fixture implements AutoCloseable {
        final URLClassLoader loader;final Class<?> target,manager,resourceType,idType,accessType;final Object api;final Method parse,get;int fallbackCalls;
        Fixture(URLClassLoader loader)throws Exception{this.loader=loader;target=loader.loadClass(TARGET.replace('/','.'));manager=loader.loadClass(MANAGER.replace('/','.'));resourceType=loader.loadClass(RESOURCE.replace('/','.'));idType=loader.loadClass(IDENTIFIER.replace('/','.'));accessType=loader.loadClass(ACCESS.replace('/','.'));Class<?> apiType=loader.loadClass(API.replace('/','.'));api=apiType.getMethod("instance").invoke(null);get=apiType.getMethod("get",String.class);parse=target.getMethod("load",InputStream.class,com.llamalad7.mixinextras.injector.wrapoperation.Operation.class,resourceType);}
        Object create()throws Exception{return target.getConstructor().newInstance();}void active(Object locale)throws Exception{manager.getFields()[0].set(null,locale);}
        @SuppressWarnings("unchecked") Map<String,String> map(Object locale)throws Exception{return(Map<String,String>)target.getField("translations").get(locale);}String translation(String key)throws Exception{return(String)get.invoke(api,key);}
        void load(Object locale,byte[] data,String path)throws Exception{load(locale,new ByteArrayInputStream(data),path);}
        void load(Object locale,InputStream data,String path)throws Exception{load(locale,data,path,(com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void>)arguments->{throw new AssertionError("Unexpected original parser fallback");});}
        void load(Object locale,InputStream data,String path,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original)throws Exception{Object id=Proxy.newProxyInstance(loader,new Class<?>[]{idType},(proxy,method,args)->switch(method.getName()){case"identifier"->path;case"namespace"->"minecraft";case"toString"->"minecraft:"+path;default->throw new UnsupportedOperationException(method.getName());});Object resource=Proxy.newProxyInstance(loader,new Class<?>[]{resourceType,accessType},(proxy,method,args)->id);parse.invoke(locale,data,original,resource);}
        @Override public void close()throws IOException{loader.close();}
    }

    private static void assertEnglish(Map<String,String> translations){assertEquals("Stone",translations.get("tile.stone.stone.name"));assertEquals("Granite",translations.get("tile.stone.granite.name"));assertEquals("Gold Ore",translations.get("tile.oreGold.name"));assertEquals("Building Blocks",translations.get("itemGroup.buildingBlocks"));}
    private static ClassNode read(Path jar,String name)throws Exception{try(ZipFile zip=new ZipFile(jar.toFile());var input=zip.getInputStream(Objects.requireNonNull(zip.getEntry(name+".class"),name))){ClassNode node=new ClassNode();new ClassReader(input.readAllBytes()).accept(node,0);return node;}}
    private static ClassNode copy(ClassNode source){ClassNode node=new ClassNode();source.accept(node);return node;}private static MethodNode copy(MethodNode source){MethodNode method=new MethodNode(source.access,source.name,source.desc,source.signature,source.exceptions.toArray(String[]::new));source.accept(method);return method;}
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(method->method.name.equals(name)).findFirst().orElseThrow();}private static byte[] bytes(ClassNode node){ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();}
    private static byte[] methodBytes(MethodNode method){ClassNode node=new ClassNode();node.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"fixture/Method",null,"java/lang/Object",null);node.methods.add(method);return bytes(node);}private static byte[] annotationBytes(AnnotationNode annotation){MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,"metadata","()V",null,null);method.visibleAnnotations=List.of(annotation);return methodBytes(method);}
}
