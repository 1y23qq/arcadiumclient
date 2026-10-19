package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

final class HostApiReferenceCompatibility {
    private HostApiReferenceCompatibility() { }

    static void normalize(ClassNode node, ForgeMappings mappings) {
        if (!node.name.startsWith("net/minecraft/")) return;
        // Fabric cannot resolve inherited vanilla members on Forge-owned references.
        for (var method : node.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                call.name = methodName(call.owner, call.name, call.desc, mappings);
            } else if (instruction instanceof FieldInsnNode field) {
                field.name = fieldName(field.owner, field.name, field.desc, mappings);
            } else if (instruction instanceof LdcInsnNode constant) {
                constant.cst = constant(constant.cst, mappings);
            } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                dynamic.bsm = (Handle) constant(dynamic.bsm, mappings);
                for (int i = 0; i < dynamic.bsmArgs.length; i++) dynamic.bsmArgs[i] = constant(dynamic.bsmArgs[i], mappings);
            }
        }
    }

    private static String methodName(String owner, String name, String descriptor, ForgeMappings mappings) {
        if (!owner.startsWith("net/minecraftforge/")) return name;
        return mappings.officialToRuntime().mapMethodName(owner, name, mappings.descriptorToOfficial(descriptor));
    }

    private static String fieldName(String owner, String name, String descriptor, ForgeMappings mappings) {
        if (!owner.startsWith("net/minecraftforge/")) return name;
        return mappings.officialToRuntime().mapFieldName(owner, name, mappings.descriptorToOfficial(descriptor));
    }

    private static Object constant(Object value, ForgeMappings mappings) {
        if (value instanceof Handle handle) {
            String name = handle.getTag() <= Opcodes.H_PUTSTATIC
                ? fieldName(handle.getOwner(), handle.getName(), handle.getDesc(), mappings)
                : methodName(handle.getOwner(), handle.getName(), handle.getDesc(), mappings);
            if (name.equals(handle.getName())) return handle;
            return new Handle(handle.getTag(), handle.getOwner(), name, handle.getDesc(), handle.isInterface());
        }
        if (value instanceof ConstantDynamic dynamic) {
            Object[] arguments = new Object[dynamic.getBootstrapMethodArgumentCount()];
            for (int i = 0; i < arguments.length; i++) arguments[i] = constant(dynamic.getBootstrapMethodArgument(i), mappings);
            return new ConstantDynamic(dynamic.getName(), dynamic.getDescriptor(), (Handle) constant(dynamic.getBootstrapMethod(), mappings), arguments);
        }
        return value;
    }
}
