package io.github.arcadiumclient.forgecompat.reflect;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.objectweb.asm.Type;

public final class ForgeReflection {
    private static volatile ForgeMappings mappings;
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private ForgeReflection() { }

    public static void configure(ForgeMappings value) { mappings = java.util.Objects.requireNonNull(value); }

    public static Class<?> forName(String name) throws ClassNotFoundException {
        return Class.forName(className(name), true, Thread.currentThread().getContextClassLoader());
    }

    public static Class<?> forName(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(className(name), initialize, loader);
    }

    public static Field getDeclaredField(Class<?> owner, String name) throws NoSuchFieldException {
        try { return owner.getDeclaredField(name); }
        catch (NoSuchFieldException original) {
            for (Field field : owner.getDeclaredFields()) if (matches(field, name)) return field;
            throw original;
        }
    }

    public static Field getField(Class<?> owner, String name) throws NoSuchFieldException {
        try { return owner.getField(name); }
        catch (NoSuchFieldException original) {
            for (Field field : owner.getFields()) if (matches(field, name)) return field;
            throw original;
        }
    }

    public static Method getDeclaredMethod(Class<?> owner, String name, Class<?>[] parameters) throws NoSuchMethodException {
        try { return owner.getDeclaredMethod(name, parameters); }
        catch (NoSuchMethodException original) {
            for (Method method : owner.getDeclaredMethods()) if (matches(method, name, parameters)) return method;
            throw original;
        }
    }

    public static Method getMethod(Class<?> owner, String name, Class<?>[] parameters) throws NoSuchMethodException {
        try { return owner.getMethod(name, parameters); }
        catch (NoSuchMethodException original) {
            for (Method method : owner.getMethods()) if (matches(method, name, parameters)) return method;
            throw original;
        }
    }

    public static Class<?>[] callingContext() {
        return WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != ForgeReflection.class).toArray(Class<?>[]::new));
    }

    private static String className(String name) {
        ForgeMappings current = mappings;
        if (current == null) return name;
        if (name.startsWith("[")) return current.srgToRuntime().mapDesc(name.replace('.', '/')).replace('/', '.');
        return current.reflectionClassFromSrg(name).replace('/', '.');
    }

    private static boolean matches(Field field, String name) {
        ForgeMappings current = mappings;
        if (current == null) return false;
        String legacy = current.runtimeToSrg().mapFieldName(Type.getInternalName(field.getDeclaringClass()),
                field.getName(), Type.getDescriptor(field.getType()));
        return name.equals(legacy);
    }

    private static boolean matches(Method method, String name, Class<?>[] parameters) {
        if (!Arrays.equals(method.getParameterTypes(), parameters == null ? new Class<?>[0] : parameters)) return false;
        ForgeMappings current = mappings;
        if (current == null) return false;
        String legacy = current.runtimeToSrg().mapMethodName(Type.getInternalName(method.getDeclaringClass()),
                method.getName(), Type.getMethodDescriptor(method));
        return name.equals(legacy);
    }
}
