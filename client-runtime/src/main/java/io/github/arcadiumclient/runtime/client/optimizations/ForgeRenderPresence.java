// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Modifier;

final class ForgeRenderPresence {
    private static final Snapshot BOOT = load();

    static final class Snapshot {
        private final boolean nativeRequired;
        private final MethodHandle configuration;

        private Snapshot(boolean nativeRequired, MethodHandle configuration) {
            this.nativeRequired = nativeRequired;
            this.configuration = configuration;
        }

        boolean active() {
            if (nativeRequired) return true;
            if (configuration == null) return false;
            try { return (Object)configuration.invokeExact() != null; }
            catch (VirtualMachineError | ThreadDeath fatal) { throw fatal; }
            catch (Throwable unavailable) { return true; }
        }
    }

    private ForgeRenderPresence() {}

    static boolean active() { return BOOT.active(); }

    static Snapshot inspect(boolean prepared, Class<?> context) {
        if (prepared) return new Snapshot(true, null);
        if (context == null) return new Snapshot(false, null);
        try {
            var field = context.getDeclaredField("configuration");
            if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) return new Snapshot(true, null);
            var lookup = MethodHandles.privateLookupIn(context, MethodHandles.lookup());
            var reader = lookup.unreflectVarHandle(field).toMethodHandle(VarHandle.AccessMode.GET_VOLATILE)
                    .asType(MethodType.methodType(Object.class));
            return new Snapshot(false, reader);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return new Snapshot(true, null);
        }
    }

    private static Snapshot load() {
        try {
            if (Boolean.getBoolean("arcadium.forge.prepared")) return inspect(true, null);
            return inspect(false, Class.forName("io.github.arcadiumclient.forgecompat.ForgeRuntimeContext"));
        } catch (ClassNotFoundException absent) {
            return inspect(false, null);
        } catch (RuntimeException | LinkageError unavailable) {
            return new Snapshot(true, null);
        }
    }
}
