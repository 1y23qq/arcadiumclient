package io.github.arcadiumclient.performance.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaSizingTest {
    @Test void keepsSmallWorldAllocationAndBoundsGrowth() {
        int previous = 0;
        int[] expected = {4, 8, 16, 16, 16, 16};
        for (int mib : expected) {
            previous = ArenaSizing.nextCapacity(512 * 1024, previous);
            assertEquals(mib * 1024 * 1024, previous);
        }
        assertEquals(ArenaSizing.INITIAL_BYTES, ArenaSizing.nextCapacity(1, 0));
    }

    @Test void retainsLargeSingleMeshesAndExactAllocationQuantum() {
        int payload = ArenaSizing.MAX_NORMAL_BYTES + 1;
        int capacity = ArenaSizing.nextCapacity(payload, ArenaSizing.MAX_NORMAL_BYTES);
        assertEquals(ArenaSizing.MAX_NORMAL_BYTES + ArenaSizing.QUANTUM, capacity);
        ArenaAllocator allocator = new ArenaAllocator(capacity, ArenaSizing.QUANTUM);
        ArenaAllocator.Range range = allocator.allocate(payload);
        assertNotNull(range);
        assertEquals(0, range.offset);
        assertEquals(capacity, range.length);
        allocator.free(range);
        assertEquals(capacity, allocator.largestFreeBlock());
        assertEquals(ArenaSizing.MAX_NORMAL_BYTES, ArenaSizing.nextCapacity(1, capacity));
    }

    @Test void rejectsUnrepresentableSizesWithoutWrapping() {
        assertThrows(IllegalArgumentException.class, () -> ArenaSizing.nextCapacity(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ArenaSizing.nextCapacity(1, -1));
        assertThrows(ArithmeticException.class, () -> ArenaSizing.nextCapacity(Integer.MAX_VALUE, 0));
        assertEquals(ArenaSizing.MAX_NORMAL_BYTES, ArenaSizing.nextCapacity(1, Integer.MAX_VALUE));
    }
}
