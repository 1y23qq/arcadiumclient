package io.github.arcadiumclient.forgecompat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import sun.misc.Unsafe;

public final class ForgeJava25Reflection {
    private static final String OBJECT_HOLDER = "net.minecraftforge.fml.common.registry.ObjectHolderRef";
    private static final String STACK_HOLDER = "net.minecraftforge.fml.common.registry.ItemStackHolderRef";
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ClassValue<Map<Field, HolderField>> HOLDERS = new ClassValue<>() {
        @Override protected Map<Field, HolderField> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    private ForgeJava25Reflection() { }

    public static void initialize() {
        if (Memory.ENUM_CONSTANTS < 0 || Memory.ENUM_DIRECTORY < 0) {
            throw new IllegalStateException("The Java runtime does not expose the required enum caches.");
        }
    }

    public static void prepareHolder(Field field) {
        Class<?> holder = holderCaller();
        Objects.requireNonNull(field, "Holder field");
        if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
            throw new IllegalArgumentException("A Forge object holder must be a static reference field: " + field);
        }
        if (!field.trySetAccessible()) throw new IllegalStateException("The Forge holder field is not accessible: " + field);
        boolean finalField = Modifier.isFinal(field.getModifiers());
        HolderField prepared = new HolderField(holder, finalField,
            finalField ? Memory.UNSAFE.staticFieldBase(field) : null,
            finalField ? Memory.UNSAFE.staticFieldOffset(field) : 0);
        HolderField previous = HOLDERS.get(field.getDeclaringClass()).putIfAbsent(field, prepared);
        if (previous != null && previous.holder != holder) {
            throw new IllegalArgumentException("The field is already registered for a different Forge holder: " + field);
        }
    }

    public static void writeHolder(Field field, Object value) throws IllegalAccessException {
        Class<?> holder = holderCaller();
        HolderField prepared = HOLDERS.get(field.getDeclaringClass()).get(field);
        if (prepared == null || prepared.holder != holder) {
            throw new IllegalAccessException("The field was not registered by this Forge holder: " + field);
        }
        checkValue(field, value);
        ensureInitialized(field.getDeclaringClass());
        if (prepared.finalField) Memory.UNSAFE.putObjectVolatile(prepared.base, prepared.offset, value);
        else field.set(null, value);
    }

    public static Enum<?> makeEnum(Class<?> type, String name, int ordinal, Class<?>[] parameterTypes, Object[] values) throws Exception {
        Objects.requireNonNull(type, "Enum type");
        if (!type.isEnum()) throw new IllegalArgumentException("The requested class is not an enum: " + type.getName());
        Objects.requireNonNull(parameterTypes, "Enum constructor parameter types");
        Objects.requireNonNull(values, "Enum constructor arguments");
        if (parameterTypes.length != values.length) throw new IllegalArgumentException("The enum constructor argument count does not match its parameter types.");
        Class<?>[] signature = new Class<?>[parameterTypes.length + 2];
        signature[0] = String.class; signature[1] = int.class;
        System.arraycopy(parameterTypes, 0, signature, 2, parameterTypes.length);
        Object[] arguments = new Object[values.length + 2];
        arguments[0] = name; arguments[1] = ordinal;
        System.arraycopy(values, 0, arguments, 2, values.length);
        var constructor = MethodHandles.privateLookupIn(type, MethodHandles.lookup()).findConstructor(type, MethodType.methodType(void.class, signature));
        try { return (Enum<?>) constructor.invokeWithArguments(Arrays.asList(arguments)); }
        catch (Throwable failure) { throw new InvocationTargetException(failure); }
    }

