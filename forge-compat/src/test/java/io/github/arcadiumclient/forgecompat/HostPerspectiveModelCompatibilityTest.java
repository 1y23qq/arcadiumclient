package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostPerspectiveModelCompatibilityTest {
    private static final String OWNER = Type.getInternalName(HostPerspectiveModelCompatibilityTest.class) + "$Generated";
    private static final String PERSPECTIVE = "net/minecraftforge/client/model/IPerspectiveAwareModel";

    @Test void reusesTheActualReadonlyDelegateButKeepsEveryLivePerspectiveAndReplacement() throws Exception {
        ClassNode node = read(HostPerspectiveModelCompatibility.TARGET);
        Map<String, byte[]> unchanged = new LinkedHashMap<>();
        for (MethodNode method : node.methods) if (!method.name.equals("handlePerspective")) unchanged.put(method.name + method.desc, methodBytes(method));
        HostPerspectiveModelCompatibility.normalize(node, mappings());
        for (MethodNode method : node.methods) if (unchanged.containsKey(method.name + method.desc)) assertArrayEquals(unchanged.get(method.name + method.desc), methodBytes(method));
        for (MethodNode method : node.methods) if ((method.access & Opcodes.ACC_ABSTRACT) == 0) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        byte[] once = bytes(node);
        HostPerspectiveModelCompatibility.normalize(node, mappings());
        assertArrayEquals(once, bytes(node));

        Map<String, String> aliases = Map.ofEntries(
            Map.entry(HostPerspectiveModelCompatibility.TARGET, OWNER + "Perspective"),
            Map.entry(HostPerspectiveModelCompatibility.TARGET + "$1", OWNER + "Delegate"),
            Map.entry(HostPerspectiveModelCompatibility.BASE, OWNER + "Base"),
            Map.entry(HostPerspectiveModelCompatibility.FLEXIBLE, Type.getInternalName(Flexible.class)),
            Map.entry(PERSPECTIVE, Type.getInternalName(Perspective.class)),
            Map.entry("net/minecraftforge/client/model/ISmartBlockModel", Type.getInternalName(Smart.class)),
            Map.entry("bgr$b", Type.getInternalName(Transform.class)),
            Map.entry("boq", Type.getInternalName(Flexible.class)),
            Map.entry("cq", Type.getInternalName(Face.class)),
            Map.entry("bgr", Type.getInternalName(Camera.class)),
            Map.entry("bmi", Type.getInternalName(Sprite.class)),
            Map.entry("bmu", Type.getInternalName(Format.class)),
            Map.entry("alz", Type.getInternalName(State.class)));
        Remapper remapper = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return aliases.getOrDefault(name, name); }
        };
        Map<String, byte[]> classes = new LinkedHashMap<>();
        for (ClassNode original : List.of(read(HostPerspectiveModelCompatibility.BASE), node, read(HostPerspectiveModelCompatibility.TARGET + "$1"))) {
            ClassWriter writer = new ClassWriter(0); original.accept(new ClassRemapper(writer, remapper));
            classes.put(remapper.map(original.name).replace('/', '.'), writer.toByteArray());
        }
        classes.put(OWNER.replace('/', '.'), concrete(OWNER, OWNER + "Perspective"));
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] code = classes.get(name);
                if (code == null) throw new ClassNotFoundException(name);
                return defineClass(name, code, 0, code.length);
            }
        };
        Parent parent = new Parent();
        Object wrapper = loader.loadClass(OWNER.replace('/', '.')).getConstructor(Perspective.class).newInstance(parent);
        var cache = wrapper.getClass().getSuperclass().getDeclaredField(HostPerspectiveModelCompatibility.CACHED);
        cache.setAccessible(true);
        assertNull(cache.get(wrapper));
        var perspective = wrapper.getClass().getMethod("handlePerspective", Transform.class);
        Flexible first = null;
        for (int frame = 0; frame < 10_000; frame++) {
            parent.matrix = new Matrix(frame);
            Pair<?, ?> result = (Pair<?, ?>) perspective.invoke(wrapper, Transform.FIRST_PERSON);
            assertSame(parent.matrix, result.getRight());
            Flexible delegate = (Flexible) result.getLeft();
            if (first == null) first = delegate;
            else assertSame(first, delegate);
            assertSame(parent.replacement.a(), delegate.a());
            assertSame(parent.replacement.a(Face.FRONT), delegate.a(Face.FRONT));
            assertSame(parent.replacement.e(), delegate.e());
            assertSame(parent.replacement.f(), delegate.f());
            assertSame(parent.replacement.getFormat(), delegate.getFormat());
            assertEquals(parent.replacement.b(), delegate.b());
            assertEquals(parent.replacement.c(), delegate.c());
            assertEquals(parent.replacement.d(), delegate.d());
            assertSame(wrapper, ((Smart) delegate).handleBlockState(new State()));
        }
        Parent alternate = new Parent();
        assertEquals(parent.replacement, alternate);
        Flexible original = parent.replacement;
        parent.replacement = alternate;
        Pair<?, ?> changed = (Pair<?, ?>) perspective.invoke(wrapper, Transform.THIRD_PERSON);
        assertNotSame(first, changed.getLeft());
        assertSame(alternate.a(), ((Flexible) changed.getLeft()).a());
        parent.replacement = original;
        assertSame(first, ((Pair<?, ?>) perspective.invoke(wrapper, Transform.FIRST_PERSON)).getLeft());
        assertEquals(10_002, parent.calls);
        assertEquals(2, ((Map<?, ?>) cache.get(wrapper)).size());
    }

    @Test void rejectsChangedDelegatesBeforeMutationAndLeavesCustomModelsAlone() throws Exception {
        ClassNode custom = read(HostPerspectiveModelCompatibility.TARGET); custom.name = "test/CustomPerspective";
        byte[] untouched = bytes(custom);
        HostPerspectiveModelCompatibility.normalize(custom, mappings());
        assertArrayEquals(untouched, bytes(custom));
        ClassNode changed = read(HostPerspectiveModelCompatibility.TARGET);
        for (MethodNode method : changed.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("$PerspectiveWrapper$1") && call.name.equals("<init>")) call.owner = "test/OtherDelegate";
        }
        byte[] unknown = bytes(changed);
        assertThrows(IllegalStateException.class, () -> HostPerspectiveModelCompatibility.normalize(changed, mappings()));
        assertArrayEquals(unknown, bytes(changed));
    }

    public interface Flexible {
        List<String> a(Face face);
        List<String> a();
        boolean b(); boolean c(); boolean d();
        Sprite e(); Camera f(); Format getFormat();
    }
    public interface Perspective extends Flexible { Pair<Flexible, Matrix> handlePerspective(Transform transform); }
    public interface Smart extends Flexible { Flexible handleBlockState(State state); }
    public enum Transform { FIRST_PERSON, THIRD_PERSON }
    public enum Face { FRONT }
    public record Matrix(int frame) { }
    public record State() { }
    public record Sprite() { }
    public record Camera() { }
    public record Format() { }
    public static final class Parent implements Perspective {
        final List<String> quads = List.of("quads");
        final Sprite sprite = new Sprite(); final Camera camera = new Camera(); final Format format = new Format();
        Flexible replacement = this;
        Matrix matrix;
        int calls;
        public Pair<Flexible, Matrix> handlePerspective(Transform transform) { calls++; return Pair.of(replacement, matrix); }
        public List<String> a(Face face) { return quads; }
        public List<String> a() { return quads; }
        public boolean b() { return true; } public boolean c() { return true; } public boolean d() { return false; }
        public Sprite e() { return sprite; } public Camera f() { return camera; } public Format getFormat() { return format; }
        @Override public boolean equals(Object other) { return other instanceof Parent; }
        @Override public int hashCode() { return 1; }
    }

    private static byte[] concrete(String name, String parent) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, parent, null);
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(L" + Type.getInternalName(Perspective.class) + ";)V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0); init.visitVarInsn(Opcodes.ALOAD, 1);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "(L" + Type.getInternalName(Perspective.class) + ";)V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        var state = writer.visitMethod(Opcodes.ACC_PUBLIC, "handleBlockState", "(L" + Type.getInternalName(State.class) + ";)L" + Type.getInternalName(Flexible.class) + ";", null, null);
        state.visitCode(); state.visitVarInsn(Opcodes.ALOAD, 0); state.visitInsn(Opcodes.ARETURN); state.visitMaxs(0, 0); state.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }
    private static ClassNode read(String name) throws Exception {
        try (ZipFile zip = new ZipFile(Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar").toFile());
             var stream = zip.getInputStream(zip.getEntry(name + ".class"))) {
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static ForgeMappings mappings() throws Exception {
        MappingResolver resolver = (MappingResolver) Proxy.newProxyInstance(MappingResolver.class.getClassLoader(), new Class<?>[] {MappingResolver.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getNamespaces" -> List.of("official", "intermediary");
                case "getCurrentRuntimeNamespace" -> "intermediary";
                case "mapClassName", "unmapClassName" -> arguments[1];
                case "mapMethodName", "mapFieldName" -> arguments[2];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new ForgeMappings(resolver, new StringReader("CL: bgr$b net/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType\n"));
    }
    private static byte[] methodBytes(MethodNode method) {
        ClassNode node = new ClassNode(); node.version = Opcodes.V1_8; node.name = "test/Method"; node.superName = "java/lang/Object";
        node.methods.add(method); return bytes(node);
    }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray(); }
}
