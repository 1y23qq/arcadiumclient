// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.ArrayList;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

final class NativePoseGroups {
    static final int LIMIT = 256;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private int count;

    static final class Entry {
        Object profile, pass;
        final Matrix4f root = new Matrix4f();
        int job, written;
        void mark(int part) { written |= 1 << part; }
    }

    Entry find(Object profile, Object pass, Matrix4fc root, int part) {
        for (int i = count - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (entry.profile == profile && entry.pass == pass && (entry.written & 1 << part) == 0 && sameRoot(entry.root, root)) return entry;
        }
        return null;
    }

    boolean full() { return count == LIMIT; }
    int remaining() { return LIMIT - count; }

    Entry add(Object profile, Object pass, Matrix4fc root, int job) {
        if (full()) return null;
        if (count == entries.size()) entries.add(new Entry());
        Entry entry = entries.get(count++);
        entry.profile = profile; entry.pass = pass; entry.root.set(root); entry.job = job; entry.written = 0;
        return entry;
    }

    void removeLast(Entry entry) {
        if (count > 0 && entries.get(count - 1) == entry) {
            count--; entry.profile = entry.pass = null; entry.written = 0;
        }
    }

    void clear() {
        for (int i = 0; i < count; i++) {
            Entry entry = entries.get(i); entry.profile = entry.pass = null; entry.written = 0;
        }
        count = 0;
    }

    static boolean sameRoot(Matrix4fc a, Matrix4fc b) {
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++)
            if (Float.floatToRawIntBits(a.get(column, row)) != Float.floatToRawIntBits(b.get(column, row))) return false;
        return true;
    }
}
