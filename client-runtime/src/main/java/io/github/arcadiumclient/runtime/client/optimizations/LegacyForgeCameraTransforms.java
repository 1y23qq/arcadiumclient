// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.resource.model.ModelTransformations;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.tuple.ImmutablePair;

public final class LegacyForgeCameraTransforms {
    private static final String MODEL = "net.minecraftforge.client.model.ItemLayerModel$BakedItemModel";
    private static final String MATRIX = "javax.vecmath.Matrix4f";
    private static final ThreadLocal<Scope> CURRENT = ThreadLocal.withInitial(Scope::new);
    private static final MethodType BRIDGE_TYPE = MethodType.methodType(boolean.class,
            Object.class, Object.class, Object.class, Object.class);
    private static final ClassValue<Boolean> BRIDGE_BOUND = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> model) {
            try {
                Class<?> bridge = Class.forName("io.github.arcadiumclient.forgecompat.ForgeItemCameraBridge",
                        false, model.getClassLoader());
                MethodHandle install = MethodHandles.publicLookup().findStatic(bridge, "install",
                        MethodType.methodType(boolean.class, MethodHandle.class));
                return (boolean) install.invokeExact(Provider.APPLY);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return false;
            } catch (Throwable failure) {
                if (failure instanceof Error fatal) throw fatal;
                return false;
            }
        }
    };

    private LegacyForgeCameraTransforms() { }

    public static int enter(Object stack, Object model, Object transform) {
        if (transform == ModelTransformations.Type.FIRST_PERSON && model != null
                && model.getClass().getName().equals(MODEL)) BRIDGE_BOUND.get(model.getClass());
        return CURRENT.get().enter(stack, model, transform);
    }

    public static void exit(int token) {
        CURRENT.get().exit(token);
    }

    public static boolean applyBridge(Object matrix, Object model, Object transform, Object pair) {
        if (!(model instanceof BakedModel baked) || !(transform instanceof ModelTransformations.Type type)) return false;
        return apply(pair, matrix, baked, type);
    }

    private static final class Provider {
        static final MethodHandle APPLY = resolve();

        private static MethodHandle resolve() {
            try {
                return MethodHandles.lookup().findStatic(LegacyForgeCameraTransforms.class, "applyBridge", BRIDGE_TYPE);
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    public static boolean apply(Object pair, Object matrix, BakedModel model, ModelTransformations.Type transform) {
        Object candidate = eligibleStack(CURRENT.get(), model, transform,
                transform == ModelTransformations.Type.FIRST_PERSON, pair, matrix,
                AnimationsConfig.getEnabled(), AnimationsConfig.getMode());
        if (!(candidate instanceof ItemStack stack) || Api.TRANSFORM == null) return false;
        try {
            return (boolean) Api.TRANSFORM.invokeExact(stack, model);
        } catch (Throwable failure) {
            throw unchecked(failure);
        }
    }

    static Object eligibleStack(Scope scope, Object model, Object transform, boolean firstPerson,
                                Object pair, Object matrix, boolean enabled, int mode) {
        if (!enabled || mode != 0 || !firstPerson || model == null || matrix == null
                || !model.getClass().getName().equals(MODEL) || !matrix.getClass().getName().equals(MATRIX)
                || pair == null || pair.getClass() != ImmutablePair.class) return null;
        ImmutablePair<?, ?> result = (ImmutablePair<?, ?>) pair;
        if (result.left != model || result.right != matrix) return null;
        return scope.stackFor(model, transform);
    }

    private static final class Api {
        static final MethodHandle TRANSFORM = resolve();

        private static MethodHandle resolve() {
            try {
                Class<?> type = Class.forName("org.polyfrost.overflowanimations.legacy.firstperson.LegacyFirstPerson",
                        false, LegacyForgeCameraTransforms.class.getClassLoader());
                var method = type.getMethod("applyFirstPersonTransform", ItemStack.class, BakedModel.class);
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.getReturnType() != boolean.class) return null;
                return MethodHandles.publicLookup().unreflect(method).asType(MethodType.methodType(boolean.class,
                        ItemStack.class, BakedModel.class));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException unchecked(Throwable failure) throws T {
        throw (T) failure;
    }

    static final class Scope {
        private final Object[] stacks = new Object[8], models = new Object[8], transforms = new Object[8];
        private int depth, overflow;

        int enter(Object stack, Object model, Object transform) {
            if (overflow != 0 || depth == stacks.length) return -(++overflow);
            stacks[depth] = stack;
            models[depth] = model;
            transforms[depth] = transform;
            return ++depth;
        }

        void exit(int token) {
            if (token < 0) {
                if (overflow != -token) throw unbalanced();
                overflow--;
                return;
            }
            if (token == 0 || overflow != 0 || depth != token) throw unbalanced();
            --depth;
            stacks[depth] = models[depth] = transforms[depth] = null;
        }

        Object stackFor(Object model, Object transform) {
            if (overflow != 0 || depth == 0) return null;
            int index = depth - 1;
            return models[index] == model && transforms[index] == transform ? stacks[index] : null;
        }

        private static IllegalStateException unbalanced() {
            return new IllegalStateException("First-person item scopes did not unwind in order");
        }
    }
}
