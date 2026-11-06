// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HologramNamePreparationTest {
    @Test void ownerIdentityAndNestedExceptionsRestoreTheOuterCall() {
        var scope = new HologramNamePreparation.Scope();
        Object first = new String("same"), second = new String("same");
        assertFalse(scope.active(first));
        Object outer = scope.enter(first);
        try {
            assertTrue(scope.active(first)); assertFalse(scope.active(second));
            IllegalStateException expected = new IllegalStateException("native callback failure");
            assertSame(expected, assertThrows(IllegalStateException.class, () -> {
                Object nested = scope.enter(second);
                try { assertTrue(scope.active(second)); assertFalse(scope.active(first)); throw expected; }
                finally { scope.exit(nested); }
            }));
            assertTrue(scope.active(first)); assertFalse(scope.active(second));
        } finally { scope.exit(outer); }
        assertFalse(scope.active(first)); assertFalse(scope.active(second));
    }

    @Test void anotherThreadCannotBorrowOrReplaceTheRenderOwner() throws Exception {
        var scope = new HologramNamePreparation.Scope();
        Object first = new Object(), second = new Object();
        Object outer = scope.enter(first);
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                assertFalse(scope.active(first));
                scope.exit(null);
                Object nested = scope.enter(second);
                try { assertFalse(scope.active(first)); assertFalse(scope.active(second)); }
                finally { scope.exit(nested); }
            }).get(5, TimeUnit.SECONDS);
            assertTrue(scope.active(first)); assertFalse(scope.active(second));
        } finally { scope.exit(outer); }
        assertFalse(scope.active(first));
    }

    @Test void nullScopesDoNotHideAnActiveNativeCall() {
        var scope = new HologramNamePreparation.Scope();
        Object owner = new Object(), outer = scope.enter(owner);
        try {
            Object absent = scope.enter(null);
            try { assertTrue(scope.active(owner)); assertFalse(scope.active(null)); }
            finally { scope.exit(absent); }
            assertTrue(scope.active(owner));
        } finally { scope.exit(outer); }
        assertFalse(scope.active(owner));
    }

    @Test void nativeFailureRetainsNoEntityAfterTheFinallyBoundary() throws Exception {
        var scope = new HologramNamePreparation.Scope(); Object owner = new Object();
        RuntimeException failure = new RuntimeException("native name callback");
        assertSame(failure, assertThrows(RuntimeException.class, () -> {
            Object previous = scope.enter(owner);
            try { assertTrue(scope.active(owner)); throw failure; }
            finally { scope.exit(previous); }
        }));
        var retained = HologramNamePreparation.Scope.class.getDeclaredField("current"); retained.setAccessible(true);
        assertNull(retained.get(scope));
        assertEquals(2, java.util.Arrays.stream(HologramNamePreparation.Scope.class.getDeclaredFields())
            .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers())).count());
    }

    @Test void repeatedScopesDoNotAllocatePerCallState() {
        Object owner = new Object();
        for (int i = 0; i < 50000; i++) { Object previous = HologramNamePreparation.enter(owner); HologramNamePreparation.active(owner); HologramNamePreparation.exit(previous); }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().threadId(), minimum = Long.MAX_VALUE;
        for (int pass = 0; pass < 3; pass++) {
            long before = bean.getThreadAllocatedBytes(id);
            for (int i = 0; i < 100000; i++) { Object previous = HologramNamePreparation.enter(owner); HologramNamePreparation.active(owner); HologramNamePreparation.exit(previous); }
            minimum = Math.min(minimum, bean.getThreadAllocatedBytes(id) - before);
        }
        assertEquals(0, minimum); assertFalse(HologramNamePreparation.active(owner));
    }
}
