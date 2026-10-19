package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostRegistrySnapshotCompatibility {
    private static final String FML = "net/minecraftforge/fml/common/registry/FMLControlledNamespacedRegistry";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostRegistrySnapshotCompatibility";
    private static final String WRAPPED = "net/ornithemc/osl/registries/impl/registry/WrappedIdRegistry";
    private static final String REGISTRIES = "net/ornithemc/osl/registries/api/registry/Registries";
    private record Names(String namespaced, String wrapped, String registries) { }
    private record Access(Names names, Class<?> wrapped, Field catalog, VarHandle registry, VarHandle frozen, MethodHandle clear, MethodHandle freeze) { }
    private record Scope(Access access, Object wrapper, boolean frozen) { }
    private static volatile Names configured;
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> registry) {
            Names names = names();
            try {
                ClassLoader loader = registry.getClassLoader();
                Class<?> wrapped = Class.forName(names.wrapped.replace('/', '.'), false, loader);
                Class<?> namespaced = Class.forName(names.namespaced.replace('/', '.'), false, loader);
                Class<?> registries = Class.forName(names.registries.replace('/', '.'), false, loader);
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(wrapped, MethodHandles.lookup());
                return new Access(names, wrapped, registries.getField("REGISTRY"), lookup.findVarHandle(wrapped, "registry", namespaced),
                    lookup.findVarHandle(wrapped, "frozen", boolean.class),
                    lookup.unreflect(wrapped.getMethod("clear")).asType(MethodType.methodType(void.class, Object.class)),
                    lookup.unreflect(wrapped.getMethod("freeze")).asType(MethodType.methodType(Object.class, Object.class)));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The native OSL registry snapshot lifecycle could not be linked", exception);
            }
        }
    };

    private HostRegistrySnapshotCompatibility() { }

    public static Object beginSnapshot(Object registry) throws Throwable {
        Access access = ACCESS.get(registry.getClass());
        if (!access.names.equals(names())) { ACCESS.remove(registry.getClass()); access = ACCESS.get(registry.getClass()); }
        Object catalog = access.catalog.get(null);
        if (!(catalog instanceof Iterable<?> entries)) throw new IllegalStateException("The OSL registry catalog is not iterable");
        Object wrapper = null;
        for (Object entry : entries) if (access.wrapped.isInstance(entry) && access.registry.get(entry) == registry) {
            if (wrapper != null) throw new IllegalStateException("Multiple OSL wrappers refer to the same native registry");
            wrapper = entry;
        }
        if (wrapper == null) return null;
        Scope scope = new Scope(access, wrapper, (boolean) access.frozen.get(wrapper));
        try {
            HostRegistryMapperCompatibility.beginSnapshotClear();
            try { access.clear.invokeExact(wrapper); }
            finally { HostRegistryMapperCompatibility.endSnapshotClear(); }
        }
        catch (Throwable failure) { endSnapshot(scope, failure); throw failure; }
        return scope;
    }

    public static void endSnapshot(Object token, Throwable failure) throws Throwable {
        if (token == null) return;
        Scope scope = (Scope) token;
        if (!scope.frozen) return;
        try { Object ignored = (Object) scope.access.freeze.invokeExact(scope.wrapper); }
        catch (Throwable cleanup) {
            if (failure == null) throw cleanup;
            if (cleanup != failure) failure.addSuppressed(cleanup);
        }
    }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(FML)) return;
        Names names = new Names(mappings.classFromSrg("net/minecraft/util/RegistryNamespaced"), mappings.classFromSrg(WRAPPED), mappings.classFromSrg(REGISTRIES));
        MethodNode original = node.methods.stream().filter(method -> method.name.equals("set") && method.desc.equals("(L" + FML + ";)V")).findFirst()
            .orElseThrow(() -> unsupported("snapshot method"));
        if (Arrays.stream(original.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(BRIDGE) && call.name.equals("beginSnapshot"))) { configured = names; return; }
        MethodNode method = expanded(node.name, original);
        List<AbstractInsnNode> code = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        String ids = mappings.classFromSrg("net/minecraft/util/ObjectIntIdentityMap");
        String idField = mappings.fieldFromSrg("net/minecraft/util/RegistryNamespaced", "field_148759_a", "Lnet/minecraft/util/ObjectIntIdentityMap;");
        String objects = mappings.fieldFromSrg("net/minecraft/util/RegistrySimple", "field_82596_a", "Ljava/util/Map;");
        List<Integer> resets = new ArrayList<>();
        for (int i = 0; i + 7 < code.size(); i++) {
            if (loadThis(code.get(i)) && code.get(i + 1) instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(ids)
                && code.get(i + 2).getOpcode() == Opcodes.DUP && code.get(i + 3) instanceof MethodInsnNode construct
                && construct.getOpcode() == Opcodes.INVOKESPECIAL && construct.owner.equals(ids) && construct.name.equals("<init>") && construct.desc.equals("()V")
                && code.get(i + 4) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD && field.owner.equals(FML) && field.name.equals(idField) && field.desc.equals("L" + ids + ";")
                && loadThis(code.get(i + 5)) && code.get(i + 6) instanceof FieldInsnNode map && map.getOpcode() == Opcodes.GETFIELD && map.owner.equals(FML) && map.name.equals(objects) && map.desc.equals("Ljava/util/Map;")
                && code.get(i + 7) instanceof MethodInsnNode clear && clear.getOpcode() == Opcodes.INVOKEINTERFACE && clear.owner.equals("java/util/Map") && clear.name.equals("clear") && clear.desc.equals("()V")) resets.add(i);
        }
        List<AbstractInsnNode> returns = code.stream().filter(instruction -> instruction.getOpcode() == Opcodes.RETURN).toList();
        if (resets.size() != 1 || returns.size() != 1) throw unsupported("snapshot map reset");
        int token = method.maxLocals, error = token + 1;
        AbstractInsnNode reset = code.get(resets.get(0)), exit = returns.get(0);
        boolean entered = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction == reset) entered = true;
            if (entered && instruction instanceof FrameNode frame) appendToken(frame.local, token);
        }
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        InsnList begin = new InsnList(); begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "beginSnapshot", "(Ljava/lang/Object;)Ljava/lang/Object;", false));
        begin.add(new VarInsnNode(Opcodes.ASTORE, token)); begin.add(start); method.instructions.insertBefore(reset, begin);
        InsnList finish = new InsnList(); finish.add(end); finish.add(new VarInsnNode(Opcodes.ALOAD, token)); finish.add(new InsnNode(Opcodes.ACONST_NULL));
        finish.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "endSnapshot", "(Ljava/lang/Object;Ljava/lang/Throwable;)V", false)); method.instructions.insertBefore(exit, finish);
        method.instructions.add(handler);
        List<Object> locals = new ArrayList<>(List.of(FML, FML)); appendToken(locals, token);
        method.instructions.add(new FrameNode(Opcodes.F_NEW, locals.size(), locals.toArray(), 1, new Object[] {"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, error)); method.instructions.add(new VarInsnNode(Opcodes.ALOAD, token)); method.instructions.add(new VarInsnNode(Opcodes.ALOAD, error));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "endSnapshot", "(Ljava/lang/Object;Ljava/lang/Throwable;)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, error)); method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
        method.maxLocals = error + 1; method.maxStack = Math.max(2, method.maxStack);
        node.methods.set(node.methods.indexOf(original), method); configured = names;
    }

    private static void appendToken(List<Object> locals, int slot) {
        int width = 0; for (Object type : locals) width += type.equals(Opcodes.LONG) || type.equals(Opcodes.DOUBLE) ? 2 : 1;
        if (width > slot) throw unsupported("snapshot local slots");
        while (width++ < slot) locals.add(Opcodes.TOP);
        locals.add("java/lang/Object");
    }
    private static MethodNode expanded(String owner, MethodNode method) {
        ClassNode holder = new ClassNode(); holder.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null); holder.methods.add(method);
        ClassWriter writer = new ClassWriter(0); holder.accept(writer); ClassNode result = new ClassNode(); new ClassReader(writer.toByteArray()).accept(result, ClassReader.EXPAND_FRAMES); return result.methods.get(0);
    }
    private static boolean loadThis(AbstractInsnNode instruction) { return instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0; }
    private static Names names() { Names names = configured; if (names == null) throw new IllegalStateException("Native OSL snapshot names are not configured"); return names; }
    private static IllegalStateException unsupported(String detail) { return new IllegalStateException("Unsupported Forge registry bytecode: " + detail); }
}
