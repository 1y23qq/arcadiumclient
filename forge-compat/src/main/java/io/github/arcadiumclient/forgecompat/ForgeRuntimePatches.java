package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.List;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class ForgeRuntimePatches implements Consumer<ClassNode> {
    private static final String CONTEXT = "io/github/arcadiumclient/forgecompat/ForgeRuntimeContext";
    private static final String REFLECTION = "io/github/arcadiumclient/forgecompat/reflect/ForgeReflection";
    private static final String CALLING_STACK = "net/minecraftforge/fml/common/LoadController$FMLSecurityManager";
    private static final String JAVA_REFLECTION = "io/github/arcadiumclient/forgecompat/ForgeJava25Reflection";

    @Override
    public void accept(ClassNode node) {
        if (node.name.equals("net/minecraftforge/fml/common/registry/ObjectHolderRef")
                || node.name.equals("net/minecraftforge/fml/common/registry/ItemStackHolderRef")) patchHolderReflection(node);
        if (node.name.equals("net/minecraftforge/common/util/EnumHelper")) patchEnumReflection(node);
        if (node.name.equals("net/minecraftforge/fml/common/asm/transformers/EventSubscriptionTransformer")) {
            patchEventSuperclassType(node);
            patchEventParentLoading(node);
        }
        if (node.name.equals(CALLING_STACK)) {
            node.superName = "java/lang/Object";
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/SecurityManager")
                            && call.name.equals("<init>")) call.owner = "java/lang/Object";
                }
            }
            MethodNode context = new MethodNode(Opcodes.ACC_PROTECTED, "getClassContext", "()[Ljava/lang/Class;", null, null);
            context.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, REFLECTION, "callingContext", "()[Ljava/lang/Class;", false));
            context.instructions.add(new InsnNode(Opcodes.ARETURN));
            context.maxStack = 1;
            context.maxLocals = 1;
            node.methods.add(context);
        }
        for (MethodNode method : node.methods) {
            if (node.name.equals("net/minecraftforge/fml/common/asm/transformers/deobf/FMLDeobfuscatingRemapper")
                    && method.name.equals("getFieldType") && method.desc.equals("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/minecraftforge/fml/common/patcher/ClassPatchManager") && call.name.equals("getPatchedResource")) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = CONTEXT;
                        call.name = "readOfficialClass";
                        call.desc = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/launchwrapper/LaunchClassLoader;)[B";
                    }
                }
            }
            if (node.name.equals("net/minecraftforge/fml/common/asm/transformers/ModAccessTransformer") && method.name.equals("<init>")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode call
                            && ((call.owner.equals("java/lang/Class") && call.name.equals("forName"))
                                || (call.owner.equals(REFLECTION) && call.name.equals("forName")))
                            && call.desc.equals("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;")) {
                        InsnList parent = new InsnList();
                        parent.add(new InsnNode(Opcodes.POP));
                        parent.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "hostClassLoader", "()Ljava/lang/ClassLoader;", false));
                        method.instructions.insertBefore(call, parent);
                    }
                }
            }
            if (node.name.equals("net/minecraftforge/fml/common/asm/ASMTransformerWrapper")
                    && method.name.equals("getTransformerWrapper")
                    && method.desc.equals("(Lnet/minecraft/launchwrapper/LaunchClassLoader;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")) {
                replace(method, new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 1),
                    new VarInsnNode(Opcodes.ALOAD, 2), new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT,
                        "createTransformerWrapper", method.desc, false), new InsnNode(Opcodes.ARETURN));
                method.maxStack = 3;
            }
            if (node.name.equals("net/minecraftforge/fml/common/launcher/FMLTweaker")
                    && method.name.equals("<init>") && method.desc.equals("()V")) {
                replace(method, new VarInsnNode(Opcodes.ALOAD, 0),
                    new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false),
                    new LdcInsnNode("java.net.preferIPv4Stack"), new LdcInsnNode("true"),
                    new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "setProperty", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false),
                    new InsnNode(Opcodes.POP),
                    new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "verifyRuntimeSource", "()V", false),
                    new InsnNode(Opcodes.RETURN));
            }
            if (node.name.startsWith("net/minecraftforge/") && node.superName.equals("java/lang/ClassLoader")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/ClassLoader")
                            && call.name.equals("<init>") && call.desc.equals("()V")) {
                        method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                CONTEXT, "hostClassLoader", "()Ljava/lang/ClassLoader;", false));
                        call.desc = "(Ljava/lang/ClassLoader;)V";
                        method.maxStack = Math.max(method.maxStack, 2);
                    }
                }
            }
            if (node.name.equals("net/minecraftforge/fml/relauncher/FMLLaunchHandler") && method.name.equals("setupHome")) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call
                            && call.owner.equals("net/minecraftforge/fml/relauncher/CoreModManager") && call.name.equals("handleLaunch")) {
                        call.owner = CONTEXT;
                        call.desc = "(Ljava/io/File;Lnet/minecraft/launchwrapper/LaunchClassLoader;Ljava/lang/Object;)V";
                    }
                }
            }
            if (node.name.equals("net/minecraftforge/fml/common/ModClassLoader")) {
                if (method.name.equals("getParentSources")) {
                    replace(method, new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "parentSources", "()[Ljava/io/File;", false),
                            new InsnNode(Opcodes.ARETURN));
                } else if (method.name.equals("<init>")) {
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode type && type.getOpcode() == Opcodes.CHECKCAST
                                && type.desc.equals("net/minecraft/launchwrapper/LaunchClassLoader")) {
                            method.instructions.set(type, new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "legacyClassLoader",
                                    "(Ljava/lang/ClassLoader;)Lnet/minecraft/launchwrapper/LaunchClassLoader;", false));
                        }
                    }
                }
            }
            if (node.name.equals("net/minecraftforge/fml/common/Loader") && method.name.equals("initializeLoader")) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() == Opcodes.RETURN) {
                        InsnList hook = new InsnList();
                        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "configureLoader", "(Ljava/lang/Object;)V", false));
                        method.instructions.insertBefore(instruction, hook);
                        method.maxStack = Math.max(method.maxStack, 1);
                    }
                }
            }
            if (node.name.equals("net/minecraftforge/fml/common/patcher/ClassPatchManager") && method.name.equals("setup")) {
                replace(method, new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "requirePreparedGame", "()V", false),
                        new InsnNode(Opcodes.RETURN));
            }
            if (node.name.equals("net/minecraftforge/fml/common/asm/FMLSanityChecker") && method.name.equals("call")
                    && method.desc.equals("()Ljava/lang/Void;")) {
                replace(method, new MethodInsnNode(Opcodes.INVOKESTATIC, CONTEXT, "verifyRuntimeSource", "()V", false),
                        new InsnNode(Opcodes.ACONST_NULL), new InsnNode(Opcodes.ARETURN));
            }
        }
    }

    public static Consumer<ClassNode> forMod(String sha256, ForgeMappings mappings) {
        Consumer<ClassNode> common = forMod(sha256);
        Consumer<ClassNode> codeChicken = CodeChickenRuntimePatches.forMod(sha256, mappings);
        Consumer<ClassNode> combined = common == null ? codeChicken : codeChicken == null ? common : common.andThen(codeChicken);
        Consumer<ClassNode> spawner = NeiSpawnerCompatibility.forMod(sha256, mappings);
        return combined == null ? spawner : spawner == null ? combined : combined.andThen(spawner);
    }

    public static Consumer<ClassNode> forMod(String sha256) {
        if (sha256.equals("10eda9be9979f33a0f7f2e6d211ff4e7f332269d122e76e2cd14457b9943c9b3")) {
            return node -> {
                if (!node.name.equals("codechicken/core/asm/DelegatedTransformer")) return;
                for (MethodNode method : node.methods) {
                    if (method.name.equals("defineClass") && method.desc.equals("(Ljava/lang/String;[B)Ljava/lang/Class;")) {
                        replace(method,
                                new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/launchwrapper/Launch", "classLoader", "Lnet/minecraft/launchwrapper/LaunchClassLoader;"),
                                new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 1),
                                new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/launchwrapper/LaunchClassLoader", "defineLegacyHelperClass",
                                        "(Ljava/lang/String;[B)Ljava/lang/Class;", false), new InsnNode(Opcodes.ARETURN));
                        method.maxStack = 3;
                    }
                }
            };
        }
        if (!sha256.equals("24d180953eed3491801cb02337e63b6edd2550f2fafcac9cd3aa16bfa9354f46")) return null;
        return node -> {
            if (node.visibleAnnotations == null) return;
            for (AnnotationNode annotation : node.visibleAnnotations) {
                if (!annotation.desc.equals("Lnet/minecraftforge/fml/common/Mod;") || annotation.values == null) continue;
                for (int index = 0; index < annotation.values.size(); index += 2) {
                    if (annotation.values.get(index).equals("acceptedMinecraftVersions")) {
                        annotation.values.set(index + 1, "[1.8.8,1.8.9]");
                    }
                }
            }
        };
    }

    private static void replace(MethodNode method, AbstractInsnNode... code) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        for (AbstractInsnNode instruction : code) method.instructions.add(instruction);
        method.maxStack = 2;
    }

    private static void patchEventSuperclassType(ClassNode node) {
        MethodInsnNode superclassType = null;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("buildEvents") || !method.desc.equals("(Lorg/objectweb/asm/tree/ClassNode;)Z")) continue;
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !call.owner.equals("org/objectweb/asm/Type") || !(call.name.equals("getType") || call.name.equals("getObjectType"))
                        || !call.desc.equals("(Ljava/lang/String;)Lorg/objectweb/asm/Type;")) continue;
                AbstractInsnNode input = call.getPrevious();
                while (input != null && input.getOpcode() < 0) input = input.getPrevious();
                if (!(input instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                        || !field.owner.equals("org/objectweb/asm/tree/ClassNode") || !field.name.equals("superName")
                        || !field.desc.equals("Ljava/lang/String;")) continue;
                if (superclassType != null) throw new IllegalStateException("The Forge event transformer has multiple superclass type conversions.");
                superclassType = call;
            }
        }
        if (superclassType == null) throw new IllegalStateException("The Forge event transformer superclass type conversion is missing.");
        superclassType.name = "getObjectType";
    }

    private static void patchHolderReflection(ClassNode node) {
        MethodNode writable = node.methods.stream().filter(method -> method.name.equals("makeWritable")
            && method.desc.equals("(Ljava/lang/reflect/Field;)V")).findFirst().orElseThrow(() -> new IllegalStateException("The Forge holder field preparation method is missing."));
        MethodNode apply = node.methods.stream().filter(method -> method.name.equals("apply") && method.desc.equals("()V"))
            .findFirst().orElseThrow(() -> new IllegalStateException("The Forge holder application method is missing."));
        if (java.util.Arrays.stream(apply.instructions.toArray()).anyMatch(instruction -> instruction instanceof MethodInsnNode call
                && call.owner.equals(JAVA_REFLECTION) && call.name.equals("writeHolder"))) return;
        AbstractInsnNode first = null, last = null;
        int accessorCount = 0, writerCount = 0;
        for (AbstractInsnNode instruction : apply.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(node.name)) {
                if (field.name.equals("newFieldAccessor")) { first = instruction; accessorCount++; }
                if (field.name.equals("fieldAccessorSet")) writerCount++;
            }
            if (first != null && instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/reflect/Method")
                    && call.name.equals("invoke") && writerCount == 1) {
                last = call.getNext();
                while (last != null && last.getOpcode() < 0) last = last.getNext();
                break;
            }
        }
        if (accessorCount != 1 || writerCount != 1 || last == null || last.getOpcode() != Opcodes.POP) {
            throw new IllegalStateException("The trusted Forge holder reflection write does not match the expected bytecode.");
        }
        InsnList write = new InsnList();
        write.add(new VarInsnNode(Opcodes.ALOAD, 0));
        write.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "field", "Ljava/lang/reflect/Field;"));
        write.add(new VarInsnNode(Opcodes.ALOAD, 1));
        write.add(new MethodInsnNode(Opcodes.INVOKESTATIC, JAVA_REFLECTION, "writeHolder", "(Ljava/lang/reflect/Field;Ljava/lang/Object;)V", false));
        apply.instructions.insertBefore(first, write);
        AbstractInsnNode after = last.getNext();
        for (AbstractInsnNode instruction = first; instruction != after;) {
            AbstractInsnNode next = instruction.getNext(); apply.instructions.remove(instruction); instruction = next;
        }
        replace(writable, new VarInsnNode(Opcodes.ALOAD, 0), new MethodInsnNode(Opcodes.INVOKESTATIC,
            JAVA_REFLECTION, "prepareHolder", "(Ljava/lang/reflect/Field;)V", false), new InsnNode(Opcodes.RETURN));
        writable.maxStack = 1;
    }

    private static void patchEventParentLoading(ClassNode node) {
        String metadata = "io/github/arcadiumclient/forgecompat/ForgeEventMetadata";
        MethodNode build = node.methods.stream().filter(method -> method.name.equals("buildEvents")
            && method.desc.equals("(Lorg/objectweb/asm/tree/ClassNode;)Z")).findFirst().orElseThrow();
        MethodInsnNode load = null;
        for (AbstractInsnNode instruction : build.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(metadata) && call.name.equals("isEventSuperclass")) return;
            if (call.owner.equals("java/lang/ClassLoader") && call.name.equals("loadClass")
                    && call.desc.equals("(Ljava/lang/String;)Ljava/lang/Class;")) {
                if (load != null) throw new IllegalStateException("The trusted Forge event transformer loads multiple parent classes.");
                load = call;
            }
        }
        if (load == null) throw new IllegalStateException("The trusted Forge event parent classification is missing.");
        AbstractInsnNode store = nextInstruction(load), literal = nextInstruction(store), argument = nextInstruction(literal), predicate = nextInstruction(argument);
        if (!(store instanceof VarInsnNode variable) || variable.getOpcode() != Opcodes.ASTORE || variable.var != 2
                || !(literal instanceof LdcInsnNode constant) || !(constant.cst instanceof org.objectweb.asm.Type type)
                || !type.getInternalName().equals("net/minecraftforge/fml/common/eventhandler/Event")
                || !(argument instanceof VarInsnNode value) || value.getOpcode() != Opcodes.ALOAD || value.var != 2
                || !(predicate instanceof MethodInsnNode assignable) || assignable.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !assignable.owner.equals("java/lang/Class") || !assignable.name.equals("isAssignableFrom")
                || !assignable.desc.equals("(Ljava/lang/Class;)Z")) {
            throw new IllegalStateException("The trusted Forge event parent classification does not match the expected bytecode.");
        }
        load.setOpcode(Opcodes.INVOKESTATIC); load.owner = metadata; load.name = "isEventSuperclass";
        load.desc = "(Ljava/lang/ClassLoader;Ljava/lang/String;)Z"; load.itf = false;
        build.instructions.insertBefore(store, new InsnNode(Opcodes.ACONST_NULL));
        build.instructions.remove(literal); build.instructions.remove(argument); build.instructions.remove(predicate);
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static void patchEnumReflection(ClassNode node) {
        int changes = 0;
        for (MethodNode method : node.methods) {
            if (method.name.equals("setup") && method.desc.equals("()V")) {
                replace(method, new MethodInsnNode(Opcodes.INVOKESTATIC, JAVA_REFLECTION, "initialize", "()V", false),
                    new InsnNode(Opcodes.ICONST_1), new FieldInsnNode(Opcodes.PUTSTATIC, node.name, "isSetup", "Z"), new InsnNode(Opcodes.RETURN));
                changes++;
            } else if (method.name.equals("makeEnum") && method.desc.equals("(Ljava/lang/Class;Ljava/lang/String;I[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Enum;")) {
                replace(method, new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ILOAD, 2),
                    new VarInsnNode(Opcodes.ALOAD, 3), new VarInsnNode(Opcodes.ALOAD, 4),
                    new MethodInsnNode(Opcodes.INVOKESTATIC, JAVA_REFLECTION, "makeEnum", method.desc, false), new InsnNode(Opcodes.ARETURN));
                method.maxStack = 5; changes++;
            } else if (method.name.equals("setFailsafeFieldValue") && method.desc.equals("(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V")) {
                replace(method, new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 1), new VarInsnNode(Opcodes.ALOAD, 2),
                    new MethodInsnNode(Opcodes.INVOKESTATIC, JAVA_REFLECTION, "setFailsafeFieldValue", method.desc, false), new InsnNode(Opcodes.RETURN));
                method.maxStack = 3; changes++;
            } else if (method.name.equals("cleanEnumCache") && method.desc.equals("(Ljava/lang/Class;)V")) {
                replace(method, new VarInsnNode(Opcodes.ALOAD, 0), new MethodInsnNode(Opcodes.INVOKESTATIC,
                    JAVA_REFLECTION, "cleanEnumCache", method.desc, false), new InsnNode(Opcodes.RETURN));
                changes++;
            }
        }
        if (changes != 4) throw new IllegalStateException("The trusted Forge enum reflection methods are missing or ambiguous.");
    }
}
