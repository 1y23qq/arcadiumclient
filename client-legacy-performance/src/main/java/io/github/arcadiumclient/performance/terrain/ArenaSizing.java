// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance.terrain;

final class ArenaSizing {
    static final int INITIAL_BYTES = 4 * 1024 * 1024;
    static final int MAX_NORMAL_BYTES = 16 * 1024 * 1024;
    static final int QUANTUM = 1024;

    private ArenaSizing() { }

    static int nextCapacity(int requiredBytes, int previousCapacity) {
        if (requiredBytes <= 0 || previousCapacity < 0) throw new IllegalArgumentException("Invalid terrain arena size");
        int grown = (int) Math.min(MAX_NORMAL_BYTES, Math.max(INITIAL_BYTES, (long) previousCapacity * 2));
        int payload = Math.multiplyExact(Math.addExact(requiredBytes, QUANTUM - 1) / QUANTUM, QUANTUM);
        return Math.max(grown, payload);
    }
}
