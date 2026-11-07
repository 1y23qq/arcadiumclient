package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

public class HostForgeCameraCompatibilityTest implements Opcodes {
    static final Path FORGE = Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
    private static final String GENERATED = Type.getInternalName(HostForgeCameraCompatibilityTest.class) + "$Generated";
    private static final MethodType APPLY = MethodType.methodType(boolean.class, Object.class, Object.class, Object.class, Object.class);
    private static final List<String> events = new ArrayList<>();
    private static final RuntimeException FAILURE = new IllegalStateException("camera callback");
    private static final Exception CHECKED = new Exception("camera bridge");
    private static Object[] received;
    private static int providerMode;
    private static boolean multiplyThrows;

    @Test void patchesOnlyTheExactStockMultiplyAndRetainsAllOtherMethodBytes() throws Exception {
        ClassNode node = read(); Map<String,byte[]> methods = new LinkedHashMap<>();
        for (MethodNode method : node.methods) if (!method.name.equals("handleCameraTransforms")) methods.put(method.name + method.desc, methodBytes(method));
        assertTrue(HostForgeCameraCompatibility.normalize(node, mappings()));
        for (MethodNode method : node.methods) if (methods.containsKey(method.name + method.desc)) assertArrayEquals(methods.get(method.name + method.desc), methodBytes(method));
        MethodNode handle = camera(node); new Analyzer<>(new BasicVerifier()).analyze(node.name, handle);
        assertEquals(1, bridgeCalls(node)); assertEquals(1, calls(handle, "multiplyCurrentGlMatrix")); assertEquals(2, calls(handle, "getRight")); assertEquals(1, calls(handle, "getLeft"));
        byte[] once = bytes(node); assertFalse(HostForgeCameraCompatibility.normalize(node, mappings())); assertArrayEquals(once, bytes(node));
    }

    @Test void preservesCallbacksBothRightReadsTheLeftReadAndTheExistingMatrixOnDecline() throws Throwable {
        Harness original = harness(false), patched = harness(true);
        for (boolean installed : new boolean[]{false,true}) {
            if (installed) assertTrue((boolean) patched.install.invokeExact(provider()));
            for (Transform transform : Transform.values()) {
                Model replacement = new PlainModel(); Matrix first = new Matrix(1), second = new Matrix(2);
                TrackingPair pair = new TrackingPair(replacement, first, second); PerspectiveModel model = new PerspectiveModel(pair);
                events.clear(); multiplyThrows = false; original.invoke(model,transform);
                assertEquals(List.of("perspective:"+transform,"right:1","right:2","multiply:2","left"),events);
                events.clear(); received = null; pair.reset(); model.calls = 0; providerMode = 0;
                assertSame(replacement,patched.invoke(model,transform));
                assertEquals(installed ? List.of("perspective:"+transform,"right:1","right:2","provider","multiply:2","left") : List.of("perspective:"+transform,"right:1","right:2","multiply:2","left"),events);
                assertEquals(1,model.calls); assertEquals(2,pair.rightCalls); assertEquals(1,pair.leftCalls);
                if (installed) assertArrayEquals(new Object[]{second,model,transform,pair},received);
                events.clear(); pair = new TrackingPair(replacement,null,second); model = new PerspectiveModel(pair);
                assertSame(replacement,patched.invoke(model,transform)); assertEquals(List.of("perspective:"+transform,"right:1","left"),events);
            }
        }
    }

    @Test void consumesOnlyWhenAcceptedAndPropagatesProviderAndNativeFailuresWithoutReplay() throws Throwable {
        Harness patched = harness(true); assertTrue((boolean) patched.install.invokeExact(provider()));
        Matrix matrix = new Matrix(7); Model replacement = new PlainModel(); TrackingPair pair = new TrackingPair(replacement,matrix,matrix); PerspectiveModel model = new PerspectiveModel(pair);
        providerMode = 1; events.clear(); received = null; multiplyThrows = false;
        assertSame(replacement,patched.invoke(model,Transform.FIRST)); assertEquals(List.of("perspective:FIRST","right:1","right:2","provider","left"),events);
        assertArrayEquals(new Object[]{matrix,model,Transform.FIRST,pair},received);
        for (int mode : new int[]{2,3}) {
            providerMode = mode; pair.reset(); model.calls = 0; events.clear();
            assertSame(mode == 2 ? FAILURE : CHECKED,assertThrows(Throwable.class,()->patched.invoke(model,Transform.FIRST)));
            assertEquals(List.of("perspective:FIRST","right:1","right:2","provider"),events); assertEquals(1,model.calls); assertEquals(0,pair.leftCalls);
        }
        providerMode = 0; multiplyThrows = true; pair.reset(); events.clear();
        assertSame(FAILURE,assertThrows(Throwable.class,()->patched.invoke(model,Transform.THIRD)));
        assertEquals(List.of("perspective:THIRD","right:1","right:2","provider","multiply:7"),events); assertEquals(0,pair.leftCalls); multiplyThrows = false;
    }

