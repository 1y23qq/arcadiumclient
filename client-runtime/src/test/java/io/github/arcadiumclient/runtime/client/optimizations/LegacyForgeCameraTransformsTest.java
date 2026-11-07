// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class LegacyForgeCameraTransformsTest {
    private static final Object MODEL = model("net/minecraftforge/client/model/ItemLayerModel$BakedItemModel");
    private static final Object MATRIX = model("javax/vecmath/Matrix4f");

    private record Fixture(LegacyForgeCameraTransforms.Scope scope, Object stack, Object transform,
                           Object matrix, Pair<Object, Object> pair, int token) {
        Object selected() {
            return LegacyForgeCameraTransforms.eligibleStack(scope, MODEL, transform, true, pair, matrix, true, 0);
        }
    }

    private static Fixture fixture() {
        var scope = new LegacyForgeCameraTransforms.Scope();
        Object stack = new Object(), transform = new Object(), matrix = MATRIX;
        return new Fixture(scope, stack, transform, matrix, ImmutablePair.of(MODEL, matrix),
                scope.enter(stack, MODEL, transform));
    }

    @Test void canonicalSelfReturningFirstPersonModelSelectsActualStack() {
        var f = fixture();
        assertSame(f.stack, f.selected());
        f.scope.exit(f.token);
        assertNull(f.selected());
    }

    @Test void moduleDisabledRetainsTheOriginalMatrix() {
        var f = fixture();
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, true,
                f.pair, f.matrix, false, 0));
    }

    @Test void badlionAndUnknownModesRetainTheOriginalMatrix() {
        var f = fixture();
        for (int mode : new int[]{-1, 1, 2}) assertNull(LegacyForgeCameraTransforms.eligibleStack(
                f.scope, MODEL, f.transform, true, f.pair, f.matrix, true, mode));
    }

    @Test void allOtherPerspectivesRetainTheOriginalMatrix() {
        var f = fixture();
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, false,
                f.pair, f.matrix, true, 0));
    }

    @Test void directForgeHookWithoutAnItemCallerRetainsNativeBehavior() {
        var f = fixture();
        f.scope.exit(f.token);
        assertNull(f.selected());
    }

    @Test void aReplacementModelIsNotInterpretedAsTheOriginalFlatItem() {
        var f = fixture();
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, true,
                ImmutablePair.of(new Object(), f.matrix), f.matrix, true, 0));
    }

    @Test void aDifferentMatrixIdentityRetainsNativeBehavior() {
        var f = fixture();
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, true,
                f.pair, new Object(), true, 0));
    }

    @Test void unknownMatrixImplementationsRetainTheirNativeCallbacks() {
        var f = fixture();
        Object matrix = new Object();
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, true,
                ImmutablePair.of(MODEL, matrix), matrix, true, 0));
    }

    @Test void nullOrUnknownModelsAreNotInspected() {
        var f = fixture();
        for (Object value : new Object[]{null, new Object(), model("fixture/UnknownItem")}) {
            assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, value, f.transform, true,
                    f.pair, f.matrix, true, 0));
        }
    }

    @Test void unknownPairsKeepTheirVirtualGettersUntouched() {
        var f = fixture();
        int[] calls = new int[1];
        Pair<Object, Object> pair = new Pair<>() {
            @Override public Object getLeft() { calls[0]++; return MODEL; }
            @Override public Object getRight() { calls[0]++; return f.matrix; }
            @Override public Object setValue(Object value) { throw new UnsupportedOperationException(); }
        };
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, true,
                pair, f.matrix, true, 0));
        assertEquals(0, calls[0]);
    }

    @Test void nullPairOrMatrixIsDeclined() {
        var f = fixture();
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, true,
                null, f.matrix, true, 0));
        assertNull(LegacyForgeCameraTransforms.eligibleStack(f.scope, MODEL, f.transform, true,
                ImmutablePair.of(MODEL, null), null, true, 0));
    }

    @Test void modelAndTransformMustMatchTheCurrentCallerByIdentity() {
        var f = fixture();
        assertNull(f.scope.stackFor(new Object(), f.transform));
        assertNull(f.scope.stackFor(MODEL, new Object()));
    }

    @Test void nestedNullStackMasksItsParentUntilUnwind() {
        var f = fixture();
        int token = f.scope.enter(null, MODEL, f.transform);
        assertNull(f.selected());
        f.scope.exit(token);
        assertSame(f.stack, f.selected());
    }

    @Test void nestedDifferentStackIsRestoredEvenWhenItsCallbackThrows() {
        var f = fixture();
        Object child = new Object();
        assertThrows(IllegalArgumentException.class, () -> {
            int token = f.scope.enter(child, MODEL, f.transform);
            try {
                assertSame(child, f.selected());
                throw new IllegalArgumentException("Native callback failed");
            } finally {
                f.scope.exit(token);
            }
        });
        assertSame(f.stack, f.selected());
    }

    @Test void unknownNestedModelsCannotBorrowTheParentStack() {
        var f = fixture();
        int token = f.scope.enter(new Object(), new Object(), f.transform);
        assertNull(f.selected());
        f.scope.exit(token);
        assertSame(f.stack, f.selected());
    }

    @Test void boundedOverflowRetainsNativeBehaviorAndRestoresOuterScopes() {
        var f = fixture();
        int[] tokens = new int[12];
        for (int i = 0; i < tokens.length; i++) tokens[i] = f.scope.enter(f.stack, MODEL, f.transform);
        assertTrue(tokens[tokens.length - 1] < 0);
        assertNull(f.selected());
        for (int i = tokens.length - 1; i >= 0; i--) f.scope.exit(tokens[i]);
        assertSame(f.stack, f.selected());
        f.scope.exit(f.token);
        assertNull(f.selected());
    }

    @Test void outOfOrderExitDoesNotClearTheActualOwner() {
        var f = fixture();
        int child = f.scope.enter(f.stack, MODEL, f.transform);
        assertThrows(IllegalStateException.class, () -> f.scope.exit(f.token));
        assertSame(f.stack, f.selected());
        f.scope.exit(child);
        f.scope.exit(f.token);
        assertThrows(IllegalStateException.class, () -> f.scope.exit(0));
    }

    @Test void finalExitReleasesAllStrongReferences() throws ReflectiveOperationException {
        var f = fixture();
        f.scope.exit(f.token);
        for (String name : new String[]{"stacks", "models", "transforms"}) {
            var field = f.scope.getClass().getDeclaredField(name);
            field.setAccessible(true);
            for (Object value : (Object[]) field.get(f.scope)) assertNull(value);
        }
    }

    @Test void aForeignThreadCannotUnwindTheRenderThreadsScope() throws InterruptedException {
        int token = LegacyForgeCameraTransforms.enter(new Object(), MODEL, new Object());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = Thread.ofPlatform().start(() -> {
            try { LegacyForgeCameraTransforms.exit(token); }
            catch (Throwable expected) { failure.set(expected); }
        });
        thread.join();
        try { assertInstanceOf(IllegalStateException.class, failure.get()); }
        finally { LegacyForgeCameraTransforms.exit(token); }
    }

    @Test void erasedBridgeRejectsUnrelatedArgumentsWithoutLoadingTheOptionalApi() {
        assertFalse(LegacyForgeCameraTransforms.applyBridge(new Object(), new Object(), new Object(), new Object()));
        assertFalse(LegacyForgeCameraTransforms.applyBridge(null, null, null, null));
    }

    @Test void registrationUsesTheModelLoaderAndRunsOnlyOnce() throws Throwable {
        var f = bindingFixture(true);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ClassLoader(previous) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.endsWith("ForgeItemCameraBridge")) throw new AssertionError("Do not resolve camera providers through the thread context loader");
                return super.loadClass(name, resolve);
            }
        });
        try {
            for (int i = 0; i < 3; i++) {
                int token = LegacyForgeCameraTransforms.enter(new Object(), f.model,
                        net.minecraft.client.resource.model.ModelTransformations.Type.FIRST_PERSON);
                LegacyForgeCameraTransforms.exit(token);
            }
            assertEquals(1, f.bridge.getField("calls").getInt(null));
            var provider = (java.lang.invoke.MethodHandle) f.bridge.getField("provider").get(null);
            assertEquals(MethodType.methodType(boolean.class, Object.class, Object.class, Object.class, Object.class), provider.type());
            assertFalse((boolean) provider.invokeExact((Object) null, (Object) null, (Object) null, (Object) null));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Test void differentCanonicalModelLoadersBindTheirOwnBridge() throws ReflectiveOperationException {
        var first = bindingFixture(true);
        var second = bindingFixture(true);
        for (var f : new BindingFixture[]{first, second}) {
            int token = LegacyForgeCameraTransforms.enter(new Object(), f.model,
                    net.minecraft.client.resource.model.ModelTransformations.Type.FIRST_PERSON);
            LegacyForgeCameraTransforms.exit(token);
            assertEquals(1, f.bridge.getField("calls").getInt(null));
        }
        assertNotSame(first.bridge, second.bridge);
    }

    @Test void absentInteropBridgeLeavesTheItemScopeUsable() throws ReflectiveOperationException {
        var f = bindingFixture(false);
        int token = LegacyForgeCameraTransforms.enter(new Object(), f.model,
                net.minecraft.client.resource.model.ModelTransformations.Type.FIRST_PERSON);
        LegacyForgeCameraTransforms.exit(token);
        assertNull(f.bridge);
    }

    @Test void warmedScopeAndAdmissionDoNotAllocatePerItem() {
        var f = fixture();
        Object selected = null;
        for (int i = 0; i < 100_000; i++) selected = f.selected();
        assertSame(f.stack, selected);
        var bean = assertInstanceOf(com.sun.management.ThreadMXBean.class, ManagementFactory.getThreadMXBean());
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 100_000; i++) {
            int token = f.scope.enter(f.stack, MODEL, f.transform);
            selected = f.selected();
            f.scope.exit(token);
        }
        long bytes = bean.getThreadAllocatedBytes(id) - before;
        assertSame(f.stack, selected);
        assertEquals(0, bytes);
    }

    private record BindingFixture(Object model, Class<?> bridge) { }

    private static BindingFixture bindingFixture(boolean present) throws ReflectiveOperationException {
        String model = "net.minecraftforge.client.model.ItemLayerModel$BakedItemModel";
        String bridge = "io.github.arcadiumclient.forgecompat.ForgeItemCameraBridge";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, bridge.replace('.', '/'), null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "calls", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "provider", "Ljava/lang/invoke/MethodHandle;", null, null).visitEnd();
        var install = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "install", "(Ljava/lang/invoke/MethodHandle;)Z", null, null);
        install.visitCode();
        install.visitFieldInsn(Opcodes.GETSTATIC, bridge.replace('.', '/'), "calls", "I");
        install.visitInsn(Opcodes.ICONST_1);
        install.visitInsn(Opcodes.IADD);
        install.visitFieldInsn(Opcodes.PUTSTATIC, bridge.replace('.', '/'), "calls", "I");
        install.visitVarInsn(Opcodes.ALOAD, 0);
        install.visitFieldInsn(Opcodes.PUTSTATIC, bridge.replace('.', '/'), "provider", "Ljava/lang/invoke/MethodHandle;");
        install.visitInsn(Opcodes.ICONST_1);
        install.visitInsn(Opcodes.IRETURN);
        install.visitMaxs(0, 0);
        install.visitEnd();
        writer.visitEnd();
        Map<String, byte[]> definitions = present
                ? Map.of(model, classBytes(model.replace('.', '/')), bridge, writer.toByteArray())
                : Map.of(model, classBytes(model.replace('.', '/')));
        class Loader extends ClassLoader {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(model) && !name.equals(bridge)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> known = findLoadedClass(name);
                    if (known == null) {
                        byte[] bytes = definitions.get(name);
                        if (bytes == null) throw new ClassNotFoundException(name);
                        known = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) resolveClass(known);
                    return known;
                }
            }
        }
        Loader loader = new Loader();
        return new BindingFixture(loader.loadClass(model).getConstructor().newInstance(),
                present ? loader.loadClass(bridge) : null);
    }

    private static byte[] classBytes(String name) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        var init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static Object model(String name) {
        class Loader extends ClassLoader {
            Class<?> define() { byte[] code = classBytes(name); return defineClass(name.replace('/', '.'), code, 0, code.length); }
        }
        try { return new Loader().define().getConstructor().newInstance(); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
