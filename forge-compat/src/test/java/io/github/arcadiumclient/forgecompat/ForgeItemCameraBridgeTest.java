package io.github.arcadiumclient.forgecompat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class ForgeItemCameraBridgeTest {
    private static final MethodType APPLY = MethodType.methodType(boolean.class, Object.class, Object.class, Object.class, Object.class);
    private static final Throwable FAILURE = new Exception("camera provider");
    private record Bridge(MethodHandle install, MethodHandle apply) { }

    @Test void hasNoProviderUntilAnExactHandleIsInstalledAndCannotBeReplaced() throws Throwable {
        Bridge bridge = isolated(); Object value = new Object();
        assertFalse((boolean) bridge.apply.invokeExact(value, value, value, value));
        assertFalse((boolean) bridge.install.invokeExact((MethodHandle) null));
        MethodHandle wrong = MethodHandles.constant(Boolean.class, Boolean.TRUE);
        assertFalse((boolean) bridge.install.invokeExact(wrong));
        MethodHandle accepted = MethodHandles.dropArguments(MethodHandles.constant(boolean.class, true), 0, Object.class, Object.class, Object.class, Object.class);
        MethodHandle declined = MethodHandles.dropArguments(MethodHandles.constant(boolean.class, false), 0, Object.class, Object.class, Object.class, Object.class);
        assertTrue((boolean) bridge.install.invokeExact(accepted));
        assertTrue((boolean) bridge.install.invokeExact(accepted));
        assertFalse((boolean) bridge.install.invokeExact(declined));
        assertTrue((boolean) bridge.apply.invokeExact(value, value, value, value));
        assertFalse((boolean) bridge.install.invokeExact((MethodHandle) null));
        assertTrue((boolean) bridge.apply.invokeExact(value, value, value, value));
    }

    @Test void preservesCheckedProviderFailuresWithoutWrappingOrRetrying() throws Throwable {
        Bridge bridge = isolated(); MethodHandle throwing = MethodHandles.lookup().findStatic(getClass(), "throwing", APPLY);
        assertTrue((boolean) bridge.install.invokeExact(throwing));
        Throwable actual = assertThrows(Throwable.class, () -> { boolean ignored = (boolean) bridge.apply.invokeExact((Object) null, (Object) null, (Object) null, (Object) null); });
        assertSame(FAILURE, actual);
    }

    @Test void concurrentOwnersCannotReplaceTheWinningRegistration() throws Throwable {
        Bridge bridge = isolated(); List<MethodHandle> candidates = new ArrayList<>();
        for (int i = 0; i < 8; i++) candidates.add(MethodHandles.dropArguments(MethodHandles.constant(boolean.class, (i & 1) == 0), 0, Object.class, Object.class, Object.class, Object.class));
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1);
        List<java.util.concurrent.Future<Boolean>> futures = new ArrayList<>();
        var executor = Executors.newFixedThreadPool(8);
        try {
            for (MethodHandle candidate : candidates) futures.add(executor.submit(() -> {
                ready.countDown(); start.await();
                try { return (boolean) bridge.install.invokeExact(candidate); }
                catch (Throwable failure) { throw new AssertionError(failure); }
            }));
            ready.await(); start.countDown(); int winners = 0, winner = -1;
            for (int i = 0; i < futures.size(); i++) if (futures.get(i).get()) { winners++; winner = i; }
            assertEquals(1, winners);
            for (int i = 0; i < candidates.size(); i++) assertEquals(i == winner, (boolean) bridge.install.invokeExact(candidates.get(i)));
        } finally { executor.shutdownNow(); }
    }

    @Test void hotApplyUsesInvokeExactWithoutReflectionOrArrayPacking() throws Exception {
        ClassNode node = new ClassNode(); new ClassReader(bytes()).accept(node, 0);
        var apply = node.methods.stream().filter(method -> method.name.equals("apply")).findFirst().orElseThrow(); int exact = 0;
        for (var instruction : apply.instructions) {
            assertFalse(instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.ANEWARRAY);
            if (instruction instanceof MethodInsnNode call) {
                assertEquals("java/lang/invoke/MethodHandle", call.owner);
                assertEquals("invokeExact", call.name); assertEquals(APPLY.toMethodDescriptorString(), call.desc); exact++;
            }
        }
        assertEquals(1, exact);
    }

    private static boolean throwing(Object matrix, Object model, Object transform, Object pair) throws Throwable { throw FAILURE; }
    private static Bridge isolated() throws Exception {
        byte[] bytes = bytes(); Class<?> type = new ClassLoader(ForgeItemCameraBridgeTest.class.getClassLoader()) {
            Class<?> load() { return defineClass(ForgeItemCameraBridge.class.getName(), bytes, 0, bytes.length); }
        }.load();
        return new Bridge(MethodHandles.publicLookup().findStatic(type, "install", MethodType.methodType(boolean.class, MethodHandle.class)), MethodHandles.publicLookup().findStatic(type, "apply", APPLY));
    }
    private static byte[] bytes() throws Exception {
        try (var input = ForgeItemCameraBridge.class.getResourceAsStream("ForgeItemCameraBridge.class")) { assertNotNull(input); return input.readAllBytes(); }
    }
}
