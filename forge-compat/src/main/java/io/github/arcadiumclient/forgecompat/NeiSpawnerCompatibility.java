package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class NeiSpawnerCompatibility implements Consumer<ClassNode> {
    private static final String SHA256 = "1375fee5865235bcd0d95243fa1c95a99c478ef647d2ce486e805b6baa77fa24";
    private static final String OWNER = "codechicken/nei/ItemMobSpawner";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/NeiSpawnerCompatibility";
    private static final String ITEM = "net/minecraft/item/Item";
    private static final String STACK = "net/minecraft/item/ItemStack";
    private static final String ENTITIES = "net/minecraft/entity/EntityList";
    private static final String ENSURE = "(Ljava/lang/Class;Ljava/lang/Object;Ljava/util/Map;Ljava/util/Map;)V";
    private final ForgeMappings mappings;
    private static final ClassValue<Binding> BINDINGS = new ClassValue<>() {
        @Override protected Binding computeValue(Class<?> owner) {
            try { return new Binding(owner); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("The audited NEI spawner API is unavailable.", failure); }
        }
    };

    private NeiSpawnerCompatibility(ForgeMappings mappings) { this.mappings = mappings; }

    public static Consumer<ClassNode> forMod(String sha256, ForgeMappings mappings) {
        return SHA256.equals(sha256) ? new NeiSpawnerCompatibility(mappings) : null;
    }

    @Override public void accept(ClassNode node) {
        if (!node.name.equals(OWNER)) return;
        String entity = mappings.classFromSrg("net/minecraft/entity/EntityLiving");
        MethodNode get = required(node, "getEntity", "(I)L" + entity + ";");
        MethodNode tag = required(node, "setDefaultTag", mappings.descriptorFromSrg("(L" + STACK + ";)V"));
        String subtypes = "(L" + ITEM + ";Lnet/minecraft/creativetab/CreativeTabs;Ljava/util/List;)V";
        MethodNode items = required(node, mappings.methodFromSrg(ITEM, "func_150895_a", subtypes), mappings.descriptorFromSrg(subtypes));
        if (Arrays.stream(get.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(BRIDGE) && call.name.equals("ensureInitialized"))) return;
        MethodInsnNode fallback = null;
        for (AbstractInsnNode instruction : get.instructions) if (instruction instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(OWNER) && call.name.equals("getEntity") && call.desc.equals(get.desc)) {
            if (fallback != null) throw new IllegalStateException("The audited NEI spawner has multiple fallback calls.");
            fallback = call;
        }
        AbstractInsnNode argument = fallback == null ? null : previous(fallback);
        if (!(argument instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETSTATIC || !field.owner.equals(OWNER)
                || !field.name.equals("idPig") || !field.desc.equals("I") || get.maxLocals < 5) {
            throw new IllegalStateException("The audited NEI spawner fallback bytecode changed.");
        }
        InsnList replacement = new InsnList();
        replacement.add(new LdcInsnNode(Type.getObjectType(OWNER)));
        replacement.add(new VarInsnNode(Opcodes.ILOAD, 0));
        replacement.add(new FieldInsnNode(Opcodes.GETSTATIC, OWNER, "idPig", "I"));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 4));
        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "fallback",
            "(Ljava/lang/Class;IILjava/lang/Throwable;)Ljava/lang/Object;", false));
        replacement.add(new TypeInsnNode(Opcodes.CHECKCAST, entity));
        get.instructions.insertBefore(argument, replacement); get.instructions.remove(argument); get.instructions.remove(fallback);
        for (MethodNode method : new MethodNode[]{get,tag,items}) {
            method.instructions.insert(prefix()); method.maxStack = Math.max(method.maxStack, 4);
        }
    }

    private InsnList prefix() {
        InsnList code = new InsnList();
        String minecraft = "net/minecraft/client/Minecraft";
        code.add(new LdcInsnNode(Type.getObjectType(OWNER)));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, mappings.classFromSrg(minecraft),
            mappings.methodFromSrg(minecraft, "func_71410_x", "()L" + minecraft + ";"), mappings.descriptorFromSrg("()L" + minecraft + ";"), false));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, mappings.classFromSrg(minecraft),
            mappings.fieldFromSrg(minecraft, "field_71441_e", "Lnet/minecraft/client/multiplayer/WorldClient;"), mappings.descriptorFromSrg("Lnet/minecraft/client/multiplayer/WorldClient;")));
        for (String field : new String[]{"field_75626_c", "field_75624_e"}) code.add(new FieldInsnNode(Opcodes.GETSTATIC,
            mappings.classFromSrg(ENTITIES), mappings.fieldFromSrg(ENTITIES, field, "Ljava/util/Map;"), "Ljava/util/Map;"));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "ensureInitialized", ENSURE, false));
        return code;
    }

    public static void ensureInitialized(Class<?> owner, Object world, Map<?,?> classNames, Map<?,?> classIds) {
        Binding binding = BINDINGS.get(owner);
        synchronized (binding) {
            if (binding.loading) return;
            Integer pig = null;
            for (var entry : classNames.entrySet()) if ("Pig".equals(entry.getValue()) && classIds.get(entry.getKey()) instanceof Integer id) pig = id;
            if (pig == null) throw new IllegalStateException("The entity registry has not registered NEI's default Pig spawner.");
            if (!(boolean) binding.loaded.get()) {
                if (world == null) throw new IllegalStateException("NEI spawner information requires an active client world.");
                binding.loading = true;
                try { binding.load.invokeExact(world); }
                catch (Throwable failure) { throw propagate("NEI could not initialize its spawner catalog.", failure); }
                finally { binding.loading = false; }
            }
            @SuppressWarnings("unchecked") Map<Integer,String> names = (Map<Integer,String>) binding.names.get();
            if ((int) binding.pig.get() != pig || !"Pig".equals(names.get(pig))) {
                throw new IllegalStateException("NEI could not initialize its default Pig spawner from the entity registry.");
            }
        }
    }

    public static Object fallback(Class<?> owner, int requested, int pig, Throwable cause) {
        if (requested == pig) throw new IllegalStateException("NEI could not construct its default Pig spawner entity.", cause);
        try { return BINDINGS.get(owner).entity.invokeExact(pig); }
        catch (Throwable failure) { throw propagate("NEI could not construct its fallback spawner entity.", failure); }
    }

    private static RuntimeException propagate(String message, Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException(message, failure);
    }

    private static final class Binding {
        final VarHandle loaded, names, pig;
        final MethodHandle load, entity;
        boolean loading;
        Binding(Class<?> owner) throws ReflectiveOperationException {
            var lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
            loaded = lookup.findStaticVarHandle(owner, "loaded", boolean.class);
            names = lookup.findStaticVarHandle(owner, "IDtoNameMap", Map.class);
            pig = lookup.findStaticVarHandle(owner, "idPig", int.class);
            var method = Arrays.stream(owner.getDeclaredMethods()).filter(candidate -> candidate.getName().equals("loadSpawners")
                && candidate.getParameterCount() == 1 && candidate.getReturnType() == void.class).findFirst()
                .orElseThrow(() -> new NoSuchMethodException("loadSpawners(World)"));
            load = lookup.unreflect(method).asType(MethodType.methodType(void.class, Object.class));
            entity = lookup.unreflect(owner.getDeclaredMethod("getEntity", int.class)).asType(MethodType.methodType(Object.class, int.class));
        }
    }

    private static MethodNode required(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst()
            .orElseThrow(() -> new IllegalStateException("An audited NEI spawner method is missing: " + name + descriptor));
    }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }
}
