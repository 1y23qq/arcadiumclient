// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainResidentResourcesTest {
    @Test void ownerRejectionLeavesEveryDeletionUnattempted() {
        AtomicBoolean current = new AtomicBoolean(true);
        int[] calls = new int[TerrainResidentResources.LIMIT];
        var resources = new TerrainResidentResources(() -> {
            if (!current.get()) throw new IllegalStateException("Foreign context");
        });
        for (int i = 0; i < calls.length; i++) {
            int slot = i;
            resources.add(() -> calls[slot]++);
        }
        current.set(false);
        assertThrows(IllegalStateException.class, resources::close);
        assertEquals(calls.length, resources.unattempted());
        assertEquals(0, resources.ambiguous());
        assertArrayEquals(new int[calls.length], calls);
        current.set(true);
        resources.close();
        assertTrue(resources.empty());
        for (int call : calls) assertEquals(1, call);
    }

    @Test void opaqueFailuresBeforeDeletionAreNotAssumedSafeToReplay() {
        deletionMasks(false);
    }

    @Test void postDeletionFailuresCannotDeleteReusedNames() {
        deletionMasks(true);
    }

    private static void deletionMasks(boolean afterDeletion) {
        for (int mask = 0; mask < 1 << TerrainResidentResources.LIMIT; mask++) {
            int failures = mask;
            int[] calls = new int[TerrainResidentResources.LIMIT];
            Object[] owned = new Object[calls.length], replacement = new Object[calls.length];
            Map<Integer, Object> names = new HashMap<>();
            var resources = new TerrainResidentResources(() -> {});
            for (int i = 0; i < calls.length; i++) {
                int slot = i, name = 100 + i;
                owned[i] = new Object();
                replacement[i] = new Object();
                names.put(name, owned[i]);
                resources.add(() -> {
                    calls[slot]++;
                    boolean fault = (failures & 1 << slot) != 0;
                    if (fault && !afterDeletion) throw new IllegalStateException("Opaque pre-call failure");
                    assertSame(owned[slot], names.remove(name));
                    if (fault) {
                        names.put(name, replacement[slot]);
                        throw new IllegalStateException("Delete completed before wrapper failure");
                    }
                });
            }
            for (int attempt = 0; attempt < 4; attempt++) {
                if (mask == 0) resources.close();
                else assertThrows(IllegalStateException.class, resources::close);
                assertEquals(Integer.bitCount(mask), resources.retained());
                assertEquals(Integer.bitCount(mask), resources.ambiguous());
                assertEquals(0, resources.unattempted());
                for (int i = 0; i < calls.length; i++) {
                    assertEquals(1, calls[i], "Deletion replay at mask " + mask + ", slot " + i);
                    if ((mask & 1 << i) == 0) assertFalse(names.containsKey(100 + i));
                    else assertSame(afterDeletion ? replacement[i] : owned[i], names.get(100 + i));
                }
            }
            assertEquals(mask == 0, resources.empty());
        }
    }

    @Test void bufferProgramAndShaderTicketsQuarantineAmbiguousCompletion() {
        for (String kind : new String[]{"GlMutableBuffer", "program", "shader"}) {
            Object owned = new Object(), foreign = new Object();
            Map<Integer, Object> names = new HashMap<>();
            names.put(7, owned);
            int[] calls = {0};
            var resources = new TerrainResidentResources(() -> {});
            int ticket = resources.add(() -> {
                calls[0]++;
                assertSame(owned, names.remove(7));
                names.put(7, foreign);
                throw new IllegalStateException(kind + " completed, wrapper failed");
            });
            assertThrows(IllegalStateException.class, () -> resources.delete(ticket));
            assertThrows(IllegalStateException.class, resources::close);
            assertThrows(IllegalStateException.class, () -> resources.delete(ticket));
            assertEquals(1, calls[0]);
            assertSame(foreign, names.get(7));
            assertEquals(1, resources.ambiguous());
        }
    }

    @Test void constructorCleanupRetainsBudgetAndPassOwnershipUntilOutcomeIsKnown() {
        synchronized (TerrainGpuBudget.class) {
            long beforeBytes = TerrainGpuBudget.bytes();
            int beforeOwners = TerrainGpuBudget.owners();
            long available = TerrainGpuBudget.LIMIT - beforeBytes;
            assertTrue(available >= 2);
            Object pageOwner = new Object(), otherOwner = new Object(), deniedOwner = new Object();
            try {
                long reserved = available / 2;
                assertTrue(TerrainGpuBudget.reserve(pageOwner, reserved));
                int[] shaderCalls = {0}, otherCalls = {0};
                var resources = new TerrainResidentResources(() -> {});
                int shader = resources.add(() -> {
                    shaderCalls[0]++;
                    throw new IllegalStateException("Constructor shader cleanup outcome unknown");
                });
                for (int i = 1; i < TerrainResidentResources.LIMIT; i++) resources.add(() -> otherCalls[0]++);
                assertThrows(IllegalStateException.class, () -> resources.delete(shader));
                assertThrows(IllegalStateException.class, resources::close);
                AutoCloseable page = () -> {
                    try { resources.close(); }
                    finally { if (resources.empty()) TerrainGpuBudget.release(pageOwner); }
                };
                var book = new TerrainResidentPassBook<AutoCloseable>(new Object());
                assertTrue(book.put(new Object(), page));
                for (int i = 0; i < 3; i++) {
                    assertThrows(IllegalStateException.class, book::close);
                    assertEquals(1, book.size());
                    assertEquals(beforeBytes + reserved, TerrainGpuBudget.bytes());
                    assertEquals(beforeOwners + 1, TerrainGpuBudget.owners());
                    assertEquals(1, shaderCalls[0]);
                    assertEquals(TerrainResidentResources.LIMIT - 1, otherCalls[0]);
                }
                assertTrue(TerrainGpuBudget.reserve(otherOwner, available - reserved));
                assertEquals(TerrainGpuBudget.LIMIT, TerrainGpuBudget.bytes());
                assertFalse(TerrainGpuBudget.reserve(deniedOwner, 1));
            } finally {
                // Release only CPU-fixture reservations; the ambiguous ledger is never declared retired.
                TerrainGpuBudget.release(pageOwner);
                TerrainGpuBudget.release(otherOwner);
                TerrainGpuBudget.release(deniedOwner);
            }
            assertEquals(beforeBytes, TerrainGpuBudget.bytes());
            assertEquals(beforeOwners, TerrainGpuBudget.owners());
        }
    }

    @Test void sharedFailureAndReentryStillCleanUnattemptedResources() {
        RuntimeException failure = new IllegalStateException("Shared failure instance");
        int[] calls = {0};
        var resources = new TerrainResidentResources(() -> {});
        resources.add(() -> { throw failure; });
        resources.add(() -> calls[0]++);
        resources.add(() -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, resources::close));
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(2, resources.ambiguous());
        assertEquals(1, calls[0]);

        var reentrant = new TerrainResidentResources(() -> {});
        reentrant.add(() -> calls[0]++);
        reentrant.add(reentrant::close);
        assertThrows(IllegalStateException.class, reentrant::close);
        assertThrows(IllegalStateException.class, reentrant::close);
        assertEquals(1, reentrant.ambiguous());
        assertEquals(2, calls[0]);
    }

    @Test void resourceCapacityAndInvalidTicketsNeverInvokeDeletion() {
        int[] calls = {0};
        var resources = new TerrainResidentResources(() -> {});
        for (int i = 0; i < TerrainResidentResources.LIMIT; i++) resources.add(() -> calls[0]++);
        assertThrows(IllegalStateException.class, () -> resources.add(() -> fail("Beyond bound")));
        assertThrows(IllegalArgumentException.class, () -> resources.delete(-1));
        assertThrows(IllegalArgumentException.class, () -> resources.delete(TerrainResidentResources.LIMIT));
        assertEquals(0, calls[0]);
        resources.close();
        resources.close();
        assertEquals(TerrainResidentResources.LIMIT, calls[0]);
    }
}
