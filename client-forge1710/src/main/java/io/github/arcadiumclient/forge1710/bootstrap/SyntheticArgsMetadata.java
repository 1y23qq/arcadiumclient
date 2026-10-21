// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.bootstrap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public final class SyntheticArgsMetadata {
    private static final Map<String, ClassNode> TYPES = new ConcurrentHashMap<>();

    public static ClassNode node(String name) {
        if (name == null) return null;
        String normalized = name.replace('.', '/');
        String prefix = "org/spongepowered/asm/synthetic/args/Args$";
        if (!normalized.startsWith(prefix) || !normalized.substring(prefix.length()).matches("[0-9]+")) return null;
        return TYPES.computeIfAbsent(normalized, key -> {
            ClassNode node = new ClassNode(Opcodes.ASM9);
            node.version = Opcodes.V1_8;
            node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
            node.name = key;
            node.superName = "org/spongepowered/asm/mixin/injection/invoke/arg/Args";
            return node;
        });
    }
}
