package io.github.arcadiumclient.forgecompat;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Objects;

final class ForgeFailsafeFieldRuntimeCheck {
    private static int coldInitializations;

    private ForgeFailsafeFieldRuntimeCheck() { }

    static int verify(Class<?> enumHelper) throws Exception {
        Method api = enumHelper.getMethod("setFailsafeFieldValue", Field.class, Object.class, Object.class);
        require(Modifier.isPublic(api.getModifiers()) && Modifier.isStatic(api.getModifiers()), "The original generic Forge field setter ABI changed");
        verifyReferences(api);
        int cases = verifyPrimitiveConversions(api);
        Field cold = ColdFields.class.getDeclaredField("VALUE");
        require(coldInitializations == 0, "Reading field metadata initialized its owner prematurely");
        expectFailure(IllegalArgumentException.class, api, cold, new Object(), null);
        require(coldInitializations == 1, "The original static field accessor initialization order changed");
        api.invoke(null, cold, new Object(), 91);
        require(Integer.valueOf(91).equals(cold.get(null)), "A final static primitive field was not updated");
        try { api.invoke(null, null, null, new Object()); throw new IllegalStateException("A null Field was accepted"); }
        catch (InvocationTargetException failure) { require(failure.getCause() instanceof NullPointerException, "The null Field error changed"); }
        return cases;
    }

    private static void verifyReferences(Method api) throws Exception {
        ReferenceFields instance = new ReferenceFields();
        Field staticField = ReferenceFields.class.getDeclaredField("STATIC_REFERENCE"), instanceField = ReferenceFields.class.getDeclaredField("reference");
        Object staticValue = new Object(), instanceValue = new StringBuilder("replacement");
        api.invoke(null, staticField, new Object(), staticValue);
        require(staticField.get(null) == staticValue, "The static setter did not ignore the target argument");
        api.invoke(null, instanceField, instance, instanceValue);
        require(instanceField.get(instance) == instanceValue, "A private final instance reference was not updated");
        api.invoke(null, staticField, null, null); api.invoke(null, instanceField, instance, null);
        require(staticField.get(null) == null && instanceField.get(instance) == null, "Null reference assignment changed");
        require(Modifier.isFinal(staticField.getModifiers()) && Modifier.isFinal(instanceField.getModifiers()), "The utility modified JDK field modifier metadata");
        Field typed = ReferenceFields.class.getDeclaredField("text");
        api.invoke(null, typed, instance, "new text"); require(typed.get(instance).equals("new text"), "A typed final reference was not updated");
        expectFailure(IllegalArgumentException.class, api, typed, instance, Integer.valueOf(3));
        require(typed.get(instance).equals("new text"), "A failed typed reference write changed the field");
        expectFailure(NullPointerException.class, api, typed, null, "invalid");
        expectFailure(IllegalArgumentException.class, api, typed, new Object(), "invalid");
        Field array = ReferenceFields.class.getDeclaredField("array");
        Integer[] values = {1,2}; api.invoke(null, array, instance, values); require(array.get(instance) == values, "Covariant reference array assignment changed");
        expectFailure(IllegalArgumentException.class, api, array, instance, new String[]{"invalid"});
        api.invoke(null, array, instance, null); require(array.get(instance) == null, "A final array reference could not be cleared");
        Field mutable = ReferenceFields.class.getDeclaredField("mutable");
        api.invoke(null, mutable, instance, instanceValue); require(mutable.get(instance) == instanceValue, "The original nonfinal setter behavior changed");
    }

