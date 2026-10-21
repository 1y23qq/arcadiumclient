// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.tool;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipFile;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class MinecraftVersionTable {
    public static void main(String[] args) throws Exception {
        var versions = new TreeSet<String>();
        versions.add("1.7.10");
        try (ZipFile jar = new ZipFile(args[0])) {
            for (var entry : java.util.Collections.list(jar.entries())) {
                if (!entry.getName().endsWith(".class")) continue;
                new ClassReader(jar.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitLdcInsn(Object value) {
                                if (value instanceof String text && text.matches("(?:[0-9]{2}w[0-9]{2}[a-z]|[0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:[-+][0-9A-Za-z.-]+)?)")) versions.add(text);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        Map<String, Map<String, String>> table = new TreeMap<>();
        for (String version : versions) {
            String release = McVersionLookup.getRelease(version);
            Map<String, String> record = new TreeMap<>();
            if (release != null) record.put("release", release);
            record.put("normalized", McVersionLookup.normalizeVersion(version, release));
            table.put(version, record);
        }
        Files.writeString(Path.of(args[1]), new Gson().toJson(table));
        System.out.println("Verified Minecraft version boundaries: " + table.size());
    }
}
