// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.tool;

import java.nio.charset.StandardCharsets;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

final class LwjglApiUsage {
    static byte[] apply(String name, byte[] bytes) {
        String constants = new String(bytes, StandardCharsets.ISO_8859_1);
        if (!constants.contains("org/lwjgl/sdl/") && !constants.contains("org/lwjgl/system/") &&
            !constants.contains("org/lwjgl/glfw/") && !constants.contains("org/lwjgl/opengl/GLCapabilities")) return bytes;
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, 0);
        for (var method : node.methods) for (var instruction : method.instructions)
            if (instruction instanceof FieldInsnNode field && field.owner.equals("org/lwjglx/input/Keyboard") && field.name.equals("KEY_PRINT_SCREEN"))
                field.name = "KEY_SYSRQ";
        if (name.toLowerCase(java.util.Locale.ROOT).contains("mixin")) {
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call &&
                    (call.owner.equals("org/lwjgl/sdl/SDLHints") && call.name.equals("SDL_SetHintWithPriority") ||
                     call.owner.equals("org/lwjgl/sdl/SDLMouse") && call.name.equals("SDL_GetMouseState")))
                    call.owner = "io/github/arcadiumclient/forge1710/platform/NativeSdl";
            }
        } else {
            if (node.visibleAnnotations == null) node.visibleAnnotations = new java.util.ArrayList<>();
            String marker = "Lme/eigenraven/lwjgl3ify/api/Lwjgl3Aware;";
            if (node.visibleAnnotations.stream().noneMatch(annotation -> annotation.desc.equals(marker)))
                node.visibleAnnotations.add(new AnnotationNode(marker));
        }
        ClassWriter result = new ClassWriter(0);
        node.accept(result);
        return result.toByteArray();
    }
}
