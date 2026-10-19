package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

final class HostEntityRenderPassCompatibility {
    static final String RENDERER = "dev/rdh/argentum/impl/render/terrain/ArgentumWorldRenderer";
    private static final String GATHERER = "dev/rdh/argentum/impl/render/entity/EntityGatherer";
    private static final String CULLER = "dev/rdh/argentum/impl/render/entity/EntityOcclusionCuller";
    private static final String MATRICES = "org/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices";
    private static final String CLIENT = "net/minecraftforge/client/MinecraftForgeClient";
    private static final String SELECT = "arcadium$entitiesForRenderPass";
    private static final String PREPARE = "arcadium$prepareEntityRenderPass";
    private static final Type LIST = Type.getType(List.class), ARRAY_LIST = Type.getType(java.util.ArrayList.class);
    private static final String SELECT_DESC = "(Ljava/util/List;)Ljava/util/List;";
    private static final int HELPER_ACCESS = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;

    private HostEntityRenderPassCompatibility() { }

    static boolean targets(String name) { return RENDERER.equals(name); }

    static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!targets(node.name)) return;
        String entity = mappings.classFromSrg("net/minecraft/entity/Entity");
        String descriptor = mappings.descriptorFromSrg("(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;FDDD)I");
        MethodNode render = node.methods.stream().filter(value -> value.name.equals("renderEntities") && value.desc.equals(descriptor))
            .findFirst().orElseThrow(() -> unsupported("render method"));
        String prepareDesc = "(Ljava/util/List;L" + entity + ";FL" + MATRICES + ";DDD)V";
        String bridgeDesc = "(L" + CULLER + ";Ljava/util/List;Ljava/util/List;L" + entity + ";FL" + MATRICES + ";DDD)V";
        List<MethodNode> helpers = node.methods.stream().filter(value -> value.name.equals(SELECT) || value.name.equals(PREPARE)).toList();
        if (!helpers.isEmpty()) {
            if (helpers.size() == 2 && helpers.stream().allMatch(value -> value.access == HELPER_ACCESS
                    && value.desc.equals(value.name.equals(SELECT) ? SELECT_DESC : bridgeDesc))
                    && calls(render, node.name, SELECT, SELECT_DESC).size() == 1
                    && calls(render, node.name, PREPARE, bridgeDesc).size() == 1
                    && calls(render, CULLER, "prepare", prepareDesc).isEmpty()) return;
            throw unsupported("existing pass helpers");
        }
        List<MethodInsnNode> gathers = Arrays.stream(render.instructions.toArray()).filter(value -> value instanceof MethodInsnNode call
            && call.owner.equals(GATHERER) && call.name.equals("getLoadedEntityList") && call.desc.endsWith(")Ljava/util/List;"))
            .map(MethodInsnNode.class::cast).toList();
        List<MethodInsnNode> prepares = calls(render, CULLER, "prepare", prepareDesc);
        if (gathers.size() != 1 || prepares.size() != 1 || (render.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            throw unsupported("entity preparation");
        }
        MethodInsnNode gather = gathers.get(0), prepare = prepares.get(0);
        if (gather.getOpcode() != Opcodes.INVOKEVIRTUAL || gather.itf
                || !(next(gather) instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ASTORE) throw unsupported("entity list local");
        AbstractInsnNode cursor = next(store);
        if (!variable(cursor, Opcodes.ALOAD, 0) || !(next(cursor) instanceof FieldInsnNode culler)
                || culler.getOpcode() != Opcodes.GETFIELD || !culler.owner.equals(node.name) || !culler.desc.equals("L" + CULLER + ";")) {
            throw unsupported("culler receiver");
        }
        AbstractInsnNode listArgument = next(culler);
        if (!variable(listArgument, Opcodes.ALOAD, store.var) || !variable(next(listArgument), Opcodes.ALOAD, 1)
                || !variable(next(next(listArgument)), Opcodes.FLOAD, 3)) throw unsupported("culler arguments");
        cursor = next(next(next(listArgument)));
        if (!variable(cursor, Opcodes.ALOAD, 0) || !(next(cursor) instanceof FieldInsnNode matrices)
                || matrices.getOpcode() != Opcodes.GETFIELD || !matrices.owner.equals(node.name) || !matrices.desc.equals("L" + MATRICES + ";")) {
            throw unsupported("culler matrices");
        }
        cursor = next(matrices);
        for (int local : new int[]{4, 6, 8}) {
            if (!variable(cursor, Opcodes.DLOAD, local)) throw unsupported("camera position");
            cursor = next(cursor);
        }
        if (cursor != prepare || prepare.getOpcode() != Opcodes.INVOKEVIRTUAL || prepare.itf) throw unsupported("culler invocation");
        for (cursor = gather.getNext(); cursor != prepare; cursor = cursor.getNext()) {
            if (cursor instanceof FrameNode) throw unsupported("preparation frame boundary");
        }
        List<MethodNode> generated = helpers(node, entity, prepareDesc, bridgeDesc);
        InsnList select = new InsnList();
        select.add(new InsnNode(Opcodes.DUP));
        select.add(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, SELECT, SELECT_DESC, false));
        render.instructions.insert(gather, select);
        render.instructions.insertBefore(listArgument, new InsnNode(Opcodes.SWAP));
        prepare.setOpcode(Opcodes.INVOKESTATIC); prepare.owner = node.name; prepare.name = PREPARE; prepare.desc = bridgeDesc;
        render.maxStack += 2;
        node.methods.addAll(generated);
    }

    private static List<MethodNode> helpers(ClassNode owner, String entity, String prepareDesc, String bridgeDesc) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(owner.version, Opcodes.ACC_PUBLIC, owner.name, null, "java/lang/Object", null);
        GeneratorAdapter method = new GeneratorAdapter(HELPER_ACCESS, new Method(SELECT, SELECT_DESC), null, null, writer);
        method.visitCode();
        int pass = method.newLocal(Type.INT_TYPE), size = method.newLocal(Type.INT_TYPE), first = method.newLocal(Type.INT_TYPE);
        int selected = method.newLocal(LIST), index = method.newLocal(Type.INT_TYPE), current = method.newLocal(Type.getType(Object.class));
        method.invokeStatic(Type.getObjectType(CLIENT), new Method("getRenderPass", "()I")); method.storeLocal(pass);
        method.loadArg(0); method.invokeInterface(LIST, new Method("size", "()I")); method.storeLocal(size);
        method.push(0); method.storeLocal(first);
        Label scan = method.newLabel(), all = method.newLabel(), rejected = method.newLabel();
        method.mark(scan); method.loadLocal(first); method.loadLocal(size); method.ifICmp(GeneratorAdapter.GE, all);
        eligible(method, entity, first, pass); method.ifZCmp(GeneratorAdapter.EQ, rejected);
        method.iinc(first, 1); method.goTo(scan);
        method.mark(all); method.loadArg(0); method.returnValue();
        method.mark(rejected); method.visitInsn(Opcodes.ACONST_NULL); method.storeLocal(selected);
        Label rest = method.newLabel(), copy = method.newLabel();
        method.loadLocal(first); method.ifZCmp(GeneratorAdapter.EQ, rest);
        allocate(method, size, selected);
        method.push(0); method.storeLocal(index);
        method.mark(copy); method.loadLocal(index); method.loadLocal(first); method.ifICmp(GeneratorAdapter.GE, rest);
        method.loadLocal(selected); element(method, index); method.invokeInterface(LIST, new Method("add", "(Ljava/lang/Object;)Z")); method.pop();
        method.iinc(index, 1); method.goTo(copy);
        method.mark(rest); method.loadLocal(first); method.push(1); method.math(GeneratorAdapter.ADD, Type.INT_TYPE); method.storeLocal(index);
        Label loop = method.newLabel(), advance = method.newLabel(), ready = method.newLabel(), done = method.newLabel(), nonempty = method.newLabel();
        method.mark(loop); method.loadLocal(index); method.loadLocal(size); method.ifICmp(GeneratorAdapter.GE, done);
        element(method, index); method.storeLocal(current);
        method.loadLocal(current); method.checkCast(Type.getObjectType(entity)); method.loadLocal(pass);
        method.invokeVirtual(Type.getObjectType(entity), new Method("shouldRenderInPass", "(I)Z")); method.ifZCmp(GeneratorAdapter.EQ, advance);
        method.loadLocal(selected); method.ifNonNull(ready); allocate(method, size, selected);
        method.mark(ready); method.loadLocal(selected); method.loadLocal(current);
        method.invokeInterface(LIST, new Method("add", "(Ljava/lang/Object;)Z")); method.pop();
        method.mark(advance); method.iinc(index, 1); method.goTo(loop);
        method.mark(done); method.loadLocal(selected); method.ifNonNull(nonempty);
        method.invokeStatic(Type.getType(java.util.Collections.class), new Method("emptyList", "()Ljava/util/List;")); method.returnValue();
        method.mark(nonempty); method.loadLocal(selected); method.returnValue(); method.endMethod();

        GeneratorAdapter bridge = new GeneratorAdapter(HELPER_ACCESS, new Method(PREPARE, bridgeDesc), null, null, writer);
        bridge.visitCode(); Label skip = bridge.newLabel(), prepare = bridge.newLabel();
        bridge.loadArg(2); bridge.invokeInterface(LIST, new Method("isEmpty", "()Z")); bridge.ifZCmp(GeneratorAdapter.EQ, prepare);
        // Keep the primary pass for query maintenance, even when nothing is rendered.
        bridge.invokeStatic(Type.getObjectType(CLIENT), new Method("getRenderPass", "()I")); bridge.ifZCmp(GeneratorAdapter.GT, skip);
        bridge.mark(prepare);
        for (int argument : new int[]{0, 1, 3, 4, 5, 6, 7, 8}) bridge.loadArg(argument);
        bridge.invokeVirtual(Type.getObjectType(CULLER), new Method("prepare", prepareDesc));
        bridge.mark(skip); bridge.returnValue(); bridge.endMethod(); writer.visitEnd();
        ClassNode generated = new ClassNode(); new ClassReader(writer.toByteArray()).accept(generated, 0); return generated.methods;
    }

    private static void element(GeneratorAdapter method, int index) {
        method.loadArg(0); method.loadLocal(index); method.invokeInterface(LIST, new Method("get", "(I)Ljava/lang/Object;"));
    }
    private static void eligible(GeneratorAdapter method, String entity, int index, int pass) {
        element(method, index); method.checkCast(Type.getObjectType(entity)); method.loadLocal(pass);
        method.invokeVirtual(Type.getObjectType(entity), new Method("shouldRenderInPass", "(I)Z"));
    }
    private static void allocate(GeneratorAdapter method, int size, int selected) {
        method.newInstance(ARRAY_LIST); method.dup(); method.loadLocal(size);
        method.invokeConstructor(ARRAY_LIST, new Method("<init>", "(I)V")); method.storeLocal(selected);
    }
    private static boolean variable(AbstractInsnNode value, int opcode, int local) { return value instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == local; }
    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name, String descriptor) {
        return Arrays.stream(method.instructions.toArray()).filter(value -> value instanceof MethodInsnNode call
            && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)).map(MethodInsnNode.class::cast).toList();
    }
    private static AbstractInsnNode next(AbstractInsnNode value) { if (value == null) return null; do { value = value.getNext(); } while (value != null && value.getOpcode() < 0); return value; }
    private static IllegalStateException unsupported(String shape) { return new IllegalStateException("Unsupported native entity render pass " + shape); }
}