    public static void setFailsafeFieldValue(Field field, Object target, Object value) throws Exception {
        Class<?> caller = WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
            .filter(type -> type != ForgeJava25Reflection.class).findFirst().orElseThrow());
        if (!caller.getName().equals("net.minecraftforge.common.util.EnumHelper")
                || caller.getClassLoader() != ForgeRuntimeContext.hostClassLoader()) {
            throw new IllegalStateException("Failsafe field writes must use the active Forge EnumHelper API.");
        }
        Objects.requireNonNull(field, "Field");
        field.trySetAccessible();
        try {
            boolean staticField = Modifier.isStatic(field.getModifiers());
            Object base;
            long offset;
            if (staticField) {
                Class<?> owner = field.getDeclaringClass();
                if (owner.isHidden()) MethodHandles.privateLookupIn(owner, MethodHandles.lookup()).ensureInitialized(owner);
                else Class.forName(owner.getName(), true, owner.getClassLoader());
                base = Memory.UNSAFE.staticFieldBase(field); offset = Memory.UNSAFE.staticFieldOffset(field);
            } else {
                Objects.requireNonNull(target, "Instance field target");
                if (!field.getDeclaringClass().isInstance(target)) throw new IllegalArgumentException("The target does not declare the field: " + field);
                base = target; offset = Memory.UNSAFE.objectFieldOffset(field);
            }
            Class<?> type = field.getType();
            Object converted = fieldValue(field, value);
            if (!type.isPrimitive()) Memory.UNSAFE.putObjectVolatile(base, offset, converted);
            else if (type == boolean.class) Memory.UNSAFE.putBooleanVolatile(base, offset, (Boolean) converted);
            else if (type == byte.class) Memory.UNSAFE.putByteVolatile(base, offset, (Byte) converted);
            else if (type == short.class) Memory.UNSAFE.putShortVolatile(base, offset, (Short) converted);
            else if (type == char.class) Memory.UNSAFE.putCharVolatile(base, offset, (Character) converted);
            else if (type == int.class) Memory.UNSAFE.putIntVolatile(base, offset, (Integer) converted);
            else if (type == long.class) Memory.UNSAFE.putLongVolatile(base, offset, (Long) converted);
            else if (type == float.class) Memory.UNSAFE.putFloatVolatile(base, offset, (Float) converted);
            else if (type == double.class) Memory.UNSAFE.putDoubleVolatile(base, offset, (Double) converted);
        } catch (Throwable failure) {
            throw new InvocationTargetException(failure);
        }
    }

    public static void cleanEnumCache(Class<?> type) {
        if (!type.isEnum()) throw new IllegalArgumentException("The requested class is not an enum: " + type.getName());
        Memory.UNSAFE.putObjectVolatile(type, Memory.ENUM_CONSTANTS, null);
        Memory.UNSAFE.putObjectVolatile(type, Memory.ENUM_DIRECTORY, null);
    }

    private static Object fieldValue(Field field, Object value) {
        Class<?> type = field.getType();
        if (!type.isPrimitive()) { checkValue(field, value); return value; }
        if (type == boolean.class && value instanceof Boolean) return value;
        if (type == char.class && value instanceof Character) return value;
        int source = value instanceof Byte ? 0 : value instanceof Short ? 1 : value instanceof Character ? 2
            : value instanceof Integer ? 3 : value instanceof Long ? 4 : value instanceof Float ? 5 : value instanceof Double ? 6 : -1;
        int destination = type == byte.class ? 0 : type == short.class ? 1 : type == int.class ? 3
            : type == long.class ? 4 : type == float.class ? 5 : type == double.class ? 6 : -1;
        if (source < 0 || destination < 0 || source > destination) {
            throw new IllegalArgumentException("The value cannot be unboxed and widened to the field type: " + field);
        }
        Number number = value instanceof Character character ? Integer.valueOf(character) : (Number) value;
        if (type == byte.class) return number.byteValue();
        if (type == short.class) return number.shortValue();
        if (type == int.class) return number.intValue();
        if (type == long.class) return number.longValue();
        if (type == float.class) return number.floatValue();
        return number.doubleValue();
    }

    private static Class<?> holderCaller() {
        Class<?> caller = WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
            .filter(type -> type != ForgeJava25Reflection.class).findFirst().orElseThrow());
        if (!(caller.getName().equals(OBJECT_HOLDER) || caller.getName().equals(STACK_HOLDER))
                || caller.getClassLoader() != ForgeRuntimeContext.hostClassLoader()) {
            throw new IllegalStateException("Only the active Forge object holders can register or write holder fields.");
        }
        return caller;
    }

    private static void checkValue(Field field, Object value) {
        if (value != null && !field.getType().isInstance(value)) throw new IllegalArgumentException("The value does not match the field type: " + field);
    }

    private static void ensureInitialized(Class<?> type) throws IllegalAccessException {
        MethodHandles.privateLookupIn(type, MethodHandles.lookup()).ensureInitialized(type);
    }

    private record HolderField(Class<?> holder, boolean finalField, Object base, long offset) { }

    private static final class Memory {
        private static final Unsafe UNSAFE;
        private static final long ENUM_CONSTANTS;
        private static final long ENUM_DIRECTORY;
        static {
            try {
                Field singleton = Unsafe.class.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
                UNSAFE = (Unsafe) singleton.get(null);
                ENUM_CONSTANTS = UNSAFE.objectFieldOffset(Class.class.getDeclaredField("enumConstants"));
                ENUM_DIRECTORY = UNSAFE.objectFieldOffset(Class.class.getDeclaredField("enumConstantDirectory"));
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }
}
