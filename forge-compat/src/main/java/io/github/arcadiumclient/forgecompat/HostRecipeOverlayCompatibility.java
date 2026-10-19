package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.util.Arrays;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class HostRecipeOverlayCompatibility {
    private static final String OVERLAY = "mezz/jei/gui/ItemListOverlay";
    private static final String PROPERTIES = "mezz/jei/gui/GuiProperties";
    private static final String HELPER = "io/github/arcadiumclient/forgecompat/HostRecipeOverlayCompatibility";
    private static final String RECIPE = "codechicken.nei.recipe.GuiRecipe";
    private static final ClassValue<Boolean> NEI_RECIPES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) { return recipeType(type, RECIPE); }
    };

    private HostRecipeOverlayCompatibility() { }

    public static boolean ownsRecipeScreen(Object screen) {
        return screen != null && NEI_RECIPES.get(screen.getClass());
    }

    static boolean recipeType(Class<?> type, String recipeName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (current.getName().equals(recipeName)) return true;
        }
        return false;
    }

    public static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.equals(OVERLAY)) return;
        String screen = mappings.classFromSrg("net/minecraft/client/gui/GuiScreen");
        String descriptor = "(L" + screen + ";)V";
        ClassNode copy = new ClassNode(); node.accept(copy);
        MethodNode close = method(copy, "close", "()V");
        if (!instance(close) || (close.access & Opcodes.ACC_PUBLIC) == 0) throw unsupported("close callback");
        for (String name : List.of("initGui", "updateGui")) {
            MethodNode method = method(copy, name, descriptor);
            if (!instance(method)) throw unsupported(name + " descriptor");
            List<AbstractInsnNode> code = Arrays.stream(method.instructions.toArray()).filter(instruction -> instruction.getOpcode() >= 0).toList();
            if (installed(code)) continue;
            if (name.equals("initGui")) {
                if (code.size() < 3 || !load(code.get(0), 1) || !(code.get(1) instanceof MethodInsnNode create)
                    || create.getOpcode() != Opcodes.INVOKESTATIC || !create.owner.equals(PROPERTIES) || !create.name.equals("create")
                    || !create.desc.equals("(L" + screen + ";)L" + PROPERTIES + ";")
                    || !(code.get(2) instanceof VarInsnNode local) || local.getOpcode() != Opcodes.ASTORE || local.var != 2) throw unsupported("initGui body");
            } else if (code.size() < 3 || !load(code.get(0), 0) || !(code.get(1) instanceof FieldInsnNode properties)
                || properties.getOpcode() != Opcodes.GETFIELD || !properties.owner.equals(OVERLAY) || !properties.name.equals("guiProperties")
                || !properties.desc.equals("L" + PROPERTIES + ";") || !(code.get(2) instanceof JumpInsnNode branch)
                || branch.getOpcode() != Opcodes.IFNONNULL) throw unsupported("updateGui body");
            LabelNode original = new LabelNode(); InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "ownsRecipeScreen", "(Ljava/lang/Object;)Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, original));
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OVERLAY, "close", "()V", false));
            guard.add(new InsnNode(Opcodes.RETURN)); guard.add(original);
            guard.add(new FrameNode(Opcodes.F_FULL, 2, new Object[] {OVERLAY, screen}, 0, null));
            method.instructions.insertBefore(method.instructions.getFirst(), guard);
            method.maxStack = Math.max(1, method.maxStack);
        }
        node.methods = copy.methods;
    }

    private static boolean installed(List<AbstractInsnNode> code) {
        return code.size() >= 6 && load(code.get(0), 1) && code.get(1) instanceof MethodInsnNode predicate
            && predicate.getOpcode() == Opcodes.INVOKESTATIC && predicate.owner.equals(HELPER) && predicate.name.equals("ownsRecipeScreen")
            && predicate.desc.equals("(Ljava/lang/Object;)Z") && code.get(2) instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.IFEQ
            && load(code.get(3), 0) && code.get(4) instanceof MethodInsnNode close && close.getOpcode() == Opcodes.INVOKEVIRTUAL
            && close.owner.equals(OVERLAY) && close.name.equals("close") && close.desc.equals("()V") && code.get(5).getOpcode() == Opcodes.RETURN;
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) { return node.methods.stream().filter(candidate -> candidate.name.equals(name) && candidate.desc.equals(descriptor)).findFirst().orElse(null); }
    private static boolean load(AbstractInsnNode instruction, int slot) { return instruction instanceof VarInsnNode local && local.getOpcode() == Opcodes.ALOAD && local.var == slot; }
    private static boolean instance(MethodNode method) { return method != null && (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) == 0; }
    private static IllegalStateException unsupported(String detail) { return new IllegalStateException("Unsupported JEI recipe-overlay interoperability: " + detail); }
}
