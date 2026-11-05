// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;
import org.joml.Matrix4fc;

final class ModernGlintMatrices {
    static final int LIMIT = 8192;
    private float[] values = new float[128 * 16];
    private final int[] slots = new int[LIMIT * 2], stamps = new int[LIMIT * 2];
    private int count, epoch = 1;
    private long generation;

    void beginFrame() {
        count = 0; generation++;
        if (++epoch == 0) { Arrays.fill(stamps, 0); epoch = 1; }
    }
    int count() { return count; }
    int remaining() { return LIMIT - count; }
    long generation() { return generation; }
    float[] values() { return values; }

    int intern(Matrix4fc matrix) {
        if (!finiteAffine(matrix)) return -1;
        int hash = 1;
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) hash = hash * 31 + Float.floatToRawIntBits(matrix.get(c, r));
        int slot = (hash ^ hash >>> 16) & (slots.length - 1);
        while (stamps[slot] == epoch) {
            int id = slots[slot], at = id * 16; boolean same = true;
            for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++)
                same &= Float.floatToRawIntBits(values[at + c * 4 + r]) == Float.floatToRawIntBits(matrix.get(c, r));
            if (same) return id;
            slot = slot + 1 & (slots.length - 1);
        }
        if (count == LIMIT) return -1;
        int id = count++, at = id * 16;
        if (at + 16 > values.length) values = Arrays.copyOf(values, Math.min(LIMIT * 16, values.length * 2));
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) values[at + c * 4 + r] = matrix.get(c, r);
        slots[slot] = id; stamps[slot] = epoch; return id;
    }

    static float marker(int id) {
        if (id < 0 || id >= LIMIT) throw new IllegalArgumentException("Invalid modern glint matrix");
        return -(id + 1f);
    }
    static int decode(float value, int count) {
        if (!Float.isFinite(value) || value > -1) return -1;
        float index = -value - 1; int id = (int)index;
        return index == id && id >= 0 && id < count ? id : -1;
    }
    private static boolean finiteAffine(Matrix4fc matrix) {
        if (matrix.m03() != 0 || matrix.m13() != 0 || matrix.m23() != 0 || matrix.m33() != 1) return false;
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) if (!Float.isFinite(matrix.get(c, r))) return false;
        return true;
    }
}
