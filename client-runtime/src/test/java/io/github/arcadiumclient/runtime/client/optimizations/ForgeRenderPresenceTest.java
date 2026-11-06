// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ForgeRenderPresenceTest {
    private static final class Context { private static volatile Object configuration; }
    private static final class Missing {}
    private static final class Primitive { private static volatile int configuration; }
    private static final class Instance { private Object configuration; }
    private static boolean initialized;
    private static final class LazyContext {
        private static volatile Object configuration = initialize();
        private static Object initialize() { initialized = true; return new Object(); }
    }

    @Test void absentCompatibilityKeepsTheCompactRendererAvailable() {
        assertFalse(ForgeRenderPresence.inspect(false, null).active());
    }

    @Test void preparedBootRequiresNativeCallbacksWithoutInitializingTheContext() {
        assertTrue(ForgeRenderPresence.inspect(true, LazyContext.class).active());
        assertFalse(initialized);
    }

    @Test void volatileConfigurationChangesRemainImmediatelyObservable() {
        Context.configuration = null;
        var presence = ForgeRenderPresence.inspect(false, Context.class);
        try {
            assertFalse(presence.active());
            Context.configuration = new Object();
            assertTrue(presence.active());
            Context.configuration = null;
            assertFalse(presence.active());
        } finally { Context.configuration = null; }
    }

    @Test void unexpectedContextLayoutsRetainNativeCallbacks() {
        assertTrue(ForgeRenderPresence.inspect(false, Missing.class).active());
        assertTrue(ForgeRenderPresence.inspect(false, Primitive.class).active());
        assertTrue(ForgeRenderPresence.inspect(false, Instance.class).active());
    }

    @Test void configurationPublicationFromAnotherThreadIsNotCachedAsAbsent() throws Exception {
        var presence = ForgeRenderPresence.inspect(false, Context.class);
        Context.configuration = null;
        Object published = new Object();
        Thread writer = new Thread(() -> Context.configuration = published);
        try {
            writer.start(); writer.join();
            assertTrue(presence.active());
            Context.configuration = null;
            assertFalse(presence.active());
        } finally { writer.join(); Context.configuration = null; }
    }

    @Test void repeatedVolatileQueriesDoNotAllocate() {
        var bean = assertInstanceOf(com.sun.management.ThreadMXBean.class, ManagementFactory.getThreadMXBean());
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        var presence = ForgeRenderPresence.inspect(false, Context.class);
        Context.configuration = new Object();
        try {
            int count = 0;
            for (int i = 0; i < 50_000; i++) count += presence.active() ? 1 : 0;
            long minimum = Long.MAX_VALUE;
            for (int pass = 0; pass < 3; pass++) {
                long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
                for (int i = 0; i < 100_000; i++) count += presence.active() ? 1 : 0;
                minimum = Math.min(minimum, bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before);
            }
            assertEquals(350_000, count);
            assertEquals(0, minimum);
        } finally { Context.configuration = null; }
    }
}
