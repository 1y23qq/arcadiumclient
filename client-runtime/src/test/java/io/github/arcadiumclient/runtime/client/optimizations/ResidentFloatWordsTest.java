// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResidentFloatWordsTest {
    @Test void writesRawNanPayloadsAndSignedZeroWithoutCanonicalizingThem() {
        int[] bits = {0x00000000, 0x80000000, 0x7fc01234, 0xffc05678};
        FloatBuffer source = FloatBuffer.allocate(bits.length);
        for (int index = 0; index < bits.length; index++) source.put(index, Float.intBitsToFloat(bits[index]));
        IntBuffer destination = IntBuffer.allocate(bits.length);
        ResidentFloatWords shadow = new ResidentFloatWords();
        assertEquals(4, shadow.write(source, destination, 1));
        assertArrayEquals(bits, destination.array());
        assertEquals(0, shadow.write(source, destination, 1));
        source.put(0, -0f); source.put(2, Float.intBitsToFloat(0x7fc06543));
        assertEquals(2, shadow.write(source, destination, 1));
        assertEquals(0x80000000, destination.get(0)); assertEquals(0x7fc06543, destination.get(2));
    }

    @Test void respectsBufferWindowsAndNeverChangesPositionsOrGuardWords() {
        FloatBuffer source = FloatBuffer.wrap(new float[]{99, 1, 2, 3, 88}); source.position(1).limit(4);
        int[] words = {7, 7, 7, 7, 7, 7, 7};
        IntBuffer target = IntBuffer.wrap(words); target.position(2).limit(5);
        ResidentFloatWords shadow = new ResidentFloatWords();
        assertEquals(3, shadow.write(source, target, 0));
        assertEquals(1, source.position()); assertEquals(4, source.limit());
        assertEquals(2, target.position()); assertEquals(5, target.limit());
        assertArrayEquals(new int[]{7, 7, Float.floatToRawIntBits(1), Float.floatToRawIntBits(2), Float.floatToRawIntBits(3), 7, 7}, words);
    }

    @Test void dirtyRangesIncludeFirstLastAndAdjacentChangedWords() {
        FloatBuffer source = FloatBuffer.allocate(9); IntBuffer target = IntBuffer.allocate(9);
        ResidentFloatWords shadow = new ResidentFloatWords();
        assertEquals(9, shadow.write(source, target, 0)); assertEquals(1, shadow.dirtyRanges());
        for (int index : new int[]{0, 3, 4, 8}) source.put(index, 1);
        assertEquals(4, shadow.write(source, target, 0)); assertEquals(3, shadow.dirtyRanges());
        assertFalse(shadow.fullWrite());
    }

    @Test void regrownWordsAreRewrittenEvenIfTheirOldShadowWasIdentical() {
        FloatBuffer source = FloatBuffer.wrap(new float[]{1, 2, 3, 4, 5}); IntBuffer target = IntBuffer.allocate(8);
        ResidentFloatWords shadow = new ResidentFloatWords();
        assertEquals(5, shadow.write(source, target, 7));
        source.limit(2); assertEquals(0, shadow.write(source, target, 7));
        for (int index = 2; index < 5; index++) target.put(index, 0x5a5a5a5a);
        source.limit(5); assertEquals(3, shadow.write(source, target, 7));
        for (int index = 0; index < 5; index++) assertEquals(Float.floatToRawIntBits(index + 1f), target.get(index));
        assertEquals(5, shadow.usedWords());
    }

    @Test void generationInvalidationAndDestinationReplacementForceCompleteWrites() {
        FloatBuffer source = FloatBuffer.wrap(new float[]{0, 2, 0, 4}); IntBuffer target = IntBuffer.allocate(4);
        ResidentFloatWords shadow = new ResidentFloatWords();
        assertEquals(4, shadow.write(source, target, 10));
        assertEquals(0, shadow.write(source, target, 10));
        assertEquals(4, shadow.write(source, target, 11)); assertTrue(shadow.fullWrite());
        IntBuffer resized = IntBuffer.allocate(16);
        assertEquals(4, shadow.write(source, resized, 11)); assertTrue(shadow.fullWrite());
        shadow.invalidate();
        assertEquals(4, shadow.write(source, resized, 11)); assertTrue(shadow.fullWrite());
        resized.position(4);
        assertEquals(4, shadow.write(source, resized, 11)); assertTrue(shadow.fullWrite());
    }

    @Test void randomizedSparseUpdatesMatchCompleteRawCopiesAcrossResizes() {
        ResidentFloatWords shadow = new ResidentFloatWords(); Random random = new Random(0x072f10a7);
        FloatBuffer source = FloatBuffer.allocate(513); IntBuffer target = IntBuffer.allocate(513);
        for (int round = 0; round < 300; round++) {
            int length = random.nextInt(514); source.limit(513);
            for (int mutation = 0; mutation < 11; mutation++) source.put(random.nextInt(513), Float.intBitsToFloat(random.nextInt()));
            source.limit(length); shadow.write(source, target, round / 31);
            for (int index = 0; index < length; index++) assertEquals(Float.floatToRawIntBits(source.get(index)), target.get(index));
            assertTrue(shadow.capacity() >= length);
        }
    }
}