    @Test void preservesFallbacksNullsCastsAndGetterExceptionsBeforeTheBridge() throws Throwable {
        Harness original = harness(false), patched = harness(true); assertTrue((boolean) patched.install.invokeExact(provider())); providerMode = 0; multiplyThrows = false;
        for (Harness harness : List.of(original,patched)) {
            events.clear(); Model plain = new PlainModel(); assertSame(plain,harness.invoke(plain,Transform.GUI)); assertEquals(List.of("camera","vanilla:GUI"),events);
            events.clear(); assertThrows(NullPointerException.class,()->harness.invoke(null,Transform.GUI)); assertTrue(events.isEmpty());
            for (int fault : new int[]{1,2,3,4}) {
                TrackingPair pair = new TrackingPair(new PlainModel(),new Matrix(3),new Matrix(4)); pair.fault = fault; PerspectiveModel model = new PerspectiveModel(pair);
                events.clear(); Throwable actual = assertThrows(Throwable.class,()->harness.invoke(model,Transform.FIRST)); assertSame(FAILURE,actual);
                assertEquals(1,model.calls);
                if (fault == 1) assertEquals(List.of("perspective:FIRST","right:1"),events);
                if (fault == 2) assertEquals(List.of("perspective:FIRST","right:1","right:2"),events);
                if (fault == 3) assertEquals(harness == patched ? List.of("perspective:FIRST","right:1","right:2","provider","multiply:4","left") : List.of("perspective:FIRST","right:1","right:2","multiply:4","left"),events);
                if (fault == 4) assertEquals(List.of("perspective:FIRST"),events);
            }
            events.clear(); TrackingPair invalid = new TrackingPair(new PlainModel(),new Matrix(2),"wrong matrix");
            assertThrows(ClassCastException.class,()->harness.invoke(new PerspectiveModel(invalid),Transform.FIRST)); assertEquals(List.of("perspective:FIRST","right:1","right:2"),events);
        }
    }

