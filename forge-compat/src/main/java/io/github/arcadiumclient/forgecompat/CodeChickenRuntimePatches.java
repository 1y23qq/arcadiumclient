package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Objects;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class CodeChickenRuntimePatches implements Consumer<ClassNode> {
    private static final String CCL_SHA256 = "d34d7c499e0e89869412dd7a65cb181d3d4fda6aac798302f94b7e8bf1574f4d";
    private static final String OWNER = "codechicken/lib/asm/ObfMapping";
    private static final String OBF = OWNER + "$ObfRemapper";
    private static final String BRIDGE = "io/github/arcadiumclient/forgecompat/CodeChickenRuntimePatches";
    private static final String MEMBER = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
    private static volatile ForgeMappings configured;

    private CodeChickenRuntimePatches(ForgeMappings mappings) { configured = Objects.requireNonNull(mappings, "mappings"); }

    public static Consumer<ClassNode> forMod(String inputSha256, ForgeMappings mappings) {
        return CCL_SHA256.equals(inputSha256) ? new CodeChickenRuntimePatches(mappings) : null;
    }

    public static Remapper runtimeRemapper() { return mappings().srgToRuntime(); }
    public static void requireMappings() { mappings(); }

    public static String legacyClassName(String name) {
        if (name == null) return null;
        ForgeMappings mappings = mappings();
        return mappings.classToSrg(mappings.classFromSrg(name));
    }

    public static String legacyFieldName(String owner, String name, String descriptor) {
        return legacyMember(owner, name, descriptor, false);
    }

    public static String legacyMethodName(String owner, String name, String descriptor) {
        return legacyMember(owner, name, descriptor, true);
    }

    private static String legacyMember(String owner, String name, String descriptor, boolean method) {
        ForgeMappings mappings = mappings();
        // NEI's field injector passes ownerless SRG names.
        if (owner == null || descriptor == null || owner.equals(legacyClassName(owner))) return name;
        String runtimeOwner = mappings.classFromSrg(owner);
        String runtimeName = name;
        String runtimeDescriptor = descriptor;
        if (!runtimeOwner.equals(owner)) {
            Remapper official = mappings.officialToRuntime();
            runtimeName = method ? official.mapMethodName(owner, name, descriptor) : official.mapFieldName(owner, name, descriptor);
            runtimeDescriptor = method ? official.mapMethodDesc(descriptor) : official.mapDesc(descriptor);
        }
        Remapper legacy = mappings.runtimeToSrg();
        return method ? legacy.mapMethodName(runtimeOwner, runtimeName, runtimeDescriptor)
            : legacy.mapFieldName(runtimeOwner, runtimeName, runtimeDescriptor);
    }

    private static ForgeMappings mappings() {
        ForgeMappings mappings = configured;
        if (mappings == null) throw new IllegalStateException("CodeChicken compatibility mappings are not configured");
        return mappings;
    }

    @Override public void accept(ClassNode node) {
        if (node.name.equals(OWNER)) {
            replace(required(node, "<clinit>", "()V"), 2, 0,
                new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "requireMappings", "()V", false),
                new TypeInsnNode(Opcodes.NEW, OBF), new InsnNode(Opcodes.DUP),
                new MethodInsnNode(Opcodes.INVOKESPECIAL, OBF, "<init>", "()V", false),
                new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, "obfMapper", "L" + OBF + ";"),
                new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "runtimeRemapper", "()Lorg/objectweb/asm/commons/Remapper;", false),
                new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, "mcpMapper", "Lorg/objectweb/asm/commons/Remapper;"),
                new InsnNode(Opcodes.ICONST_1), new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, "obfuscated", "Z"),
                new InsnNode(Opcodes.RETURN));
            replace(required(node, "loadMCPRemapper", "()V"), 1, 0,
                new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "runtimeRemapper", "()Lorg/objectweb/asm/commons/Remapper;", false),
                new FieldInsnNode(Opcodes.PUTSTATIC, OWNER, "mcpMapper", "Lorg/objectweb/asm/commons/Remapper;"),
                new InsnNode(Opcodes.RETURN));
        } else if (node.name.equals(OBF)) {
            replace(required(node, "<init>", "()V"), 2, 1,
                new VarInsnNode(Opcodes.ALOAD, 0), new LdcInsnNode(Opcodes.ASM9),
                new MethodInsnNode(Opcodes.INVOKESPECIAL, "org/objectweb/asm/commons/Remapper", "<init>", "(I)V", false),
                new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "requireMappings", "()V", false),
                new InsnNode(Opcodes.RETURN));
            for (String name : new String[]{"map", "unmap"}) {
                replace(required(node, name, "(Ljava/lang/String;)Ljava/lang/String;"), 1, 2,
                    new VarInsnNode(Opcodes.ALOAD, 1),
                    new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "legacyClassName", "(Ljava/lang/String;)Ljava/lang/String;", false),
                    new InsnNode(Opcodes.ARETURN));
            }
            for (String name : new String[]{"mapFieldName", "mapMethodName"}) {
                replace(required(node, name, MEMBER), 3, 4,
                    new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ALOAD, 2), new VarInsnNode(Opcodes.ALOAD, 3),
                    new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE,
                        name.equals("mapFieldName") ? "legacyFieldName" : "legacyMethodName", MEMBER, false),
                    new InsnNode(Opcodes.ARETURN));
            }
        }
    }

    private static MethodNode required(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Audited CodeChickenLib method is missing: " + node.name + "." + name + descriptor));
    }

    private static void replace(MethodNode method, int stack, int locals, AbstractInsnNode... instructions) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        for (AbstractInsnNode instruction : instructions) method.instructions.add(instruction);
        method.maxStack = stack;
        method.maxLocals = locals;
    }
}
