// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ResolvedInstanceRecordsTest {
    private static ResolvedInstanceRecords.Stamp stamp(Object backend, long epoch, long models, long actors, long poses, long templates) {
        return new ResolvedInstanceRecords.Stamp().set(backend, epoch, models, actors, poses, templates);
    }
    private static IntBuffer payload(int value) { int[] words = new int[38]; Arrays.fill(words, value); return IntBuffer.wrap(words); }
    private static ResolvedInstanceRecords.Entry commit(ResolvedInstanceRecords records, Object owner, Object buffer, int handle, ResolvedInstanceRecords.Stamp stamp, IntBuffer input) {
        var entry = records.stage(owner, buffer, handle, stamp, input); assertNotNull(entry);
        assertTrue(records.commit(entry, entry.serial, buffer, handle, stamp, input)); return entry;
    }

    @Test void onlyAnExactlyPreparedPayloadCanReuseItsExistingOutput() {
        var records = new ResolvedInstanceRecords(); Object owner = new Object(), buffer = new Object(), backend = new Object();
        var stamp = stamp(backend, 1, 2, 3, 4, 5); var input = payload(19);
        var pending = records.stage(owner, buffer, 37, stamp, input);
        assertNull(records.find(owner, buffer, 37, stamp, input));
        assertTrue(records.commit(pending, pending.serial, buffer, 37, stamp, input));
        assertSame(pending, records.find(owner, buffer, 37, stamp, input));
        input.put(37, 20); assertNull(records.find(owner, buffer, 37, stamp, input));
        input.put(37, 19); assertSame(pending, records.find(owner, buffer, 37, stamp, input));
        assertNull(records.find(owner, new Object(), 37, stamp, input));
        assertNull(records.find(owner, buffer, 38, stamp, input));
        input.position(19); assertNull(records.find(owner, buffer, 37, stamp, input));
    }

    @Test void everyInputDomainAndBatchOwnerIsPartOfTheKey() {
        var records = new ResolvedInstanceRecords(); Object owner = new Object(), buffer = new Object(), backend = new Object();
        var input = payload(1); var original = stamp(backend, 4, 5, 6, 7, 8); commit(records, owner, buffer, 2, original, input);
        assertNull(records.find(owner, buffer, 2, stamp(new Object(), 4, 5, 6, 7, 8), input));
        assertNull(records.find(owner, buffer, 2, stamp(backend, 9, 5, 6, 7, 8), input));
        assertNull(records.find(owner, buffer, 2, stamp(backend, 4, 9, 6, 7, 8), input));
        assertNull(records.find(owner, buffer, 2, stamp(backend, 4, 5, 9, 7, 8), input));
        assertNull(records.find(owner, buffer, 2, stamp(backend, 4, 5, 6, 9, 8), input));
        assertNull(records.find(owner, buffer, 2, stamp(backend, 4, 5, 6, 7, 9), input));
    }

    @Test void anotherWriteDeletionAndResetCannotExposeAnOlderOutput() {
        var records = new ResolvedInstanceRecords(); Object owner = new Object(), buffer = new Object(); var stamp = stamp(new Object(), 1, 2, 3, 4, 5);
        commit(records, owner, buffer, 11, stamp, payload(1));
        commit(records, owner, buffer, 11, stamp, payload(2));
        assertNull(records.find(owner, buffer, 11, stamp, payload(1)));
        records.invalidate(owner); assertNull(records.find(owner, buffer, 11, stamp, payload(2)));
        commit(records, owner, buffer, 11, stamp, payload(2)); records.clear();
        assertNull(records.find(owner, buffer, 11, stamp, payload(2))); assertEquals(0, records.ownerCount());
    }

    @Test void stagingIsTransactionalEvenWhenInputsChangeOrARequestIsReentered() {
        var records = new ResolvedInstanceRecords(); Object owner = new Object(), buffer = new Object(); var stamp = stamp(new Object(), 1, 2, 3, 4, 5); var input = payload(1);
        var entry = records.stage(owner, buffer, 1, stamp, input); long serial = entry.serial;
        input.put(0, 2); assertFalse(records.commit(entry, serial, buffer, 1, stamp, input));
        input.put(0, 1); records.stage(owner, buffer, 1, stamp, input);
        assertFalse(records.commit(entry, serial, buffer, 1, stamp, input));
        assertFalse(records.commit(entry, entry.serial, buffer, 1, stamp(new Object(), 1, 2, 3, 4, 5), input));
        assertNull(records.find(owner, buffer, 1, stamp, input));
    }

    @Test void integerBitsIncludingSortFlagsSignedZeroAndNanPayloadsAreComparedExactly() {
        var records = new ResolvedInstanceRecords(); Object owner = new Object(), buffer = new Object(); var stamp = stamp(new Object(), 1, 2, 3, 4, 5); var input = payload(0);
        input.put(2, Float.floatToRawIntBits(-7654321f)); input.put(14, 0x7fc00001); commit(records, owner, buffer, 1, stamp, input);
        input.put(0, 0x80000000); assertNull(records.find(owner, buffer, 1, stamp, input)); input.put(0, 0);
        input.put(14, 0x7fc00002); assertNull(records.find(owner, buffer, 1, stamp, input)); input.put(14, 0x7fc00001);
        input.put(2, Float.floatToRawIntBits(-8765432f)); assertNull(records.find(owner, buffer, 1, stamp, input));
    }

    @Test void ownerAndByteBudgetsAreBoundedAndWarmCapacityIsReused() {
        var records = new ResolvedInstanceRecords(); Object buffer = new Object(); var stamp = stamp(new Object(), 1, 2, 3, 4, 5);
        for (int index = 0; index < ResolvedInstanceRecords.MAX_OWNERS; index++) commit(records, new Object(), buffer, 1, stamp, payload(index));
        assertNull(records.stage(new Object(), buffer, 1, stamp, payload(0)));
        long capacity = records.residentBytes(); records.clear();
        for (int index = 0; index < ResolvedInstanceRecords.MAX_OWNERS; index++) commit(records, new Object(), buffer, 1, stamp, payload(index));
        assertEquals(capacity, records.residentBytes()); assertTrue(capacity <= ResolvedInstanceRecords.CPU_BYTES);
        assertNull(records.stage(new Object(), buffer, 1, stamp, IntBuffer.allocate(ResolvedInstanceRecords.MAX_WORDS + 1)));
        assertTrue(records.residentBytes() <= ResolvedInstanceRecords.CPU_BYTES);
    }

    @Test void aggregateShadowGrowthCannotExceedTheCpuByteBudget() {
        var records = new ResolvedInstanceRecords(); Object buffer = new Object(); var stamp = stamp(new Object(), 1, 2, 3, 4, 5);
        IntBuffer input = IntBuffer.allocate(1 << 19);
        for (int index = 0; index < 3; index++) commit(records, new Object(), buffer, 1, stamp, input);
        assertNull(records.stage(new Object(), buffer, 1, stamp, input));
        assertTrue(records.residentBytes() <= ResolvedInstanceRecords.CPU_BYTES);
        long retained = records.residentBytes(); records.clear();
        for (int index = 0; index < 3; index++) commit(records, new Object(), buffer, 1, stamp, input);
        assertEquals(retained, records.residentBytes());
    }

    @Test void coldOrDeletedBufferIdentityCannotBePrepared() {
        var records = new ResolvedInstanceRecords(); Object owner = new Object(); var stamp = stamp(new Object(), 1, 2, 3, 4, 5);
        assertNull(records.stage(owner, null, 0, stamp, payload(1)));
        assertNull(records.stage(owner, new Object(), 0, stamp, payload(1)));
        assertEquals(0, records.ownerCount());
    }
}