    @Test void unsupportedBodiesRemainByteIdenticalWithoutAnyPartialMutation() throws Exception {
        List<Consumer<ClassNode>> changes = List.of(
            node -> node.name = "test/OtherForgeHooks",
            node -> camera(node).access |= ACC_SYNCHRONIZED,
            node -> camera(node).maxLocals = 4,
            node -> camera(node).instructions.insert(new InsnNode(NOP)),
            node -> { for (var instruction : camera(node).instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("multiplyCurrentGlMatrix")) call.name = "customMultiply"; },
            node -> { for (var instruction : camera(node).instructions) if (instruction instanceof MethodInsnNode call && call.name.equals("getRight")) { call.name = "customRight"; break; } },
            node -> { for (var instruction : camera(node).instructions) if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == IFNULL) { jump.setOpcode(IFNONNULL); break; } },
            node -> { MethodNode method = camera(node); LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode(); method.instructions.insert(start); method.instructions.add(end); method.instructions.add(handler); method.tryCatchBlocks.add(new TryCatchBlockNode(start,end,handler,"java/lang/RuntimeException")); },
            node -> { MethodNode method = camera(node), duplicate = new MethodNode(ASM9,method.access,method.name,method.desc,null,null); method.accept(duplicate); node.methods.add(duplicate); }
        );
        for (Consumer<ClassNode> change : changes) { ClassNode node = read(); change.accept(node); byte[] before = bytes(node); assertFalse(HostForgeCameraCompatibility.normalize(node,mappings())); assertArrayEquals(before,bytes(node)); }
    }

    public enum Transform { FIRST,THIRD,GUI }
    public record Matrix(int id) { }
    public interface Model { Camera f(); }
    public interface Perspective extends Model { Pair<Model,?> handlePerspective(Transform transform); }
    public static final class Camera { public void a(Transform transform) { events.add("vanilla:"+transform); } }
    public static final class PlainModel implements Model { public Camera f() { events.add("camera"); return new Camera(); } }
    public static final class PerspectiveModel implements Perspective {
        final TrackingPair pair; int calls;
        PerspectiveModel(TrackingPair pair) { this.pair=pair; }
        public Pair<Model,?> handlePerspective(Transform transform) { calls++; events.add("perspective:"+transform); if (pair.fault==4) throw FAILURE; return pair; }
        public Camera f() { throw new AssertionError("Unexpected vanilla fallback"); }
    }
    public static final class TrackingPair extends Pair<Model,Object> {
        final Model left; final Object first,second; int rightCalls,leftCalls,fault;
        TrackingPair(Model left,Object first,Object second) { this.left=left;this.first=first;this.second=second; }
        void reset() { rightCalls=leftCalls=0; }
        public Model getLeft() { leftCalls++; events.add("left"); if(fault==3)throw FAILURE; return left; }
        public Object getRight() { events.add("right:"+(++rightCalls)); if(fault==rightCalls)throw FAILURE; return rightCalls==1?first:second; }
        public Object setValue(Object value) { throw new UnsupportedOperationException(); }
    }
    public static void multiply(Matrix matrix) { events.add("multiply:"+(matrix==null?"null":matrix.id())); if(multiplyThrows)throw FAILURE; }
    public static boolean apply(Object matrix,Object model,Object transform,Object pair) throws Throwable {
        events.add("provider"); received=new Object[]{matrix,model,transform,pair};
        if(providerMode==2)throw FAILURE; if(providerMode==3)throw CHECKED; return providerMode==1;
    }
    private static MethodHandle provider() throws Exception { return MethodHandles.lookup().findStatic(HostForgeCameraCompatibilityTest.class,"apply",APPLY); }
    private record Harness(java.lang.reflect.Method camera,MethodHandle install) {
        Object invoke(Model model,Transform transform) throws Throwable { try{return camera.invoke(null,model,transform);}catch(InvocationTargetException failure){throw failure.getCause();} }
    }
    private static Harness harness(boolean patched) throws Exception {
        ClassNode source=read(); if(patched)assertTrue(HostForgeCameraCompatibility.normalize(source,mappings()));
        source.methods.removeIf(method -> !method.name.equals("handleCameraTransforms")); source.fields.clear(); source.interfaces.clear(); source.innerClasses.clear();
        source.superName="java/lang/Object"; source.signature=null; source.nestHostClass=null; source.nestMembers=null;
        Map<String,String> aliases=Map.of("boq",Type.getInternalName(Model.class),"bgr$b",Type.getInternalName(Transform.class),"bgr",Type.getInternalName(Camera.class),"javax/vecmath/Matrix4f",Type.getInternalName(Matrix.class),"net/minecraftforge/client/model/IPerspectiveAwareModel",Type.getInternalName(Perspective.class),source.name,GENERATED);
        ClassNode target=new ClassNode(); source.accept(new ClassRemapper(target,new Remapper(ASM9){@Override public String map(String name){return aliases.getOrDefault(name,name);}}));
        for(var instruction:camera(target).instructions) if(instruction instanceof MethodInsnNode call&&call.name.equals("multiplyCurrentGlMatrix")) {call.owner=Type.getInternalName(HostForgeCameraCompatibilityTest.class);call.name="multiply";}
        byte[] cameraBytes=bytes(target),bridgeBytes; try(var input=ForgeItemCameraBridge.class.getResourceAsStream("ForgeItemCameraBridge.class")){bridgeBytes=input.readAllBytes();}
        Map<String,byte[]> definitions=Map.of(GENERATED.replace('/','.'),cameraBytes,ForgeItemCameraBridge.class.getName(),bridgeBytes);
        ClassLoader loader=new ClassLoader(HostForgeCameraCompatibilityTest.class.getClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null){byte[] code=definitions.get(name);type=code==null?super.loadClass(name,false):defineClass(name,code,0,code.length);}if(resolve)resolveClass(type);return type;}}
        };
        Class<?> generated=loader.loadClass(GENERATED.replace('/','.')),bridge=loader.loadClass(ForgeItemCameraBridge.class.getName());
        return new Harness(generated.getMethod("handleCameraTransforms",Model.class,Transform.class),MethodHandles.publicLookup().findStatic(bridge,"install",MethodType.methodType(boolean.class,MethodHandle.class)));
    }
    static ForgeMappings mappings() throws Exception {
        MappingResolver resolver=(MappingResolver)Proxy.newProxyInstance(MappingResolver.class.getClassLoader(),new Class<?>[]{MappingResolver.class},(proxy,method,args)->switch(method.getName()){
            case "getNamespaces" -> List.of("official","intermediary"); case "getCurrentRuntimeNamespace" -> "intermediary";
            case "mapClassName","unmapClassName" -> args[1]; case "mapMethodName","mapFieldName" -> args[2]; default -> throw new UnsupportedOperationException(method.getName());});
        return new ForgeMappings(resolver,Files.newBufferedReader(Path.of("../build/forge-compat/dependencies/forge-1.8.9.srg")));
    }
    static ClassNode read() throws Exception { assertEquals(ForgeCompatBootstrap.FORGE_SHA256,ForgeModScanner.sha256(FORGE)); try(ZipFile zip=new ZipFile(FORGE.toFile());var input=zip.getInputStream(zip.getEntry(HostForgeCameraCompatibility.TARGET+".class"))){ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);return node;} }
    static MethodNode camera(ClassNode node) { return node.methods.stream().filter(method->method.name.equals("handleCameraTransforms")).findFirst().orElseThrow(); }
    static long bridgeCalls(ClassNode node) { return node.methods.stream().flatMap(method->Arrays.stream(method.instructions.toArray())).filter(instruction->instruction instanceof MethodInsnNode call&&call.owner.equals(HostForgeCameraCompatibility.BRIDGE)&&call.name.equals("apply")).count(); }
    private static long calls(MethodNode method,String name) { return Arrays.stream(method.instructions.toArray()).filter(instruction->instruction instanceof MethodInsnNode call&&call.name.equals(name)).count(); }
    private static byte[] methodBytes(MethodNode method) { ClassNode holder=new ClassNode();holder.version=V1_8;holder.access=ACC_PUBLIC;holder.name="test/Holder";holder.superName="java/lang/Object";holder.methods.add(method);return bytes(holder); }
    static byte[] bytes(ClassNode node) { ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray(); }
}
