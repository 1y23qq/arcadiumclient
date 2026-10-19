package io.github.arcadiumclient.forgecompat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.FloatBuffer;

public final class ForgeMatrixCapture {
    private static final MethodHandle CURRENT = currentCapture();

    private ForgeMatrixCapture() { }

    public static boolean multiply(FloatBuffer matrix) {
        if (CURRENT == null) return false;
        try {
            NativeMatrixCapture capture = (NativeMatrixCapture) CURRENT.invokeExact();
            return capture != null && capture.arcadium$multiplyMatrix(matrix);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot capture a Forge model transformation", failure);
        }
    }

    private static MethodHandle currentCapture() {
        try {
            Class<?> type = Class.forName(HostMatrixCompatibility.CAPTURE.replace('/', '.'), false, ForgeMatrixCapture.class.getClassLoader());
            if (!NativeMatrixCapture.class.isAssignableFrom(type)) {
                throw new IllegalStateException("Native entity matrix capture was not installed");
            }
            return MethodHandles.publicLookup().unreflect(type.getMethod("current"))
                .asType(MethodType.methodType(NativeMatrixCapture.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot link native entity matrix capture", failure);
        }
    }
}
