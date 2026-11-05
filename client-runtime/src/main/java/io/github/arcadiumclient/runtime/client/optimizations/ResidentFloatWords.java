// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public final class ResidentFloatWords {
    private int[] words = new int[0];
    private IntBuffer destination;
    private int destinationStart, used, dirtyRanges;
    private long generation;
    private boolean valid, fullWrite;

    public int write(FloatBuffer source, IntBuffer target, long storageGeneration) {
        int count = source.remaining();
        if (target.remaining() < count || target.isReadOnly()) throw new IllegalArgumentException("Resident destination is too small or read-only");
        boolean full = !valid || generation != storageGeneration || destination != target || destinationStart != target.position();
        if (words.length < count) words = Arrays.copyOf(words, Math.max(count, Math.max(16, words.length * 2)));
        int dirty = 0, ranges = 0, first = source.position(), output = target.position();
        boolean run = false;
        for (int index = 0; index < count; index++) {
            int value = Float.floatToRawIntBits(source.get(first + index));
            if (full || index >= used || words[index] != value) {
                target.put(output + index, value); words[index] = value; dirty++;
                if (!run) ranges++;
                run = true;
            } else run = false;
        }
        destination = target; destinationStart = output; used = count;
        generation = storageGeneration; valid = true; fullWrite = full; dirtyRanges = ranges;
        return dirty;
    }

    public int dirtyRanges() { return dirtyRanges; }
    public int usedWords() { return used; }
    public int capacity() { return words.length; }
    public boolean fullWrite() { return fullWrite; }
    public void invalidate() { valid = false; used = dirtyRanges = 0; destination = null; }
}
