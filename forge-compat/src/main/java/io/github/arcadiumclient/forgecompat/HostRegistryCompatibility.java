package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostRegistryCompatibility {
    private static final String FML = "net/minecraftforge/fml/common/registry/FMLControlledNamespacedRegistry";
    private static final String NAMESPACED = "net/minecraft/util/RegistryNamespaced";
    private static final String DEFAULTED = "net/minecraft/util/RegistryNamespacedDefaultedByKey";
    private static final String SIMPLE = "net/minecraft/util/RegistrySimple";
    private static final String IDS = "net/minecraft/util/ObjectIntIdentityMap";
    private static final String OSL_ACCESS = "net/ornithemc/osl/registries/impl/access/IdRegistryAccess";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostRegistryCompatibility";
    private static final String REGISTER = "(ILjava/lang/Object;Ljava/lang/Object;)V";
    private static final String HELPER = "(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;)V";
    private record Names(String namespaced, String defaulted, String register, String oslAccess) { }
    private record Binding(Names names, MethodHandle handle) { }
    private static volatile Names configured;
    private static final ClassValue<Binding> HANDLES = new ClassValue<>() {
        @Override protected Binding computeValue(Class<?> registryClass) {
            Names names = configuration();
            try {
                ClassLoader loader = registryClass.getClassLoader();
                Class<?> namespaced = Class.forName(names.namespaced.replace('/', '.'), false, loader);
                Class<?> defaulted = Class.forName(names.defaulted.replace('/', '.'), false, loader);
                Class<?> oslAccess = Class.forName(names.oslAccess.replace('/', '.'), false, loader);
                if (defaulted.getSuperclass() != namespaced || !defaulted.isAssignableFrom(registryClass)
                    || !oslAccess.isInterface() || !oslAccess.isAssignableFrom(namespaced)) {
                    throw new IllegalStateException("The native registry hierarchy is not ready for OSL interoperability");
                }
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(defaulted, MethodHandles.lookup());
                MethodHandle register = lookup.findSpecial(namespaced, names.register,
                    MethodType.methodType(void.class, int.class, Object.class, Object.class), defaulted)
                    .asType(MethodType.methodType(void.class, Object.class, int.class, Object.class, Object.class));
                return new Binding(names, register);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The native registry registration method could not be linked", exception);
            }
        }
    };

    private HostRegistryCompatibility() { }

    public static void registerVanilla(Object registry, int id, Object key, Object value) throws Throwable {
        Objects.requireNonNull(registry, "registry");
        Names names = configuration();
        Binding binding = HANDLES.get(registry.getClass());
        if (!binding.names.equals(names)) {
            HANDLES.remove(registry.getClass());
            binding = HANDLES.get(registry.getClass());
        }
        Object scope = HostRegistryRegistrationCompatibility.beginRegistration(registry);
        try { binding.handle.invokeExact(registry, id, key, value); }
        catch (Throwable failure) { HostRegistryRegistrationCompatibility.endRegistration(scope, failure); throw failure; }
        HostRegistryRegistrationCompatibility.endRegistration(scope, null);
    }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(FML)) return;
        Names names = new Names(mappings.classFromSrg(NAMESPACED), mappings.classFromSrg(DEFAULTED),
            mappings.methodFromSrg(NAMESPACED, "func_177775_a", REGISTER), mappings.classFromSrg(OSL_ACCESS));
        if (!node.superName.equals(names.defaulted)) throw unsupported("registry superclass");
        String key = mappings.classFromSrg("net/minecraft/util/ResourceLocation");
        MethodNode method = node.methods.stream().filter(candidate -> candidate.name.equals("addObjectRaw")
            && candidate.desc.equals("(IL" + key + ";Ljava/lang/Object;)V")).findFirst()
            .orElseThrow(() -> unsupported("raw registration method"));
        List<AbstractInsnNode> code = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
        long installed = code.stream().filter(instruction -> instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
            && call.owner.equals(BRIDGE) && call.name.equals("registerVanilla") && call.desc.equals(HELPER)).count();
        String map = mappings.classFromSrg(IDS);
        String field = mappings.fieldFromSrg(NAMESPACED, "field_148759_a", "L" + IDS + ";");
        String putId = mappings.methodFromSrg(IDS, "func_148746_a", "(Ljava/lang/Object;I)V");
        String put = mappings.methodFromSrg(SIMPLE, "func_82595_a", "(Ljava/lang/Object;Ljava/lang/Object;)V");
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i + 8 < code.size(); i++) {
            if (load(code.get(i), Opcodes.ALOAD, 0) && code.get(i + 1) instanceof FieldInsnNode lookup
                && lookup.getOpcode() == Opcodes.GETFIELD && List.of(FML, names.defaulted, names.namespaced).contains(lookup.owner)
                && lookup.name.equals(field) && lookup.desc.equals("L" + map + ";")
                && load(code.get(i + 2), Opcodes.ALOAD, 3) && load(code.get(i + 3), Opcodes.ILOAD, 1)
                && call(code.get(i + 4), Opcodes.INVOKEVIRTUAL, map, putId, "(Ljava/lang/Object;I)V")
                && load(code.get(i + 5), Opcodes.ALOAD, 0) && load(code.get(i + 6), Opcodes.ALOAD, 2)
                && load(code.get(i + 7), Opcodes.ALOAD, 3)
                && call(code.get(i + 8), Opcodes.INVOKESPECIAL, names.defaulted, put, "(Ljava/lang/Object;Ljava/lang/Object;)V")) matches.add(i);
        }
        if (matches.isEmpty() && installed == 1) { configured = names; return; }
        if (matches.size() != 1 || installed != 0) throw unsupported("raw registration instruction pattern");
        int start = matches.get(0);
        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0)); replacement.add(new VarInsnNode(Opcodes.ILOAD, 1));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 2)); replacement.add(new VarInsnNode(Opcodes.ALOAD, 3));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "registerVanilla", HELPER, false));
        method.instructions.insertBefore(code.get(start), replacement);
        for (int i = start; i < start + 9; i++) method.instructions.remove(code.get(i));
        method.maxStack = Math.max(4, method.maxStack);
        configured = names;
    }

    private static Names configuration() {
        Names names = configured;
        if (names == null) throw new IllegalStateException("Native registry compatibility names are not configured");
        return names;
    }
    private static boolean load(AbstractInsnNode instruction, int opcode, int variable) { return instruction instanceof VarInsnNode load && load.getOpcode() == opcode && load.var == variable; }
    private static boolean call(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) { return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor) && !call.itf; }
    private static IllegalStateException unsupported(String detail) { return new IllegalStateException("Unsupported Forge registry bytecode: " + detail); }
}
