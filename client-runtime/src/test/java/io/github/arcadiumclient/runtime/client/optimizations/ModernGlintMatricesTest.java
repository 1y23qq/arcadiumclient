// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModernGlintMatricesTest {
    @Test void exactTextureMatricesAreSharedAndSnapshotted() {
        ModernGlintMatrices table = new ModernGlintMatrices(); table.beginFrame();
        Matrix4f texture = new Matrix4f().rotationZ(.4f).scale(.16f).translate(.125f, -.37f, 0);
        int first = table.intern(texture);
        assertEquals(first, table.intern(new Matrix4f(texture)));
        float x = table.values()[first * 16 + 12]; texture.translate(.5f, 0, 0);
        assertNotEquals(first, table.intern(texture));
        assertEquals(x, table.values()[first * 16 + 12]);
        assertEquals(2, table.count());
    }

    @Test void bitDifferencesIncludingSignedZeroRemainDistinct() {
        ModernGlintMatrices table = new ModernGlintMatrices(); table.beginFrame();
        int first = table.intern(new Matrix4f());
        assertNotEquals(first, table.intern(new Matrix4f().m01(-0f)));
        assertNotEquals(first, table.intern(new Matrix4f().m00(Math.nextUp(1f))));
        assertEquals(3, table.count());
    }

    @Test void budgetAndResetCannotRetainAnOldFrameIndex() {
        ModernGlintMatrices table = new ModernGlintMatrices(); table.beginFrame();
        Matrix4f texture = new Matrix4f();
        for (int i = 0; i < ModernGlintMatrices.LIMIT; i++) assertEquals(i, table.intern(texture.translation(i, 0, 0)));
        assertEquals(0, table.remaining());
        assertEquals(2, table.intern(texture.translation(2, 0, 0)));
        assertEquals(-1, table.intern(texture.translation(ModernGlintMatrices.LIMIT, 0, 0)));
        long previous = table.generation(); table.beginFrame();
        assertTrue(table.generation() > previous);
        assertEquals(0, table.count());
        assertEquals(0, table.intern(texture.translation(999, 0, 0)));
        assertEquals(999, table.values()[12]);
    }

    @Test void unsupportedMatricesNeverConsumeAValidSlot() {
        ModernGlintMatrices table = new ModernGlintMatrices();
        assertEquals(-1, table.intern(new Matrix4f().m00(Float.NaN)));
        assertEquals(-1, table.intern(new Matrix4f().m30(Float.POSITIVE_INFINITY)));
        assertEquals(-1, table.intern(new Matrix4f().m03(.1f)));
        assertEquals(-1, table.intern(new Matrix4f().m33(0)));
        assertEquals(0, table.count());
    }

    @Test void markersAreExactAndNeverOverlapNativeNonnegativeTimes() {
        for (int i = 0; i < ModernGlintMatrices.LIMIT; i++) assertEquals(i, ModernGlintMatrices.decode(ModernGlintMatrices.marker(i), ModernGlintMatrices.LIMIT));
        for (float value : new float[]{0, 1, 160.5f, Float.MAX_VALUE, Float.NaN, Float.NEGATIVE_INFINITY, -.5f, -1.5f})
            assertEquals(-1, ModernGlintMatrices.decode(value, ModernGlintMatrices.LIMIT));
        assertEquals(-1, ModernGlintMatrices.decode(-2, 1));
        assertThrows(IllegalArgumentException.class, () -> ModernGlintMatrices.marker(-1));
    }
}
