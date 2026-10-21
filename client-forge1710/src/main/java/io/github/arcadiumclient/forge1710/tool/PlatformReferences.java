// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.tool;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Handle;

public final class PlatformReferences {
    public static void main(String[] args) throws Exception {
        Set<String> references = new TreeSet<>();
        for (String argument : args) {
            try (ZipFile jar = new ZipFile(Path.of(argument).toFile())) {
                for (var entry : java.util.Collections.list(jar.entries())) {
                    if (!entry.getName().endsWith(".class")) continue;
                    new ClassReader(jar.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                                    for (Object argument : arguments) if (argument instanceof Handle handle)
                                        visitMethodInsn(Opcodes.INVOKESTATIC, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
                                }
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                                    if (owner.startsWith("net/fabricmc/") || owner.startsWith("net/ornithemc/") || owner.startsWith("pl/tomgirl/"))
                                        references.add(owner + "." + method + desc);
                                }
                                @Override
                                public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                                    if (owner.startsWith("net/fabricmc/") || owner.startsWith("net/ornithemc/") || owner.startsWith("pl/tomgirl/"))
                                        references.add(owner + "." + field + " " + desc);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        references.forEach(System.out::println);
    }
}
