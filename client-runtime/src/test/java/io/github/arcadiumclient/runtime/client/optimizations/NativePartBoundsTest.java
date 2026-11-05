// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NativePartBoundsTest {
    @Test void alreadyScaledOriginalVerticesReceiveTheBonePoseExactlyOnce() {
        float[] template = new float[12 * 16];
        NativePartBounds.write(template, 3, true, 4.5f, -.5f, -2.5f, -9, 13, 5);
        Matrix4f pose = new Matrix4f().translation(.31f, 1.7f, -13).rotateZYX(.13f, .7f, -.8f).scale(.9375f);
        int at = 3 * 16;
        Matrix4f gpu = new Matrix4f(pose).translate(template[at + 8], template[at + 9], template[at + 10])
                .scale(template[at + 12], template[at + 13], template[at + 14]);
        for (int corner = 0; corner < 8; corner++) {
            Vector3f nativeVertex = new Vector3f((4.5f - ((corner & 1) == 0 ? 0 : 9)) * .0625f,
                    (-.5f + ((corner & 2) == 0 ? 0 : 13)) * .0625f, (-2.5f + ((corner & 4) == 0 ? 0 : 5)) * .0625f);
            assertEquals(pose.transformPosition(new Vector3f(nativeVertex)), gpu.transformPosition(new Vector3f(nativeVertex)));
        }
    }

    @Test void legacySharedCubesKeepTheExistingMirroredBounds() {
        float[] template = new float[12 * 16];
        NativePartBounds.write(template, 1, false, 4.5f, -.5f, -2.5f, -9, 13, 5);
        int at = 16;
        assertArrayEquals(new float[]{4.5f * .0625f, -.5f * .0625f, -2.5f * .0625f}, new float[]{template[at + 8], template[at + 9], template[at + 10]});
        assertArrayEquals(new float[]{-9 * .0625f, 13 * .0625f, 5 * .0625f}, new float[]{template[at + 12], template[at + 13], template[at + 14]});
        Matrix4f box = new Matrix4f().translate(template[at + 8], template[at + 9], template[at + 10])
                .scale(template[at + 12], template[at + 13], template[at + 14]);
        assertEquals(new Vector3f(-4.5f * .0625f, 12.5f * .0625f, 2.5f * .0625f), box.transformPosition(new Vector3f(1)));
        assertEquals(0, template[at + 4]);
        assertEquals(0, template[at - 1]);
        assertEquals(0, template[at + 16]);
    }
}
