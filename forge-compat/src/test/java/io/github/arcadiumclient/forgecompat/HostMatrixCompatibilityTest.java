package io.github.arcadiumclient.forgecompat;

import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

class HostMatrixCompatibilityTest {
    @Test void preservesTheOrderAndBufferPositionOfRealMatricesWithoutPerCallStorage() throws Exception {
        Object capture = fixture();
        Matrix4fStack model = (Matrix4fStack) capture.getClass().getField("matrices").get(capture);
        model.translation(3, 5, 7).rotateY(.3f);
        Matrix4f transform = new Matrix4f().rotationX(.4f).scale(.375f, .25f, .5f).translate(.25f, .5f, .7f);
        FloatBuffer buffer = java.nio.ByteBuffer.allocateDirect(80).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().position(2).limit(18);
        transform.get(buffer);
        Matrix4f expected = new Matrix4f(model).mul(transform);
        assertFalse(((NativeMatrixCapture) capture).arcadium$multiplyMatrix(buffer));
        assertMatrix(expected, model);
        assertEquals(2, buffer.position());
        assertEquals(18, buffer.limit());
        var scratch = capture.getClass().getDeclaredField(HostMatrixCompatibility.SCRATCH);
        scratch.setAccessible(true);
        Object first = scratch.get(capture);
        assertNotNull(first);
        ((NativeMatrixCapture) capture).arcadium$multiplyMatrix(buffer);
        assertSame(first, scratch.get(capture));
        assertMatrix(expected.mul(transform), model);
    }

    @Test void matchesNativeModelTextureAndFixedFunctionCaptureRules() throws Exception {
        Object capture = fixture();
        Matrix4fStack model = (Matrix4fStack) capture.getClass().getField("matrices").get(capture);
        Matrix4fStack texture = (Matrix4fStack) capture.getClass().getField("textureMatrices").get(capture);
        Matrix4f transform = new Matrix4f().translation(1, 2, 3).scale(.375f);
        FloatBuffer buffer = transform.get(java.nio.ByteBuffer.allocateDirect(64).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer());
        capture.getClass().getField("suppressFixedFunction").setBoolean(capture, true);
        assertTrue(((NativeMatrixCapture) capture).arcadium$multiplyMatrix(buffer));
        assertMatrix(transform, model);
        assertMatrix(new Matrix4f(), texture);
        capture.getClass().getField("modelTracked").setBoolean(capture, false);
        capture.getClass().getField("textureTracked").setBoolean(capture, true);
        assertFalse(((NativeMatrixCapture) capture).arcadium$multiplyMatrix(buffer));
        assertMatrix(transform, texture);
        assertMatrix(transform, model);
    }

    @Test void leavesUntrackedMatricesAndOrdinaryForgeRenderingUntouched() throws Exception {
        Object capture = fixture();
        capture.getClass().getField("modelTracked").setBoolean(capture, false);
        assertFalse(((NativeMatrixCapture) capture).arcadium$multiplyMatrix(FloatBuffer.allocate(0)));
        var scratch = capture.getClass().getDeclaredField(HostMatrixCompatibility.SCRATCH);
        scratch.setAccessible(true);
        assertNull(scratch.get(capture));
        assertFalse(ForgeMatrixCapture.multiply(FloatBuffer.allocate(0)));
    }

    @Test void patchesTheActualForgeHookOnceAndKeepsOtherMethodsIdentical() throws Exception {
        ClassNode node = new ClassNode();
        try (ZipFile archive = new ZipFile(Path.of("../build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar").toFile())) {
            new ClassReader(archive.getInputStream(archive.getEntry("net/minecraftforge/client/ForgeHooksClient.class"))).accept(node, 0);
        }
        var original = node.methods.stream().filter(method -> !method.name.equals("multiplyCurrentGlMatrix"))
            .collect(java.util.stream.Collectors.toMap(method -> method.name + method.desc, HostMatrixCompatibilityTest::methodBytes));
        HostMatrixCompatibility.normalizeForge(node);
        for (MethodNode method : node.methods) {
            if (original.containsKey(method.name + method.desc)) assertArrayEquals(original.get(method.name + method.desc), methodBytes(method));
            if (method.name.equals("arcadium$multiplyGlMatrix")) new Analyzer<>(new BasicVerifier()).analyze(node.name, method);
        }
        byte[] once = bytes(node);
        HostMatrixCompatibility.normalizeForge(node);
        assertArrayEquals(once, bytes(node));
        MethodNode originalMethod = node.methods.stream().filter(method -> method.name.equals("multiplyCurrentGlMatrix")).findFirst().orElseThrow();
        assertEquals(1, java.util.Arrays.stream(originalMethod.instructions.toArray()).filter(value -> value instanceof MethodInsnNode call
            && call.owner.equals(node.name) && call.name.equals("arcadium$multiplyGlMatrix")).count());
    }

    private static Object fixture() throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String owner = HostMatrixCompatibility.CAPTURE;
        Type type = Type.getObjectType(owner), stack = Type.getType(Matrix4fStack.class);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        for (String field : List.of("matrices", "textureMatrices")) writer.visitField(Opcodes.ACC_PUBLIC, field, stack.getDescriptor(), null, null).visitEnd();
        for (String field : List.of("modelTracked", "textureTracked", "suppressFixedFunction")) writer.visitField(Opcodes.ACC_PUBLIC, field, "Z", null, null).visitEnd();
        GeneratorAdapter constructor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, new Method("<init>", "()V"), null, null, writer);
        constructor.loadThis(); constructor.invokeConstructor(Type.getType(Object.class), new Method("<init>", "()V"));
        for (String field : List.of("matrices", "textureMatrices")) {
            constructor.loadThis(); constructor.newInstance(stack); constructor.dup(); constructor.push(8);
            constructor.invokeConstructor(stack, new Method("<init>", "(I)V")); constructor.putField(type, field, stack);
        }
        constructor.loadThis(); constructor.push(true); constructor.putField(type, "modelTracked", Type.BOOLEAN_TYPE);
        constructor.returnValue(); constructor.endMethod();
        for (String method : List.of("tracksModelView", "tracksTexture")) {
            GeneratorAdapter tracking = new GeneratorAdapter(Opcodes.ACC_PRIVATE, new Method(method, "()Z"), null, null, writer);
            tracking.loadThis(); tracking.getField(type, method.equals("tracksModelView") ? "modelTracked" : "textureTracked", Type.BOOLEAN_TYPE);
            tracking.returnValue(); tracking.endMethod();
        }
        writer.visitEnd();
        ClassNode node = new ClassNode(); new ClassReader(writer.toByteArray()).accept(node, 0);
        HostMatrixCompatibility.normalizeNative(node);
        byte[] once = bytes(node); HostMatrixCompatibility.normalizeNative(node); assertArrayEquals(once, bytes(node));
        String generatedName = "io/github/arcadiumclient/forgecompat/GeneratedMatrixCapture";
        ClassWriter mapped = new ClassWriter(0);
        node.accept(new ClassRemapper(mapped, new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return name.equals(owner) ? generatedName : name; }
        }));
        byte[] code = mapped.toByteArray();
        Class<?> generated = new ClassLoader(HostMatrixCompatibilityTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(generatedName.replace('/', '.'), code, 0, code.length); }
        }.define();
        return generated.getConstructor().newInstance();
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual) {
        assertArrayEquals(expected.get(new float[16]), actual.get(new float[16]), .00001f);
    }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static byte[] methodBytes(MethodNode method) { ClassNode node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "MethodProbe", null, "java/lang/Object", null); node.methods.add(method); return bytes(node); }
}
