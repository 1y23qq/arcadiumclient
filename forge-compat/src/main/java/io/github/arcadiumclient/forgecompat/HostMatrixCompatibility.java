package io.github.arcadiumclient.forgecompat;

import java.nio.FloatBuffer;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

final class HostMatrixCompatibility {
    static final String CAPTURE = "dev/rdh/argentum/impl/render/entity/instancing/EntityCapture";
    static final String METHOD = "arcadium$multiplyMatrix";
    static final String SCRATCH = "arcadium$forgeMatrix";
    private static final String GL_METHOD = "arcadium$multiplyGlMatrix";
    private static final String DESCRIPTOR = "(Ljava/nio/FloatBuffer;)Z";
    private static final Type MATRIX = Type.getObjectType("org/joml/Matrix4f");
    private static final Type STACK = Type.getObjectType("org/joml/Matrix4fStack");
    private static final Type VIEW = Type.getObjectType("org/joml/Matrix4fc");

    private HostMatrixCompatibility() { }

    static void normalizeForge(ClassNode node) {
        if (!node.name.equals("net/minecraftforge/client/ForgeHooksClient")) return;
        MethodNode method = node.methods.stream().filter(value -> value.name.equals("multiplyCurrentGlMatrix")
            && value.desc.equals("(Ljavax/vecmath/Matrix4f;)V")).findFirst().orElseThrow(() -> unsupported("Forge matrix method"));
        if (node.methods.stream().anyMatch(value -> value.name.equals(GL_METHOD))) {
            if (node.methods.stream().filter(value -> value.name.equals(GL_METHOD) && value.desc.equals("(Ljava/nio/FloatBuffer;)V")).count() == 1
                    && java.util.Arrays.stream(method.instructions.toArray()).anyMatch(value -> value instanceof MethodInsnNode call
                        && call.owner.equals(node.name) && call.name.equals(GL_METHOD))) return;
            throw unsupported("existing Forge matrix bridge");
        }
        List<MethodInsnNode> calls = java.util.Arrays.stream(method.instructions.toArray())
            .filter(value -> value instanceof MethodInsnNode call && call.owner.equals("org/lwjgl/opengl/GL11")
                && call.name.equals("glMultMatrix") && call.desc.equals("(Ljava/nio/FloatBuffer;)V"))
            .map(MethodInsnNode.class::cast).toList();
        if (calls.size() != 1) throw unsupported("Forge matrix multiplication");
        MethodInsnNode original = calls.get(0);
        original.owner = node.name;
        original.name = GL_METHOD;
        node.methods.add(forgeMultiply(node.version, node.name));
    }

