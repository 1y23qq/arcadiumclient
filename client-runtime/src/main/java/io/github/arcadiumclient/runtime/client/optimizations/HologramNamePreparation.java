// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class HologramNamePreparation {
    private static final Scope CURRENT = new Scope();
    public static long batchTypedCalls, layoutTypedCalls;
    public static int preparedSites;

    private HologramNamePreparation() { }
    public static Object enter(Object owner) { return CURRENT.enter(owner); }
    public static void exit(Object previous) { CURRENT.exit(previous); }
    public static boolean active(Object owner) { return CURRENT.active(owner); }
    public static void noteTyped(int kind) { if (kind == 0) batchTypedCalls++; else layoutTypedCalls++; }

    static final class Scope {
        private static final Object SKIPPED = new Object();
        private volatile Thread thread;
        private Object current;

        Object enter(Object owner) {
            if (owner == null) return SKIPPED;
            Thread caller = Thread.currentThread();
            if (thread == null) {
                synchronized (this) { if (thread == null) thread = caller; }
            }
            if (thread != caller) return SKIPPED;
            Object previous = current;
            current = owner;
            return previous;
        }

        void exit(Object previous) {
            if (previous != SKIPPED && Thread.currentThread() == thread) current = previous;
        }

        boolean active(Object owner) {
            return owner != null && Thread.currentThread() == thread && current == owner;
        }
    }
}
