// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.IntBuffer;
import java.util.IdentityHashMap;

final class ResolvedInstanceRecords {
    static final int MAX_OWNERS = 128, CPU_BYTES = 8 << 20, METADATA_BYTES = 64 << 10;
    static final int MAX_WORDS = (CPU_BYTES - METADATA_BYTES) / Integer.BYTES;
    private final IdentityHashMap<Object, Entry> owners = new IdentityHashMap<>();
    private final Entry[] pool = new Entry[MAX_OWNERS];
    private int used, allocatedWords;
    long comparedWords;

    static final class Stamp {
        Object backend;
        long epoch, models, actors, poses, templates;
        Stamp set(Object backend, long epoch, long models, long actors, long poses, long templates) {
            this.backend = backend; this.epoch = epoch; this.models = models;
            this.actors = actors; this.poses = poses; this.templates = templates; return this;
        }
        void copy(Stamp value) { set(value.backend, value.epoch, value.models, value.actors, value.poses, value.templates); }
        boolean same(Stamp other) {
            return backend == other.backend && epoch == other.epoch && models == other.models && actors == other.actors && poses == other.poses && templates == other.templates;
        }
    }

    static final class Entry {
        final Stamp stamp = new Stamp();
        Object buffer;
        int handle, first, count;
        int[] words = new int[0];
        long serial;
        boolean valid;
    }

    Entry find(Object owner, Object buffer, int handle, Stamp stamp, IntBuffer input) {
        Entry entry = owners.get(owner);
        return entry != null && entry.valid && entry.buffer == buffer && entry.handle == handle && entry.stamp.same(stamp) && equal(entry, input) ? entry : null;
    }

    Entry stage(Object owner, Object buffer, int handle, Stamp stamp, IntBuffer input) {
        Entry entry = owners.get(owner);
        if (entry != null) { entry.valid = false; entry.serial++; }
        int count = input.remaining();
        if (owner == null || buffer == null || handle == 0 || count <= 0 || count > MAX_WORDS) return null;
        if (entry == null) {
            if (used == MAX_OWNERS) return null;
            entry = pool[used];
            if (entry == null) pool[used] = entry = new Entry();
            entry.valid = false; entry.serial++;
        }
        if (entry.words.length < count) {
            int available = MAX_WORDS - allocatedWords + entry.words.length;
            if (count > available) return null;
            int capacity = Math.max(64, Integer.highestOneBit(count - 1) << 1);
            if (capacity <= 0 || capacity > available) capacity = count;
            allocatedWords += capacity - entry.words.length; entry.words = new int[capacity];
        }
        if (!owners.containsKey(owner)) { owners.put(owner, entry); used++; }
        entry.buffer = buffer; entry.handle = handle; entry.first = input.position(); entry.count = count; entry.stamp.copy(stamp);
        for (int word = 0; word < count; word++) entry.words[word] = input.get(entry.first + word);
        return entry;
    }

    boolean commit(Entry entry, long serial, Object buffer, int handle, Stamp current, IntBuffer input) {
        if (entry == null || entry.serial != serial || entry.buffer != buffer || entry.handle != handle || !entry.stamp.same(current) || !equal(entry, input)) return false;
        entry.valid = true; return true;
    }

    private boolean equal(Entry entry, IntBuffer input) {
        if (input.position() != entry.first || input.remaining() != entry.count) return false;
        for (int word = 0; word < entry.count; word++) {
            comparedWords++;
            if (entry.words[word] != input.get(entry.first + word)) return false;
        }
        return true;
    }

    void invalidate(Object owner) {
        Entry entry = owners.get(owner);
        if (entry != null) { entry.valid = false; entry.serial++; entry.buffer = null; }
    }

    void clear() {
        owners.clear();
        for (int index = 0; index < used; index++) {
            Entry entry = pool[index]; entry.valid = false; entry.serial++; entry.buffer = null; entry.stamp.backend = null;
        }
        used = 0;
    }

    int ownerCount() { return used; }
    long residentBytes() { return METADATA_BYTES + (long)allocatedWords * Integer.BYTES; }
}