    static void normalizeNative(ClassNode node) {
        if (!node.name.equals(CAPTURE)) return;
        String access = Type.getInternalName(NativeMatrixCapture.class);
        if (node.interfaces.contains(access)) {
            if (node.methods.stream().filter(value -> value.name.equals(METHOD) && value.desc.equals(DESCRIPTOR)).count() == 1) return;
            throw unsupported("existing native matrix bridge");
        }
        for (String field : List.of("matrices", "textureMatrices")) {
            if (node.fields.stream().noneMatch(value -> value.name.equals(field) && value.desc.equals(STACK.getDescriptor())))
                throw unsupported("native matrix stack " + field);
        }
        for (String method : List.of("tracksTexture", "tracksModelView")) {
            if (node.methods.stream().noneMatch(value -> value.name.equals(method) && value.desc.equals("()Z")))
                throw unsupported("native matrix tracking " + method);
        }
        if (node.fields.stream().noneMatch(value -> value.name.equals("suppressFixedFunction") && value.desc.equals("Z"))
                || node.fields.stream().anyMatch(value -> value.name.equals(SCRATCH))
                || node.methods.stream().anyMatch(value -> value.name.equals(METHOD))) throw unsupported("native matrix storage");
        node.interfaces.add(access);
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, SCRATCH, MATRIX.getDescriptor(), null, null));
        node.methods.add(multiply(node.version));
    }

    private static MethodNode multiply(int version) {
        Type owner = Type.getObjectType(CAPTURE);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(version, Opcodes.ACC_PUBLIC, CAPTURE, null, "java/lang/Object", null);
        GeneratorAdapter method = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
            new Method(METHOD, DESCRIPTOR), null, null, writer);
        method.visitCode();
        int modelView = method.newLocal(Type.BOOLEAN_TYPE), texture = method.newLocal(Type.BOOLEAN_TYPE);
        method.loadThis(); method.invokeVirtual(owner, new Method("tracksModelView", "()Z")); method.storeLocal(modelView);
        method.loadThis(); method.invokeVirtual(owner, new Method("tracksTexture", "()Z")); method.storeLocal(texture);
        Label tracked = method.newLabel(), ready = method.newLabel(), model = method.newLabel(), done = method.newLabel();
        method.loadLocal(modelView); method.ifZCmp(GeneratorAdapter.NE, tracked);
        method.loadLocal(texture); method.ifZCmp(GeneratorAdapter.NE, tracked);
        method.push(false); method.returnValue();
        method.mark(tracked);
        method.loadThis(); method.getField(owner, SCRATCH, MATRIX); method.ifNonNull(ready);
        method.loadThis(); method.newInstance(MATRIX); method.dup(); method.invokeConstructor(MATRIX, new Method("<init>", "()V"));
        method.putField(owner, SCRATCH, MATRIX);
        method.mark(ready);
        method.loadThis(); method.getField(owner, SCRATCH, MATRIX); method.loadArg(0);
        method.invokeVirtual(MATRIX, new Method("set", MATRIX, new Type[]{Type.getType(FloatBuffer.class)})); method.pop();
        method.loadLocal(texture); method.ifZCmp(GeneratorAdapter.EQ, model);
        multiplyStack(method, owner, "textureMatrices");
        method.mark(model); method.loadLocal(modelView); method.ifZCmp(GeneratorAdapter.EQ, done);
        multiplyStack(method, owner, "matrices");
        method.loadThis(); method.getField(owner, "suppressFixedFunction", Type.BOOLEAN_TYPE); method.returnValue();
        method.mark(done); method.push(false); method.returnValue(); method.endMethod(); writer.visitEnd();
        ClassNode generated = new ClassNode(); new ClassReader(writer.toByteArray()).accept(generated, 0);
        return generated.methods.get(0);
    }

    private static MethodNode forgeMultiply(int version, String owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(version, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        GeneratorAdapter method = new GeneratorAdapter(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
            new Method(GL_METHOD, "(Ljava/nio/FloatBuffer;)V"), null, null, writer);
        method.visitCode();
        Label done = method.newLabel();
        method.loadArg(0); method.invokeStatic(Type.getType(ForgeMatrixCapture.class), new Method("multiply", DESCRIPTOR));
        method.ifZCmp(GeneratorAdapter.NE, done);
        method.loadArg(0); method.invokeStatic(Type.getObjectType("org/lwjgl/opengl/GL11"), new Method("glMultMatrix", "(Ljava/nio/FloatBuffer;)V"));
        method.mark(done); method.returnValue(); method.endMethod(); writer.visitEnd();
        ClassNode generated = new ClassNode(); new ClassReader(writer.toByteArray()).accept(generated, 0);
        return generated.methods.get(0);
    }

    private static void multiplyStack(GeneratorAdapter method, Type owner, String field) {
        method.loadThis(); method.getField(owner, field, STACK);
        method.loadThis(); method.getField(owner, SCRATCH, MATRIX);
        method.invokeVirtual(STACK, new Method("mul", MATRIX, new Type[]{VIEW})); method.pop();
    }

    private static IllegalStateException unsupported(String shape) { return new IllegalStateException("Unsupported entity matrix capture: " + shape); }
}
