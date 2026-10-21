// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.tool;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public final class KotlinMappingAudit {
    public static void main(String[] args) throws Exception {
        Map<String, Integer> versions = new TreeMap<>(), minecraft = new TreeMap<>();
        var unmapped = new java.util.ArrayList<String>();
        try (ZipFile jar = new ZipFile(args[0])) {
            for (var entry : java.util.Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                new ClassReader(jar.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (!descriptor.equals("Lkotlin/Metadata;")) return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            String version = "unknown";
                            boolean mentionsMinecraft, mentionsUnmapped;
                            @Override public void visit(String name, Object value) {
                                if (name.equals("mv") && value instanceof int[] numbers) version = numbers[0] + "." + numbers[1];
                            }
                            @Override public AnnotationVisitor visitArray(String name) {
                                if (!name.equals("d2")) return null;
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override public void visit(String ignored, Object value) {
                                        String text = value.toString();
                                        mentionsMinecraft |= text.contains("net/minecraft/");
                                        mentionsUnmapped |= text.contains("net/minecraft/unmapped/");
                                    }
                                };
                            }
                            @Override public void visitEnd() {
                                versions.merge(version, 1, Integer::sum);
                                if (mentionsMinecraft) minecraft.merge(version, 1, Integer::sum);
                                if (mentionsUnmapped) unmapped.add(entry.getName());
                            }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        var result = Map.of("kotlinMetadataVersions", versions, "minecraftTypesByVersion", minecraft, "intermediaryMetadataClasses", unmapped);
        Files.writeString(Path.of(args[1]), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        if (args.length > 2 && args[2].equals("--require-forge") && !unmapped.isEmpty())
            throw new IllegalStateException("Kotlin metadata still refers to intermediary Minecraft classes: " + unmapped);
        System.out.println("Kotlin Minecraft metadata: " + minecraft + "; intermediary references: " + unmapped.size());
    }
}
