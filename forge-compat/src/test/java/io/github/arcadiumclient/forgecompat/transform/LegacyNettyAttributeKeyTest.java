package io.github.arcadiumclient.forgecompat.transform;

import io.github.arcadiumclient.forgecompat.ForgeTestFixtures;

import io.github.arcadiumclient.forgecompat.LegacyNettyAttributeKey;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static io.github.arcadiumclient.forgecompat.transform.TransformerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyNettyAttributeKeyTest {
    private static final String KEY="io/netty/util/AttributeKey", PACKET="codechicken/lib/packet/PacketCustom";
    private static final String BRIDGE="io/github/arcadiumclient/forgecompat/LegacyNettyAttributeKey", FACTORY=BRIDGE+"$Factory";
    private static final String EVENTS=Type.getInternalName(Events.class);
    @TempDir Path temporary;

    @Test void rewritesTheRealCclInitializerForBothInputKindsAndKeepsEveryOtherMethodAndInput() throws Exception {
        Path original=ccl();byte[] unchanged=Files.readAllBytes(original);
        ClassNode source=read(original,PACKET);
        for(boolean official:List.of(false,true)) {
            ForgeMappings mapping=mappings();
            Path output=new ForgeJarTransformer().transform(original,temporary.resolve("converted-"+official),mapping,official,null);
            ClassNode result=read(output,PACKET);
            ClassNode expectedSource=new ClassNode();source.accept(new ClassRemapper(expectedSource,official?mapping.officialToRuntime():mapping.srgToRuntime()));
            MethodNode init=method(result,"<clinit>");
            assertEquals(1,calls(init).stream().filter(call->call.owner.equals(BRIDGE)&&call.name.equals("factory")).count());
            assertEquals(1,calls(init).stream().filter(call->call.owner.equals(FACTORY)&&call.name.equals("create")).count());
            assertEquals(0,calls(init).stream().filter(call->call.owner.equals(KEY)&&call.name.equals("<init>")).count());
            for(MethodNode originalMethod:expectedSource.methods) if(!originalMethod.name.equals("<clinit>")) {
                ClassNode expected=nodeWith(originalMethod,PACKET);ClassNode actual=nodeWith(result.methods.stream().filter(value->value.name.equals(originalMethod.name)&&value.desc.equals(originalMethod.desc)).findFirst().orElseThrow(),PACKET);
                LegacyNettyAttributeKey.normalize(expected);assertArrayEquals(bytes(expected),bytes(actual),originalMethod.name+originalMethod.desc);
            }
            new Analyzer<>(new BasicVerifier()).analyze(result.name,init);
        }
        assertArrayEquals(unchanged,Files.readAllBytes(original));
    }

    @Test void executesTheWholeActualCclInitializerOnBothNettyVersionsWithDuplicateRejection() throws Throwable {
        for(boolean modern:List.of(false,true)) for(boolean fixed:List.of(false,true)) {
            ClassNode source=read(ccl(),PACKET);MethodNode original=method(source,"<clinit>");
            ClassNode owned=nodeWith(original,"fixture/ActualPacketInitialization");
            owned.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"cclHandler","L"+KEY+";",null,null));
            ClassNode moved=new ClassNode();owned.accept(new ClassRemapper(moved,new Remapper(){@Override public String map(String name){return name.equals(PACKET)?owned.name:name;}}));
            if(fixed)LegacyNettyAttributeKey.normalize(moved);
            try(Isolated loader=loader(modern,Map.of(moved.name,bytes(moved)))) {
                Class<?> type=loader.loadClass(moved.name.replace('/','.'));
                if(modern&&!fixed) {
                    NoSuchMethodError failure=assertThrows(NoSuchMethodError.class,()->type.getField("cclHandler").get(null));
                    assertTrue(failure.getMessage().contains("AttributeKey"));continue;
                }
                Object key=type.getField("cclHandler").get(null);Class<?> keyType=loader.loadClass(KEY.replace('/','.'));
                assertSame(keyType,key.getClass());assertEquals("ccl:handler",keyType.getMethod("name").invoke(key));
                assertThrows(IllegalArgumentException.class,()->LegacyNettyAttributeKey.factory(keyType).create("ccl:handler"));
            }
        }
    }

    @Test void verifiesConditionalUninitializedFramesAndArgumentSideEffectsOnBothRealNettyVersions() throws Throwable {
        for(boolean modern:List.of(false,true)) {
            ClassNode node=branch("fixture/Conditional",false);LegacyNettyAttributeKey.normalize(node);
            assertTrue(Arrays.stream(method(node,"make").instructions.toArray()).filter(FrameNode.class::isInstance).map(FrameNode.class::cast).flatMap(frame->frame.stack.stream()).noneMatch(LabelNode.class::isInstance));
            try(Isolated loader=loader(modern,Map.of(node.name,bytes(node)))) {
                Events.values.clear();Class<?> type=loader.loadClass(node.name.replace('/','.'));
                Object left=type.getMethod("make",boolean.class).invoke(null,true),right=type.getMethod("make",boolean.class).invoke(null,false);
                assertEquals("left",left.getClass().getMethod("name").invoke(left));assertEquals("right",right.getClass().getMethod("name").invoke(right));
                assertEquals(List.of("arg:left","arg:right"),Events.values);
                InvocationTargetException duplicate=assertThrows(InvocationTargetException.class,()->type.getMethod("make",boolean.class).invoke(null,true));
                assertInstanceOf(IllegalArgumentException.class,duplicate.getCause());
            }
        }
    }

    @Test void preservesInitializationBeforeArgumentEvaluationAndUnderlyingExceptionIdentity() throws Throwable {
        for(boolean factoryOnly:List.of(false,true)) {
            ClassNode caller=branch("fixture/Initialization",false);LegacyNettyAttributeKey.normalize(caller);
            try(Isolated loader=new Isolated(new URL[0],Map.of(caller.name,bytes(caller),KEY,provider(factoryOnly)))) {
                Events.values.clear();Class<?> type=loader.loadClass(caller.name.replace('/','.'));Object key=type.getMethod("make",boolean.class).invoke(null,true);
                assertNotNull(key);assertEquals(List.of("init","arg:left","ctor:left"),Events.values);
                Class<?> keyType=loader.loadClass(KEY.replace('/','.'));
                Throwable error=assertThrows(RuntimeException.class,()->LegacyNettyAttributeKey.factory(keyType).create("explode"));assertSame(Events.FAILURE,error);
            }
        }
    }

    @Test void handlesInitializedKeysStoredAcrossLoopIterationsWithoutTreatingThemAsUninitializedAliases() throws Throwable {
        ClassNode node=branch("fixture/Loop",true);LegacyNettyAttributeKey.normalize(node);
        for(boolean modern:List.of(false,true))try(Isolated loader=loader(modern,Map.of(node.name,bytes(node)))) {
            Object result=loader.loadClass(node.name.replace('/','.')).getMethod("make").invoke(null);
            assertEquals("right",result.getClass().getMethod("name").invoke(result));
        }
    }

    @Test void rejectsStoredUninitializedAliasesAndLeavesTheWholeClassUnmodified() throws Exception {
        ClassNode node=branch("fixture/StoredAlias",false);MethodNode invalid=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"stored","()L"+KEY+";",null,null);
        invalid.visitTypeInsn(Opcodes.NEW,KEY);invalid.visitInsn(Opcodes.DUP);invalid.visitVarInsn(Opcodes.ASTORE,0);invalid.visitVarInsn(Opcodes.ALOAD,0);invalid.visitLdcInsn("stored");invalid.visitMethodInsn(Opcodes.INVOKESPECIAL,KEY,"<init>","(Ljava/lang/String;)V",false);invalid.visitInsn(Opcodes.ARETURN);invalid.visitMaxs(3,1);node.methods.add(invalid);
        byte[] before=bytes(node);IllegalStateException error=assertThrows(IllegalStateException.class,()->LegacyNettyAttributeKey.normalize(node));
        assertTrue(error.getMessage().contains("uninitialized"));assertArrayEquals(before,bytes(node));
    }

    @Test void isolatesRealConstantPoolsByOwnerLoaderAndMatchesEachVersionsNullEmptyAndDuplicateBehavior() throws Throwable {
        for(boolean modern:List.of(false,true))try(Isolated first=loader(modern,Map.of());Isolated second=loader(modern,Map.of())) {
            Class<?> a=first.loadClass(KEY.replace('/','.')),b=second.loadClass(KEY.replace('/','.'));
            Object one=LegacyNettyAttributeKey.factory(a).create("isolated"),two=LegacyNettyAttributeKey.factory(b).create("isolated");
            assertSame(a,one.getClass());assertSame(b,two.getClass());assertNotSame(a,b);
            assertThrows(IllegalArgumentException.class,()->LegacyNettyAttributeKey.factory(a).create("isolated"));
            for(String name:Arrays.asList(null,"")) {
                Object direct=null;Throwable expected=null;
                try{direct=direct(b,name,modern);}catch(InvocationTargetException error){expected=error.getCause();}
                if(expected==null){assertNotNull(direct);Object value=LegacyNettyAttributeKey.factory(a).create(name);assertEquals(name,a.getMethod("name").invoke(value));}
                else{Throwable error=assertThrows(expected.getClass(),()->LegacyNettyAttributeKey.factory(a).create(name));assertEquals(expected.getMessage(),error.getMessage());}
            }
        }
    }

    @Test void leavesUnrelatedAllocationsAndAlreadyNormalizedCodeUnchanged() throws Exception {
        ClassNode node=branch("fixture/Idempotent",false);LegacyNettyAttributeKey.normalize(node);byte[] once=bytes(node);LegacyNettyAttributeKey.normalize(node);assertArrayEquals(once,bytes(node));
        ClassNode other=simple("fixture/Other");byte[] before=bytes(other);LegacyNettyAttributeKey.normalize(other);assertArrayEquals(before,bytes(other));
    }

    private Isolated loader(boolean modern,Map<String,byte[]> definitions)throws Exception {
        Path library=ForgeTestFixtures.nativeJar(modern?"netty-common-4.2.18.Final.jar":"netty-all-4.0.23.Final.jar");
        return new Isolated(new URL[]{library.toUri().toURL()},definitions);
    }
    private static Object direct(Class<?> type,String name,boolean modern)throws Exception{return modern?type.getMethod("newInstance",String.class).invoke(null,name):type.getConstructor(String.class).newInstance(name);}
    private static final class Isolated extends URLClassLoader {
        final Map<String,byte[]> definitions;
        Isolated(URL[] urls,Map<String,byte[]> definitions){super(urls,LegacyNettyAttributeKeyTest.class.getClassLoader());this.definitions=definitions;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(!name.startsWith("io.netty.")&&!name.startsWith("fixture."))return super.loadClass(name,resolve);
            synchronized(getClassLoadingLock(name)){Class<?> found=findLoadedClass(name);if(found==null){byte[] value=definitions.get(name.replace('.','/'));found=value==null?findClass(name):defineClass(name,value,0,value.length);}if(resolve)resolveClass(found);return found;}
        }
    }
    public static final class Events {
        static final List<String> values=new ArrayList<>();static final RuntimeException FAILURE=new IllegalArgumentException("owned constructor failure");
        public static void record(String event){values.add(event);}
        public static String argument(String value){record("arg:"+value);return value;}
        public static void constructing(String value){record("ctor:"+value);if(value.equals("explode"))throw FAILURE;}
    }
    private static ClassNode branch(String name,boolean loop) {
        ClassNode node=new ClassNode();node.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);
        MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"make",loop?"()L"+KEY+";":"(Z)L"+KEY+";",null,null);node.methods.add(method);
        Label start=new Label(),allocation=new Label(),right=new Label(),joined=new Label(),end=new Label();
        Object[] locals=loop?new Object[]{Opcodes.INTEGER,KEY}:new Object[]{Opcodes.INTEGER};
        if(loop){method.visitInsn(Opcodes.ICONST_0);method.visitVarInsn(Opcodes.ISTORE,0);method.visitInsn(Opcodes.ACONST_NULL);method.visitVarInsn(Opcodes.ASTORE,1);method.visitLabel(start);method.visitFrame(Opcodes.F_FULL,2,locals,0,new Object[0]);method.visitVarInsn(Opcodes.ILOAD,0);method.visitInsn(Opcodes.ICONST_2);method.visitJumpInsn(Opcodes.IF_ICMPGE,end);}
        method.visitLabel(allocation);method.visitTypeInsn(Opcodes.NEW,KEY);method.visitInsn(Opcodes.DUP);method.visitVarInsn(Opcodes.ILOAD,0);method.visitJumpInsn(loop?Opcodes.IFNE:Opcodes.IFEQ,right);
        method.visitLdcInsn("left");method.visitJumpInsn(Opcodes.GOTO,joined);method.visitLabel(right);method.visitFrame(Opcodes.F_FULL,locals.length,locals,2,new Object[]{allocation,allocation});method.visitLdcInsn("right");
        method.visitLabel(joined);method.visitFrame(Opcodes.F_FULL,locals.length,locals,3,new Object[]{allocation,allocation,"java/lang/String"});method.visitMethodInsn(Opcodes.INVOKESTATIC,EVENTS,"argument","(Ljava/lang/String;)Ljava/lang/String;",false);method.visitMethodInsn(Opcodes.INVOKESPECIAL,KEY,"<init>","(Ljava/lang/String;)V",false);
        if(loop){method.visitVarInsn(Opcodes.ASTORE,1);method.visitIincInsn(0,1);method.visitJumpInsn(Opcodes.GOTO,start);method.visitLabel(end);method.visitFrame(Opcodes.F_FULL,2,locals,0,new Object[0]);method.visitVarInsn(Opcodes.ALOAD,1);}
        method.visitInsn(Opcodes.ARETURN);method.visitMaxs(3,loop?2:1);return node;
    }
    private static byte[] provider(boolean factoryOnly) {
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,KEY,null,"java/lang/Object",null);
        var init=writer.visitMethod(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);init.visitCode();init.visitLdcInsn("init");init.visitMethodInsn(Opcodes.INVOKESTATIC,EVENTS,"record","(Ljava/lang/String;)V",false);init.visitInsn(Opcodes.RETURN);init.visitMaxs(0,0);init.visitEnd();
        var ctor=writer.visitMethod(factoryOnly?Opcodes.ACC_PRIVATE:Opcodes.ACC_PUBLIC,"<init>","(Ljava/lang/String;)V",null,null);ctor.visitCode();ctor.visitVarInsn(Opcodes.ALOAD,0);ctor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);ctor.visitVarInsn(Opcodes.ALOAD,1);ctor.visitMethodInsn(Opcodes.INVOKESTATIC,EVENTS,"constructing","(Ljava/lang/String;)V",false);ctor.visitInsn(Opcodes.RETURN);ctor.visitMaxs(0,0);ctor.visitEnd();
        if(factoryOnly){var factory=writer.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"newInstance","(Ljava/lang/String;)L"+KEY+";",null,null);factory.visitCode();factory.visitTypeInsn(Opcodes.NEW,KEY);factory.visitInsn(Opcodes.DUP);factory.visitVarInsn(Opcodes.ALOAD,0);factory.visitMethodInsn(Opcodes.INVOKESPECIAL,KEY,"<init>","(Ljava/lang/String;)V",false);factory.visitInsn(Opcodes.ARETURN);factory.visitMaxs(0,0);factory.visitEnd();}
        writer.visitEnd();return writer.toByteArray();
    }
    private static Path ccl(){Path path=ForgeTestFixtures.optionalGuest("CodeChickenLib-1.8.9-2.0.2.1-universal.jar");assertTrue(Files.isRegularFile(path));return path;}
    private static ClassNode nodeWith(MethodNode source,String name){ClassNode node=new ClassNode();node.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);MethodNode copy=new MethodNode(source.access,source.name,source.desc,source.signature,source.exceptions.toArray(String[]::new));source.accept(copy);node.methods.add(copy);return node;}
    private static ClassNode read(Path path,String name)throws Exception{try(ZipFile zip=new ZipFile(path.toFile())){return node(zip.getInputStream(zip.getEntry(name+".class")).readAllBytes());}}
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(method->method.name.equals(name)).findFirst().orElseThrow();}
    private static List<MethodInsnNode> calls(MethodNode node){return Arrays.stream(node.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();}
}
