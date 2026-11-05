// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GpuVoxelSkinMathTest {
    private static final GpuVoxelSkinMath.Options OPTIONS = new GpuVoxelSkinMath.Options(1.15f, 1.05f, 1.18f, 1.1f, 1.1f);

    @Test void vertexConversionKeepsEveryCornerUvWindingAndOriginalPixelScale() {
        float[] quad = quad(); var offset = GpuVoxelSkinMath.offset("BODY", false, false, false, OPTIONS);
        int[] vertices = GpuVoxelSkinMath.vertices(quad, .0625f, offset);
        assertEquals(36, vertices.length);
        for (int corner = 0; corner < 4; corner++) {
            int source = 3 + corner * 5, output = corner * 9;
            for (int component = 0; component < 5; component++) assertEquals(Float.floatToRawIntBits(quad[source + component] * (component < 3 ? .0625f : 1)), vertices[output + component]);
            assertEquals(Float.floatToRawIntBits(1.15f / 1.05f), vertices[output + 5]);
            assertEquals(0, vertices[output + 6]); assertEquals(0, vertices[output + 7]); assertEquals(-1, vertices[output + 8]);
        }
    }

    @Test void dedicatedTemplateAppliesMeshOffsetInsteadOfCuboidOriginAndSize() {
        float[] template = new float[192]; int part = 10, at = part * 16;
        template[at] = 5; template[at + 1] = 2;
        template[at + 8] = -4; template[at + 9] = -8; template[at + 12] = 40; template[at + 13] = 120; template[at + 14] = 40;
        var offset = GpuVoxelSkinMath.offset("SLIM_ARM", true, false, false, OPTIONS);
        GpuVoxelSkinMath.applyTemplate(template, part, offset);
        assertEquals(5, template[at]); assertEquals(2, template[at + 1]);
        assertEquals(-1.15f * (.499f / 16f), template[at + 8]);
        assertEquals(1.035f * (-.1f / 16f), template[at + 9]);
        assertEquals(1.15f, template[at + 12]); assertEquals(1.035f, template[at + 13]); assertEquals(1.15f, template[at + 14]);
    }

    @Test void meshOffsetsMatchTheirOriginalOrderedAffineOperations() {
        var head = GpuVoxelSkinMath.offset("HEAD", false, false, false, OPTIONS);
        Matrix4f expectedHead = new Matrix4f().translate(0, -.25f, 0).scale(1.18f).translate(0, .25f - .04f, 0);
        assertTrue(expectedHead.equals(matrix(head), 1e-7f));
        var arm = GpuVoxelSkinMath.offset("ARM", true, false, false, OPTIONS);
        Matrix4f expectedArm = new Matrix4f().scale(1.15f, 1.035f, 1.15f).translate(-.998f / 16f, -.1f / 16f, 0);
        assertTrue(expectedArm.equals(matrix(arm), 1e-7f));
    }

    @Test void floatNormalMagnitudePreservesRescaledFixedFunctionLightingForRigidBones() {
        Random random = new Random(72027);
        for (String shape : new String[]{"HEAD", "BODY", "LEG", "ARM", "SLIM_ARM"}) {
            var offset = GpuVoxelSkinMath.offset(shape, true, false, false, OPTIONS);
            for (int pose = 0; pose < 100; pose++) {
                Matrix4f transform = new Matrix4f().rotateXYZ(random.nextFloat()*6, random.nextFloat()*6, random.nextFloat()*6)
                        .scale(-.9375f, -.9375f, .9375f).mul(matrix(offset));
                assertTrue(GpuVoxelSkinMath.normalTransformMatches(transform, offset));
                Matrix3f inverse = new Matrix3f(transform).invert(), normal = new Matrix3f(inverse).transpose();
                float rescale = 1 / inverse.getRow(2, new Vector3f()).length();
                for (int axis = 0; axis < 3; axis++) for (int sign : new int[]{-1, 1}) {
                    Vector3f original = new Vector3f(); original.setComponent(axis, sign);
                    Vector3f expected = normal.transform(new Vector3f(original)).mul(rescale);
                    float magnitude = axis == 0 ? offset.normalX() : axis == 1 ? offset.normalY() : offset.normalZ();
                    Vector3f actual = new Matrix3f(transform).transform(new Vector3f(original).mul(magnitude)).normalize().mul(magnitude);
                    assertTrue(expected.equals(actual, 2e-6f), expected + " != " + actual);
                }
            }
        }
    }

    @Test void unknownShearNonfiniteVerticesAndNormalsUseTheOriginalPath() {
        var offset = GpuVoxelSkinMath.offset("BODY", false, false, false, OPTIONS);
        Matrix4f shear = matrix(offset); shear.m10(.4f);
        assertFalse(GpuVoxelSkinMath.normalTransformMatches(shear, offset));
        float[] malformed = quad(); malformed[2] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> GpuVoxelSkinMath.vertices(malformed, .0625f, offset));
        float[] nonfinite = quad(); nonfinite[3] = Float.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, () -> GpuVoxelSkinMath.vertices(nonfinite, .0625f, offset));
        assertThrows(IllegalArgumentException.class, () -> GpuVoxelSkinMath.vertices(new float[24], .0625f, offset));
    }

    private static Matrix4f matrix(GpuVoxelSkinMath.Offset offset) { return new Matrix4f().translation(offset.x(), offset.y(), offset.z()).scale(offset.scaleX(), offset.scaleY(), offset.scaleZ()); }
    static float[] quad() { return new float[]{1,0,0, 1,2,3,0,.25f, 4,5,6,.5f,.25f, 7,8,9,.5f,.75f, 10,11,12,0,.75f}; }
}
