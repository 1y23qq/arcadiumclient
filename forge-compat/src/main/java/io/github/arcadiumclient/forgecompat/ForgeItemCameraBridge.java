package io.github.arcadiumclient.forgecompat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public final class ForgeItemCameraBridge {
    private static final MethodType TYPE = MethodType.methodType(boolean.class,
        Object.class, Object.class, Object.class, Object.class);
    private static volatile MethodHandle handler;

    private ForgeItemCameraBridge() { }

    public static synchronized boolean install(MethodHandle candidate) {
        if (candidate == null || !candidate.type().equals(TYPE)) return false;
        if (handler == null) handler = candidate;
        return handler == candidate;
    }

    public static boolean apply(Object matrix, Object model, Object transform, Object pair) throws Throwable {
        MethodHandle current = handler;
        return current != null && (boolean) current.invokeExact(matrix, model, transform, pair);
    }
}
