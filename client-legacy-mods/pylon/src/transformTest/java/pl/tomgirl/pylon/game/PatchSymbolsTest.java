// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package pl.tomgirl.pylon.game;

import java.util.Arrays;
import java.util.Random;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class PatchSymbolsTest {
    private static int checked;
    private static int changed;

    private static byte[] sample(String owner, String marker, int mode, int version) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(version, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        String descriptor = mode == 2 ? "()Ljava/lang/String;" : mode == 3 ? "(Ljava/lang/String;)V" :
                mode == 4 ? "(Ljava/net/URI;)V" :
                mode == 5 ? "()L" + owner.substring(0, owner.lastIndexOf('/') + 1) + "discovery/ModDiscoverer;" : "()V";
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | (mode == 5 ? 0 : Opcodes.ACC_STATIC),
                mode == 5 ? "identifyMods" : "sample", descriptor, null, null);
        method.visitCode();
        if (mode == 1) {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "create", "()V", false);
            method.visitLdcInsn("sample");
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/opengl/Display", "setTitle", "(Ljava/lang/String;)V", false);
        } else if (mode == 2 || mode == 3) {
            method.visitInsn(Opcodes.ACONST_NULL);
            method.visitInsn(Opcodes.ACONST_NULL);
            if (mode == 2) {
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/awt/datatransfer/Clipboard", "getContents",
                        "(Ljava/lang/Object;)Ljava/awt/datatransfer/Transferable;", false);
                method.visitInsn(Opcodes.POP);
                method.visitLdcInsn("");
            } else {
                method.visitInsn(Opcodes.ACONST_NULL);
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/awt/datatransfer/Clipboard", "setContents",
                        "(Ljava/awt/datatransfer/Transferable;Ljava/awt/datatransfer/ClipboardOwner;)V", false);
            }
        } else {
            method.visitLdcInsn(mode == 4 ? "java.awt.Desktop" : marker);
            method.visitInsn(Opcodes.POP);
            if (mode == 5) method.visitInsn(Opcodes.ACONST_NULL);
        }
        method.visitInsn(mode == 2 || mode == 5 ? Opcodes.ARETURN : Opcodes.RETURN);
        method.visitMaxs(3, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void check(byte[] input) {
        byte[] original = ReferenceTransformer.transform(input);
        byte[] actual = GameTransformer.transform(input);
        if (!Arrays.equals(original, actual)) throw new AssertionError("Transformer output changed");
        if (!Arrays.equals(original, input)) {
            changed++;
            if (!PatchSymbols.mayNeedPatches(input)) throw new AssertionError("Patch was skipped");
        }
        checked++;
    }

    public static void main(String[] args) {
        for (String marker : new String[]{"", "org/lwjgl/opengl/Display", "java/awt/datatransfer/Clipboard",
                "java.awt.Desktop", "net/minecraftforge/fml/common/Loader", "cpw/mods/fml/common/Loader"}) {
            check(sample("example/Unrelated", marker, 0, Opcodes.V1_8));
        }
        check(sample("example/DisplayCalls", "", 1, Opcodes.V1_8));
        check(sample("example/ClipboardGet", "", 2, Opcodes.V1_8));
        check(sample("example/ClipboardSet", "", 3, Opcodes.V1_8));
        check(sample("example/Desktop", "", 4, Opcodes.V1_8));
        check(sample("net/minecraftforge/fml/common/Loader", "", 5, Opcodes.V1_8));
        check(sample("cpw/mods/fml/common/Loader", "", 5, Opcodes.V1_8));
        Random random = new Random(29072026);
        for (int index = 0; index < 2000; index++) {
            String marker = Long.toHexString(random.nextLong()) + "\u0000\u00e9\u3042";
            check(sample("example/C" + index, marker, 0, 52 + index % 18));
        }
        byte[] ordinary = sample("example/Ordinary", "safe", 0, Opcodes.V1_8);
        if (PatchSymbols.mayNeedPatches(ordinary)) throw new AssertionError("Ordinary classes are still scanned");
        for (int size = 0; size < 40; size++) {
            if (!PatchSymbols.mayNeedPatches(Arrays.copyOf(ordinary, size))) throw new AssertionError("Truncated pool was accepted");
        }
        byte[] unknown = ordinary.clone(); unknown[10] = (byte) 255;
        if (!PatchSymbols.mayNeedPatches(unknown)) throw new AssertionError("Unknown pool tag was accepted");
        byte[] future = ordinary.clone(); future[7] = 70;
        if (!PatchSymbols.mayNeedPatches(future)) throw new AssertionError("Future version was accepted");
        if (!PatchSymbols.mayNeedPatches(null)) throw new AssertionError("Null was accepted");
        if (changed != 6) throw new AssertionError("Incomplete patch coverage: " + changed);
        System.out.println("Pylon transformer equivalence: " + checked + " valid classes, " + changed + " patched, malformed pools delegated");
    }
}
