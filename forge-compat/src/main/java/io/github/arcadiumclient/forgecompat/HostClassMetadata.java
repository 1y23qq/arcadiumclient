package io.github.arcadiumclient.forgecompat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

final class HostClassMetadata {
    private static final Method ADD_METHOD = addMethod();
    private static final Field MODIFIERS = modifiers();

    private HostClassMetadata() { }

    static void synchronize(String internal, byte[] bytes) {
        ClassInfo info = ClassInfo.fromCache(internal);
        if (info == null) return;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(internal)) throw new IllegalStateException("Unexpected class identity while updating Mixin metadata.");
        synchronized (info) {
            for (var method : node.methods) {
                ClassInfo.Method existing = info.findMethod(method, ClassInfo.INCLUDE_ALL);
                if (existing == null) {
                    try { ADD_METHOD.invoke(info, method, false); }
                    catch (ReflectiveOperationException exception) {
                        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getCause() : exception;
                        throw new IllegalStateException("Mixin metadata could not register the Forge compatibility method "
                            + internal + "." + method.name + method.desc + ".", cause);
                    }
                } else {
                    synchronizeAccess(existing, method.access);
                    var frames = info.new Method(method, false).getFrames();
                    existing.getFrames().clear();
                    existing.getFrames().addAll(frames);
                }
            }
            for (var field : node.fields) {
                ClassInfo.Field existing = info.findField(field.name, field.desc, ClassInfo.INCLUDE_ALL);
                if (existing != null) synchronizeAccess(existing, field.access);
            }
        }
    }

    private static void synchronizeAccess(Object member, int access) {
        try {
            if (MODIFIERS.getInt(member) != access) MODIFIERS.setInt(member, access);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Mixin metadata could not synchronize transformed member access flags.", exception);
        }
    }

    private static Field modifiers() {
        try {
            Field field = ClassInfo.Method.class.getSuperclass().getDeclaredField("modifiers");
            if (field.getType() != int.class) throw new NoSuchFieldException("modifiers is not an integer");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This Mixin version cannot update Forge compatibility access metadata.", exception);
        }
    }

    private static Method addMethod() {
        try {
            Method method = ClassInfo.class.getDeclaredMethod("addMethod", org.objectweb.asm.tree.MethodNode.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This Mixin version cannot update Forge compatibility method metadata.", exception);
        }
    }
}
