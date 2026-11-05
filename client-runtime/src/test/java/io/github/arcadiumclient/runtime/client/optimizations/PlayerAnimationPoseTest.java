// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PlayerAnimationPoseTest {
    @Test void wavyArmsKeepTheirRollWhileIdleAndBlocking() {
        float[] right = pose(0, 1, 0, -10000, 0, 0, false, false, false, 2, PlayerPoseOptions.WAVY_ARMS);
        float[] left = pose(0, 1, 0, -10000, 0, 0, false, false, false, 3, PlayerPoseOptions.WAVY_ARMS);
        assertEquals(-2, right[3], .000001f);
        assertEquals(2.1f, right[5], .000001f);
        assertEquals(2, left[3], .000001f);
        assertEquals(-.1f, left[5], .000001f);
        float[] blocking = pose(0, 1, 0, -10000, 0, 3, false, false, false, 2, PlayerPoseOptions.WAVY_ARMS);
        assertEquals(2.1f, blocking[5], .000001f);
        assertEquals(-.5235988f, blocking[4], .000001f);
        float[] oldBlocking = pose(0, 1, 0, -10000, 0, 3, false, false, false, 2,
                PlayerPoseOptions.WAVY_ARMS | PlayerPoseOptions.OLD_BLOCKING);
        assertEquals(0, oldBlocking[4], .000001f);
    }

    @Test void bowPoseTakesPriorityOverWavyArms() {
        for (int sample = 0; sample < 64; sample++) {
            for (int bone = 0; bone < 6; bone++) {
                float[] ordinary = pose(sample * .13f, .8f, sample * 2.1f, sample / 64f, 1, 3,
                        sample % 3 == 0, sample % 7 == 0, true, bone, PlayerPoseOptions.OLD_BLOCKING);
                float[] wavy = pose(sample * .13f, .8f, sample * 2.1f, sample / 64f, 1, 3,
                        sample % 3 == 0, sample % 7 == 0, true, bone,
                        PlayerPoseOptions.OLD_BLOCKING | PlayerPoseOptions.WAVY_ARMS);
                assertArrayEquals(ordinary, wavy, 0f, "bone " + bone + " sample " + sample);
            }
        }
    }

    @Test void noWalkSpeedRetainsTheOrdinaryPose() {
        for (int sample = 0; sample < 64; sample++) {
            for (int bone = 0; bone < 6; bone++) {
                float[] ordinary = pose(sample * .3f, 0, sample * 7.2f, sample / 64f, 1, sample % 4,
                        sample % 3 == 0, sample % 7 == 0, false, bone, 0);
                float[] wavy = pose(sample * .3f, 0, sample * 7.2f, sample / 64f, 1, sample % 4,
                        sample % 3 == 0, sample % 7 == 0, false, bone, PlayerPoseOptions.WAVY_ARMS);
                assertArrayEquals(ordinary, wavy, 0f);
            }
        }
    }

    @Test void nativeUpsideDownNamesUseCapeVisibilityAndMinecraftFormatting() {
        assertTrue(PlayerPoseOptions.nativeUpsideDown("Dinnerbone", true));
        assertTrue(PlayerPoseOptions.nativeUpsideDown("Grumm", true));
        assertTrue(PlayerPoseOptions.nativeUpsideDown("\u00a7cDinnerbone\u00a7r", true));
        assertTrue(PlayerPoseOptions.nativeUpsideDown("\u00a7LGrumm", true));
        assertFalse(PlayerPoseOptions.nativeUpsideDown("Dinnerbone", false));
        assertFalse(PlayerPoseOptions.nativeUpsideDown("dinnerbone", true));
        assertFalse(PlayerPoseOptions.nativeUpsideDown("Dinnerbone2", true));
        assertFalse(PlayerPoseOptions.nativeUpsideDown("\u00a7zDinnerbone", true));
        assertFalse(PlayerPoseOptions.nativeUpsideDown(null, true));
    }

    private static float[] pose(float walk, float speed, float age, float swing, int left, int right,
                                boolean sneak, boolean riding, boolean bow, int bone, int options) {
        float[] out = new float[6];
        PlayerPoseMath.part(walk, speed, age, 35, -12, swing, left, right, sneak, riding, bow,
                bone, 0, bone == 2 || bone == 3 ? 2 : 0, 0, options, out);
        return out;
    }
}
