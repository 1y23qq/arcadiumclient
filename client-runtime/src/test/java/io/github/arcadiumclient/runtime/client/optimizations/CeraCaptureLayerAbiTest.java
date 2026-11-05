// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class CeraCaptureLayerAbiTest {
    @Test void pinnedLayerBoundaryClearsModelActiveWithTheFirstZeroConstant() throws Exception {
        String jar = System.getProperty("arcadium.voxelSkin072.argentumJar");
        assumeTrue(jar != null, "Run with the pinned Argentum jar");
        List<String> firstZeroFields = new ArrayList<>(); boolean[] layerEntry = {false};
        try (ZipFile zip = new ZipFile(jar)) {
            var entry = zip.getEntry("dev/rdh/argentum/impl/render/entity/instancing/EntityCapture.class"); assertNotNull(entry);
            new ClassReader(zip.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("beginLayer") && descriptor.endsWith(")Z")) layerEntry[0] = true;
                    if (!name.equals("endLayer") || !descriptor.equals("()V")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        boolean seenZero, recorded;
                        @Override public void visitInsn(int opcode) { if (opcode == Opcodes.ICONST_0 && !recorded) seenZero = true; }
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String type) {
                            if (seenZero && !recorded) { firstZeroFields.add(opcode + ":" + field + ":" + type); recorded = true; }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(layerEntry[0]); assertEquals(List.of(Opcodes.PUTFIELD + ":modelActive:Z"), firstZeroFields);
    }
}
