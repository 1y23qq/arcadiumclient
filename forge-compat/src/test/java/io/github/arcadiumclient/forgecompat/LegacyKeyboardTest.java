package io.github.arcadiumclient.forgecompat;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class LegacyKeyboardTest {
    @Test void preservesExtendedHostKeysAndIgnoresUnboundAndOutOfRangeValues() throws Exception {
        for (int size : new int[] {256, 513}) {
            ClassLoader loader = loader(size);
            Class<?> keyboard = loader.loadClass("org.lwjgl.input.Keyboard");
            var query = loader.loadClass(LegacyKeyboard.class.getName()).getMethod("isKeyDown", int.class);
            for (int key : new int[] {-100, -1, size, Integer.MAX_VALUE}) assertEquals(false, query.invoke(null, key));
            assertEquals(0, keyboard.getField("calls").getInt(null));
            assertEquals(true, query.invoke(null, 54));
            assertEquals(false, query.invoke(null, 55));
            if (size > 320) assertEquals(true, query.invoke(null, 320));
            assertEquals(size > 320 ? 3 : 2, keyboard.getField("calls").getInt(null));
        }
    }

    @Test void propagatesHostLifecycleErrorsRatherThanMaskingFailures() throws Exception {
        ClassLoader loader = loader(256);
        Class<?> keyboard = loader.loadClass("org.lwjgl.input.Keyboard");
        keyboard.getField("failure").setBoolean(null, true);
        var query = loader.loadClass(LegacyKeyboard.class.getName()).getMethod("isKeyDown", int.class);
        var failure = assertThrows(InvocationTargetException.class, () -> query.invoke(null, 54));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("Keyboard not created", failure.getCause().getMessage());
    }

    @Test void showsUnsupportedBindingsAsUnboundWhilePreservingValidExtendedKeyNames() throws Exception {
        for (int size : new int[] {256, 513}) {
            ClassLoader loader = loader(size);
            var name = loader.loadClass(LegacyKeyboard.class.getName()).getMethod("getKeyName", int.class);
            for (int key : new int[] {-100, -1, size, Integer.MAX_VALUE}) assertEquals("0", name.invoke(null, key));
            for (int key : new int[] {0, 54, size - 1}) assertEquals(Integer.toString(key), name.invoke(null, key));
        }
    }

    private static ClassLoader loader(int size) throws Exception {
        String keyboard = "org/lwjgl/input/Keyboard";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, keyboard, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "KEYBOARD_SIZE", "I", null, size).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "failure", "Z", null, null).visitEnd();
        var query = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "isKeyDown", "(I)Z", null, null);
        query.visitCode();
        query.visitFieldInsn(Opcodes.GETSTATIC, keyboard, "failure", "Z");
        var ready = new org.objectweb.asm.Label();
        query.visitJumpInsn(Opcodes.IFEQ, ready);
        query.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException"); query.visitInsn(Opcodes.DUP);
        query.visitLdcInsn("Keyboard not created");
        query.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
        query.visitInsn(Opcodes.ATHROW); query.visitLabel(ready);
        query.visitFieldInsn(Opcodes.GETSTATIC, keyboard, "calls", "I"); query.visitInsn(Opcodes.ICONST_1); query.visitInsn(Opcodes.IADD);
        query.visitFieldInsn(Opcodes.PUTSTATIC, keyboard, "calls", "I");
        var down = new org.objectweb.asm.Label();
        query.visitVarInsn(Opcodes.ILOAD, 0); query.visitIntInsn(Opcodes.BIPUSH, 54); query.visitJumpInsn(Opcodes.IF_ICMPEQ, down);
        query.visitVarInsn(Opcodes.ILOAD, 0); query.visitIntInsn(Opcodes.SIPUSH, 320); query.visitJumpInsn(Opcodes.IF_ICMPEQ, down);
        query.visitInsn(Opcodes.ICONST_0); query.visitInsn(Opcodes.IRETURN);
        query.visitLabel(down); query.visitInsn(Opcodes.ICONST_1); query.visitInsn(Opcodes.IRETURN);
        query.visitMaxs(0, 0); query.visitEnd();
        var name = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getKeyName", "(I)Ljava/lang/String;", null, null);
        name.visitCode(); name.visitVarInsn(Opcodes.ILOAD, 0);
        name.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false);
        name.visitInsn(Opcodes.ARETURN); name.visitMaxs(0, 0); name.visitEnd(); writer.visitEnd();
        byte[] keyboardBytes = writer.toByteArray();
        byte[] helper;
        try (InputStream input = LegacyKeyboard.class.getResourceAsStream("LegacyKeyboard.class")) { helper = input.readAllBytes(); }
        return new ClassLoader(LegacyKeyboardTest.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("org.lwjgl.input.Keyboard") && !name.equals(LegacyKeyboard.class.getName())) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    byte[] bytes = name.equals("org.lwjgl.input.Keyboard") ? keyboardBytes : helper;
                    if (loaded == null) loaded = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
    }
}
