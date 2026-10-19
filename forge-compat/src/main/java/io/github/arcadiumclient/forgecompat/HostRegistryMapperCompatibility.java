package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class HostRegistryMapperCompatibility {
    private static final String FML = "net/minecraftforge/fml/common/registry/FMLControlledNamespacedRegistry";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/HostRegistryMapperCompatibility";
    private static final String CLEAR = "osl$registries$clear";
    private static final ThreadLocal<Integer> SNAPSHOT = ThreadLocal.withInitial(() -> 0);
    private record Access(VarHandle available, VarHandle blocked, MethodHandle values, MethodHandle id) { }
    private record Reservations(Access access, BitSet reserved) { }
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> registry) {
            try {
                Class<?> owner = registry;
                while (owner != null && !declares(owner, "availabilityMap")) owner = owner.getSuperclass();
                if (owner == null) throw new NoSuchFieldException("availabilityMap");
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
                return new Access(lookup.findVarHandle(owner, "availabilityMap", BitSet.class), lookup.findVarHandle(owner, "blockedIds", Set.class),
                    lookup.unreflect(owner.getMethod("typeSafeIterable")).asType(MethodType.methodType(Iterable.class, Object.class)),
                    lookup.unreflect(owner.getMethod("getId", Object.class)).asType(MethodType.methodType(int.class, Object.class, Object.class)));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("The Forge registry reservation state could not be linked", failure); }
        }
    };

    private HostRegistryMapperCompatibility() { }

    static void beginSnapshotClear() { SNAPSHOT.set(SNAPSHOT.get() + 1); }
    static void endSnapshotClear() { int depth = SNAPSHOT.get(); if (depth <= 0) throw new IllegalStateException("Unbalanced registry snapshot clear scope"); if (depth == 1) SNAPSHOT.remove(); else SNAPSHOT.set(depth - 1); }

    public static Object beforeClear(Object registry, Class<?> declaringClass) throws Throwable {
        if (SNAPSHOT.get() != 0) return null;
        Access access = ACCESS.get(declaringClass);
        BitSet reserved = (BitSet) ((BitSet) access.available.get(registry)).clone();
        Iterable<?> values = (Iterable<?>) access.values.invokeExact(registry);
        for (Object value : values) { int id = (int) access.id.invokeExact(registry, value); if (id >= 0) reserved.clear(id); }
        Set<?> blocked = (Set<?>) access.blocked.get(registry);
        for (Object entry : blocked) { if (!(entry instanceof Integer id) || id < 0) throw new IllegalStateException("Invalid blocked Forge registry ID"); reserved.set(id); }
        return new Reservations(access, reserved);
    }

    public static void afterClear(Object registry, Object token) {
        if (token == null) return;
        Reservations reservations = (Reservations) token;
        BitSet available = (BitSet) reservations.access.available.get(registry);
        available.clear(); available.or(reservations.reserved);
    }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(FML)) return;
        if (!node.superName.equals(mappings.classFromSrg("net/minecraft/util/RegistryNamespacedDefaultedByKey"))) throw new IllegalStateException("Unsupported Forge registry superclass for OSL remapping");
        MethodNode existing = node.methods.stream().filter(method -> method.name.equals(CLEAR) && method.desc.equals("()V")).findFirst().orElse(null);
        if (existing != null) {
            if (Arrays.stream(existing.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals(BRIDGE) && call.name.equals("afterClear"))) return;
            throw new IllegalStateException("The Forge registry already defines an unsupported OSL clear method");
        }
        MethodNode clear = new MethodNode(Opcodes.ACC_PUBLIC, CLEAR, "()V", null, null);
        clear.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); clear.instructions.add(new LdcInsnNode(Type.getObjectType(FML))); clear.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "beforeClear", "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;", false)); clear.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        clear.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); clear.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.superName, CLEAR, "()V", false));
        clear.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); clear.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); clear.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "afterClear", "(Ljava/lang/Object;Ljava/lang/Object;)V", false)); clear.instructions.add(new InsnNode(Opcodes.RETURN));
        clear.maxLocals = 2; clear.maxStack = 2; node.methods.add(clear);
    }

    private static boolean declares(Class<?> type, String name) { for (Field field : type.getDeclaredFields()) if (field.getName().equals(name)) return true; return false; }
}
