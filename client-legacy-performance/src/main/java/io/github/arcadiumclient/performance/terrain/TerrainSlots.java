// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.terrain;

public final class TerrainSlots<T> {
    private final int firstId;
    private final Object[] slots;

    public TerrainSlots(int firstId, int capacity) {
        if (firstId < 0 || capacity <= 0 || (long) firstId + capacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid terrain display-list range");
        }
        this.firstId = firstId;
        slots = new Object[capacity];
    }

    @SuppressWarnings("unchecked")
    public T get(int id) {
        int index = id - firstId;
        return index >= 0 && index < slots.length ? (T) slots[index] : null;
    }

    @SuppressWarnings("unchecked")
    public T set(int id, T next) {
        int index = id - firstId;
        if (index < 0 || index >= slots.length) throw new IllegalArgumentException("Unknown terrain list: " + id);
        T old = (T) slots[index];
        slots[index] = next;
        return old;
    }

    public void clear() {
        java.util.Arrays.fill(slots, null);
    }
}
