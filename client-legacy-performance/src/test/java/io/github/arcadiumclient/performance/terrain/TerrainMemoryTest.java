package io.github.arcadiumclient.performance.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TerrainMemoryTest {
    @Test void fragmentedArenaNeverOverlapsAndFullyCoalescesAfterRebuilds() {
        ArenaAllocator arena = new ArenaAllocator(1024 * 1024, 1024);
        List<ArenaAllocator.Range> live = new ArrayList<>();
        Random random = new Random(1710);
        for (int step = 0; step < 20_000; step++) {
            if (!live.isEmpty() && random.nextInt(3) == 0) {
                arena.free(live.remove(random.nextInt(live.size())));
            } else {
                int payload = random.nextInt(64 * 1024) + 1;
                ArenaAllocator.Range next = arena.allocate(payload);
                if (next == null) continue;
                assertTrue(next.length >= payload);
                assertEquals(0, next.offset % 1024);
                assertEquals(0, next.length % 1024);
                for (ArenaAllocator.Range other : live) {
                    assertTrue(next.offset + next.length <= other.offset || other.offset + other.length <= next.offset);
                }
                live.add(next);
            }
            assertEquals(live.size(), arena.allocationCount());
            assertEquals(live.stream().mapToInt(range -> range.length).sum(), arena.liveBytes());
        }
        for (ArenaAllocator.Range range : live) arena.free(range);
        assertEquals(0, arena.liveBytes());
        assertEquals(0, arena.allocationCount());
        assertEquals(1, arena.freeBlockCount());
        assertEquals(arena.capacity(), arena.largestFreeBlock());
        assertNotNull(arena.allocate(arena.capacity()));
    }

    @Test void rejectsDoubleFreeAndOverflowInsteadOfAliasingLiveGeometry() {
        ArenaAllocator arena = new ArenaAllocator(4096, 1024);
        ArenaAllocator.Range range = arena.allocate(1025);
        arena.free(range);
        assertThrows(IllegalStateException.class, () -> arena.free(range));
        assertThrows(IllegalArgumentException.class, () -> arena.allocate(0));
        assertThrows(ArithmeticException.class, () -> arena.allocate(Integer.MAX_VALUE));
    }

    @Test void nativeDisplayListRangeKeepsBothLayersSeparateAndDropsStaleSlots() {
        int first = 401;
        int count = 34 * 34 * 16 * 3;
        TerrainSlots<Object> slots = new TerrainSlots<>(first, count);
        Object[] layers = {new Object(), new Object()};
        for (int id = first; id < first + count; id += 3) {
            assertNull(slots.set(id, layers[0]));
            assertNull(slots.set(id + 1, layers[1]));
            assertNull(slots.get(id + 2));
        }
        for (int id = first; id < first + count; id += 3) {
            assertSame(layers[0], slots.get(id));
            assertSame(layers[1], slots.get(id + 1));
        }
        assertNull(slots.get(first - 1));
        assertNull(slots.get(first + count));
        assertThrows(IllegalArgumentException.class, () -> slots.set(first + count, new Object()));
        slots.clear();
        for (int id = first; id < first + count; id++) assertNull(slots.get(id));
    }
}