    private static int verifyPrimitiveConversions(Method api) throws Exception {
        Object[] values = {(byte)7, (short)320, '\u1111', 3000, (1L << 40) + 3, -0.0F, Float.NaN, 3.125D,
            Double.POSITIVE_INFINITY, true, null, "wrong type", new BigDecimal("1")};
        int cases = 0;
        for (Field field : FinalPrimitives.class.getDeclaredFields()) {
            require(field.getType().isPrimitive() && Modifier.isFinal(field.getModifiers()), "The primitive fixture is not a final field");
            field.setAccessible(true);
            Field oracleField = MutablePrimitives.class.getDeclaredField(field.getType().getName() + "Value"); oracleField.setAccessible(true);
            for (Object value : values) {
                MutablePrimitives oracle = new MutablePrimitives();
                Object target = Modifier.isStatic(field.getModifiers()) ? new Object() : new FinalPrimitives(0);
                Object before = field.get(Modifier.isStatic(field.getModifiers()) ? null : target);
                boolean allowed;
                try { oracleField.set(oracle, value); allowed = true; }
                catch (IllegalArgumentException failure) { allowed = false; }
                if (allowed) {
                    api.invoke(null, field, target, value);
                    require(equalValue(oracleField.get(oracle), field.get(Modifier.isStatic(field.getModifiers()) ? null : target)), "The public Forge setter changed a primitive unboxing or widening conversion for " + field.getType().getName());
                } else {
                    expectFailure(IllegalArgumentException.class, api, field, target, value);
                    require(equalValue(before, field.get(Modifier.isStatic(field.getModifiers()) ? null : target)), "An invalid primitive assignment changed its field");
                }
                require(Modifier.isFinal(field.getModifiers()), "A primitive write changed final modifier metadata"); cases++;
            }
        }
        require(cases == 208, "The static/instance primitive conversion matrix is incomplete");
        return cases;
    }

    private static boolean equalValue(Object expected, Object actual) {
        if (expected instanceof Float left && actual instanceof Float right) return Float.floatToRawIntBits(left) == Float.floatToRawIntBits(right);
        if (expected instanceof Double left && actual instanceof Double right) return Double.doubleToRawLongBits(left) == Double.doubleToRawLongBits(right);
        return Objects.equals(expected, actual);
    }

    private static void expectFailure(Class<? extends Throwable> cause, Method api, Field field, Object target, Object value) throws Exception {
        try { api.invoke(null, field, target, value); }
        catch (InvocationTargetException outer) {
            Throwable failure = outer.getCause();
            require(failure instanceof InvocationTargetException && cause.isInstance(failure.getCause()), "The public Forge setter changed its original reflection exception wrapping: " + failure);
            return;
        }
        throw new IllegalStateException("The public Forge setter accepted an invalid assignment");
    }

    private static final class ReferenceFields {
        private static final Object STATIC_REFERENCE = new Object();
        private final Object reference = new Object();
        private final String text = new String("initial");
        private final Number[] array = new Number[0];
        private Object mutable;
    }

    private static final class MutablePrimitives {
        boolean booleanValue; byte byteValue; short shortValue; char charValue; int intValue; long longValue; float floatValue; double doubleValue;
    }

    private static final class FinalPrimitives {
        private static final boolean STATIC_BOOLEAN = Boolean.parseBoolean("false");
        private static final byte STATIC_BYTE = Byte.parseByte("0");
        private static final short STATIC_SHORT = Short.parseShort("0");
        private static final char STATIC_CHAR = Character.forDigit(0, 10);
        private static final int STATIC_INT = Integer.parseInt("0");
        private static final long STATIC_LONG = Long.parseLong("0");
        private static final float STATIC_FLOAT = Float.parseFloat("0");
        private static final double STATIC_DOUBLE = Double.parseDouble("0");
        private final boolean booleanValue;
        private final byte byteValue;
        private final short shortValue;
        private final char charValue;
        private final int intValue;
        private final long longValue;
        private final float floatValue;
        private final double doubleValue;
        FinalPrimitives(int seed) {
            booleanValue = seed != 0; byteValue = (byte)seed; shortValue = (short)seed; charValue = (char)seed;
            intValue = seed; longValue = seed; floatValue = seed; doubleValue = seed;
        }
    }

    private static final class ColdFields {
        private static final int VALUE;
        static { coldInitializations++; VALUE = -1; }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
